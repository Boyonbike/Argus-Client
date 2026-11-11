#!/usr/bin/env python3
"""Resolve Argus mixin targets against the 26.1.2 Minecraft jar.

Checks:
  * every @Mixin target class exists,
  * every explicit method = "name(descriptor)" / "name" exists,
  * every @At(target = "Lowner;name(descriptor)ret") member exists.

Usage: python3 tools/audit_mixins.py [--jar PATH]
"""
import argparse
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main/java"
DEFAULT_JAR = (
    pathlib.Path.home()
    / ".gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
    / "minecraft-merged-deobf/26.1.2/minecraft-merged-deobf-26.1.2.jar"
)

CLASS_RE = re.compile(r"class\s+(\w+)")
IMPORT_RE = re.compile(r"import\s+(?:static\s+)?([\w.]+);")
MIXIN_CLASS_RE = re.compile(r"@Mixin\(\s*([\w.$]+)\.class\s*\)")
MIXIN_TARGETS_RE = re.compile(r"@Mixin\([^)]*targets\s*=\s*\{?\s*\"([\w.$]+)\"")
METHOD_RE = re.compile(r"method\s*=\s*\"([^\"]+)\"")
AT_TARGET_RE = re.compile(r"target\s*=\s*\"L([^;]+);([^(;\"]+)(\([^\"]*)\"")


class Jar:
    def __init__(self, path: pathlib.Path):
        self.path = path
        self.cache = {}

    def members(self, name: str):
        if name in self.cache:
            return self.cache[name]
        proc = subprocess.run(
            ["javap", "-p", "-s", "-classpath", str(self.path), name],
            capture_output=True, text=True,
        )
        if proc.returncode != 0:
            self.cache[name] = None
            return None
        names, full, previous = set(), set(), None
        for line in proc.stdout.splitlines():
            line = line.strip()
            if line.startswith("descriptor:"):
                desc = line.split("descriptor:", 1)[1].strip()
                decl = (previous or "").rstrip(";")
                if "(" in decl:
                    name = decl.split("(", 1)[0].split()[-1].split(".")[-1]
                else:
                    name = decl.split()[-1] if decl else ""
                if name:
                    names.add(name)
                    full.add(name + desc)
                previous = None
                continue
            if line and not line.startswith(("Compiled from", "}", "{")):
                previous = line
        supers = []
        for line in proc.stdout.splitlines():
            line = line.strip()
            if " extends " in line or " implements " in line:
                rest = line
                if " extends " in rest:
                    rest = rest.split(" extends ", 1)[1]
                    for token in rest.replace(" implements ", ",").split(","):
                        token = token.strip().split("<", 1)[0].strip().rstrip("{")
                        if "." in token or token[:1].isupper():
                            supers.append(token)
        result = (names, full, supers)
        self.cache[name] = result
        return result


def resolve_class(path: pathlib.Path, simple: str) -> str:
    text = path.read_text()
    imports = IMPORT_RE.findall(text)
    if "." in simple:
        outer, rest = simple.split(".", 1)
        for imp in imports:
            if imp == outer or imp.endswith("." + outer):
                return imp + "$" + rest.replace(".", "$")
        return simple
    for imp in imports:
        if imp.endswith("." + simple):
            return imp
    # Nested imports appear as Outer.Inner; try converting the last dot to '$'.
    for imp in imports:
        parts = imp.split(".")
        for i in range(len(parts) - 1, 1, -1):
            cand = ".".join(parts[:i]) + "$" + "$".join(parts[i:])
            if cand.endswith("$" + simple):
                return cand
    return simple


def has_member(jar: Jar, owner: str, member: str, seen=None) -> bool:
    if member.startswith("<init>("):
        member = owner.split(".")[-1].split("$")[-1] + member[6:]
    seen = seen or set()
    if owner in seen:
        return False
    seen.add(owner)
    members = jar.members(owner)
    if members is None:
        return False
    names, full, supers = members
    if "(" in member:
        if member.split("(", 1)[0] in names and member in full:
            return True
    elif member in names:
        return True
    for sup in supers:
        if has_member(jar, sup, member, seen):
            return True
    return False


def is_mc(owner: str) -> bool:
    return owner.startswith(("net.minecraft", "com.mojang.blaze3d"))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", default=str(DEFAULT_JAR))
    args = parser.parse_args()
    jar = Jar(pathlib.Path(args.jar))
    if not jar.path.exists():
        print(f"jar not found: {jar.path}", file=sys.stderr)
        return 2

    errors = []
    files = sorted(SOURCE.rglob("*.java"))
    for path in files:
        text = path.read_text()
        rel = path.relative_to(ROOT)
        targets = []
        m = MIXIN_CLASS_RE.search(text)
        if m:
            targets.append(resolve_class(path, m.group(1)))
        targets += MIXIN_TARGETS_RE.findall(text)
        if not targets:
            continue
        for owner in targets:
            if not is_mc(owner):
                continue
            if jar.members(owner) is None:
                errors.append(f"{rel}: mixin target class missing: {owner}")
                continue
            for member in METHOD_RE.findall(text):
                if "*" in member or member == "<clinit>":
                    continue
                if member.startswith("lambda$") or member.startswith("<"):
                    # Synthetic members are generated by the target; only check they exist.
                    if not has_member(jar, owner, member) and member != "<init>":
                        errors.append(f"{rel}: method not found in {owner}: {member}")
                elif not has_member(jar, owner, member):
                    errors.append(f"{rel}: method not found in {owner}: {member}")
            for owner2, name, desc_plus in AT_TARGET_RE.findall(text):
                if not is_mc(owner2):
                    continue
                desc = desc_plus.rstrip('"')
                if desc and not desc.startswith("("):
                    continue
                member = name + desc
                if not has_member(jar, owner2, member):
                    errors.append(f"{rel}: @At member not found in {owner2}: {member}")

    for err in sorted(set(errors)):
        print(err)
    print(f"checked {len(files)} files, {len(set(errors))} problems")
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())

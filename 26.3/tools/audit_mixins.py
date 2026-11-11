#!/usr/bin/env python3
"""Resolve Argus mixin targets against the 26.3 Minecraft jar.

Checks:
  * every @Mixin target class exists,
  * every explicit method = "name(descriptor)" / "name" (or {"a", "b"}) exists,
  * every @At(target = "Lowner;name(descriptor)ret") method exists,
  * every @At(target = "Lowner;name:descriptor") field exists,
  * every injector's INVOKE/FIELD @At target is actually referenced from the
    bytecode of the method(s) it selects (catches calls that moved to another
    method; with "defaultRequire": 0 such injections are silently skipped),
  * every @Shadow / @Accessor / @Invoker member exists with the descriptor the
    Java declaration implies (falls back to a name check, reported as
    unverified, when a declared type cannot be resolved, e.g. type variables).

Usage: python3 tools/audit_mixins.py [--jar PATH] [--verbose]
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
    / "minecraft-merged-deobf/26.3/minecraft-merged-deobf-26.3.jar"
)

IMPORT_RE = re.compile(r"import\s+([\w.]+);")
PACKAGE_RE = re.compile(r"package\s+([\w.]+);")
MIXIN_CLASS_RE = re.compile(r"@Mixin\(\s*(?:value\s*=\s*)?\{?\s*([\w.$]+)\.class")
MIXIN_TARGETS_RE = re.compile(r"@Mixin\([^)]*targets\s*=\s*\{?\s*\"([\w.$/]+)\"")
METHOD_RE = re.compile(r"\bmethod\s*=\s*(\"[^\"]+\"|\{[^}]*\})", re.S)
STRING_RE = re.compile(r"\"([^\"]+)\"")
AT_METHOD_RE = re.compile(r"target\s*=\s*\"L([^;]+);([^(;:\"]+)(\([^\"]*)\"")
AT_FIELD_RE = re.compile(r"target\s*=\s*\"L([^;]+);([^(;:\"]+):([^\"]+)\"")
MEMBER_RE = re.compile(
    r"@(Shadow|Accessor|Invoker)\b(\([^)]*\))?"
    r"((?:\s*@[\w.]+(?:\([^)]*\))?)*)\s*"
    r"((?:(?:public|protected|private|final|abstract|static|transient|volatile|default|synchronized|native)\s+)*)"
    r"([^;{=]*?)\s*[;{=]",
    re.S,
)
COMMENT_RE = re.compile(r"//[^\n]*|/\*.*?\*/", re.S)
PARAM_ANNOTATION_RE = re.compile(r"@[\w.]+(?:\([^)]*\))?\s*")

PRIMITIVES = {
    "void": "V", "boolean": "Z", "byte": "B", "char": "C", "short": "S",
    "int": "I", "long": "J", "float": "F", "double": "D",
}


class Jar:
    def __init__(self, path: pathlib.Path):
        self.path = path
        self.cache = {}
        self.binary = {}

    def members(self, name: str):
        name = name.replace("/", ".")
        if name in self.cache:
            return self.cache[name]
        proc = subprocess.run(
            ["javap", "-p", "-s", "-classpath", str(self.path), name],
            capture_output=True, text=True,
        )
        if proc.returncode != 0:
            self.cache[name] = None
            return None
        names, full, previous, supers = set(), set(), None, []
        for line in proc.stdout.splitlines():
            line = line.strip()
            if line.startswith("descriptor:"):
                desc = line.split("descriptor:", 1)[1].strip()
                decl = (previous or "").rstrip(";")
                if "(" in decl:
                    member = decl.split("(", 1)[0].split()[-1].split(".")[-1]
                else:
                    member = decl.split()[-1] if decl else ""
                if member:
                    names.add(member)
                    full.add(member + desc)
                previous = None
                continue
            if line.endswith("{") and (" class " in f" {line}" or " interface " in f" {line}" or " enum " in f" {line}" or " record " in f" {line}"):
                header = re.sub(r"<[^{]*?>(?=\s|$|,|\{)", "", line.rstrip("{").strip())
                for keyword in (" extends ", " implements "):
                    if keyword in header:
                        rest = header.split(keyword, 1)[1]
                        rest = rest.split(" implements ", 1)[0] if keyword == " extends " else rest
                        for token in rest.split(","):
                            token = token.strip().split("<", 1)[0].strip()
                            if token:
                                supers.append(token)
                continue
            if line and not line.startswith(("Compiled from", "}", "{")):
                previous = line
        header = re.search(r"\b(?:class|interface|enum|record)\s+([\w.$]+)", proc.stdout)
        result = (names, full, supers)
        self.cache[name] = result
        self.binary[name] = header.group(1) if header else name
        return result

    def exists(self, name: str) -> bool:
        return self.members(name) is not None

    def binary_name(self, name: str):
        """Canonical binary name ('Outer$Inner') of a class javap can load, else None."""
        if self.members(name) is None:
            return None
        return self.binary[name.replace("/", ".")]


def strip_generics(text: str) -> str:
    out, depth = [], 0
    for ch in text:
        if ch == "<":
            depth += 1
        elif ch == ">":
            depth -= 1
        elif depth == 0:
            out.append(ch)
    return "".join(out)


def split_top_level(text: str):
    parts, depth, current = [], 0, []
    for ch in text:
        if ch in "<(":
            depth += 1
        elif ch in ">)":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append("".join(current))
            current = []
        else:
            current.append(ch)
    if "".join(current).strip():
        parts.append("".join(current))
    return parts


class SourceFile:
    def __init__(self, path: pathlib.Path, jar: Jar):
        self.path = path
        self.jar = jar
        self.raw = path.read_text()
        self.text = COMMENT_RE.sub("", self.raw)
        self.imports = IMPORT_RE.findall(self.text)
        package = PACKAGE_RE.search(self.text)
        self.package = package.group(1) if package else ""

    def _nested_variants(self, dotted: str):
        """Yield dotted, then forms with trailing segments joined by '$'."""
        parts = dotted.split(".")
        yield dotted
        for i in range(len(parts) - 1, 0, -1):
            yield ".".join(parts[:i]) + "$" + "$".join(parts[i:])

    def _first_existing(self, dotted: str):
        for candidate in self._nested_variants(dotted):
            binary = self.jar.binary_name(candidate)
            if binary:
                return binary
        return None

    def resolve_class(self, simple: str):
        """Resolve a source type name to a binary class name, or None."""
        simple = simple.strip()
        head = simple.split(".", 1)[0]
        if "." in simple and head[:1].islower():
            found = self._first_existing(simple)
            return found if found else simple  # fully qualified; trust it
        rest = simple.split(".", 1)[1] if "." in simple else ""
        for imp in self.imports:
            if imp == head or imp.endswith("." + head):
                dotted = imp + ("." + rest if rest else "")
                found = self._first_existing(dotted)
                if found:
                    return found
                # Library classes outside the jar/JDK: trust the import.
                return imp + ("$" + rest.replace(".", "$") if rest else "")
        for prefix in ("java.lang.", self.package + "." if self.package else ""):
            if prefix:
                found = self._first_existing(prefix + simple)
                if found:
                    return found
        return None

    def descriptor(self, type_text: str):
        text = PARAM_ANNOTATION_RE.sub("", strip_generics(type_text)).strip()
        text = re.sub(r"\bfinal\s+", "", text)
        dims = text.count("[]") + text.count("...")
        base = text.replace("[]", "").replace("...", "").strip()
        if base in PRIMITIVES:
            return "[" * dims + PRIMITIVES[base]
        binary = self.resolve_class(base)
        if binary is None:
            return None
        return "[" * dims + "L" + binary.replace(".", "/") + ";"


INJECTOR_RE = re.compile(
    r"@(Inject|Redirect|ModifyExpressionValue|WrapOperation|WrapWithCondition|ModifyArg|ModifyArgs|"
    r"ModifyVariable|ModifyReturnValue|ModifyConstant)\s*\(")
REF_RE = re.compile(r"//\s*(?:Method|InterfaceMethod|Field)\s+(?:([\w/$]+)\.)?([\w$<>]+):(\S+)")


def balanced(text: str, start: int) -> str:
    """Return text from the '(' at start to its matching ')'."""
    depth, in_string = 0, False
    for i in range(start, len(text)):
        ch = text[i]
        if ch == '"' and text[i - 1] != "\\":
            in_string = not in_string
        elif not in_string:
            if ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
                if depth == 0:
                    return text[start:i + 1]
    return text[start:]


class Bytecode:
    def __init__(self, jar_path: pathlib.Path):
        self.jar_path = jar_path
        self.cache = {}

    def methods(self, owner: str):
        """{name: [(descriptor, {referenced 'owner.name:desc'})]} for owner's own methods."""
        owner = owner.replace(".", "/")
        if owner in self.cache:
            return self.cache[owner]
        proc = subprocess.run(
            ["javap", "-c", "-p", "-s", "-classpath", str(self.jar_path), owner.replace("/", ".")],
            capture_output=True, text=True,
        )
        result = {}
        if proc.returncode == 0:
            simple = owner.split("/")[-1].split("$")[-1]
            previous, current = None, None
            for line in proc.stdout.splitlines():
                stripped = line.strip()
                if stripped.startswith("descriptor:") and previous and "(" in previous:
                    name = previous.split("(", 1)[0].split()[-1].split(".")[-1]
                    if name == simple or name.endswith("$" + simple):
                        name = "<init>"
                    if previous.startswith("static {}"):
                        name = "<clinit>"
                    current = {}
                    result.setdefault(name, []).append((stripped.split(":", 1)[1].strip(), current))
                    continue
                if stripped == "static {};":
                    previous = "static {}("
                    continue
                ref = REF_RE.search(stripped)
                if ref and current is not None:
                    ref_owner = ref.group(1) or owner
                    key = f"{ref_owner}.{ref.group(2)}:{ref.group(3)}"
                    current[key] = current.get(key, 0) + 1
                    continue
                if line.startswith("  ") and not line.startswith("   ") and stripped.endswith(";"):
                    previous, current = stripped, None
        self.cache[owner] = result
        return result


def check_injection_points(text: str, owner: str, code: Bytecode, rel, errors):
    methods = code.methods(owner)
    if not methods:
        return
    for match in INJECTOR_RE.finditer(text):
        body = balanced(text, match.end() - 1)
        selectors = []
        for group in METHOD_RE.findall(body):
            selectors += STRING_RE.findall(group)
        refs = []
        for at in re.finditer(r"@At\s*\(", body):
            at_body = balanced(body, at.end() - 1)
            ordinal = re.search(r"\bordinal\s*=\s*(\d+)", at_body)
            ordinal = int(ordinal.group(1)) if ordinal else None
            for o, n, d in AT_METHOD_RE.findall(at_body):
                refs.append((f"{o}.{n}:{d}", ordinal))
            for o, n, d in AT_FIELD_RE.findall(at_body):
                refs.append((f"{o}.{n}:{d}", ordinal))
        if not selectors or not refs:
            continue
        candidates = []
        for selector in selectors:
            name, _, desc = selector.partition("(")
            desc = "(" + desc if desc else ""
            for method_desc, method_refs in methods.get(name, []):
                if not desc or method_desc == desc:
                    candidates.append(method_refs)
        if not candidates:
            continue  # missing selector is reported elsewhere
        for ref, ordinal in refs:
            counts = [method_refs.get(ref, 0) for method_refs in candidates]
            if not any(counts):
                errors.append(f"{rel}: @At target not referenced by {owner}::{'/'.join(selectors)}: {ref}")
            elif ordinal is not None and max(counts) <= ordinal:
                errors.append(
                    f"{rel}: @At ordinal {ordinal} out of range in {owner}::{'/'.join(selectors)}"
                    f" (only {max(counts)} occurrence(s)): {ref}")


def find_owner_full(jar: Jar, owner: str, key: str, seen=None) -> bool:
    if key.startswith("<init>("):
        key = owner.replace("/", ".").split(".")[-1].split("$")[-1] + key[6:]
    seen = seen or set()
    if owner in seen:
        return False
    seen.add(owner)
    members = jar.members(owner)
    if members is None:
        return False
    names, full, supers = members
    if key in full:
        return True
    return any(find_owner_full(jar, sup, key, seen) for sup in supers)


def has_name(jar: Jar, owner: str, name: str, seen=None) -> bool:
    seen = seen or set()
    if owner in seen:
        return False
    seen.add(owner)
    members = jar.members(owner)
    if members is None:
        return False
    names, full, supers = members
    if name in names:
        return True
    return any(has_name(jar, sup, name, seen) for sup in supers)


def has_selector(jar: Jar, owner: str, selector: str) -> bool:
    if "(" in selector:
        return find_owner_full(jar, owner, selector)
    if selector == "<init>":
        return True
    return has_name(jar, owner, selector)


def is_mc(owner: str) -> bool:
    return owner.replace("/", ".").startswith(("net.minecraft", "com.mojang.blaze3d"))


def parse_declaration(decl: str):
    """Return (name, return_type, params or None for fields)."""
    decl = " ".join(decl.split())
    decl = re.sub(r"^<[^>]*>\s*", "", decl)  # method type parameters
    if "(" in decl:
        head, params = decl.split("(", 1)
        params = params.rsplit(")", 1)[0]
        tokens = head.strip().rsplit(" ", 1)
        if len(tokens) != 2:
            return None
        ret, name = tokens
        param_types = []
        for param in split_top_level(params):
            param = PARAM_ANNOTATION_RE.sub("", param).strip()
            param = re.sub(r"^final\s+", "", param)
            if not param:
                continue
            ptype = param.rsplit(" ", 1)[0] if " " in param else param
            param_types.append(ptype)
        return name, ret, param_types
    tokens = decl.strip().rsplit(" ", 1)
    if len(tokens) != 2:
        return None
    ftype, name = tokens
    return name, ftype, None


def inferred_name(kind: str, method_name: str, value: str):
    if value:
        return value
    name = method_name.split("$")[-1]
    prefixes = ("get", "is", "set") if kind == "Accessor" else ("call", "invoke", "new", "create")
    for prefix in prefixes:
        if name.startswith(prefix) and len(name) > len(prefix):
            rest = name[len(prefix):]
            return rest[0].lower() + rest[1:]
    return name


def check_members(src: SourceFile, owner: str, rel, errors, unverified):
    jar = src.jar
    for match in MEMBER_RE.finditer(src.text):
        kind, args, _, _, decl = match.groups()
        args = args or ""
        if "remap = false" in args or "remap=false" in args or "prefix" in args:
            continue
        parsed = parse_declaration(decl)
        if parsed is None:
            unverified.append(f"{rel}: could not parse @{kind} declaration: {decl.strip()}")
            continue
        name, ret, params = parsed
        value_match = STRING_RE.search(args)
        value = value_match.group(1) if value_match else ""
        if kind == "Shadow":
            target, desc_params, desc_ret = name, params, ret
        elif kind == "Accessor":
            target = inferred_name(kind, name, value)
            if params:  # setter: void setX(T value)
                desc_params, desc_ret = None, params[0]
            else:
                desc_params, desc_ret = None, ret
        else:  # Invoker
            target = inferred_name(kind, name, value)
            desc_params = params if params is not None else []
            desc_ret = "void" if target == "<init>" else ret

        if desc_params is None:  # field
            desc = src.descriptor(desc_ret)
            key = None if desc is None else target + desc
        else:
            pieces = [src.descriptor(p) for p in desc_params]
            ret_desc = src.descriptor(desc_ret)
            key = None if None in pieces or ret_desc is None else target + "(" + "".join(pieces) + ")" + ret_desc

        if key is None:
            unverified.append(f"{rel}: @{kind} {target}: unresolved type, name-only check")
            if target != "<init>" and not has_name(jar, owner, target):
                errors.append(f"{rel}: @{kind} member not found in {owner}: {target}")
            continue
        if not find_owner_full(jar, owner, key):
            hint = "" if (target == "<init>" or has_name(jar, owner, target)) else " (name missing too)"
            errors.append(f"{rel}: @{kind} descriptor mismatch in {owner}: {key}{hint}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", default=str(DEFAULT_JAR))
    parser.add_argument("--verbose", action="store_true", help="list name-only (unverified) checks")
    args = parser.parse_args()
    jar = Jar(pathlib.Path(args.jar))
    if not jar.path.exists():
        print(f"jar not found: {jar.path}", file=sys.stderr)
        return 2

    errors, unverified = [], []
    code = Bytecode(jar.path)
    files = sorted(SOURCE.rglob("*.java"))
    for path in files:
        src = SourceFile(path, jar)
        text = src.text
        rel = path.relative_to(ROOT)
        targets = []
        m = MIXIN_CLASS_RE.search(text)
        if m:
            resolved = src.resolve_class(m.group(1))
            targets.append(resolved or m.group(1))
        targets += [t.replace("/", ".") for t in MIXIN_TARGETS_RE.findall(text)]
        if not targets:
            continue
        for owner in targets:
            if not is_mc(owner):
                continue
            if jar.members(owner) is None:
                errors.append(f"{rel}: mixin target class missing: {owner}")
                continue
            check_members(src, owner, rel, errors, unverified)
            check_injection_points(text, owner, code, rel, errors)
            for group in METHOD_RE.findall(text):
                for selector in STRING_RE.findall(group):
                    if "*" in selector or selector == "<clinit>":
                        continue
                    if not has_selector(jar, owner, selector):
                        errors.append(f"{rel}: method not found in {owner}: {selector}")
        for owner2, name, desc in AT_METHOD_RE.findall(text):
            if is_mc(owner2) and not find_owner_full(jar, owner2, name + desc):
                errors.append(f"{rel}: @At method not found in {owner2}: {name}{desc}")
        for owner2, name, desc in AT_FIELD_RE.findall(text):
            if is_mc(owner2) and not find_owner_full(jar, owner2, name + desc):
                errors.append(f"{rel}: @At field not found in {owner2}: {name}:{desc}")

    for err in sorted(set(errors)):
        print(err)
    unverified = sorted(set(unverified))
    if args.verbose:
        for note in unverified:
            print("note:", note)
    print(f"checked {len(files)} files, {len(set(errors))} problems, {len(unverified)} name-only checks")
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())

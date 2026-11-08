import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Removes notification delivery from the supplied compiled client, including registration and
 * legacy aliases. Run with the cached ASM and ASM Tree jars on the class path:
 * java -cp asm.jar:asm-tree.jar:gson.jar tools/RemoveWebhook.java input.jar output.jar
 * Input and output must be different paths. Unrelated archive entries are copied unchanged.
 */
public final class RemoveWebhook implements Opcodes {
    private static boolean removed(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("webhook") || lower.contains("telegram") || lower.contains("discordnotifs");
    }

    private static AbstractInsnNode next(AbstractInsnNode node) {
        do { node = node.getNext(); } while (node != null && node.getOpcode() < 0);
        return node;
    }

    private static AbstractInsnNode previous(AbstractInsnNode node) {
        do { node = node.getPrevious(); } while (node != null && node.getOpcode() < 0);
        return node;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void removeThrough(MethodNode method, AbstractInsnNode start, AbstractInsnNode end) {
        require(start != null && end != null, "Incomplete removal range in " + method.name);
        AbstractInsnNode stop = end.getNext();
        while (start != stop) {
            AbstractInsnNode following = start.getNext();
            method.instructions.remove(start);
            start = following;
        }
    }

    private static AbstractInsnNode find(AbstractInsnNode start, java.util.function.Predicate<AbstractInsnNode> predicate) {
        for (AbstractInsnNode node = start; node != null; node = node.getNext()) {
            if (predicate.test(node)) return node;
        }
        throw new IllegalStateException("Expected registration instruction not found");
    }

    private static int removeRegistrations(ClassNode type) {
        int changes = 0;
        for (MethodNode method : type.methods) {
            for (AbstractInsnNode node : method.instructions.toArray()) {
                if (node instanceof TypeInsnNode creation && creation.getOpcode() == NEW) {
                    if (creation.desc.endsWith("/managers/WebhookManager")) {
                        AbstractInsnNode end = find(node, n -> n instanceof FieldInsnNode field
                                && field.getOpcode() == PUTSTATIC && field.name.equals("webhookManager"));
                        removeThrough(method, node, end);
                        changes++;
                    } else if (creation.desc.endsWith("/commands/WebhookCommand")) {
                        AbstractInsnNode start = previous(node);
                        require(start.getOpcode() == ALOAD, "Unexpected command registration");
                        AbstractInsnNode registration = find(node, n -> n instanceof MethodInsnNode call
                                && call.name.equals("registerCommand"));
                        AbstractInsnNode end = next(registration);
                        require(end.getOpcode() == POP, "Unexpected command registration result");
                        removeThrough(method, start, end);
                        changes++;
                    } else if (creation.desc.endsWith("/modules/misc/WebhookNotifsModule")) {
                        AbstractInsnNode start = previous(node);
                        require(start.getOpcode() == ALOAD, "Unexpected module registration");
                        AbstractInsnNode registration = find(node, n -> n instanceof MethodInsnNode call
                                && call.name.equals("registerModule"));
                        AbstractInsnNode branch = next(registration);
                        require(branch instanceof JumpInsnNode && branch.getOpcode() == IFNE,
                                "Unexpected module registration assertion");
                        LabelNode end = ((JumpInsnNode) branch).label;
                        removeThrough(method, start, end.getPrevious());
                        changes++;
                    }
                } else if (node instanceof FieldInsnNode field && field.getOpcode() == GETSTATIC
                        && field.name.equals("webhookManager")) {
                    AbstractInsnNode start = previous(node), end = next(node);
                    require(start.getOpcode() == ALOAD && end instanceof MethodInsnNode call
                            && call.name.equals("register") && call.desc.endsWith(")V"),
                            "Unexpected configurable registration");
                    removeThrough(method, start, end);
                    changes++;
                }
            }
        }
        return changes;
    }

    private static Integer integer(AbstractInsnNode node) {
        int opcode = node.getOpcode();
        if (opcode >= ICONST_M1 && opcode <= ICONST_5) return opcode - ICONST_0;
        if (node instanceof IntInsnNode value) return value.operand;
        if (node instanceof LdcInsnNode value && value.cst instanceof Integer i) return i;
        return null;
    }

    /** Remove obsolete cases from javac's string switches and its migration allow-list. */
    private static void removeLegacyAliases(ClassNode type) {
        for (MethodNode method : type.methods) {
            Set<Integer> removedCases = new HashSet<>();
            for (AbstractInsnNode node : method.instructions.toArray()) {
                if (!(node instanceof LdcInsnNode value) || !(value.cst instanceof String text) || !removed(text)) continue;
                AbstractInsnNode following = next(node);
                boolean constantFirst = following instanceof VarInsnNode variable && variable.getOpcode() == ALOAD;
                AbstractInsnNode equals = constantFirst ? next(following) : following;
                AbstractInsnNode branch = equals == null ? null : next(equals);
                if (!(equals instanceof MethodInsnNode call) || !call.owner.equals("java/lang/String")
                        || !call.name.equals("equals")) continue; // Return values become unreachable below.
                require(branch instanceof JumpInsnNode, "Unexpected legacy alias comparison");
                AbstractInsnNode start = constantFirst ? node : previous(node);
                require(constantFirst || start.getOpcode() == ALOAD, "Unexpected legacy alias input");
                if (branch.getOpcode() == IFEQ) {
                    AbstractInsnNode ordinal = next(branch);
                    Integer index = integer(ordinal);
                    if (index != null && next(ordinal) instanceof VarInsnNode store && store.getOpcode() == ISTORE)
                        removedCases.add(index);
                    method.instructions.insertBefore(start, new JumpInsnNode(GOTO, ((JumpInsnNode) branch).label));
                } else require(branch.getOpcode() == IFNE, "Unexpected legacy alias branch");
                removeThrough(method, start, branch);
            }
            for (AbstractInsnNode node : method.instructions) {
                if (node instanceof TableSwitchInsnNode table) {
                    for (int index : removedCases) {
                        if (index >= table.min && index <= table.max) table.labels.set(index - table.min, table.dflt);
                    }
                }
            }
        }
    }

    private static byte[] transform(byte[] input, String name) {
        ClassNode type = new ClassNode();
        new ClassReader(input).accept(type, 0);
        boolean aliases = name.endsWith("/config/LegacyBranding.class");
        if (name.endsWith("/ClientContext.class")) {
            require(type.fields.removeIf(field -> field.name.equals("webhookManager")), "Missing manager field");
        } else if (aliases) {
            removeLegacyAliases(type);
        } else {
            require(removeRegistrations(type) == 1, "Expected one notification registration in " + name);
        }
        // Recompute legacy switch frames, eliminating unreachable cases and their constants.
        // Registration ranges enter and leave with an empty stack, so their existing frames remain valid.
        ClassWriter writer = new ClassWriter(aliases ? ClassWriter.COMPUTE_FRAMES : 0);
        type.accept(writer);
        byte[] output = writer.toByteArray();
        if (aliases) {
            // Frame computation erases unreachable instructions after visiting their constants.
            // Rebuild the pool once more so those unused alias strings are removed as well.
            ClassWriter compact = new ClassWriter(0);
            new ClassReader(output).accept(compact, 0);
            output = compact.toByteArray();
        }
        require(!removed(new String(output, StandardCharsets.ISO_8859_1)), "Notification reference remains in " + name);
        return output;
    }

    public static void main(String[] args) throws Exception {
        require(args.length == 2, "Usage: RemoveWebhook input.jar output.jar");
        Path input = Path.of(args[0]), output = Path.of(args[1]);
        require(!input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize()), "Use distinct paths");
        int deleted = 0, rewritten = 0;
        try (ZipFile source = new ZipFile(input.toFile()); ZipOutputStream target = new ZipOutputStream(Files.newOutputStream(output))) {
            target.setComment(source.getComment());
            for (ZipEntry entry : Collections.list(source.entries())) {
                String name = entry.getName();
                if (removed(name)) { deleted++; continue; }
                byte[] data = source.getInputStream(entry).readAllBytes();
                if (name.endsWith(".class") && removed(new String(data, StandardCharsets.ISO_8859_1))) {
                    data = transform(data, name);
                    rewritten++;
                } else if (name.endsWith(".ini")) {
                    String text = new String(data, StandardCharsets.UTF_8);
                    if (removed(text)) {
                        data = text.lines().filter(line -> !removed(line)).collect(java.util.stream.Collectors.joining("\n", "", "\n"))
                                .getBytes(StandardCharsets.UTF_8);
                        rewritten++;
                    }
                }
                if (name.endsWith(".class") || name.endsWith(".ini") || name.endsWith(".json"))
                    require(!removed(new String(data, StandardCharsets.ISO_8859_1)), "Unhandled reference in " + name);
                ZipEntry copy = new ZipEntry(name);
                copy.setTime(entry.getTime());
                target.putNextEntry(copy);
                target.write(data);
                target.closeEntry();
            }
        }
        require(deleted >= 3 && rewritten >= 5, "Input did not contain the expected notification implementation");
        System.out.printf("Removed %d entries and cleaned %d connected classes/resources in %s%n", deleted, rewritten, output);
    }
}

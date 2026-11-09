import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Replaces everything inherited from Zio/Mio in the precompiled base client with Argus Client:
 * Zio project links (Announcer messages, crash report footer, project URL), the AutoSign default
 * sign, the mod metadata author/contact, and the internal GUI projection label. LegacyBranding's
 * migration of old Mio/Zio defaults is removed entirely.
 *
 * Run from the project directory (ASM jars from the Gradle cache on the class path):
 *   java -cp asm.jar:asm-tree.jar:gson.jar tools/RebrandLegacyText.java libs/client-base.jar out.jar
 */
public final class RebrandLegacyText implements Opcodes {
    private static final String SIGN = "dev/argus/client/modules/misc/AutoSignModule.class";
    private static final String LEGACY = "dev/argus/client/config/LegacyBranding.class";
    private static final String PROJECTION = "dev/argus/client/render/GuiProjectionUtils.class";

    private static final String URL = "https://github.com/Boyonbike/Argus-Client";
    private static final String[][] LINKS = {
            {"https://github.com/spectrumdevs/zio", URL},
            {"https://github.com/Cypphi/open-zio", URL},
    };

    private static String relink(String value) {
        for (String[] link : LINKS) value = value.replace(link[0], link[1]);
        return value;
    }

    /** Rewrites Zio links in string constants and constant field values; returns how many changed. */
    private static int relink(ClassNode type) {
        int changed = 0;
        for (FieldNode field : type.fields)
            if (field.value instanceof String text && !relink(text).equals(text)) { field.value = relink(text); changed++; }
        for (MethodNode method : type.methods)
            for (AbstractInsnNode insn : method.instructions)
                if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String text && !relink(text).equals(text)) { ldc.cst = relink(text); changed++; }
        return changed;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static List<LdcInsnNode> constants(ClassNode type, String value) {
        List<LdcInsnNode> found = new ArrayList<>();
        for (MethodNode method : type.methods)
            for (AbstractInsnNode insn : method.instructions)
                if (insn instanceof LdcInsnNode ldc && value.equals(ldc.cst)) found.add(ldc);
        return found;
    }

    private static LdcInsnNode replaceOnce(ClassNode type, String from, String to) {
        List<LdcInsnNode> found = constants(type, from);
        require(found.size() == 1, "Expected one \"" + from + "\" in " + type.name + ", found " + found.size());
        found.get(0).cst = to;
        return found.get(0);
    }

    private static AbstractInsnNode next(AbstractInsnNode node) {
        do { node = node.getNext(); } while (node != null && node.getOpcode() < 0);
        return node;
    }

    private static AbstractInsnNode previous(AbstractInsnNode node) {
        do { node = node.getPrevious(); } while (node != null && node.getOpcode() < 0);
        return node;
    }

    private static void rebrandSign(ClassNode type) {
        // Default sign: "get Argus Client" / "at github.com/" / "Boyonbike/" / "Argus-Client"
        replaceOnce(type, "at", "at github.com/");
        replaceOnce(type, "github.com/", "Boyonbike/");
        replaceOnce(type, "spectrumdevs/zio", "Argus-Client");
    }

    /**
     * Removes the Watermark, Welcomer and AutoSign cases from LegacyBranding.migrateModule. Those
     * cases only rewrote old Mio/Zio defaults; they are routed to the switch's no-op default so the
     * old text and the code that matched it are dropped from the class.
     */
    private static void rebrandLegacy(ClassNode type) {
        MethodNode migrate = type.methods.stream().filter(m -> m.name.equals("migrateModule")).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing migrateModule"));
        TableSwitchInsnNode cases = null;
        for (AbstractInsnNode insn : migrate.instructions)
            if (insn instanceof TableSwitchInsnNode table) { require(cases == null, "Several migration switches"); cases = table; }
        require(cases != null && cases.min == 0 && cases.max == 7, "Unexpected migration switch");
        // Case order follows the name checks: AutoFarm, EntityControl, FeetPlace, Watermark, Welcomer, AutoSign.
        for (int index = 3; index <= 5; index++) cases.labels.set(index, cases.dflt);
    }

    private static final String[] OLD = {"zio", "spectrumdevs", "cypphi", "mio"};

    private static boolean mentionsZio(byte[] data) {
        String text = new String(data, java.nio.charset.StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
        return text.contains("zio") || text.contains("spectrumdevs") || text.contains("cypphi/");
    }

    private static boolean mentionsOld(byte[] data) {
        String text = new String(data, java.nio.charset.StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
        for (String old : OLD) if (text.contains(old)) return true;
        return false;
    }

    /** Credits Argus Client in the mod metadata instead of the original project's author and Discord. */
    private static byte[] rebrandMetadata(byte[] data) {
        com.google.gson.JsonObject metadata = com.google.gson.JsonParser.parseString(
                new String(data, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        com.google.gson.JsonArray authors = new com.google.gson.JsonArray();
        authors.add("Boyonbike");
        metadata.add("authors", authors);
        com.google.gson.JsonObject contact = new com.google.gson.JsonObject();
        contact.addProperty("homepage", URL);
        contact.addProperty("sources", URL);
        metadata.add("contact", contact);
        byte[] output = (new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                .toJson(metadata) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        require(!mentionsOld(output) && !new String(output).contains("discord.com/invite"), "Old project in fabric.mod.json");
        return output;
    }

    private static byte[] transform(byte[] data, String name) {
        ClassNode type = new ClassNode();
        new ClassReader(data).accept(type, 0);
        int links = relink(type);
        if (name.equals(SIGN)) rebrandSign(type);
        else if (name.equals(LEGACY)) rebrandLegacy(type);
        else if (name.equals(PROJECTION)) replaceOnce(type, "gui_mio", "gui_argus");
        else require(links > 0, "Zio text in " + name + " is not a known link");
        // A fresh writer rebuilds the constant pool, so the replaced strings are dropped entirely.
        boolean legacy = name.equals(LEGACY);
        ClassWriter writer = new ClassWriter(legacy ? ClassWriter.COMPUTE_FRAMES : 0);
        type.accept(writer);
        byte[] output = writer.toByteArray();
        if (legacy) {
            // Frame computation erases the now-unreachable cases after visiting their constants.
            // Rebuild the pool once more so that old text is removed as well.
            ClassWriter compact = new ClassWriter(0);
            new ClassReader(output).accept(compact, 0);
            output = compact.toByteArray();
        }
        require(!mentionsOld(output), "Old branding remains in " + name);
        return output;
    }

    public static void main(String[] args) throws Exception {
        require(args.length == 2, "Usage: RebrandLegacyText input.jar output.jar");
        Path input = Path.of(args[0]), output = Path.of(args[1]);
        require(!input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize()), "Use distinct paths");
        int rewritten = 0, dropped = 0;
        try (ZipFile source = new ZipFile(input.toFile()); ZipOutputStream target = new ZipOutputStream(Files.newOutputStream(output))) {
            target.setComment(source.getComment());
            for (ZipEntry entry : Collections.list(source.entries())) {
                String name = entry.getName();
                byte[] data = source.getInputStream(entry).readAllBytes();
                // The project's own source replaces this class; drop the base copy and its old folder names.
                if (name.startsWith("dev/argus/client/config/ClientDataDirectory")) { dropped++; continue; }
                if (name.equals("fabric.mod.json")) data = rebrandMetadata(data);
                else if (name.endsWith(".class") && (name.equals(SIGN) || name.equals(LEGACY) || name.equals(PROJECTION) || mentionsZio(data))) {
                    data = transform(data, name);
                    rewritten++;
                }
                ZipEntry copy = new ZipEntry(name);
                copy.setTime(entry.getTime());
                target.putNextEntry(copy);
                target.write(data);
                target.closeEntry();
            }
        }
        require(rewritten >= 2, "Expected to rewrite the sign and legacy classes, rewrote " + rewritten);
        System.out.println("Rewrote " + rewritten + " classes, dropped " + dropped);
    }
}

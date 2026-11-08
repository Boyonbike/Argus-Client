package dev.argus.client.gui;
import java.util.*;
import java.util.zip.ZipFile;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import static org.junit.jupiter.api.Assertions.*;
final class GuiEntrypointTest {
    @Test void highDensityInputAccessorTargetsTheActualSelectionField() throws Exception {
        try(var source=getClass().getResourceAsStream("/net/minecraft/client/gui/components/EditBox.class")){
            assertNotNull(source);var type=new ClassNode();new ClassReader(source).accept(type,0);
            assertTrue(type.fields.stream().anyMatch(field->field.name.equals("highlightPos")&&field.desc.equals("I")));
        }
    }
    @Test void guiBindIntegrationTargetsRealCoreMethodsAndHasRegisteredMixin() throws Exception {
        try(var resource=getClass().getResourceAsStream("/argusclient.gui.mixins.json")){
            assertNotNull(resource,"The GUI bind integration needs a packaged Mixin configuration");
            assertTrue(new String(resource.readAllBytes(),StandardCharsets.UTF_8).contains("UIModuleMixin"));
        }
        try(var jar=new ZipFile("libs/client-base.jar")){
            var node=new ClassNode();new ClassReader(jar.getInputStream(jar.getEntry("dev/argus/client/modules/client/UIModule.class"))).accept(node,0);
            Set<String> names=new HashSet<>();node.methods.forEach(method->names.add(method.name));
            assertTrue(names.containsAll(Set.of("onEnable","onDisable","onLocalPlayerTick")),"The integration must use actual core lifecycle methods");
        }
    }
}

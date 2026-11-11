package dev.argus.client.gui;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;
final class InlineSearchTest {
    @Test void rightShiftMenuOwnsTheSearchInputInsteadOfANavigationTab() throws Exception {
        var modules=new ClassNode();
        try(var input=getClass().getResourceAsStream("/dev/argus/client/gui/ModulesScreen.class")){new ClassReader(input).accept(modules,0);}
        assertTrue(modules.fields.stream().anyMatch(field->field.desc.equals("Ldev/argus/client/gui/UiInput;")),"Search input must live in the dropdown menu screen");
        var screen=new ClassNode();
        try(var input=getClass().getResourceAsStream("/dev/argus/client/gui/UiScreen.class")){new ClassReader(input).accept(screen,0);}
        for(var method:screen.methods)if(method.name.equals("init"))for(var instruction:method.instructions)
            if(instruction instanceof LdcInsnNode constant)assertNotEquals("Search",constant.cst,"Search must not be a top navigation tab");
    }
}

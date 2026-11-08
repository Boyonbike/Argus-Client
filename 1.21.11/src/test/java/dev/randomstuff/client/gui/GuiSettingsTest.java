package dev.randomstuff.client.gui;
import dev.randomstuff.client.settings.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
final class GuiSettingsTest {
    private static final class Flag extends Setting<Boolean> {
        Flag(String name,java.util.function.Predicate<Boolean> predicate){super(name,false,predicate);}
        @Override public void parseValue(String text){setValue(Boolean.parseBoolean(text));}
        public void fromJson(com.google.gson.JsonElement value){setValue(value.getAsBoolean());}
        public com.google.gson.JsonElement toJson(){return new com.google.gson.JsonPrimitive(getValue());}
    }
    private static final class Names extends SetSetting<String> {
        Names(){super("Names","foo","bar");}
        public void fromJson(com.google.gson.JsonElement value){throw new UnsupportedOperationException();}
        public String parseEntry(String text){return text.isBlank()?null:text;}
        public String formatEntry(String value){return value;}
        public Collection<String> getAvailableEntries(){return List.of("foo","bar","baz");}
    }
    @Test void keepsExpandableGroupsAndRevealsTheirChildrenForSearch(){
        var group=new Flag("Colours",null);group.markAsGroup();
        var child=new Flag("Accent",ignored->group.isExpanded());List<Setting<?>> settings=List.of(group,child);
        assertEquals(List.of(group),GuiSettings.rows(settings,null));
        GuiSettings.expandGroups(settings);assertTrue(GuiSettings.rows(settings,"Accent").contains(child));
    }
    @Test void aConditionallyHiddenSearchTargetRemainsDiscoverableWithoutChangingValues(){
        var hidden=new Flag("Advanced",ignored->false);
        assertTrue(GuiSettings.rows(List.of(hidden),null).isEmpty());
        assertEquals(List.of(hidden),GuiSettings.rows(List.of(hidden),"Advanced"));
        assertFalse(hidden.isVisible());
    }
    @Test void expandsDependencyControlsWithoutChangingTheirValues(){
        var parent=new Flag("Hover",null);parent.markVisibilityDependency();
        var child=new Flag("Volume",null);child.dependsOn(parent);
        List<Setting<?>> settings=List.of(parent,child);
        assertFalse(child.isVisible());GuiSettings.expandGroups(settings);
        assertTrue(child.isVisible());assertFalse(parent.getValue());
    }
    @Test void collectionEditingStartsBlankAndChangesOnlyTheChosenEntry(){
        var names=new Names();assertEquals("",GuiSettings.editText(names));
        GuiSettings.toggleCollectionEntry(names,"foo");assertEquals(Set.of("bar"),names.getValue());
        GuiSettings.toggleCollectionEntry(names,"baz");assertEquals(Set.of("bar","baz"),names.getValue());
    }
}

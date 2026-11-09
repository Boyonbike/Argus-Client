package dev.argus.client.config;

import com.google.gson.JsonElement;
import dev.argus.client.managers.PresetRegistry;
import dev.argus.client.settings.options.PresetCategory;
import java.io.IOException;

public sealed interface PresetUndoAction permits PresetUndoAction.Load, PresetUndoAction.Save, PresetUndoAction.Delete, PresetUndoAction.Rename {
   void undo() throws IOException;

   static PresetUndoAction beforeLoad(String name) {
      return new PresetUndoAction.Load(name, PresetCategory.ALL.toJson());
   }

   static PresetUndoAction beforeSave(PresetRegistry registry, String name) {
      return new PresetUndoAction.Save(registry, name, registry.getPreset(name));
   }

   static PresetUndoAction beforeDelete(PresetRegistry registry, String name) {
      PresetData preset = registry.getPreset(name);
      return preset == null ? null : new PresetUndoAction.Delete(registry, preset);
   }

   static PresetUndoAction beforeRename(PresetRegistry registry, String oldName, String newName) {
      return new PresetUndoAction.Rename(registry, oldName, newName, registry.getPreset(newName), registry.getPreset(oldName));
   }

   record Delete(PresetRegistry registry, PresetData preset) implements PresetUndoAction {
      @Override
      public void undo() throws IOException {
         this.registry.restorePreset(this.preset);
      }
   }

   record Load(String name, JsonElement snapshot) implements PresetUndoAction {
      @Override
      public void undo() {
         PresetCategory.ALL.fromJson(this.snapshot);
      }
   }

   record Rename(PresetRegistry registry, String oldName, String newName, PresetData previousTarget, PresetData source) implements PresetUndoAction {
      @Override
      public void undo() throws IOException {
         this.registry.renamePresetForUndo(this.newName, this.oldName, this.previousTarget, this.source);
      }
   }

   record Save(PresetRegistry registry, String name, PresetData previous) implements PresetUndoAction {
      @Override
      public void undo() throws IOException {
         if (this.previous == null) {
            this.registry.deletePresetForUndo(this.name);
         } else {
            this.registry.restorePreset(this.previous, this.name);
         }
      }
   }
}

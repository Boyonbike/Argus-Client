package dev.argus.client.modules.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientContext;
import dev.argus.client.gui.HudGrid;
import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.modules.client.HUDModule;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.HudWidgetRenderer;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Mth;

/**
 * Text rows stacked into one HUD element. A {@link #splittable()} element also lets each of its lines be pulled out and
 * placed on its own in the HUD editor. The element then becomes a frame holding "slots": one for the lines still
 * stacked, and one per pulled-out line. Slots are kept in GUI units relative to the frame's top-left corner, and a
 * line grows away from the screen edge it is nearer, so a line hiding or changing width never shifts the others.
 * Lines are tracked by identity (rows may be re-sorted) and saved under {@link HudTextRow#key()} or their index.
 */
public class HudTextListRenderer extends HudWidgetRenderer {
   private static final HUDModule hudSettings = ClientContext.getModule(HUDModule.class);
   /** Width given to a pulled-out line in the editor while it has nothing to show, so it can still be grabbed. */
   private static final float PLACEHOLDER_WIDTH = 24.0F;
   private final List<? extends HudTextRow> rows;
   private boolean splittable;
   /** Frame size while split, and the slot {x, y, w, h} of the stack and of each pulled-out line. */
   private float frameW, frameH;
   private float[] stackSlot;
   private final Map<HudTextRow, float[]> rowSlots = new IdentityHashMap<>();
   /** Slots read from a config before the rows they belong to existed, by save key. */
   private final Map<String, float[]> pendingSlots = new HashMap<>();
   /** Last drawn rect {x, y, w, h} of each line, and of the stack, in GUI coordinates. */
   private final Map<HudTextRow, float[]> drawnRows = new IdentityHashMap<>();
   private float[] drawnStack;
   private float stackW, stackH;

   public HudTextListRenderer(HudWidgetModule widget, HudTextRow row) {
      this(widget, Collections.singletonList(row));
   }

   public HudTextListRenderer(HudWidgetModule widget, List<? extends HudTextRow> rows) {
      super(widget);
      this.rows = rows;
      this.markAlignsContent();
   }

   /** Lets each line be moved on its own in the HUD editor. */
   public HudTextListRenderer splittable() {
      this.splittable = true;
      return this;
   }

   public boolean isSplittable() {
      return this.splittable && this.rows.size() > 1;
   }

   /** Whether any line has been pulled out of the stack. */
   public boolean isSplit() {
      return !this.rowSlots.isEmpty();
   }

   public boolean isFree(HudTextRow row) {
      return this.rowSlots.containsKey(row);
   }

   private float scale() {
      return hudSettings.getTextScale();
   }

   private float rowHeight() {
      return this.getHeight() * this.scale();
   }

   /** Width of a pulled-out line's text, or a placeholder in the editor while it is empty. */
   private float freeRowWidth(HudTextRow row) {
      float width = row.getVisibility() > 0.0F ? FontRenderer.INSTANCE.getWidth(row.getText().getString()) * this.scale() : 0.0F;
      return width <= 0.0F && !renderingHud ? PLACEHOLDER_WIDTH : width;
   }

   private String keyOf(HudTextRow row) {
      if (row.key() != null) return row.key();
      int index = 0;
      for (HudTextRow candidate : this.rows) {
         if (candidate == row) return "#" + index;
         index++;
      }
      return null;
   }

   /** Attaches slots loaded before their rows existed (Metrics builds its rows when first switched on). */
   private void resolvePending() {
      if (this.pendingSlots.isEmpty() || this.rows.isEmpty()) return;
      for (HudTextRow row : this.rows) {
         float[] slot = this.pendingSlots.remove(this.keyOf(row));
         if (slot != null) this.rowSlots.put(row, slot);
      }
      this.pendingSlots.clear();
      if (this.rowSlots.isEmpty()) this.stackSlot = null;
   }

   @Override
   public float[] calculateDimensions() {
      this.resolvePending();
      // Rows a module dropped (rebuilt lists) lose their slot.
      if (!this.rowSlots.isEmpty()) this.rowSlots.keySet().removeIf(row -> !this.rows.contains(row));
      float height = 0.0F;
      float width = 0.0F;
      boolean split = this.isSplit();

      for (HudTextRow row : this.rows) {
         row.updateVisibility();
         if (split && this.rowSlots.containsKey(row)) continue;
         if (row.getVisibility() != 0.0F) {
            float easedVisibility = eased(row.getVisibility());
            float textWidth = FontRenderer.INSTANCE.getWidth(row.getText().getString());
            height += this.getHeight() * easedVisibility + 1.0F;
            if (textWidth > width) {
               width = textWidth;
            }
         }
      }

      if (!renderingHud && width == 0.0F && !split) {
         width = 50.0F;
         height = this.getHeight();
      }

      width *= this.scale();
      height *= this.scale();
      this.stackW = width;
      this.stackH = height;
      return split ? new float[]{this.frameW, this.frameH} : new float[]{width, height};
   }

   private static float eased(float visibility) {
      return (float)Mth.clamp(1.0 - Math.pow(1.0F - visibility, 4.0), 0.0, 1.0);
   }

   @Override
   public void render(PoseStack poses, double mouseX, double mouseY) {
      super.render(poses, mouseX, mouseY);
      this.drawnRows.clear();
      if (!this.isSplit()) {
         float[] size = this.getDimensions();
         this.drawnStack = new float[]{this.getX(), this.getY(), size[0], size[1]};
         this.renderStack(poses, this.getX(), this.getY(), size[0], size[1]);
         return;
      }

      float x = this.getX();
      float y = this.getY();
      boolean anyStacked = false;
      for (HudTextRow row : this.rows) anyStacked |= !this.rowSlots.containsKey(row);
      if (anyStacked && this.stackSlot != null) {
         float sx = x + this.stackSlot[0];
         float sy = y + this.stackSlot[1];
         this.drawnStack = new float[]{sx, sy, this.stackSlot[2], this.stackSlot[3]};
         this.renderStack(poses, sx, sy, this.stackSlot[2], this.stackSlot[3]);
      } else {
         this.drawnStack = null;
      }

      float scale = this.scale();
      for (HudTextRow row : this.rows) {
         float[] slot = this.rowSlots.get(row);
         if (slot == null) continue;
         float width = this.freeRowWidth(row);
         float rowX = this.getHorizontalDirection() == -1 ? x + slot[0] + slot[2] - width : x + slot[0];
         float rowY = this.getVerticalDirection() == -1 ? y + slot[1] + slot[3] - this.rowHeight() : y + slot[1];
         this.drawnRows.put(row, new float[]{rowX, rowY, width, this.rowHeight()});
         if (row.getVisibility() == 0.0F) continue;
         float animatedWidth = row.widthAnimation.updateAndGet() * scale;
         float textX = this.getHorizontalDirection() == -1 ? rowX + width - animatedWidth : rowX;
         poses.pushPose();
         poses.translate(textX, rowY, 0.0F);
         poses.scale(scale, scale, 1.0F);
         row.renderRow(poses, 0.0F, 0.0F, this.getRowColor(rowY, row), hudSettings.shouldDrawTextShadow());
         poses.popPose();
      }
   }

   /** Draws the stacked lines inside a box, growing away from the nearer screen edges like an unsplit element. */
   private void renderStack(PoseStack poses, float boxX, float boxY, float boxW, float boxH) {
      float scale = this.scale();
      float rowY = this.getVerticalDirection() == -1 ? boxY + boxH - this.rowHeight() : boxY;

      for (HudTextRow row : this.rows) {
         if (this.rowSlots.containsKey(row)) continue;
         float easedVisibility = row.getVisibility();
         if (easedVisibility != 0.0F) {
            poses.pushPose();
            easedVisibility = eased(easedVisibility);
            Color rowColor = this.getRowColor(rowY, row);
            float animatedWidth = row.widthAnimation.updateAndGet() * scale;
            float hiddenWidth = animatedWidth * (1.0F - easedVisibility);
            float contentX = this.getHorizontalDirection() == -1 ? boxX + boxW - animatedWidth : boxX;
            this.drawnRows.put(row, new float[]{contentX, rowY, Math.max(animatedWidth, 4.0F), this.rowHeight()});
            poses.translate(contentX - hiddenWidth * this.getHorizontalDirection(), rowY, 0.0F);
            poses.scale(scale, scale, 1.0F);
            row.renderRow(poses, 0.0F, 0.0F, rowColor, hudSettings.shouldDrawTextShadow());
            rowY += (this.getHeight() * easedVisibility + 1.0F) * this.getVerticalDirection() * scale;
            poses.popPose();
         }
      }
   }

   public Color getRowColor(float rowPosition, HudTextRow row) {
      return this.widget.getHudColor(rowPosition);
   }

   public List<? extends HudTextRow> getRows() {
      return this.rows;
   }

   // ---- editing: lines as their own pieces ------------------------------------------------------

   /** A line the editor can grab, with its last drawn rect in GUI coordinates. */
   public record Line(HudTextRow row, boolean free, float x, float y, float w, float h) {
      public boolean contains(double px, double py) {
         return px >= this.x && px <= this.x + Math.max(this.w, 4.0F) && py >= this.y && py <= this.y + Math.max(this.h, 4.0F);
      }
   }

   /** Lines drawn last frame, in row order. */
   public List<Line> lines() {
      List<Line> result = new ArrayList<>();
      for (HudTextRow row : this.rows) {
         float[] rect = this.drawnRows.get(row);
         if (rect != null) result.add(new Line(row, this.rowSlots.containsKey(row), rect[0], rect[1], rect[2], rect[3]));
      }
      return result;
   }

   public Line lineAt(double x, double y) {
      Line found = null;
      for (Line line : this.lines()) if (line.contains(x, y)) found = line;
      return found;
   }

   /** The stacked lines' box in GUI coordinates, or null when every line has been pulled out. */
   public float[] stackBounds() {
      return this.drawnStack == null ? null : this.drawnStack.clone();
   }

   @Override
   protected List<float[]> editorAreas() {
      if (!this.isSplit()) return super.editorAreas();
      List<float[]> areas = new ArrayList<>();
      if (this.drawnStack != null) areas.add(this.drawnStack);
      for (var entry : this.rowSlots.entrySet()) {
         float[] drawn = this.drawnRows.get(entry.getKey());
         areas.add(drawn != null ? drawn : this.absoluteSlot(entry.getValue()));
      }
      return areas;
   }

   @Override
   public boolean isMouseOver(double mouseX, double mouseY) {
      if (!this.isSplit()) return super.isMouseOver(mouseX, mouseY);
      for (float[] area : this.editorAreas()) {
         if (mouseX >= area[0] && mouseX <= area[0] + Math.max(area[2], 4.0F) && mouseY >= area[1] && mouseY <= area[1] + Math.max(area[3], 4.0F)) return true;
      }
      return false;
   }

   /**
    * Pulls a line out (if stacked) and puts its top-left corner at ({@code x}, {@code y}) in GUI coordinates. The frame
    * is rebuilt around every piece and the element moved to its new corner, so nothing else moves on screen.
    */
   public void moveLine(HudTextRow line, float x, float y) {
      if (!this.isSplittable() || !this.rows.contains(line)) return;
      float[] stack = this.stackRect(line);
      Map<HudTextRow, float[]> rects = new IdentityHashMap<>();
      for (var entry : this.rowSlots.entrySet()) if (entry.getKey() != line) rects.put(entry.getKey(), this.absoluteSlot(entry.getValue()));
      float[] drawn = this.drawnRows.get(line);
      // A moved line's slot is whole grid steps too, so pieces line up with each other and with other elements.
      float width = drawn != null ? drawn[2] : this.freeRowWidth(line);
      rects.put(line, new float[]{x, y, HudGrid.fit(width), HudGrid.fit(this.rowHeight())});
      this.reframe(stack, rects);
   }

   /** Puts a pulled-out line back into the stack. */
   public void dockLine(HudTextRow line) {
      float[] slot = this.rowSlots.get(line);
      if (slot == null) return;
      float[] stack = this.drawnStack != null ? this.drawnStack.clone() : this.absoluteSlot(slot);
      Map<HudTextRow, float[]> rects = new IdentityHashMap<>();
      for (var entry : this.rowSlots.entrySet()) if (entry.getKey() != line) rects.put(entry.getKey(), this.absoluteSlot(entry.getValue()));
      this.reframe(stack, rects);
   }

   /** Stacks every line again, keeping the element where its stack was. */
   public void restack() {
      if (!this.isSplit()) return;
      float[] at = this.drawnStack;
      if (at == null) {
         // Everything was pulled out: stack where the first line is.
         for (HudTextRow row : this.rows) { at = this.drawnRows.get(row); if (at != null) break; }
      }
      if (at != null) {
         this.setX(at[0], true);
         this.setY(at[1], true);
      }
      this.rowSlots.clear();
      this.stackSlot = null;
   }

   /** Where the stack is now, in GUI coordinates; null when {@code leaving} is its last line. */
   private float[] stackRect(HudTextRow leaving) {
      boolean othersStacked = false;
      for (HudTextRow row : this.rows) if (row != leaving && !this.rowSlots.containsKey(row)) othersStacked = true;
      if (!othersStacked || this.drawnStack == null) return null;
      float[] stack = this.drawnStack.clone();
      float[] drawn = this.drawnRows.get(leaving);
      if (!this.rowSlots.containsKey(leaving) && drawn != null) {
         // The stack loses the line: it shrinks from the side it grows towards.
         float lost = drawn[3] + this.scale();
         stack[3] = Math.max(this.rowHeight(), stack[3] - lost);
         if (this.getVerticalDirection() == -1) stack[1] += lost;
      }
      return stack;
   }

   private float[] absoluteSlot(float[] slot) {
      return new float[]{this.getX() + slot[0], this.getY() + slot[1], slot[2], slot[3]};
   }

   /** Rebuilds the frame around the stack and the pulled-out lines, given in GUI coordinates. */
   private void reframe(float[] stack, Map<HudTextRow, float[]> rects) {
      if (rects.isEmpty()) {
         this.rowSlots.clear();
         this.stackSlot = null;
         if (stack != null) {
            this.setX(stack[0], true);
            this.setY(stack[1], true);
         }
         return;
      }
      float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
      List<float[]> all = new ArrayList<>(rects.values());
      if (stack != null) all.add(stack);
      for (float[] rect : all) {
         minX = Math.min(minX, rect[0]);
         minY = Math.min(minY, rect[1]);
         maxX = Math.max(maxX, rect[0] + rect[2]);
         maxY = Math.max(maxY, rect[1] + rect[3]);
      }
      this.stackSlot = stack == null ? null : new float[]{stack[0] - minX, stack[1] - minY, stack[2], stack[3]};
      this.rowSlots.clear();
      for (var entry : rects.entrySet()) {
         float[] rect = entry.getValue();
         this.rowSlots.put(entry.getKey(), new float[]{rect[0] - minX, rect[1] - minY, rect[2], rect[3]});
      }
      this.frameW = maxX - minX;
      this.frameH = maxY - minY;
      this.setDimensions(new float[]{this.frameW, this.frameH});
      this.setX(minX, true);
      this.setY(minY, true);
   }

   // ---- saving ---------------------------------------------------------------------------------

   @Override
   public void writeLayout(JsonObject hud) {
      if (!this.isSplit() && this.pendingSlots.isEmpty()) return;
      JsonObject split = new JsonObject();
      split.addProperty("w", this.frameW);
      split.addProperty("h", this.frameH);
      if (this.stackSlot != null) split.add("stack", array(this.stackSlot));
      JsonObject rows = new JsonObject();
      for (var entry : this.pendingSlots.entrySet()) rows.add(entry.getKey(), array(entry.getValue()));
      for (var entry : this.rowSlots.entrySet()) {
         String key = this.keyOf(entry.getKey());
         if (key != null) rows.add(key, array(entry.getValue()));
      }
      split.add("rows", rows);
      hud.add("split", split);
   }

   @Override
   public void readLayout(JsonObject hud) {
      if (!this.splittable) return;
      this.rowSlots.clear();
      this.pendingSlots.clear();
      this.stackSlot = null;
      if (!hud.has("split")) return;
      try {
         JsonObject split = hud.getAsJsonObject("split");
         for (var entry : split.getAsJsonObject("rows").entrySet()) {
            float[] slot = slot(entry.getValue().getAsJsonArray());
            if (slot != null) this.pendingSlots.put(entry.getKey(), slot);
         }
         if (this.pendingSlots.isEmpty()) return;
         this.stackSlot = split.has("stack") ? slot(split.getAsJsonArray("stack")) : null;
         this.frameW = split.get("w").getAsFloat();
         this.frameH = split.get("h").getAsFloat();
         this.resolvePending();
      } catch (RuntimeException ignored) {
         // A hand-edited or outdated layout falls back to the stacked element.
         this.rowSlots.clear();
         this.pendingSlots.clear();
         this.stackSlot = null;
      }
   }

   private static JsonArray array(float[] values) {
      JsonArray array = new JsonArray();
      for (float value : values) array.add(value);
      return array;
   }

   private static float[] slot(JsonArray array) {
      if (array == null || array.size() != 4) return null;
      float[] slot = new float[4];
      for (int i = 0; i < 4; i++) {
         slot[i] = array.get(i).getAsFloat();
         if (!Float.isFinite(slot[i])) return null;
      }
      return slot;
   }
}

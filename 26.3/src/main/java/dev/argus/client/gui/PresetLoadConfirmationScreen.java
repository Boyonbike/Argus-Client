package dev.argus.client.gui;

import dev.argus.client.utils.InputKeys;
import com.google.gson.JsonObject;
import dev.argus.client.ClientContext;
import dev.argus.client.config.importing.ImportedPresetStore;
import dev.argus.client.settings.options.PresetCategory;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import java.util.*;
import java.util.function.Consumer;

/** Modal replacement warning shared by native and imported manual GUI loads. */
final class PresetLoadConfirmationScreen extends UiScreen {
    private final PresetCategory category;
    private final String name;
    private final boolean imported;
    private final PresetLoadController controller;
    private final Consumer<String> completed;
    private final List<String> body=new ArrayList<>();
    private UiInput saveName;
    private UiButton primary,without,cancel,close;
    private int left,top,panelWidth,panelHeight,bodyTop,bodyHeight,scroll;
    private boolean submitted;
    private String typedName;
    private long actionAfter=System.nanoTime()+300_000_000L;

    PresetLoadConfirmationScreen(Screen parent,PresetCategory category,String name,Consumer<String> completed) {
        super("Replace current config?",parent,"Settings");this.category=category;this.name=name;this.completed=completed;
        var registry=ClientContext.configurationManager.getPresetRegistry(category);var preset=registry.getPreset(name);
        JsonObject fingerprint=preset==null?null:preset.contents().deepCopy();
        final String diskVersion=dev.argus.client.config.importing.PresetFileVersion.capture(category.getDirectory().resolve(name+".json"));
        imported=category==PresetCategory.MODULES && fingerprint!=null && ImportedPresetStore.isImported(fingerprint);
        typedName=suggestName(category,name);
        controller=new PresetLoadController(new PresetLoadController.Actions(){
            public boolean unchanged(){var p=registry.getPreset(name);return p!=null && p.contents().equals(fingerprint) && diskVersion!=null && diskVersion.equals(dev.argus.client.config.importing.PresetFileVersion.capture(category.getDirectory().resolve(name+".json")));}
            public boolean exists(String save){return Presets.exists(category,save) || java.nio.file.Files.exists(category.getDirectory().resolve(save+".json"));}
            public void save(String save)throws Exception{if(exists(save))throw new java.io.IOException("That preset name already exists");if(imported)ImportedPresetStore.saveCurrent(save);else Presets.save(category,save);}
            public String load(){
                if(!Presets.load(category,name)) {
                    var result=imported?ImportedPresetStore.lastResult():null;
                    throw new IllegalStateException(result==null?"Couldn’t load this preset.":result.issues().stream().map(i->i.message()).findFirst().orElse("Couldn’t load this preset."));
                }
                var result=imported?ImportedPresetStore.lastResult():null;
                return "Loaded "+name+"."+(result!=null && result.backupName()!=null?" Recovery backup: "+result.backupName()+".":"");
            }
        });
        body.add("Loading “"+name+"” will replace your current "+scope(category)+".");
        body.add("Save your current config first if you want to keep it.");
        if(imported) {
            if(!ImportedPresetStore.isRecovery(fingerprint))body.add("Settings without a supported match will use Argus defaults.");
            int on=0,off=0;for(var e:fingerprint.entrySet())if(e.getValue().isJsonObject() && e.getValue().getAsJsonObject().has("enabled")){if(e.getValue().getAsJsonObject().get("enabled").getAsBoolean())on++;else off++;}
            body.add(on+" modules on · "+off+" modules off");
            var meta=fingerprint.getAsJsonObject("argusImport");
            if(meta.has("copiedSettings") && meta.has("defaultedSettings"))body.add(meta.get("copiedSettings").getAsInt()+" settings copied · "+meta.get("defaultedSettings").getAsInt()+" at Argus defaults");
            body.add("An automatic recovery backup is also created before loading imported configs.");
        }
    }
    private static String suggestName(PresetCategory category,String name){
        String clean=name.replaceAll("[^A-Za-z0-9 _.\\-]", "");String base="Before "+clean; if(base.length()>28)base=base.substring(0,28);String result=base;
        for(int n=2;Presets.exists(category,result);n++)result=base+" "+n;return result;
    }
    static String scope(PresetCategory category){return switch(category){case MODULES->"module settings and enabled modules";case ALL->"module settings, binds, HUD, colours, visuals and macros";case BINDS->"keybinds";case HUD->"HUD configuration";case COLORS->"colours";case VISUALS->"visual settings";case MACRO->"macros";};}
    @Override protected boolean modal(){return true;}
    /** The parent stays visible underneath; a single dim layer fades in with the dialog. */
    @Override protected void backdrop(GuiGraphicsExtractor g){UiDraw.rect(g,0,0,width,height,0x70000000);}
    @Override protected void buildContent(){
        panelWidth=Math.min(440,width-24);panelHeight=Math.min(height-24,imported?310:280);left=(width-panelWidth)/2;top=(height-panelHeight)/2;
        bodyTop=top+48;bodyHeight=Math.max(28,panelHeight-178);
        boolean naming=controller.state()==PresetLoadController.State.NAMING;
        saveName=new UiInput(left+16,top+panelHeight-110,panelWidth-32,12,"Name for your current config");saveName.setMaxLength(32);saveName.setValue(typedName);saveName.setResponder(v->typedName=v);
        if(naming)addRenderableWidget(saveName);
        int row=top+panelHeight-90;
        primary=addRenderableWidget(new UiButton(left+16,row,panelWidth-32,22,naming?"Save & load":"Save current & load",true,()->{
            if(!ready())return;
            if(controller.state()==PresetLoadController.State.CONFIRM){controller.chooseSaveCurrent();actionAfter=System.nanoTime()+300_000_000L;rebuildWidgets();setInitialFocus(saveName);return;}
            controller.confirmSaveAndLoad(saveName.getValue());finish();
        }));
        without=addRenderableWidget(new UiButton(left+16,row+27,panelWidth-32,22,"Load without saving",()->{if(ready()){controller.confirmLoadWithoutNamedSave();finish();}}));
        without.visible=!naming;
        cancel=addRenderableWidget(new UiButton(left+16,row+54,panelWidth-32,22,"Cancel",this::onClose));
        boolean valid=controller.state()==PresetLoadController.State.CONFIRM || naming;primary.active=valid;without.active=valid;
        close=addRenderableWidget(new UiButton(left+panelWidth-30,top+15,18,18,"",false,"close",this::onClose));
        setInitialFocus(cancel);
    }
    private boolean ready(){return !submitted && !closing() && System.nanoTime()>actionAfter;}
    private void finish(){
        if(controller.state()==PresetLoadController.State.COMPLETE){submitted=true;completed.accept(controller.message());beginClose(parent);}
        else {
            rebuildWidgets();
            if(controller.state()==PresetLoadController.State.INVALID){primary.active=false;without.active=false;}
            if(controller.state()==PresetLoadController.State.NAMING)setInitialFocus(saveName);
        }
    }
    @Override protected void renderUi(GuiGraphicsExtractor g,int mx,int my,float delta){
        UiTheme t=GuiState.theme();UiDraw.panel(g,left,top,panelWidth,panelHeight,t,1);
        UiDraw.circle(g,left+26,top+24,12,UiDraw.alpha(t.yellow(),.15f));UiDraw.center(g,"!",left+14,top+24,24,UiDraw.TITLE,t.yellow());
        UiDraw.bold(g,"Replace current config?",left+46,top+24,UiDraw.TITLE,t.text());
        List<String> lines=new ArrayList<>();List<Integer> starts=new ArrayList<>();for(String text:body){starts.add(lines.size());lines.addAll(UiDraw.wrap(text,panelWidth-36,UiDraw.SMALL));}
        int max=Math.max(0,lines.size()*13-bodyHeight);scroll=Math.clamp(scroll,0,max);
        g.enableScissor(left+12,bodyTop-4,left+panelWidth-12,bodyTop+bodyHeight);
        int firstEnd=starts.size()>1?starts.get(1):lines.size();
        for(int i=0;i<lines.size();i++)UiDraw.text(g,lines.get(i),left+18,bodyTop+7+i*13-scroll,UiDraw.SMALL,i<firstEnd?t.text():t.muted());g.disableScissor();
        if(max>0)UiDraw.text(g,"Scroll to read",left+18,bodyTop+bodyHeight+7,5.5f,t.muted());
        if(!controller.message().isBlank()) {
            if(UiDraw.over(mx,my,left,top+panelHeight-30,panelWidth,30))tooltip("load.error",controller.message());
            UiDraw.text(g,UiDraw.fit(controller.message(),panelWidth-32,UiDraw.SMALL),left+16,top+panelHeight-119,UiDraw.SMALL,t.red());
        }
    }
    @Override protected boolean mouseScrolledUi(double x,double y,double h,double v){scroll-=(int)(v*20);return true;}
    @Override public boolean keyPressed(KeyEvent event){
        if(InputKeys.glfw(event)==256){onClose();return true;}
        if(InputKeys.glfw(event)==258){
            List<net.minecraft.client.gui.components.events.GuiEventListener> focus=new ArrayList<>();if(saveName.visible && controller.state()==PresetLoadController.State.NAMING)focus.add(saveName);if(primary.active)focus.add(primary);if(without.visible && without.active)focus.add(without);focus.add(cancel);focus.add(close);
            int i=focus.indexOf(getFocused());setFocused(focus.get(Math.floorMod(i+(event.hasShiftDown()?-1:1),focus.size())));return true;
        }
        return super.keyPressed(event);
    }
    @Override public void onClose(){if(submitted || closing())return;controller.cancel();beginClose(parent);}
}

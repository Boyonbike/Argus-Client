package dev.argus.client.gui;
import dev.argus.client.config.importing.ImportedPresetStore;

/** Confirmation state, independent of rendering. All actions execute on the client thread. */
final class PresetLoadController {
    enum State { CONFIRM, NAMING, BUSY, COMPLETE, CANCELLED, INVALID }
    interface Actions {
        boolean unchanged();
        boolean exists(String name);
        void save(String name) throws Exception;
        String load() throws Exception;
    }
    private final Actions actions;
    private State state=State.CONFIRM;
    private String message="",savedName;
    PresetLoadController(Actions actions) { this.actions=actions; }
    State state(){return state;}
    String message(){return message;}
    void cancel(){if(state!=State.BUSY && state!=State.COMPLETE)state=State.CANCELLED;}
    void chooseSaveCurrent(){if(state==State.CONFIRM){state=State.NAMING;message="";}}
    void confirmSaveAndLoad(String name){
        if(state!=State.NAMING)return;
        name=name.trim();
        if(!ImportedPresetStore.validName(name)){message="Use 1–32 letters, numbers, spaces, dots, dashes or underscores.";return;}
        if(actions.exists(name)){message="That name already exists. Choose another name.";return;}
        if(!validate())return;
        state=State.BUSY;
        try{actions.save(name);savedName=name;}
        catch(Exception e){state=State.NAMING;message="Couldn’t save your current config. Nothing has been loaded. "+e.getMessage();return;}
        load();
    }
    void confirmLoadWithoutNamedSave(){if(state!=State.CONFIRM || !validate())return;state=State.BUSY;load();}
    private boolean validate(){
        if(!actions.unchanged()){state=State.INVALID;message="This preset changed or disappeared. Cancel and select it again.";return false;}return true;
    }
    private void load(){
        try{message=actions.load();if(savedName!=null)message+=" Previous config saved as "+savedName+".";state=State.COMPLETE;}
        catch(Exception e){message=e.getMessage();state=State.CONFIRM;}
    }
}

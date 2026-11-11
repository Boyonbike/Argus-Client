package dev.argus.client.config.importing;
import com.google.gson.*;
import dev.argus.client.settings.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.argus.client.config.importing.ConfigImportModel.*;
class ConfigImportConverterTest {
    static ArgusModuleCatalog catalog() {
        var speed=new NumberSetting<>("Speed",2.0,0.0,10.0); speed.setValue(9.0);
        return new ArgusModuleCatalog(Map.of(
            "Sprint",new ArgusModuleCatalog.Target("Sprint",Map.of("Speed",ArgusModuleCatalog.describe(speed)),new JsonObject()),
            "Offhand",new ArgusModuleCatalog.Target("Offhand",Map.of(),new JsonObject()),
            "AutoEat",new ArgusModuleCatalog.Target("AutoEat",Map.of(),new JsonObject()),
            "AutoReconnect",new ArgusModuleCatalog.Target("AutoReconnect",Map.of("Minutes",ArgusModuleCatalog.describe(new NumberSetting<>("Minutes",0,0,15)),"Delay",ArgusModuleCatalog.describe(new NumberSetting<>("Delay",5f,0f,59f))),new JsonObject())));
    }
    static SourceModule module(String id,boolean enabled,Map<String,JsonElement> settings) { return new SourceModule(id,enabled,settings,new JsonObject(),Optional.empty()); }
    @Test void enablesCounterpartsAndFillsArgusDefaultsRatherThanCurrentValues() {
        var result=ConfigImportConverter.convert(new SourceConfig(Format.METEOR,List.of(module("sprint",true,Map.of()),module("addon-only",true,Map.of()))),catalog(),false);
        var sprint=result.preset().getAsJsonObject("Sprint");
        assertTrue(sprint.get("enabled").getAsBoolean()); assertEquals(2,sprint.getAsJsonObject("settings").get("Speed").getAsDouble());
        assertFalse(result.preset().getAsJsonObject("Offhand").get("enabled").getAsBoolean());
        assertTrue(result.issues().stream().anyMatch(i->i.sourceId().equals("addon-only") && i.severity()==Severity.WARNING));
    }
    @Test void nativeExactSettingsValidateAndMeteorMissingValuesStayDefault() {
        var nativeConfig=new SourceConfig(Format.ARGUS,List.of(module("Sprint",true,Map.of("Speed",new JsonPrimitive(4.0)))));
        assertEquals(4,ConfigImportConverter.convert(nativeConfig,catalog(),false).preset().getAsJsonObject("Sprint").getAsJsonObject("settings").get("Speed").getAsDouble());
        var invalid=new SourceConfig(Format.ARGUS,List.of(module("Sprint",true,Map.of("Speed",new JsonPrimitive(40)))));
        assertEquals(2,ConfigImportConverter.convert(invalid,catalog(),false).preset().getAsJsonObject("Sprint").getAsJsonObject("settings").get("Speed").getAsDouble());
        var meteor=new SourceConfig(Format.METEOR,List.of(module("auto-eat",true,Map.of("General/blacklist",new JsonArray())),module("auto-reconnect",true,Map.of("General/delay",new JsonPrimitive(90)))));
        var converted=ConfigImportConverter.convert(meteor,catalog(),false);
        assertTrue(converted.preset().getAsJsonObject("AutoEat").getAsJsonObject("settings").isEmpty());
        assertEquals(1,converted.preset().getAsJsonObject("AutoReconnect").getAsJsonObject("settings").get("Minutes").getAsInt());
        assertEquals(30,converted.preset().getAsJsonObject("AutoReconnect").getAsJsonObject("settings").get("Delay").getAsDouble());
    }
    @Test void reconnectDelayFallsBackAsAUnitWhenSecondsCannotBeRepresented() {
        var meteor=new SourceConfig(Format.METEOR,List.of(module("auto-reconnect",true,Map.of("General/delay",new JsonPrimitive(119.5)))));
        var settings=ConfigImportConverter.convert(meteor,catalog(),false).preset().getAsJsonObject("AutoReconnect").getAsJsonObject("settings");
        assertEquals(0,settings.get("Minutes").getAsInt());assertEquals(5,settings.get("Delay").getAsDouble());
    }
    @Test void combinesEnabledStatesAndRejectsUnrepresentableBinds() {
        var first=module("auto-totem",true,Map.of()); var second=module("offhand",false,Map.of());
        var bound=new SourceModule("sprint",true,Map.of(),new JsonObject(),Optional.of(new SourceBind(false,80,2,"toggle")));
        var result=ConfigImportConverter.convert(new SourceConfig(Format.METEOR,List.of(first,second,bound)),catalog(),true);
        assertTrue(result.preset().getAsJsonObject("Offhand").get("enabled").getAsBoolean());
        assertFalse(result.preset().getAsJsonObject("Sprint").has("key"));
        assertTrue(result.issues().stream().anyMatch(i->i.field().equals("bind")));
        var copy=result.preset();copy.remove("Sprint");assertTrue(result.preset().has("Sprint"));
    }
}

package dev.argus.client.config.importing;
import com.google.gson.*;
import java.util.*;
import java.util.function.Function;

/** Deliberate module counterparts, not runtime fuzzy matching. Settings require separate evidence. */
public final class MeteorMappings {
    public record SettingMapping(String sourcePath,String target,Function<JsonElement,JsonElement> transform) {}
    public record ModuleMapping(String source,String target,List<SettingMapping> settings) {}
    private MeteorMappings() {}
    public static List<ModuleMapping> modules() { return MAPPINGS; }
    private static final List<ModuleMapping> MAPPINGS = build();
    private static List<ModuleMapping> build() {
        List<ModuleMapping> result=new ArrayList<>();
        // Core module purpose audited against the cached Meteor source and Argus module descriptions.
        // No settings are inferred from these names, and addon variants require their own entries.
        String pairs="""
            anchor-aura AnchorAura
            anti-afk AntiAFK
            anti-hunger AntiHunger
            anti-packet-kick NoPacketKick
            auto-armor AutoArmor
            auto-breed AutoBreed
            auto-clicker AutoClicker
            auto-craft AutoCraft
            auto-drop AutoDrop
            auto-eat AutoEat
            auto-exp AutoExp
            auto-fish AutoFish
            auto-log AutoLog
            auto-mount AutoMount
            auto-nametag AutoNameTag
            auto-reconnect AutoReconnect
            auto-replenish Replenish
            replenish Replenish
            auto-respawn AutoRespawn
            auto-sign AutoSign
            auto-tool AutoTool
            auto-totem Offhand
            auto-trap AutoTrap
            auto-walk AutoWalk
            bed-aura BedAura
            better-chat BetterChat
            better-tab ExtraTab
            better-tooltips Tooltips
            blink FakeLag
            bow-aimbot BowAim
            break-indicators BreakHighlight
            burrow SelfFill
            chams Chams
            criticals Criticals
            crystal-aura AutoCrystal
            elytra-fly ElytraFly
            entity-control EntityControl
            esp ESP
            fast-climb FastClimb
            flight Flight
            free-look FreeLook
            freecam Freecam
            high-jump HighJump
            hole-esp HoleESP
            hole-filler HoleFill
            inventory-tweaks InventoryTweaks
            jesus Jesus
            kill-aura Aura
            kill-effects KillEffects
            liquid-filler SourceFill
            logout-spots LogoutSpots
            long-jump LongJump
            middle-click-extra MiddleClick
            multitask MultiTask
            name-protect NameProtect
            nametags NameTags
            no-fall NoFall
            no-ghost-blocks NoGlitchBlocks
            no-interact NoInteract
            no-jump-delay NoJumpDelay
            NoJumpDelay NoJumpDelay
            no-render NoRender
            no-rotate NoRotate
            no-slow NoSlow
            no-status-effects NoEffects
            nuker Nuker
            offhand Offhand
            phase Phase
            PortalGodMode PortalGodMode
            reach Reach
            reverse-step FastFall
            rotation RotationLock
            safe-walk SafeWalk
            scaffold Scaffold
            self-trap SelfTrap
            self-web SelfWeb
            skin-blink SkinFlicker
            sound-blocker SoundBlocker
            spam Spammer
            speed Speed
            speed-mine SpeedMine
            sprint Sprint
            stash-finder StashFinder
            step Step
            surround Blocker
            timer Timer
            tracers Tracers
            trajectories Trajectories
            trail Trails
            trident-boost Trident
            tunnel-esp Tunnels
            velocity Velocity
            visual-range-notifier VisualRange
            void-esp VoidESP
            xray Xray
            zoom Zoom
            """;
        for(String line:pairs.strip().split("\\n")) {
            String[] pair=line.trim().split("\\s+"); List<SettingMapping> settings=List.of();
            // Meteor misc/AutoReconnect.java: General/delay is seconds.
            // Argus AutoReconnectModule constructor: Minutes is minutes; config ID Delay is seconds [0,59].
            // Use Argus range validation after splitting; unsupported fractions at the minute boundary default as a unit.
            if(pair[0].equals("auto-reconnect")) settings=List.of(
                new SettingMapping("General/delay","Minutes",v->new JsonPrimitive((int)Math.floor(seconds(v)/60))),
                new SettingMapping("General/delay","Delay",v->new JsonPrimitive(seconds(v)%60)));
            result.add(new ModuleMapping(pair[0],pair[1],settings));
        }
        return List.copyOf(result);
    }
    private static double seconds(JsonElement value) {
        if(!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !Double.isFinite(value.getAsDouble()) || value.getAsDouble()<0 || value.getAsDouble()>959) throw new IllegalArgumentException("Invalid reconnect delay");
        return value.getAsDouble();
    }
}

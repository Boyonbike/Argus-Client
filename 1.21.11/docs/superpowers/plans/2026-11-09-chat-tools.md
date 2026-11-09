# Chat Tools Implementation Plan

> **For agentic workers:** Plan only. Do not implement until the user approves this plan. Open questions were answered 2026-11-09.

**Goal:** Bring Comet's Chat Tools to Argus. That means DMs and conversations, a chat log, players and clan, homes, teleport requests and cooldowns, Auto TPY, hotspot and server settings. Rebuild the window in the Argus UI style, and use Argus's settings, friends (`SocialRegistry`) and Discord systems instead of Meteor's. Merge it with Argus's BetterChat, so chat is one module, one settings page and one window.

**Architecture:** Split Comet's feature into three layers and treat each one differently:
1. **Core logic** (`dev.tntcoating.core.Chat*`, `AutoTpy*`, `DiscordRoles`, `DiscordIgn*`): pure Java, mostly free of Meteor and already unit-tested. Port it nearly verbatim into `dev.argus.client.chat` and bring its tests along.
2. **Module** (`modules/ChatTools.java`, 1.2k lines): rewrite it against the Argus `Module`, settings, events and `SocialRegistry` APIs. Keep the public method surface the screen relies on.
3. **Screen** (`gui/ChatToolsScreen.java` 2k lines + `ChatToolsStyle` 400 lines): replace it. Keep the page model, but build it on `UiScreen`, `UiDraw`, `UiButton`, `UiInput`, `UiTheme`, `UiAnim` and `SettingsList`, so it shares the top bar, theme, accent, scale and animations.

**Tech Stack:** Java 21, Minecraft 1.21.11 (Mojang mappings), Fabric, Argus `client-base.jar` module API, JUnit 5.

**Source of truth:** Comet `26.1.2` tree (newest; has `ChatToolsFiles`). Where Minecraft APIs differ (`GuiGraphicsExtractor` is 26.x), use Comet's `1.21.11` tree as the reference.

## What Comet's Chat Tools does (inventory)
| Area | Comet pieces | Notes |
| --- | --- | --- |
| Overview | `overview()` | What needs you: pending TPAs, unread DMs, problems, homes, recent players |
| Chat log | `ChatLine` deque (300), `chatPage()` | Server chat with times, DMs flagged |
| DMs | `ChatMessages`, `ChatWhisperQueue`, `conversation()` | Incoming/outgoing regex patterns, per-player threads, queued whispers with cooldown, pending bubbles, toasts, hide DMs from chat |
| Public chat queue | `ChatPublicQueue` | Rate-limited sending, cancellable outgoing tray |
| Players | `ChatRecentPlayers`, `ChatPlayerSearch`, `options()` | Recent, friends, online; per-player actions found from the server's command tree (`discover`) |
| Clan | `ChatClan`, `DiscordRoles` | Member list grouped by Discord role; highlight in chat, tab names and tab rows |
| Homes | `ChatHomeNames`, `ChatHomeFavorites`, `ChatHomeEdit` | Read from `/home` suggestions or replies, saved per server, favourites, rename/delete with confirmation |
| Teleports | `ChatTeleportState`, `ChatTpaRequests`, `ChatCountdowns`, `ChatSavedCooldowns`, `TeleportCountdownHud` | Incoming request list with accept/deny, learned and manual cooldowns, cancel, HUD countdown |
| Auto TPY | `AutoTpyController/Queue/Policy/Feedback/WhitelistImport` | Whitelist, blocklist, friends, Discord roles, adaptive pacing, retries |
| Hotspot / Server settings | `refreshHotspot()`, `ChatServerSettings` | 6b6t-specific; filtered by the commands the server actually has |
| Discord sync | `DiscordIgnSync`, `DiscordIgnClient` | Relay URL → IGN↔Discord map and role roster, feeding Clan and Auto TPY |
| Chat hooks | `ChatScreenMixin`, `ChatFriendHighlightMixin`, `TabListHighlightMixin` | Click a name in chat to open actions; name highlighting |

## Meteor → Argus mapping
| Comet / Meteor | Argus |
| --- | --- |
| `Module` + `TntCoatingAddon.CATEGORY` | `dev.argus.client.modules.Module`, `ModuleCategory.MISC`, registered in `gui/mixin/ModuleRegistryMixin` (like `AutoRekitModule`) |
| `BoolSetting`, `IntSetting`, `StringSetting`, `KeybindSetting` | `BooleanSetting`, `NumberSetting<Integer>` (`withUnit("ms")`), `StringSetting`, `KeyBindSetting` |
| `StringListSetting`, `ColorSetting` | Verify the `client-base` equivalents behind `CollectionSettingScreen` / `ColorPicker`. Otherwise use `ChatToolsStore` |
| `Friends.get()` | `ClientContext.socialRegistry`: `isFriendName`, `addFriend`, `removeSocial`, `getRole`, `getFriendColor`; enemies come for free |
| `ReceiveMessageEvent` | `ChatMessageEvent` (cancellable, `setMessage` for highlighting/hiding) |
| Outgoing chat/command packets | `ClientPacketListenerSendChatEvent`, `CommandExecuteEvent`, `PacketEvent` |
| `Render2DEvent` / tick | `HudRenderEvent`, `MinecraftTickEvent` |
| Meteor `ColorSettingScreen` | Argus `ColorPicker` |
| `SystemToast` DM toasts | `NotificationsModule` / `HudNotification` so DMs look like other Argus notifications |
| Per-server lists in string settings | `ChatToolsStore`: JSON under `ClientDataDirectory`, keyed by server (like `RekitPresets`) |

## Integration decisions
- **Friends:** one friends list. Players and Overview read and write `SocialRegistry`. The Settings → Friends panel and `/friends` show the same people. Add an "Enemy" action next to "Friend".
- **Highlighting:** BetterChat's `highlightNames` (self/friends/enemies) becomes the single chat highlighter. Clan is added to it, as described in the BetterChat merge section. ExtraTab (`Social`) stays the tab-list highlighter, and clan is added there the same way. Chat Tools has no friend colours of its own.
- **Settings:** every option is a real module setting. It appears in Argus search, presets and config save, and gets a description in `assets/argusclient/descriptions.tsv`. The window's Settings page embeds the module's `ModulePage`/`SettingsList` instead of Comet's hand-built settings page.
- **UI shell:** add "Chat" to `UiScreen.PAGE_TABS` (optional in the top bar, like HUD). The page is a `UiScreen` subclass with a left sidebar matching `SettingsScreen` (same `SIDE` width, item height, icons, indicator animation). It covers Overview, Chat, Messages, Players, Clan, Homes, Teleports (requests + Auto TPY) and Server (hotspot + server settings). A conversation replaces the body in place. Dialogs reuse `UiInput`. Open it with a keybind setting, `/chattools`, the top-bar tab, or by clicking a name in chat.
- **Quick tray:** keep Comet's small "waiting" pill (`WaitingPill`) as an Argus HUD module registered via `HudModuleRegistry`. It shows the unread and request count and can be placed in the HUD editor.
- **Config import:** extend the existing Meteor/Comet import (`MeteorDataReader`, `ArgusDataPlan`) to carry `chat-tools` and `discord-ign-sync` settings, clan list, homes, favourites and saved cooldowns. Old Comet users then keep their data.

## Merging with BetterChat
BetterChat lives in the closed `client-base.jar`. Its own mixins (`dev.argus.mixin.ChatComponentMixin`, `ChatScreenMixin`, `ClientPacketListenerChatTimeMixin`, …) call `BetterChatModule.getInstance()` and its package-visible settings. So it can't be deleted or rewritten. Instead, it becomes the engine inside one user-facing **Chat** module:

- **One module in the list:** `ChatToolsModule` is shown as **Chat** (MISC, aliases "better chat", "chat tools", "dm", "tpa"). `GuiState.modules()` filters `BetterChatModule` out, which hides it from the Modules screen, Favourites and search. Search hits for BetterChat's settings resolve to the Chat page.
- **One switch:** turning Chat on or off also turns the hidden BetterChat on or off. A one-time migration (a `ModuleConfigMixin`-style hook) turns Chat on if an old config had BetterChat enabled.
- **One settings page:** the Chat page shows BetterChat's existing `Setting` objects (taken from `getSettings()`, no copies) next to Chat Tools' own. They're grouped as Chat style (timestamps, format, brackets, colours, rainbow), Animation, Highlights (self/friends/enemies + clan), Sounds (mention, DM), Prefix & suffix, History, Messages, Teleports, Auto TPY, Clan. BetterChat's settings keep saving under BetterChat's config entry, so presets, Meteor/Egg/Zio import and existing configs carry on working.
- **Shared behaviour:**
  - Clan highlighting: a mixin at the `RETURN` of `BetterChatModule.highlightNames` adds clan colours, so names are coloured once.
  - DM sound: BetterChat's private-message sound uses Chat Tools' DM detection (incoming patterns) through a redirect in `playPrivateMessageSound`. DMs then mean the same thing in the window, toasts and sounds.
  - Chat log: the window's log page uses BetterChat's timestamp format when timestamps are on.
  - Long history: when on, the window's line limit (300) rises to match.
  - Prefix/suffix and the send queue: Chat Tools' public-chat queue holds plain text. BetterChat adds the prefix/suffix only when the queued message is actually sent, and never twice. A test covers this.
- **Clicks in chat:** client-base already has a `ChatScreenMixin`. Port Comet's name-click handler as a separate injector with a unique prefix (`argus$chat…`), and check it doesn't consume clicks that BetterChat's mixins rely on.

## Global Constraints
- Preserve the current uncommitted HUD/GUI work on `rebrand-argus-everywhere`. Do this on a new branch after that work is committed.
- No new runtime dependencies. Use Java `HttpClient` for the Discord relay, as Comet does.
- Never send a chat message or command the user didn't ask for, apart from Auto TPY accepts within its policy and pacing, and read-only probes (`/home` suggestions, `/hotspot info`) on page open.
- Close the game before installing the jar (memory: install only when the game is closed).

## Tasks

### Task 1: Port core logic and tests
- [ ] Copy `core/Chat*`, `AutoTpy*`, `DiscordRoles`, `DiscordIgn*` into `dev.argus.client.chat`, and rename the package.
- [ ] Remove any Meteor imports left in the core (expected: none or very few).
- [ ] Port `ChatMessagesTest`, `ChatSenderTest`, `ChatToolsDataTest`, `ChatToolsStateTest`, `ChatToolsLayoutTest`, `ChatCommandTemplateTest`, `AutoTpy*Test` and `ChatFriendHighlightTest`; run them green.

### Task 2: Storage
- [ ] `ChatToolsStore` holds per-server homes, favourites, recent players and saved cooldowns, plus clan members and the Discord roster, in a JSON file under `ClientDataDirectory`.
- [ ] Add round-trip and corrupt-file tests.

### Task 3: Module
- [ ] `ChatToolsModule` in MISC, with groups mirroring Comet: General, Messages, Teleports, Auto TPY, Clan.
- [ ] Wire `ChatMessageEvent` for capture, hiding, highlighting and request/home/cooldown parsing, and the send events for queueing. Tick drives the whisper/public queues and Auto TPY.
- [ ] Use `SocialRegistry` for all friend logic. Show DM toasts through Argus notifications.
- [ ] Register in `ModuleRegistryMixin` before config load. Add descriptions to `descriptions.tsv`.

### Task 4: Chat and tab hooks
- [ ] Mixin on `ChatScreen.mouseClicked` (port `ChatScreenMixin`): clicking a player name opens player actions.
- [ ] Clan highlight: in chat, a mixin on `BetterChatModule.highlightNames`; in the tab list, the same approach on ExtraTab's social colouring.

### Task 4b: Merge BetterChat
- [ ] Hide `BetterChatModule` in `GuiState.modules()`, link its enabled state to Chat, and migrate old configs.
- [ ] Chat settings page: group BetterChat's live settings with Chat Tools' settings, and redirect search for BetterChat's settings to that page.
- [ ] DM sound uses Chat Tools' DM detection. Timestamps and long history carry into the window. The queue works with prefix/suffix.
- [ ] Tests:
  - enabling or disabling one module toggles both
  - an old config with BetterChat enabled migrates to Chat enabled
  - prefix/suffix applied exactly once to queued messages
  - BetterChat settings still save and load under its own entry

### Task 5: Discord
- [ ] Port `DiscordIgnSync` and `DiscordIgnClient` as a CLIENT-category module "Discord Sync" (relay URL, apply to clan, apply to Auto TPY, auto-sync hours). It gets its own Settings section beside Discord, using the existing sidebar and `ModulePage`.
- [ ] Feed the roster into Clan grouping and Auto TPY role filters. Rich Presence (`DiscordModule`) stays unchanged.
- [ ] Tests for roster parsing, ownership of synced entries (only remove names the sync itself added) and failure handling when the relay is unreachable.

### Task 6: Window
- [ ] `ChatToolsScreen extends UiScreen`, with sidebar pages as listed above. Port each page's content onto Argus widgets: toolbar, captions, grouped lists, tiles, bubbles.
- [ ] Embed `SettingsList` for the Settings page. Theme, accent, scale and light/dark come from `UiTheme`/`GuiPreferences`.
- [ ] Add a "Chat" top-bar tab, `/chattools`, a keybind, and a "Open Chat Tools" button on the module page.
- [ ] Waiting-pill HUD module.

### Task 7: Import, build, review
- [ ] Extend config import for Comet chat-tools and discord-ign-sync data. Add tests in the style of the existing import tests.
- [ ] Run the full tests and build the single jar. Test in game on 6b6t: DMs, TPA, homes, Auto TPY.
- [ ] Code review, then install with the game closed.

## Open questions
1. ~~**Discord:**~~ Decided 2026-11-09: port the relay sync only; Rich Presence is not touched.
2. ~~**Friend colours:**~~ Decided: drop Chat Tools' own friend colours and defer to BetterChat/ExtraTab; add clan highlighting only.
3. ~~**6b6t-specific pages:**~~ Decided: keep Hotspot, Server settings and `/homecancel`, hidden whenever the server doesn't offer those commands.
4. ~~**Webhooks:**~~ Decided: out of scope.

5. ~~**BetterChat:**~~ Decided: merge into one Chat module, with BetterChat kept as a hidden engine (see "Merging with BetterChat").

All open questions are answered; the plan is ready for implementation approval.

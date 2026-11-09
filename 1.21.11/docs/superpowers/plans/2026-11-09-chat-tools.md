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
BetterChat lives in the closed `client-base.jar`. Its own mixins (`dev.argus.mixin.ChatComponentMixin`, `ChatScreenMixin`, `ClientPacketListenerChatTimeMixin`, …) call `BetterChatModule.getInstance()` and its package-visible settings. So it can't be deleted or rewritten. Instead, it becomes the engine inside one user-facing **ChatTools** module (shown as "Chat Tools"; "Chat" is the HUD module):

- **One module in the list:** `ChatToolsModule` is shown as **Chat Tools** (MISC, aliases "chat", "better chat", "dm", "tpa"). `GuiState.modules()` filters `BetterChatModule` out, which hides it from the Modules screen, Favourites and search. Search hits for BetterChat's settings resolve to the Chat page.
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
- [x] Copy `core/Chat*`, `AutoTpy*`, `DiscordRoles`, `DiscordIgn*`, `DiscordConnection` (29 files) into `dev.argus.client.chat`, and rename the package.
- [x] Remove any Meteor imports left in the core (none; only Gson and Minecraft `Component`). Rebrand the relay User-Agent to "Argus discord-ign-sync" (the relay worker doesn't check it).
- [x] Port 25 test classes (all Chat*, AutoTpy*, DiscordIgn*, DiscordRoles, DiscordConnection; not DiscordWebhook): 235 tests green.
- Deferred: `AutoTpyWiringTest` reads the compiled module class, so it is held back until Task 3. Point it at `ChatToolsModule` and its tick handler then.

### Task 2: Storage
- [x] `ChatToolsStore` (`dev.argus.client.chat`) holds homes, favourite homes, saved cooldowns, recent players, clan members, the Auto TPY whitelist/blocklist/role IDs, the Discord-sync ownership lists and the Discord roster/source/usernames. Per-server lists keep the core helpers' `server|value` entries. Keys equal the old Comet setting names, so Task 7's import maps one to one.
- [x] Behaviour:
  - a missing file is an empty store
  - a malformed file throws `IOException` and is never overwritten (same as `ConnectionProfiles`)
  - wrong-typed values are skipped
  - unknown keys are kept on save
  - changes mark the store dirty, and `save()` writes atomically only when dirty
- [x] 8 tests in `ChatToolsStoreTest`: round trip, missing/empty, dirty tracking, copies, malformed, wrong types + unknown keys, no temp files, key names. All 243 chat tests are green.
- Task 3 wiring: file at `<gameDir>/argusclient/chat.json`. The module saves dirty data in batches (at most every few seconds, plus on disconnect/disable/close). If the file can't be read, the module shows the error and works in memory without saving, so the broken file is never replaced.

### Task 3: Module
- [x] `dev.argus.client.modules.misc.ChatToolsModule`, named **ChatTools** (MISC; aliases chat, better chat, dm, messages, tpa, homes, clan). "Chat" is already taken by the HUD Chat module, so the merged module keeps the ChatTools name. 34 settings in Comet's order: General, Messages, Teleports, Auto TPY (all depend on the Auto TPY switch), Clan highlighting.
- [x] Events:
  - `ChatMessageEvent` (Pre stage only) captures DMs and hides refusals and DMs. A second listener at priority -10000 records the lines chat actually shows.
  - `PacketEvent.Send`/`Receive` handle commands sent, system chat, the action bar and `/home` suggestions.
  - `MinecraftTickEvent` drives the queues, Auto TPY, the open key and batched saving.
  - `MinecraftDisconnectEvent` covers leaving. A `ClientboundLoginPacket` arriving while in a world counts as a server transfer.
- [x] Friends: `SocialRegistry` is used throughout (friends, enemies, toggles; the config is saved after a change). DM toasts use vanilla `SystemToast`, because Argus notifications have no public API for posting.
- [x] Registered in `ModuleRegistryMixin` (renamed `argus$registerModules`). 34 descriptions added. New `EntityArgumentAccessor` mixin for players-only detection.
- [x] `AutoTpyWiringTest` is back and points at `ChatToolsModule`. Full suite: 450 tests green. `./gradlew build` produces the jar.
- Changes from Comet, and why:
  - **Lists in the store:** command options, DM patterns and extra homes live in `ChatToolsStore`, not settings. Argus's `StringSetSetting` lowercases entries, which would break regexes and labels. The store now tells "emptied" apart from "never set" (`has`), so emptied defaults stay empty.
  - **Open key:** not bound by default. Comet used Y.
  - **Window scale:** dropped. The window follows Argus's GUI scale.
  - **Friend colours:** removed, as decided. Clan colouring skips friends and enemies so BetterChat's colours win.
  - **Hooks for later tasks:** `ChatToolsModule.Window` (filled in by Task 6) and `ChatToolsModule.DiscordSync` (Task 5). Until then the window key does nothing, and Discord roles are empty.

### Task 4: Chat and tab hooks
- [x] `ChatScreenPlayerClickMixin` (HEAD of `ChatScreen.mouseClicked`): left-clicking an online player's name opens Chat Tools on them. Names count whether styled or plain text. The click is only consumed if the window actually opened, so it does nothing until Task 6 attaches the window. It has a separate name and injector from client-base's `ChatScreenMixin`, which only touches animations, input background and inline suggestions.
- [x] `BetterChatClanHighlightMixin` (`RETURN` of `BetterChatModule.highlightNames`, `remap = false`): client-base's `ComponentRenderUtilsMixin` calls `highlightNames` on every wrapped chat line, even with BetterChat off. Clan colours are added to its result and skip friends and enemies, so each name is coloured once.
- [x] `TabListClanHighlightMixin`: a `@WrapOperation` on the `getNameForDisplay` call in `render` colours clan names after ExtraTab's own colouring. A wrap of the third `fill` draws the clan row bar. Render locals (slot 22 = row, slot 5 = list) were checked against the 1.21.11 bytecode in Loom's cache.
- [x] `openWindow` now returns whether it opened. Build is green. Remapped targets were checked in the jar (`method_25402`, `method_1918`, `method_25294`). 450 tests green.
- Not yet checked in game. This is part of Task 7's in-game pass: clan colours in chat and tab, row bars, and that clicks on server-styled names still work when Chat Tools is off.

### Task 4b: Merge BetterChat
- [x] Hiding: `GuiState.hidden` drops `BetterChatModule` from `GuiState.modules()`, so it's gone from the Modules screen, Favourites, the Settings sections and the Modules-screen search. That search only indexes module names and aliases, and "better chat" is a Chat Tools alias, so no redirect was needed. The client-base Classic GUI still lists BetterChat, since it isn't Argus's to change.
- [x] On/off link: `ChatModuleLink` (pure) plus `ChatToolsModule.syncBetterChat()`, run every client tick from `FriendsMod`.
  - Whichever module was switched wins, so BetterChat's own keybind or the Classic GUI still work.
  - The first run turns Chat Tools on if an old config had BetterChat on. It then records `MergedBetterChat` in Chat Tools' module JSON. A partial preset never clears that flag.
- [x] Settings page: Chat Tools' own settings now sit in group cards (General, Messages, Teleports, Auto TPY, Clan). The Auto TPY switch is renamed "Accept automatically" because the card is called Auto TPY.
  - `SettingsCards` adds BetterChat's live settings after them as six cards: Chat style, Sounds, Animation, Prefix & suffix, Highlights, History. `ChatSettingsSections` does the split; settings it doesn't recognise join the card before them.
  - Each card's list still belongs to BetterChat, so descriptions and config saving are unchanged.
- [x] DM sound: `BetterChatDmSoundMixin` redirects BetterChat's `ChatUtils.isIncomingWhisper` call to `ChatToolsModule.isIncomingPrivateMessage` while Chat Tools is on.
  - Chat Tools' DM listener now runs at priority -100, after BetterChat, so DM and mention sounds still play for DMs it hides.
  - It reads `getOriginalText()`, because BetterChat has already put its timestamp in front of the shown message.
- [x] Window feed: lines are stored without BetterChat's timestamp (the window has its own time column). Long chat history raises the line limit from 300 to 1000 (`chatLineLimit()`). Showing times in BetterChat's format is part of Task 6.
- [x] Prefix/suffix: the queue sends plain text through `sendChat`, so BetterChat adds its prefix and suffix once, when the message is sent. `ChatPublicQueue.queuedText` maps the decorated packet back to the queued text, so a refusal still re-queues the plain message and the prefix is never doubled.
- [x] Tests (10 new; 460 total, green):
  - `ChatModuleLinkTest`: migration, startup, either switch toggles both, a simultaneous change
  - `ChatSettingsSectionsTest`: six cards, full coverage, unknown settings
  - `ChatPublicQueuePrefixTest`: decorated match, other chat untouched, refusal re-queues plain text
  - BetterChat's settings keep saving under their own entry by construction (the same `Setting` objects owned by BetterChat). Can only be checked in game.
- Not yet checked in game (Task 7):
  - toggling Chat Tools may show BetterChat's own on/off notification, because `Module.enable()` posts `ModuleToggleEvent`
  - the six BetterChat cards
  - the DM sound on 6b6t
- Found in passing: BetterChat's default Suffix in client-base is still "⋆ ᴍɪᴏ", leftover Mio branding. Not changed here.

### Task 5: Discord
- [x] `dev.argus.client.modules.client.DiscordSyncModule` ("DiscordSync", CLIENT) has four settings: Relay URL, Apply to clan, Apply to Auto TPY, Auto sync (0–24 h). It shows in Settings beside Discord with the chat icon, and has a "Sync now" button whose label shows the state (Syncing… / Synced N · sync again / Sync failed · try again).
  - Results go to chat through `ChatUtils.printChat`, only while in a world.
  - The first sync of a launch waits for the first game tick, because modules switch on while the config loads.
  - Rich Presence (`DiscordModule`) is untouched.
- [x] Registered after Chat Tools in `ModuleRegistryMixin`. `attach()` hands Chat Tools the relay (for role access and Discord role groups in Clan) and the current failure (for the waiting notice). Changing the relay re-checks Auto TPY role access at once.
- [x] Ownership tracking lives in the store (`clan-synced`, `auto-tpy-synced`, plus the new `ownership-version` text), not settings, so presets can't roll it back.
- [x] The apply step is now pure (`DiscordSyncApply`): unreadable or empty reads change nothing (an empty read clears role access), only names the sync added are removed, and old untrusted tracking keeps everything as hand-added.
- [x] Tests: 4 new in `DiscordSyncApplyTest`. Roster parsing, retries, rate limits, network failures and overlapping reads were already covered by the ported `DiscordIgnTest`, `DiscordIgnClientTest` and `DiscordIgnSyncStateTest`. A failed read never touches the lists. 464 tests green, build green.
- Not yet checked in game (Task 7): a real sync against your relay, the Sync now label, and chat output.

### Task 6: Window
- [x] `gui/ChatToolsScreen` (`UiScreen`) uses Settings' frame: panel, sidebar and sliding indicator, page header, edge fades, scrollbar and footer notices. Theme, accent, scale and light/dark all come from `UiTheme`/`GuiPreferences`.
  - **Sidebar pages:** Overview, Chat, Messages, Players, Clan, Homes, Teleports, Auto TPY, Server, Settings. Badges show unread messages, pending requests and Auto TPY's buffer.
  - **Server** appears only when the server has `/hotspot` or one of the server-settings commands.
  - Every control records where it was drawn, so clicks match what's on screen. Anything typed goes through a modal dialog (new/rename/delete home, new message, reply, request teleport, add clan members, ignore, commands that need text).
  - **Composer and tray:** the composer sends public chat, `/commands` or the open conversation's DMs. Drafts are kept per conversation. A send tray shows up to two queued messages, with cancel buttons.
- [x] Pages ported from Comet:
  - **Overview:** quick actions; a waiting list (requests, unread DMs, a Discord problem with a Fix button that opens Settings → Discord Sync); timers; Auto TPY and Clan tiles; favourite-home tiles; recent players.
  - **Chat:** the server's lines as in-game chat shows them (vanilla font at 0.8 scale, styles kept), bottom-anchored, with a Newest button. Times use BetterChat's format (HH, HH:mm or HH:mm:ss) while its Timestamps setting is on.
  - **Messages:** a conversation list, plus chat bubbles with pending sends and a request strip.
  - **Players:** search; an actions card (message, TP, friend and enemy through `SocialRegistry`, clan, `/ignore`, detected and configured commands); recent; Argus friends with a "Manage" link to Settings → Friends; online players only while searching.
  - **Clan:** a Discord sync bar (Sync now, Discord settings), add members, CSV import (picker or file drop), and members grouped by role with Discord name and role labels.
  - **Homes, Teleports, Auto TPY, Server:** as Comet. Teleports has requests, timers, and Auto/Manual cooldown lengths with steppers.
  - **Auto TPY:** Queue, Roles, Lists (with import) and Timing. Timing points to the settings page instead of duplicating the number steppers.
- [x] Settings page: the Chat Tools `ModulePage`, so its cards include BetterChat's six cards.
- [x] Entry points: the module key, clicking a name in chat, `/chattools`, a "Chat" top-bar page tab (`PAGE_TABS`, chat icon), and an "Open Chat Tools" button on the module's page. `ChatToolsScreen.attach()` (from the client tick) gives the module its `Window` hook.
- [x] Waiting indicator: `ChatWaitingHudModule` ("Chat Waiting") is a HUD widget registered next to Volytra Speed, so it can be placed and styled in the HUD editor. It shows a line each for requests, unread messages and a Discord sync problem. It replaces Chat Tools' "Waiting notification" setting, which was removed.
- [x] `UiInput.placeholder(String)` added, so the composer's hint follows the conversation. Build green, 464 tests green.
- Changes from Comet:
  - Icons come from Argus's set (the closest match per page).
  - The window follows Argus's GUI scale instead of being a resizable floating window.
  - The Discord connection form lives in Settings → Discord Sync.
- Not yet checked in game (Task 7): any page rendering, input focus, dialogs, CSV picker and drop, the top-bar tab, HUD widget placement.

### Task 7: Import, build, review
- [x] **Settings import:** `MeteorMappings` maps Comet's `chat-tools` onto ChatTools, covering 31 settings. Meteor's NBT bytes become booleans, and its `{r,g,b,a}` colours become Argus's rgba. The open key is left behind. `discord-ign-sync` is a CLIENT module, so it isn't a preset target; its relay comes over with the data instead.
- [x] **Data import:** `ChatToolsDataImport` (pure extract and plan, plus a live `apply`) adds a new `DataKind.CHAT_TOOLS` "Chat Tools data" row in Import → From an instance, read from the main modules.nbt. It only adds:
  - names compare without case
  - a server Argus already has cooldowns or recent players for keeps its own
  - Argus's own sync tracking, roster and relay are kept
  - roster and source come over only together
  - unreadable Discord JSON is skipped
  - Comet's command options join Argus's defaults
  The chat data file is backed up to `import-backups/` first. `ChatToolsModule.dataImported()` refreshes its caches afterwards.
- [x] Tests: 8 in `ChatToolsImportTest`, covering extraction, an empty Argus, add-only rules, importing twice adds nothing, broken Discord data, settings mapping, colours and booleans, and that every mapped name is a real setting. Full suite: 472 green. `./gradlew build` green.
- [x] Self-review of the risky paths: threading in the packet handlers, the BetterChat link, config loading, mixin targets, window input routing and saving. No blocking issues found. README has a new Chat Tools section, plus the import note.
- [x] Installed 2026-11-09 with the game closed (checked for the instance's Java process). The old jar was backed up to `minecraft/mod-backups/argus-client.jar.before-chat-tools-20261109-151827`. Repo release copy `1.21.11/argus-client.jar` updated; all three sha256 match.
- [ ] **In-game test on 6b6t (needs you):**
  - DMs (send, receive, hide, sound), TPA accept/deny, homes, Auto TPY
  - clan colours in chat and tab, clicking names in chat, the window's pages and dialogs
  - CSV import, Discord sync against your relay, the Chat Waiting HUD widget, BetterChat cards
  - whether toggling Chat Tools also shows BetterChat's own toggle notification

## Open questions
1. ~~**Discord:**~~ Decided 2026-11-09: port the relay sync only; Rich Presence is not touched.
2. ~~**Friend colours:**~~ Decided: drop Chat Tools' own friend colours and defer to BetterChat/ExtraTab; add clan highlighting only.
3. ~~**6b6t-specific pages:**~~ Decided: keep Hotspot, Server settings and `/homecancel`, hidden whenever the server doesn't offer those commands.
4. ~~**Webhooks:**~~ Decided: out of scope.

5. ~~**BetterChat:**~~ Decided: merge into one Chat module, with BetterChat kept as a hidden engine (see "Merging with BetterChat").

All open questions are answered; the plan is ready for implementation approval.

# Accounts and Proxies Implementation Plan

> Execute inline using superpowers:executing-plans and test-driven-development.

**Goal:** Add Meteor-style multiplayer account and proxy managers using Argus widgets.
**Architecture:** Independent JSON-backed account and proxy stores under argusclient; asynchronous authentication/checks; Minecraft session accessor and Netty connection mixins; Argus screens opened from multiplayer top right with current status top left.
**Tech Stack:** Java 21, Fabric 1.21.11, MinecraftAuth 5.0.2, Gson, Netty, JUnit.
**Spec:** User request and preceding explanation in this chat: same placement and behaviour as Meteor, existing Argus UI design.

## Constraints and rulings
- Preserve all existing uncommitted work; work in place because GUI integration depends on that work. Do not commit unrelated edits or create a checkout lacking them.
- Independently implement behaviour; do not copy Meteor implementation.
- Microsoft browser authorisation, saved refresh tokens, offline accounts, session tokens and Altening; switches require no active world and affect the next join.
- One active SOCKS4/5 proxy; add/edit/import/check/cleanup/remove; credentials never shown in list/status or errors.
- Persist atomically with restrictive file permissions. Parse failures must not overwrite saved credentials.
- All session/UI mutations on client thread; background operations cancellable and no stale completion on closed screens.

## Review focus
Invalid proxy import must preserve valid entries. Failed account login must preserve current session. Cancelled browser login must not apply. Missing/broken saved files must surface errors. Nested authentication jars must reach merged distribution.

## Tasks
- [ ] 1. Add tested proxy parsing, single selection, persistence and SOCKS handshake checks; prove invalid formats and credential failures are rejected.
- [ ] 2. Add account persistence/authentication and session services replacement; test validation/roundtrip and cancellation coordination.
- [ ] 3. Add managers using UiScreen/UiInput/UiButton; multiplayer buttons/status and connection hook; tests verify hook selection and release contents.
- [ ] 4. Run full test/build and jar checks; review changes; deliver updated argus-client.jar with manual authentication verification limits.

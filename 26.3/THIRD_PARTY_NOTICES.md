# Third-party notices

## Design reference

The floating-window styling and combined module/alias/setting search interaction
were informed by [X-C-0/catppuccin-addon](https://github.com/X-C-0/catppuccin-addon),
inspected at commit `eaf2d93cb39db9261d490fd36c76aa8333a7fb79`.
The GUI implementation in this repository is independently written for this
client's APIs; no Meteor/add-on implementation or assets are bundled.

## Lexend Deca

The bundled Lexend Deca font comes from
[Google Fonts](https://github.com/google/fonts/tree/main/ofl/lexenddeca).
Copyright and SIL Open Font License 1.1 terms are included in
[`OFL-Lexend-Deca.txt`](src/main/resources/licenses/OFL-Lexend-Deca.txt)
and under `licenses/` in the client JAR. The unmodified
source font is in `tools/font-source/lexend-deca.ttf`; it is not loaded by Minecraft.

## VolytraFly

- **Project:** VolytraFly
- **Author:** Volizray
- **Source:** <https://github.com/Volizray/VolytraFly-Addon>
- **Licence:** CC0 1.0 Universal (public domain dedication)
- **Full licence text:** [licenses/VolytraFly-CC0.txt](licenses/VolytraFly-CC0.txt)

The **Volytra Fly** mode in Argus Client is a port and adaptation of the elytra
flight logic from VolytraFly. The original project is dedicated to the public
domain under CC0 1.0 Universal. Argus Client credits Volizray as the author of
the original flight logic; the adaptation, integration and any subsequent
changes are part of Argus Client.

This notice and the full CC0 text are included inside the built client JAR
under `THIRD_PARTY_NOTICES.md` and `licenses/VolytraFly-CC0.txt`.

## DEVILS Auto Wasp

- **Project:** DEVILS
- **Authors:** ThianYong-hub and DEVILS contributors
- **Source:** <https://github.com/ThianYong-hub/DEVILS>, revision `2440a168c3ba6f051f2adf8249a8e6fb5f3a5b02`
- **Also based on:** Meteor Client, Copyright (c) Meteor Development,
  <https://github.com/MeteorDevelopment/meteor-client>
- **Licence:** GNU GPL version 3 only (GPL-3.0-only)
- **Full licence text:** [licenses/GPL-3.0.txt](licenses/GPL-3.0.txt)

The **AutoWasp** module (`AutoWaspModule.java`, the `modules/movement/autowasp`
package and `mixin/AutoWaspFireworkAccessor.java`) is adapted from DEVILS' Auto
Wasp by way of the Comet add-on. Boyonabike modified it for Comet on 22-23
October 2026 (boost ramps, setback handling, server-glide wall guard, target
prediction, chest swapping and a time-budgeted path search) and ported it to
Argus Client on 10 November 2026, replacing the Meteor settings, events,
friends, chat and rotation calls with Argus's own. Each file carries its own
provenance notice.

These files are covered by GPL-3.0-only. A client JAR that contains them is a
combined work, so distributing that JAR means distributing it as a whole under
GPL-3.0-only, together with its corresponding source.

This notice and the full GPL text are included inside the built client JAR
under `THIRD_PARTY_NOTICES.md` and `licenses/GPL-3.0.txt`.

## Meteor Client tooltip previews

- **Project:** Meteor Client
- **Authors:** Meteor Development and contributors
- **Source:** <https://github.com/MeteorDevelopment/meteor-client>
- **Licence:** GNU GPL version 3 (GPL-3.0)
- **Full licence text:** [licenses/GPL-3.0.txt](licenses/GPL-3.0.txt)

The **Tooltips** module's ender chest, book, banner and bucket-mob previews,
its food and status-effect lines, the key-held display mode and the rebindable
open key are ported from Meteor Client's BetterTooltips module. They were
rewritten for Argus's settings, events and Mojang-mapped rendering calls on
10 November 2026 (`TooltipsModule.java`, `BookTooltipComponent.java`,
`BannerTooltipComponent.java`, `EntityTooltipComponent.java` and
`BookPreviewScreen.java`). These parts are covered by GPL-3.0 on the same
terms as the AutoWasp files above.

## Meteor Client boss bar stacking

- **Project:** Meteor Client
- **Authors:** Meteor Development and contributors
- **Source:** <https://github.com/MeteorDevelopment/meteor-client>
- **Licence:** GNU GPL version 3 (GPL-3.0)
- **Full licence text:** [licenses/GPL-3.0.txt](licenses/GPL-3.0.txt)

The **BetterBossBars** module (`BetterBossBarsModule.java` and its hooks in
`BossHealthOverlayMixin.java`) is ported from Meteor Client's BossStack module:
boss bars that share a name are stacked into one with an xN counter, names can
be hidden and the bar spacing changed. It was rewritten for Argus's settings and
Mojang-mapped boss bar overlay on 12 November 2026 and is covered by GPL-3.0 on
the same terms as the AutoWasp files above.

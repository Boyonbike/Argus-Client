# GUI scale, rendering and inline-search correction

User feedback supersedes the earlier Search-tab design: Search belongs in a
draggable/collapsible panel in the right-shift menu. The GUI should be compact,
sharper, and distributed without saved config.

- [x] Decouple the GUI canvas from Minecraft GUI zoom; default panel width ≈196 physical pixels, adjustable in Theme.
- [x] Apply the same transformation to rendering, scissor rectangles, clicks, drag/release and wheel input.
- [x] Bake 128px glyph cells into a2048×896 atlas and use the font for input text/cursor/selection too.
- [x] Generate antialiased nine-slice corners and original high-density category icons.
- [x] Embed Search beside category panels; remove the standalone screen/top tab; retain ranked modules/aliases/settings.
- [x] Gate clipped Search input so it cannot intercept navigation clicks.
- [x] Exclude/reject saved data in release archives and ignore local game-data directories in Git.
- [x] Verify/review the final build, install with the prior client config backed up and an empty config directory, then push.

All font/art rasterization is offline; no runtime TrueType provider is added.

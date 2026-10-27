# Troubleshooting And Known Issues

## No Speech

If the mod does not speak:

1. Confirm `mods\snd-access` in the game folder holds `snd-host-all.jar`, `snd-module.jar` and `prism.dll`.
2. Confirm `SliceAndDice.json` in the game folder lists the three `mods/snd-access` entries in its `vmArgs`; see [Manual Installation](installation.md#manual-installation).
3. Launch through Steam.

A game update, or Steam's "Verify integrity of game files", can put back the game's own `SliceAndDice.json`, and the mod stops loading without a word. Run the installer's Repair, or add the entries again by hand.

## The Game Will Not Start

If the game exits at once after you edited `SliceAndDice.json` by hand, the file was most likely saved with a byte order mark, or is no longer valid JSON. Save it again as UTF-8 without one, check the commas between entries, or put back your copy and start over.

## Shift+Tab Opens Something Else

That is the Steam overlay. Turn it off; see [Installation](installation.md#turn-off-the-steam-overlay-first).

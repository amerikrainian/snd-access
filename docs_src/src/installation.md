# Installation

The mod requires the Steam version of Slice & Dice on Windows.

## Turn Off The Steam Overlay First

Do this before anything else. The overlay's default hotkey is Shift+Tab, which the mod uses to move to the previous control group. With the overlay on, every Shift+Tab opens the overlay instead, and you will be very confused about where your focus went.

To turn it off for Slice & Dice only:

1. In the Steam library, open Slice & Dice's context menu and choose Properties.
2. On the General page, uncheck "Enable the Steam Overlay while in-game".

To turn it off everywhere: Steam menu, Settings, In Game, uncheck "Enable the Steam Overlay while in-game".

## Installer

The recommended install path is the installer. Download `SnDAccessInstaller.exe` from the [latest releases page](https://github.com/amerikrainian/snd-access/releases/latest) and run it. It finds your Steam install on its own, downloads the newest release, verifies it, installs it into the game folder, and adds the mod to the game's launch settings, backing up anything it replaces. It can later update, repair, or uninstall the mod.

The default Steam path on Windows is:

```text
C:\Program Files (x86)\Steam\steamapps\common\Slice_n_Dice
```

## Manual Installation

1. Download the latest `SnDAccess-vX.Y.Z.zip` release.
2. Extract the zip into the game folder (the one containing `SliceAndDice.exe`), replacing files when prompted.
3. After extraction, the game folder should contain `mods\snd-access`, with `snd-host-all.jar`, `snd-module.jar` and `prism.dll` in it.
4. Keep a copy of `SliceAndDice.json` from the game folder. Putting it back is how the mod comes out again.
5. Open `SliceAndDice.json` in a text editor and add these three entries to the end of its `vmArgs` list, keeping the ones already there:

   ```text
   "-javaagent:mods/snd-access/snd-host-all.jar",
   "-Dsnd.module=mods/snd-access/snd-module.jar",
   "-Djna.library.path=mods/snd-access"
   ```

## First Launch

Launch the game through Steam. Once the game is up the mod says "Slice and Dice access X loaded", X being its version, and starts reading focus.

## Updating

At launch the mod checks the releases page and says "Slice and Dice access update X available". To update, run the installer again and choose Update, or extract the latest zip over the game folder. The check can be turned off in the mod menu, under Settings, General.

## Uninstalling

The installer has an uninstall option that restores the game folder from its install records, `SliceAndDice.json` included. To remove the mod manually:

- put back your copy of `SliceAndDice.json`, or remove the three `mods/snd-access` entries from its `vmArgs`;
- delete the `mods\snd-access` folder, and `mods` if nothing else is in it;
- delete the `SnDAccessDocs` folder.

The mod's settings live in `mods\snd-access\settings.json`, so deleting that folder removes them too.

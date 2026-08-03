use std::path::{Path, PathBuf};

// The full release list, newest first: one call serves both the latest-version
// lookup and the per-version release notes shown after an update.
pub const GITHUB_RELEASES_URL: &str =
    "https://api.github.com/repos/amerikrainian/snd-access/releases?per_page=100";
pub const MOD_ZIP_PREFIX: &str = "SnDAccess-v";
pub const MOD_ZIP_SUFFIX: &str = ".zip";
pub const GAME_EXES: &[&str] = &["SliceAndDice.exe"];
// SliceAndDice.exe is a packr-style shim that reads this file to boot the JVM;
// the installer patches its vmArgs so the game's own launcher loads the agent.
// It sits next to the exe in every desktop install, so together they identify
// the game dir.
pub const LAUNCHER_JSON: &str = "SliceAndDice.json";
pub const MOD_DIR_REL: &str = "mods/snd-access";
pub const AGENT_JAR_REL: &str = "mods/snd-access/snd-host-all.jar";
pub const MODULE_JAR_REL: &str = "mods/snd-access/snd-module.jar";
pub const PRISM_DLL_REL: &str = "mods/snd-access/prism.dll";
pub const MANIFEST_REL: &str = "mods/snd-access/install.json";
pub const BACKUPS_REL: &str = "mods/snd-access/backups";

pub fn manifest_path(game_dir: &Path) -> PathBuf {
    game_dir.join(MANIFEST_REL)
}

pub fn normalize_rel(path: &str) -> String {
    path.replace('\\', "/").trim_start_matches("./").to_string()
}

pub fn required_loader_files() -> &'static [&'static str] {
    &[AGENT_JAR_REL, MODULE_JAR_REL, PRISM_DLL_REL]
}

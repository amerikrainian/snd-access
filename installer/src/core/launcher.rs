//! SliceAndDice.json vmArgs patching. The game's packr-style shim reads this
//! file to boot the JVM; adding the agent args there makes the game's own
//! launcher load the mod, with no separate loader executable.

use std::fs;
use std::path::Path;

use serde_json::Value;

use super::paths::LAUNCHER_JSON;

// Paths inside the args are relative to the game dir, which is the shim's cwd.
pub const MOD_VM_ARGS: &[&str] = &[
    "-javaagent:mods/snd-access/snd-host-all.jar",
    "-Dsnd.module=mods/snd-access/snd-module.jar",
    "-Djna.library.path=mods/snd-access",
];

/// Any arg pointing into the mod dir counts as ours, so a re-patch also sweeps
/// stale variants (a hand-edited path, an older arg set).
fn is_mod_arg(arg: &str) -> bool {
    arg.contains("mods/snd-access") || arg.contains("mods\\snd-access")
}

/// Add the mod's vmArgs to SliceAndDice.json, keeping every other arg and
/// field. Idempotent: existing mod args are replaced, not duplicated.
pub fn patch(game_dir: &Path) -> Result<(), String> {
    let mut root = read(game_dir)?;
    let args = vm_args_mut(&mut root)?;
    args.retain(|v| v.as_str().is_none_or(|s| !is_mod_arg(s)));
    for arg in MOD_VM_ARGS {
        args.push(Value::String((*arg).to_string()));
    }
    write(game_dir, &root)
}

/// Remove the mod's vmArgs from SliceAndDice.json. A no-op when none are
/// present (the usual case after uninstall restores the backed-up original),
/// so a pristine file is never rewritten.
pub fn unpatch(game_dir: &Path) -> Result<(), String> {
    if !game_dir.join(LAUNCHER_JSON).exists() {
        return Ok(());
    }
    let mut root = read(game_dir)?;
    let args = vm_args_mut(&mut root)?;
    let before = args.len();
    args.retain(|v| v.as_str().is_none_or(|s| !is_mod_arg(s)));
    if args.len() == before {
        return Ok(());
    }
    write(game_dir, &root)
}

fn vm_args_mut(root: &mut Value) -> Result<&mut Vec<Value>, String> {
    let obj = root
        .as_object_mut()
        .ok_or_else(|| format!("{LAUNCHER_JSON} is not a JSON object"))?;
    obj.entry("vmArgs")
        .or_insert_with(|| Value::Array(Vec::new()))
        .as_array_mut()
        .ok_or_else(|| format!("vmArgs in {LAUNCHER_JSON} is not an array"))
}

fn read(game_dir: &Path) -> Result<Value, String> {
    let path = game_dir.join(LAUNCHER_JSON);
    let text = fs::read_to_string(&path)
        .map_err(|e| format!("Failed to read {}: {e}", path.display()))?;
    serde_json::from_str(text.trim_start_matches('\u{feff}'))
        .map_err(|e| format!("Invalid JSON in {}: {e}", path.display()))
}

/// Written without a UTF-8 BOM: the shim's JSON parser rejects one and exits
/// silently, leaving the game unable to start.
fn write(game_dir: &Path, root: &Value) -> Result<(), String> {
    let path = game_dir.join(LAUNCHER_JSON);
    let json = serde_json::to_string_pretty(root)
        .map_err(|e| format!("Failed to serialize {LAUNCHER_JSON}: {e}"))?;
    fs::write(&path, format!("{json}\n"))
        .map_err(|e| format!("Failed to write {}: {e}", path.display()))
}

#[cfg(test)]
mod tests {
    use super::*;

    const SHIPPED_JSON: &str = r#"{
  "jrePath": "jre",
  "classPath": [
    "dice.jar"
  ],
  "mainClass": "com.tann.dice.desktop.DicetopLauncher",
  "useZgcIfSupportedOs": false,
  "vmArgs": [
    "-Xmx1G"
  ]
}"#;

    fn game_dir_with(json: &str) -> tempfile::TempDir {
        let dir = tempfile::tempdir().unwrap();
        fs::write(dir.path().join(LAUNCHER_JSON), json).unwrap();
        dir
    }

    fn vm_args(dir: &Path) -> Vec<String> {
        let root = read(dir).unwrap();
        root["vmArgs"]
            .as_array()
            .unwrap()
            .iter()
            .map(|v| v.as_str().unwrap().to_string())
            .collect()
    }

    #[test]
    fn patch_appends_mod_args_after_existing_ones() {
        let dir = game_dir_with(SHIPPED_JSON);
        patch(dir.path()).unwrap();
        let args = vm_args(dir.path());
        assert_eq!(args[0], "-Xmx1G");
        assert_eq!(&args[1..], MOD_VM_ARGS);
    }

    #[test]
    fn patch_is_idempotent_and_sweeps_stale_variants() {
        let dir = game_dir_with(
            r#"{"vmArgs": ["-Xmx2G", "-javaagent:mods\\snd-access\\old-name.jar"]}"#,
        );
        patch(dir.path()).unwrap();
        patch(dir.path()).unwrap();
        let args = vm_args(dir.path());
        assert_eq!(args[0], "-Xmx2G");
        assert_eq!(&args[1..], MOD_VM_ARGS);
    }

    #[test]
    fn patch_preserves_field_order_and_writes_no_bom() {
        let dir = game_dir_with(SHIPPED_JSON);
        patch(dir.path()).unwrap();
        let bytes = fs::read(dir.path().join(LAUNCHER_JSON)).unwrap();
        assert_ne!(&bytes[..3], [0xEF, 0xBB, 0xBF]);
        let text = String::from_utf8(bytes).unwrap();
        let order: Vec<usize> = ["jrePath", "classPath", "mainClass", "useZgcIfSupportedOs", "vmArgs"]
            .iter()
            .map(|key| text.find(key).unwrap())
            .collect();
        assert!(order.windows(2).all(|w| w[0] < w[1]));
    }

    #[test]
    fn patch_tolerates_a_bom_left_by_another_tool() {
        let dir = game_dir_with(&format!("\u{feff}{SHIPPED_JSON}"));
        patch(dir.path()).unwrap();
        assert!(vm_args(dir.path()).contains(&MOD_VM_ARGS[0].to_string()));
    }

    #[test]
    fn patch_creates_vm_args_when_absent() {
        let dir = game_dir_with(r#"{"jrePath": "jre"}"#);
        patch(dir.path()).unwrap();
        assert_eq!(vm_args(dir.path()), MOD_VM_ARGS);
    }

    #[test]
    fn unpatch_removes_only_mod_args() {
        let dir = game_dir_with(SHIPPED_JSON);
        patch(dir.path()).unwrap();
        unpatch(dir.path()).unwrap();
        assert_eq!(vm_args(dir.path()), vec!["-Xmx1G"]);
    }

    #[test]
    fn unpatch_leaves_a_pristine_file_untouched() {
        let dir = game_dir_with(SHIPPED_JSON);
        let before = fs::read(dir.path().join(LAUNCHER_JSON)).unwrap();
        unpatch(dir.path()).unwrap();
        assert_eq!(fs::read(dir.path().join(LAUNCHER_JSON)).unwrap(), before);
    }

    #[test]
    fn unpatch_without_launcher_json_is_ok() {
        let dir = tempfile::tempdir().unwrap();
        assert!(unpatch(dir.path()).is_ok());
    }

    #[test]
    fn patch_rejects_invalid_json() {
        let dir = game_dir_with("{not json");
        assert!(patch(dir.path()).is_err());
    }
}

use std::collections::{HashMap, HashSet};
use std::fs;
use std::io::{Read, Seek};
use std::path::{Component, Path, PathBuf};

use semver::Version;
use sha2::{Digest, Sha256};
use time::OffsetDateTime;
use time::format_description::well_known::Rfc3339;
use zip::ZipArchive;

use super::detect::GameSource;
use super::github::Asset;
use super::launcher;
use super::manifest::{InstallManifest, ManifestRead, SUPPORTED_SCHEMA};
use super::paths;
use super::uninstall;

#[derive(Debug, Clone)]
pub enum InstallState {
    Fresh,
    Managed(InstallManifest),
    Unmanaged,
    DamagedState(String),
}

pub fn classify_install(game_dir: &Path) -> InstallState {
    match InstallManifest::read(game_dir) {
        ManifestRead::Valid(manifest) => InstallState::Managed(manifest),
        ManifestRead::Missing => {
            if has_installed_mod_files(game_dir) {
                InstallState::Unmanaged
            } else {
                InstallState::Fresh
            }
        }
        ManifestRead::Invalid(reason) => {
            if has_installed_mod_files(game_dir) {
                InstallState::DamagedState(reason)
            } else {
                InstallState::Fresh
            }
        }
    }
}

pub fn has_installed_mod_files(game_dir: &Path) -> bool {
    paths::required_loader_files()
        .iter()
        .any(|rel| game_dir.join(rel).exists())
}

pub fn installed_version(state: &InstallState) -> Option<Version> {
    match state {
        InstallState::Managed(manifest) => Version::parse(&manifest.mod_version).ok(),
        _ => None,
    }
}

pub fn verify_sha256(path: &Path, expected: &str) -> Result<(), String> {
    let actual = sha256_file(path)?;
    if !actual.eq_ignore_ascii_case(expected) {
        return Err(format!(
            "Downloaded zip digest mismatch. Expected {expected}, got {actual}."
        ));
    }
    Ok(())
}

/// Clear the read-only attribute so the file can be overwritten or removed.
/// Windows refuses both on a read-only file even for an elevated process
/// (e.g. a jar extracted read-only by a manual unzip).
pub fn ensure_writable(path: &Path) -> Result<(), String> {
    let Ok(metadata) = fs::metadata(path) else {
        return Ok(());
    };
    let mut permissions = metadata.permissions();
    if permissions.readonly() {
        permissions.set_readonly(false);
        fs::set_permissions(path, permissions).map_err(|e| {
            format!(
                "Failed to clear the read-only attribute on {}: {e}",
                path.display()
            )
        })?;
    }
    Ok(())
}

pub fn sha256_file(path: &Path) -> Result<String, String> {
    let mut file =
        fs::File::open(path).map_err(|e| format!("Failed to open file for hashing: {e}"))?;
    let mut hasher = Sha256::new();
    let mut buffer = [0u8; 81920];
    loop {
        let read = file
            .read(&mut buffer)
            .map_err(|e| format!("Failed to read file for hashing: {e}"))?;
        if read == 0 {
            break;
        }
        hasher.update(&buffer[..read]);
    }
    let digest = hasher.finalize();
    Ok(digest.iter().map(|b| format!("{b:02x}")).collect())
}

pub fn install_from_zip(
    zip_path: &Path,
    game_dir: &Path,
    source: &GameSource,
    asset: &Asset,
    prior_state: &InstallState,
) -> Result<InstallManifest, String> {
    let version = asset
        .version()
        .ok_or_else(|| format!("Release asset name is not a mod zip: {}", asset.name))?;
    let prior_manifest = match prior_state {
        InstallState::Managed(manifest) => Some(manifest),
        _ => None,
    };
    let mut backups = prior_manifest
        .map(|m| m.backups.clone())
        .unwrap_or_else(HashMap::new);
    let prior_owned: HashSet<String> = prior_manifest
        .map(|m| m.installed_files.iter().cloned().collect())
        .unwrap_or_default();
    let backup_stamp = backup_stamp()?;
    let mut installed_files = Vec::new();

    let file = fs::File::open(zip_path).map_err(|e| format!("Failed to open zip: {e}"))?;
    let mut archive = ZipArchive::new(file).map_err(|e| format!("Failed to read zip: {e}"))?;
    extract_archive(
        &mut archive,
        game_dir,
        &prior_owned,
        &mut backups,
        &backup_stamp,
        &mut installed_files,
    )?;

    prune_orphans(game_dir, &prior_owned, &installed_files, &mut backups)?;

    // The launcher json is patched in place, not shipped in the zip; the backup
    // taken before the first patch is what uninstall restores. An upgrade keeps
    // that first backup - the current file already carries the mod's args.
    if !backups.contains_key(paths::LAUNCHER_JSON) {
        backup_file(game_dir, paths::LAUNCHER_JSON, &mut backups, &backup_stamp)?;
    }
    launcher::patch(game_dir)?;

    let sha256 = asset.sha256_digest().or_else(|| sha256_file(zip_path).ok());
    let manifest = InstallManifest {
        schema_version: SUPPORTED_SCHEMA,
        mod_version: version,
        installed_at: OffsetDateTime::now_utc()
            .format(&Rfc3339)
            .unwrap_or_else(|_| "unknown".to_string()),
        source: source.as_manifest_str().to_string(),
        release_asset: asset.name.clone(),
        sha256,
        installed_files,
        backups,
    };
    manifest.write(game_dir)?;
    Ok(manifest)
}

/// Remove files the prior install owned that the new zip no longer ships, so an
/// upgrade never strands a stale file. A file the prior install had backed up
/// (it overwrote something pre-existing) is restored from that backup instead,
/// the same as uninstall would.
fn prune_orphans(
    game_dir: &Path,
    prior_owned: &HashSet<String>,
    installed_files: &[String],
    backups: &mut HashMap<String, String>,
) -> Result<(), String> {
    let current: HashSet<&String> = installed_files.iter().collect();
    for rel in prior_owned {
        if current.contains(rel) {
            continue;
        }
        let target = game_dir.join(rel);
        ensure_writable(&target)?;
        if let Some(backup_rel) = backups.remove(rel) {
            let backup = game_dir.join(&backup_rel);
            if backup.exists() {
                fs::copy(&backup, &target)
                    .map_err(|e| format!("Failed to restore {}: {e}", target.display()))?;
                fs::remove_file(&backup)
                    .map_err(|e| format!("Failed to remove backup {}: {e}", backup.display()))?;
                uninstall::remove_empty_parents(game_dir, backup.parent());
                continue;
            }
        }
        if target.exists() {
            fs::remove_file(&target)
                .map_err(|e| format!("Failed to remove {}: {e}", target.display()))?;
            uninstall::remove_empty_parents(game_dir, target.parent());
        }
    }
    Ok(())
}

fn extract_archive<R: Read + Seek>(
    archive: &mut ZipArchive<R>,
    game_dir: &Path,
    prior_owned: &HashSet<String>,
    backups: &mut HashMap<String, String>,
    backup_stamp: &str,
    installed_files: &mut Vec<String>,
) -> Result<(), String> {
    for i in 0..archive.len() {
        let mut entry = archive
            .by_index(i)
            .map_err(|e| format!("Failed to read zip entry: {e}"))?;
        let raw_name = entry.name().to_string();
        let Some(rel) = safe_zip_entry_name(&raw_name) else {
            return Err(format!("Unsafe zip entry path: {raw_name}"));
        };
        if rel.as_os_str().is_empty() {
            continue;
        }
        let dest = game_dir.join(&rel);
        if entry.is_dir() {
            fs::create_dir_all(&dest)
                .map_err(|e| format!("Failed to create directory {}: {e}", dest.display()))?;
            continue;
        }

        let rel_key = paths::normalize_rel(rel.to_string_lossy().as_ref());
        if dest.exists() && !prior_owned.contains(&rel_key) && !backups.contains_key(&rel_key) {
            backup_file(game_dir, &rel_key, backups, backup_stamp)?;
        }

        if let Some(parent) = dest.parent() {
            fs::create_dir_all(parent)
                .map_err(|e| format!("Failed to create parent directory: {e}"))?;
        }
        ensure_writable(&dest)?;
        let mut output = fs::File::create(&dest)
            .map_err(|e| format!("Failed to create {}: {e}", dest.display()))?;
        std::io::copy(&mut entry, &mut output)
            .map_err(|e| format!("Failed to write {}: {e}", dest.display()))?;
        installed_files.push(rel_key);
    }
    Ok(())
}

fn backup_file(
    game_dir: &Path,
    rel_key: &str,
    backups: &mut HashMap<String, String>,
    backup_stamp: &str,
) -> Result<(), String> {
    let src = game_dir.join(rel_key);
    if !src.exists() {
        return Ok(());
    }
    let backup_rel = paths::normalize_rel(
        Path::new(paths::BACKUPS_REL)
            .join(backup_stamp)
            .join(rel_key)
            .to_string_lossy()
            .as_ref(),
    );
    let backup_abs = game_dir.join(&backup_rel);
    if let Some(parent) = backup_abs.parent() {
        fs::create_dir_all(parent)
            .map_err(|e| format!("Failed to create backup directory: {e}"))?;
    }
    fs::copy(&src, &backup_abs).map_err(|e| format!("Failed to back up {}: {e}", src.display()))?;
    // fs::copy carries the read-only attribute onto the backup, which would
    // block removing it on uninstall.
    ensure_writable(&backup_abs)?;
    backups.insert(rel_key.to_string(), backup_rel);
    Ok(())
}

fn backup_stamp() -> Result<String, String> {
    let now = OffsetDateTime::now_utc()
        .format(&Rfc3339)
        .map_err(|e| format!("Failed to format backup timestamp: {e}"))?;
    Ok(now
        .replace(':', "")
        .replace('-', "")
        .replace('T', "_")
        .replace('Z', "Z"))
}

pub fn temp_session_dir() -> PathBuf {
    let nanos = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_nanos())
        .unwrap_or(0);
    std::env::temp_dir()
        .join("SnDAccessInstaller")
        .join(format!("{}-{nanos}", std::process::id()))
}

pub fn safe_zip_entry_name(name: &str) -> Option<PathBuf> {
    let normalized = name.replace('\\', "/");
    let path = Path::new(&normalized);
    let mut out = PathBuf::new();
    for component in path.components() {
        match component {
            Component::Normal(part) => out.push(part),
            Component::CurDir => {}
            Component::ParentDir | Component::RootDir | Component::Prefix(_) => return None,
        }
    }
    Some(out)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    use crate::core::github::Asset;
    use crate::core::launcher::MOD_VM_ARGS;
    use crate::core::uninstall;

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

    fn game_fixture() -> tempfile::TempDir {
        let dir = tempfile::tempdir().unwrap();
        fs::write(dir.path().join("SliceAndDice.exe"), "shim").unwrap();
        fs::write(dir.path().join(paths::LAUNCHER_JSON), SHIPPED_JSON).unwrap();
        dir
    }

    fn launcher_vm_args(game_dir: &Path) -> Vec<String> {
        let text = fs::read_to_string(game_dir.join(paths::LAUNCHER_JSON)).unwrap();
        let root: serde_json::Value = serde_json::from_str(&text).unwrap();
        root["vmArgs"]
            .as_array()
            .unwrap()
            .iter()
            .map(|v| v.as_str().unwrap().to_string())
            .collect()
    }

    #[test]
    fn rejects_zip_slip_paths() {
        assert!(safe_zip_entry_name("../../evil.dll").is_none());
        assert!(safe_zip_entry_name("C:/evil.dll").is_none());
        assert!(safe_zip_entry_name("/evil.dll").is_none());
        assert!(safe_zip_entry_name("mods/snd-access/snd-host-all.jar").is_some());
        // Compress-Archive writes backslash separators into the release zip.
        assert_eq!(
            safe_zip_entry_name("mods\\snd-access\\snd-host-all.jar"),
            Some(PathBuf::from("mods/snd-access/snd-host-all.jar"))
        );
    }

    #[test]
    fn classifies_unmanaged_by_file_presence_only() {
        let dir = tempfile::tempdir().unwrap();
        let jar = dir.path().join(paths::AGENT_JAR_REL);
        fs::create_dir_all(jar.parent().unwrap()).unwrap();
        fs::write(jar, "not a real jar").unwrap();
        assert!(matches!(
            classify_install(dir.path()),
            InstallState::Unmanaged
        ));
    }

    #[test]
    fn invalid_manifest_with_files_is_damaged_state() {
        let dir = tempfile::tempdir().unwrap();
        let manifest = paths::manifest_path(dir.path());
        fs::create_dir_all(manifest.parent().unwrap()).unwrap();
        fs::write(&manifest, "{not json").unwrap();
        let jar = dir.path().join(paths::AGENT_JAR_REL);
        fs::create_dir_all(jar.parent().unwrap()).unwrap();
        fs::write(jar, "").unwrap();
        assert!(matches!(
            classify_install(dir.path()),
            InstallState::DamagedState(_)
        ));
    }

    #[test]
    fn fresh_install_then_uninstall_leaves_game_dir_clean() {
        let dir = game_fixture();
        let zip_path = dir.path().join("release.zip");
        create_zip(
            &zip_path,
            &[
                (paths::AGENT_JAR_REL, "agent"),
                (paths::MODULE_JAR_REL, "module"),
                (paths::PRISM_DLL_REL, "speech"),
            ],
        );

        let manifest = install_from_zip(
            &zip_path,
            dir.path(),
            &GameSource::Manual,
            &test_asset(),
            &InstallState::Fresh,
        )
        .unwrap();

        // The launcher json is patched and its original backed up.
        assert!(manifest.backups.contains_key(paths::LAUNCHER_JSON));
        let args = launcher_vm_args(dir.path());
        assert_eq!(args[0], "-Xmx1G");
        assert_eq!(&args[1..], MOD_VM_ARGS);
        assert!(dir.path().join(paths::AGENT_JAR_REL).exists());
        assert!(matches!(
            classify_install(dir.path()),
            InstallState::Managed(_)
        ));

        uninstall::uninstall(dir.path(), &manifest).unwrap();

        assert!(!dir.path().join("mods").exists());
        assert!(!paths::manifest_path(dir.path()).exists());
        assert_eq!(
            fs::read_to_string(dir.path().join(paths::LAUNCHER_JSON)).unwrap(),
            SHIPPED_JSON
        );
        assert!(dir.path().join("SliceAndDice.exe").exists());
    }

    #[test]
    fn overwritten_file_is_backed_up_and_restored_on_uninstall() {
        let dir = game_fixture();
        // A pre-existing prism.dll (say, a deploy.ps1 install) must survive a
        // managed install + uninstall round trip.
        let prism = dir.path().join(paths::PRISM_DLL_REL);
        fs::create_dir_all(prism.parent().unwrap()).unwrap();
        fs::write(&prism, "original speech").unwrap();

        let zip_path = dir.path().join("release.zip");
        create_zip(
            &zip_path,
            &[
                (paths::AGENT_JAR_REL, "agent"),
                (paths::PRISM_DLL_REL, "new speech"),
            ],
        );

        let manifest = install_from_zip(
            &zip_path,
            dir.path(),
            &GameSource::Manual,
            &test_asset(),
            &InstallState::Unmanaged,
        )
        .unwrap();

        let backup_rel = manifest
            .backups
            .get(paths::PRISM_DLL_REL)
            .expect("existing file should be backed up")
            .clone();
        assert!(dir.path().join(&backup_rel).exists());
        assert_eq!(fs::read_to_string(&prism).unwrap(), "new speech");

        uninstall::uninstall(dir.path(), &manifest).unwrap();

        assert_eq!(fs::read_to_string(&prism).unwrap(), "original speech");
        assert!(!dir.path().join(paths::BACKUPS_REL).exists());
    }

    #[test]
    fn overwrites_and_uninstalls_read_only_files() {
        let dir = game_fixture();
        // A read-only pre-existing file (e.g. from a manual unzip) must not
        // block install or uninstall.
        let existing = dir.path().join(paths::MODULE_JAR_REL);
        fs::create_dir_all(existing.parent().unwrap()).unwrap();
        fs::write(&existing, "old module").unwrap();
        let mut perms = fs::metadata(&existing).unwrap().permissions();
        perms.set_readonly(true);
        fs::set_permissions(&existing, perms).unwrap();

        let zip_path = dir.path().join("release.zip");
        create_zip(
            &zip_path,
            &[
                (paths::AGENT_JAR_REL, "agent"),
                (paths::MODULE_JAR_REL, "new module"),
            ],
        );

        let manifest = install_from_zip(
            &zip_path,
            dir.path(),
            &GameSource::Manual,
            &test_asset(),
            &InstallState::Unmanaged,
        )
        .unwrap();

        assert_eq!(fs::read_to_string(&existing).unwrap(), "new module");
        let backup_rel = manifest.backups.get(paths::MODULE_JAR_REL).unwrap();
        assert_eq!(
            fs::read_to_string(dir.path().join(backup_rel)).unwrap(),
            "old module"
        );

        uninstall::uninstall(dir.path(), &manifest).unwrap();

        assert_eq!(fs::read_to_string(&existing).unwrap(), "old module");
        assert!(!dir.path().join(paths::BACKUPS_REL).exists());
    }

    #[test]
    fn orphaned_file_with_backup_is_restored_on_upgrade() {
        let dir = game_fixture();
        // v1 shipped prism.dll over a pre-existing copy and backed the original up.
        let prism = dir.path().join(paths::PRISM_DLL_REL);
        fs::create_dir_all(prism.parent().unwrap()).unwrap();
        fs::write(&prism, "user original").unwrap();
        let zip1 = dir.path().join("v1.zip");
        create_zip(
            &zip1,
            &[
                (paths::AGENT_JAR_REL, "agent"),
                (paths::PRISM_DLL_REL, "modded"),
            ],
        );
        let m1 = install_from_zip(
            &zip1,
            dir.path(),
            &GameSource::Manual,
            &test_asset(),
            &InstallState::Unmanaged,
        )
        .unwrap();
        assert!(m1.backups.contains_key(paths::PRISM_DLL_REL));

        // v2 no longer ships it: the original comes back and the backup is consumed.
        let zip2 = dir.path().join("v2.zip");
        create_zip(&zip2, &[(paths::AGENT_JAR_REL, "agent v2")]);
        let m2 = install_from_zip(
            &zip2,
            dir.path(),
            &GameSource::Manual,
            &test_asset(),
            &InstallState::Managed(m1),
        )
        .unwrap();
        assert_eq!(fs::read_to_string(&prism).unwrap(), "user original");
        assert!(!m2.backups.contains_key(paths::PRISM_DLL_REL));
    }

    #[test]
    fn upgrade_keeps_the_original_launcher_backup() {
        let dir = game_fixture();
        let zip = dir.path().join("release.zip");
        create_zip(&zip, &[(paths::AGENT_JAR_REL, "agent v1")]);
        let m1 = install_from_zip(
            &zip,
            dir.path(),
            &GameSource::Manual,
            &test_asset(),
            &InstallState::Fresh,
        )
        .unwrap();
        let first_backup = m1.backups.get(paths::LAUNCHER_JSON).unwrap().clone();

        // The current json now carries the mod args; the v2 install must keep
        // backing the ORIGINAL, not the patched file.
        let zip2 = dir.path().join("v2.zip");
        create_zip(&zip2, &[(paths::AGENT_JAR_REL, "agent v2")]);
        let m2 = install_from_zip(
            &zip2,
            dir.path(),
            &GameSource::Manual,
            &test_asset(),
            &InstallState::Managed(m1),
        )
        .unwrap();
        assert_eq!(m2.backups.get(paths::LAUNCHER_JSON).unwrap(), &first_backup);

        uninstall::uninstall(dir.path(), &m2).unwrap();
        assert_eq!(
            fs::read_to_string(dir.path().join(paths::LAUNCHER_JSON)).unwrap(),
            SHIPPED_JSON
        );
    }

    #[test]
    fn uninstall_unpatches_launcher_when_backup_is_missing() {
        let dir = game_fixture();
        let zip = dir.path().join("release.zip");
        create_zip(&zip, &[(paths::AGENT_JAR_REL, "agent")]);
        let manifest = install_from_zip(
            &zip,
            dir.path(),
            &GameSource::Manual,
            &test_asset(),
            &InstallState::Fresh,
        )
        .unwrap();
        let backup_rel = manifest.backups.get(paths::LAUNCHER_JSON).unwrap();
        fs::remove_file(dir.path().join(backup_rel)).unwrap();

        uninstall::uninstall(dir.path(), &manifest).unwrap();

        // No backup to restore, but the game must still boot: the mod args are
        // stripped from the surviving json.
        assert_eq!(launcher_vm_args(dir.path()), vec!["-Xmx1G"]);
    }

    #[test]
    fn uninstall_removes_empty_dirs_the_zip_created() {
        let dir = game_fixture();
        // The mod zip can carry directory entries with no files in them; the
        // manifest records files only, so uninstall must sweep them separately.
        let zip_path = dir.path().join("release.zip");
        let file = fs::File::create(&zip_path).unwrap();
        let mut zip = zip::ZipWriter::new(file);
        let options = zip::write::SimpleFileOptions::default();
        zip.add_directory("mods/snd-access/LICENSES", options).unwrap();
        zip.start_file(paths::AGENT_JAR_REL, options).unwrap();
        zip.write_all(b"agent").unwrap();
        zip.finish().unwrap();

        let manifest = install_from_zip(
            &zip_path,
            dir.path(),
            &GameSource::Manual,
            &test_asset(),
            &InstallState::Fresh,
        )
        .unwrap();
        assert!(dir.path().join("mods/snd-access/LICENSES").is_dir());

        uninstall::uninstall(dir.path(), &manifest).unwrap();
        assert!(!dir.path().join("mods").exists());
    }

    fn create_zip(path: &Path, entries: &[(&str, &str)]) {
        let file = fs::File::create(path).unwrap();
        let mut zip = zip::ZipWriter::new(file);
        let options = zip::write::SimpleFileOptions::default();
        for (name, content) in entries {
            zip.start_file(*name, options).unwrap();
            zip.write_all(content.as_bytes()).unwrap();
        }
        zip.finish().unwrap();
    }

    fn test_asset() -> Asset {
        Asset {
            name: "SnDAccess-v1.2.3.zip".to_string(),
            browser_download_url: "https://example.invalid/release.zip".to_string(),
            digest: None,
        }
    }
}

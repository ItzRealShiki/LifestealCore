#!/usr/bin/env python3
import hashlib
import json
from pathlib import Path
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parent
GAME_VERSION = "26.3"

def package():
    compat = json.loads((ROOT / "compatibility.json").read_text())
    manifest_path = ROOT / "mixins/fastplace/horizon-mixin.toml"
    mixin = tomllib.loads(manifest_path.read_text())
    mod = mixin["mod"]
    if mixin["compatibility"]["horizon_revision"] != compat["horizon_revision"]:
        raise SystemExit("Horizon revision mismatch")
    manifest = {
        "schema": 1,
        "id": mod["id"],
        "version": mod["version"],
        "game": GAME_VERSION,
        "horizon": compat["horizon_revision"],
        "mixins": compat["horizon_mixins_revision"],
    }
    entries = {
        "horizon-native.json": (json.dumps(manifest, indent=2, sort_keys=True) + "\n").encode(),
    }
    for path in sorted((ROOT / "mixins/fastplace").glob("*")):
        if path.is_file() and path.suffix in (".rs", ".toml"):
            entries[path.relative_to(ROOT).as_posix()] = path.read_bytes()
    output = ROOT / "dist" / f'{mod["id"]}-{mod["version"]}.hnmod'
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, content in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, content)
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    print(json.dumps({"file": str(output), "size": output.stat().st_size, "sha256": digest}, indent=2))

if __name__ == "__main__":
    package()

"""Package authentic Mystery P.I.: The Vegas Heist resources into case-recomp-sda-content ZIP."""
import hashlib
import json
import os
from pathlib import Path
import sys
import zipfile
from PIL import Image
import io

root = Path(__file__).resolve().parent
sys.path.insert(0, str(root))

from runtime import Resources, parse_xui, local_name


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def build_package(resources_dll_path: Path, output_zip_path: Path, scene_name: str = "SCENE_VAULT.MSL"):
    res = Resources(resources_dll_path)
    output_zip_path.parent.mkdir(parents=True, exist_ok=True)

    files_data = {}

    # Cover image from distributor
    dist_img = Image.open("private/mystery-pi-vegas/game/distributor.jpg")
    cover_buf = io.BytesIO()
    dist_img.resize((320, 240)).save(cover_buf, format="PNG")
    files_data["cover.png"] = cover_buf.getvalue()

    # Base strings
    if "STRINGS.TXT" in res.entries:
        files_data["STRINGS.TXT"] = res.read("STRINGS.TXT")

    # Scene strings
    scene_txt = scene_name.rsplit(".", 1)[0] + ".TXT"
    if scene_txt in res.entries:
        files_data[scene_txt] = res.read(scene_txt)

    # Scene XUI
    scene_raw = res.read(scene_name)
    files_data[scene_name] = scene_raw

    # Parse XUI to find all textures
    tree = parse_xui(scene_raw)
    textures = {x.attrib["id"]: x.attrib["uri"] for x in tree.iter() if local_name(x.tag) == "texture"}
    print(f"Found {len(textures)} textures declared in {scene_name}")

    for tid, uri in textures.items():
        entry_name = uri.upper()
        if entry_name in res.entries:
            files_data[uri] = res.read(entry_name)
        else:
            print(f"Warning: texture {uri} ({entry_name}) not found in Resources.dll")

    # Build manifest
    files_meta = {}
    for path, data in files_data.items():
        files_meta[path] = [len(data), sha256(data)]

    manifest = {
        "format": "case-recomp-sda-content",
        "version": 1,
        "game_id": "vegas_heist",
        "cover": "cover.png",
        "files": files_meta
    }
    manifest_bytes = json.dumps(manifest, indent=2, sort_keys=True).encode("utf-8")
    files_data["manifest.json"] = manifest_bytes

    # Write zip
    with zipfile.ZipFile(output_zip_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for path, data in files_data.items():
            zf.writestr(path, data)

    print(f"Successfully generated package at {output_zip_path} ({output_zip_path.stat().st_size} bytes, {len(files_data)} files)")


if __name__ == "__main__":
    dll_path = Path("private/mystery-pi-vegas/game/Resources.dll")
    out_path = Path("local-output/vegas_vault.zip")
    build_package(dll_path, out_path)

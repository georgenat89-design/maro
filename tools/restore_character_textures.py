"""Audit or restore original GLB textures without changing a character's rig or mesh.

Run with --write to replace only the image section of each shipped version-5 ANC.
No resampling or JPEG recompression: original image bytes and UVs remain intact.
"""
import argparse
import io
import json
from pathlib import Path
import struct

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/maro/characters"


class Reader:
    def __init__(self, data):
        self.data, self.at = data, 0

    def integer(self):
        value = struct.unpack_from(">i", self.data, self.at)[0]
        self.at += 4
        return value

    def text(self):
        count = struct.unpack_from(">H", self.data, self.at)[0]
        self.at += 2
        value = self.data[self.at:self.at + count].decode("utf-8")
        self.at += count
        return value

    def skip(self, count):
        self.at += count


def original_images(path):
    data = path.read_bytes()
    assert struct.unpack_from("<II", data) == (0x46546C67, 2)
    length = struct.unpack_from("<I", data, 12)[0]
    model = json.loads(data[20:20 + length])
    binary = data[28 + length:]
    result = {}
    for material in model.get("materials", []):
        pbr = material.get("pbrMetallicRoughness", {})
        texture = pbr.get("baseColorTexture") or material.get("extensions", {}).get(
            "KHR_materials_pbrSpecularGlossiness", {}).get("diffuseTexture")
        if texture is None:
            continue
        source = model["textures"][texture["index"]]["source"]
        view = model["bufferViews"][model["images"][source]["bufferView"]]
        offset = view.get("byteOffset", 0)
        encoded = binary[offset:offset + view["byteLength"]]
        with Image.open(io.BytesIO(encoded)) as image:
            width, height = image.size
        result[material["name"]] = (width, height, encoded)
    return result


def restore(path, write):
    source = original_images(path.with_suffix(".glb"))
    data = path.read_bytes()
    r = Reader(data)
    assert (r.integer(), r.integer()) == (1095648072, 5), path
    r.skip(1)
    nodes = r.integer()
    for _ in range(nodes):
        r.text()
        r.skip(134)
    r.skip(r.integer() * 4)
    r.skip(r.integer() * 4)
    r.skip((4 + 12 + 4 + 2 + 2 + 3 + 6 + 6 + 18) * 4 + 1)
    by_index = {}
    materials = r.integer()
    for _ in range(materials):
        name, index = r.text(), r.integer()
        r.skip(7)
        if index >= 0:
            assert name in source, (path, name)
            if index in by_index:
                assert by_index[index] == source[name], (path, name, index)
            by_index[index] = source[name]
    start = r.at
    count = r.integer()
    out = bytearray(struct.pack(">i", count))
    changed = 0
    for i in range(count):
        raw = data[r.at]
        r.skip(1)
        old_width, old_height, size = r.integer(), r.integer(), r.integer()
        old_bytes = data[r.at:r.at + size]
        r.skip(size)
        width, height, encoded = by_index[i]
        if raw or (old_width, old_height, old_bytes) != (width, height, encoded):
            changed += 1
        out += struct.pack(">?iii", False, width, height, len(encoded)) + encoded
    if write and changed:
        path.write_bytes(data[:start] + out + data[r.at:])
    print(f"{path.stem}: {count} original images, {changed} {'restored' if write else 'differ'}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args()
    for anc in sorted(ASSETS.glob("*.anc")):
        if anc.with_suffix(".glb").exists():
            restore(anc, args.write)

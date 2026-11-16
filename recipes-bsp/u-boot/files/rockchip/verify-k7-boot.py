"""Verify the RK FIT hashes, firmware entry and IDB inputs before deployment."""
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import sys

build = Path(sys.argv[1])
fit_path = build / 'u-boot.itb'
fit = fit_path.read_bytes()
magic, total = struct.unpack_from('>II', fit)
if magic != 0xd00dfeed:
    raise SystemExit('Invalid FIT header')

def query(*args):
    result = subprocess.run(['fdtget', *args], capture_output=True, text=True)
    return result.returncode, result.stdout.strip()

def prop(node, key):
    code, text = query('-t', 'bx', str(fit_path), node, key)
    return None if code else bytes(int(word, 16) for word in text.split())

def integer(node, key):
    value = prop(node, key)
    return None if value is None else int.from_bytes(value, 'big')

def require(condition, message):
    if not condition:
        raise SystemExit(message)

code, names = query('-l', str(fit_path), '/images')
require(code == 0, 'Missing FIT images')
images = []
payloads = {}
for name in names.split():
    node = '/images/' + name
    payload = prop(node, 'data')
    if payload is None:
        size = integer(node, 'data-size')
        start = integer(node, 'data-position')
        if start is None:
            offset = integer(node, 'data-offset')
            require(offset is not None, f'Missing FIT data location: {name}')
            start = ((total + 511) & ~511) + offset
        require(size is not None and start >= total and start + size <= len(fit),
                f'Invalid FIT data bounds: {name}')
        payload = fit[start:start + size]
    expected = prop(node + '/hash', 'value')
    require(prop(node + '/hash', 'algo') == b'sha256\0', f'Unsupported FIT hash: {name}')
    digest = hashlib.sha256(payload).digest()
    require(digest == expected, f'FIT SHA256 mismatch: {name}')
    payloads[name] = payload
    images.append({'name': name, 'size': len(payload), 'load': integer(node, 'load'),
                   'sha256': digest.hex()})
require(prop('/configurations/conf', 'firmware') == b'atf-1\0', 'FIT must start BL31')
require(b'optee' in (prop('/configurations/conf', 'loadables') or b'').split(b'\0'),
        'FIT configuration does not load BL32')
elf = (build / 'bl31.elf').read_bytes()
require(elf[:6] == b'\x7fELF\x02\x01', 'BL31 must be little-endian ELF64')
entry = struct.unpack_from('<Q', elf, 24)[0]
require(integer('/images/atf-1', 'load') == entry, 'BL31 FIT load differs from ELF entry')
for image, filename in [('uboot', 'u-boot-nodtb.bin'), ('optee', 'tee.bin'), ('fdt', 'u-boot.dtb')]:
    require(payloads.get(image) == (build / filename).read_bytes(), f'FIT input differs: {image}')
idb = (build / 'k7-rockchip-idbloader.img').read_bytes()
spl = (build / 'spl/u-boot-spl.bin').read_bytes()
ddr = (build / 'rk3576-ddr-k7-1560.bin').read_bytes()
require(idb.find(spl) >= 0 and idb.find(ddr) >= 0, 'IDB does not contain current SPL/DDR')
require(len(idb) <= 8355840 and len(fit) <= 8388608, 'Boot images overlap WIC partitions')
report = {'components': json.loads((build / 'k7-rockchip-components.json').read_text()),
          'fit_images': images, 'bl31_entry': hex(entry),
          'idb_size': len(idb), 'fit_size': len(fit),
          'fit_hashes': 'passed', 'fit_inputs': 'passed', 'idb_inputs': 'passed',
          'partition_bounds': 'passed', 'full_wic': 'not validated by this check',
          'hardware_validation': 'pending for v5 + develop-6.12 combination'}
(build / 'k7-rockchip-validation.json').write_text(json.dumps(report, indent=2) + '\n')
print(f'K7 verification passed: {len(images)} FIT hashes, BL31 entry, firmware inputs and IDB bounds')

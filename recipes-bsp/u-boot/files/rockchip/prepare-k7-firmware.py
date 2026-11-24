"""Select RK3576 firmware from the checked-out Rockchip configuration files."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys

from k7_rkbin import firmware, select_inputs

rkbin, build, uboot, inputs = map(Path, sys.argv[1:5])

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def revision(path):
    return subprocess.check_output(['git', '-C', str(path), 'rev-parse', 'HEAD'], text=True).strip()

loader, trust, ddr, bl31 = select_inputs(rkbin)
bl32 = firmware(rkbin, trust['BL32_OPTION']['PATH'])
if trust['BL32_OPTION']['SEC'] != '1':
    raise SystemExit('Expected enabled BL32_OPTION')
for section, key in [('LOADER_OPTION', 'FlashBoost'), ('CODE472_OPTION', 'Path1')]:
    firmware(rkbin, loader[section][key])
for name in ('boot_merger', 'ddrbin_tool.py'):
    firmware(rkbin, 'tools/' + name)
shutil.copyfile(bl31, build / 'bl31.elf')
shutil.copyfile(bl32, build / 'tee.bin')
tee_offset = int(trust['BL32_OPTION']['ADDR'], 0)
if not 0 < tee_offset < 0x40000000:
    raise SystemExit('Unexpected RK3576 BL32 DRAM offset')
(build / 'k7-rockchip-tee-offset').write_text(hex(tee_offset) + '\n')
subprocess.run([sys.executable, str(inputs / 'prepare-k7-ddr.py'), str(rkbin),
                str(inputs / 'k7-ddr-1560.params'), str(build / 'rk3576-ddr-k7-1560.bin'),
                str(inputs / 'rk3576_ddr_lp4_1560MHz_lp5_2736MHz_v1.08.bin'), str(ddr)], check=True)
paths = {'ddr_original': ddr, 'bl31': bl31, 'bl32': bl32,
         'boost': firmware(rkbin, loader['LOADER_OPTION']['FlashBoost']),
         'usbplug': firmware(rkbin, loader['CODE472_OPTION']['Path1']),
         'boot_merger': firmware(rkbin, 'tools/boot_merger')}
report = {'uboot_branch': 'v5', 'uboot_commit': revision(uboot),
          'rkbin_branch': 'master', 'rkbin_commit': revision(rkbin),
          'firmware': {name: {'path': str(path.relative_to(rkbin.resolve())), 'sha256': digest(path)}
                       for name, path in paths.items()},
          'ddr_output_sha256': digest(build / 'rk3576-ddr-k7-1560.bin'),
          'hardware_validation': 'pending'}
(build / 'k7-rockchip-components.json').write_text(json.dumps(report, indent=2) + '\n')

"""Select RK3576 firmware from the checked-out Rockchip configuration files."""
import configparser
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys

rkbin, build, uboot, inputs = map(Path, sys.argv[1:5])

def read_ini(relative):
    config = configparser.ConfigParser()
    config.optionxform = str
    with (rkbin / relative).open() as stream:
        config.read_file(stream)
    return config

def firmware(relative):
    path = (rkbin / relative).resolve()
    if not path.is_relative_to(rkbin.resolve()) or not path.is_file():
        raise SystemExit(f'Missing or invalid Rockchip firmware: {relative}')
    return path

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def revision(path):
    return subprocess.check_output(['git', '-C', str(path), 'rev-parse', 'HEAD'], text=True).strip()

loader = read_ini('RKBOOT/RK3576MINIALL_V5.ini')
trust = read_ini('RKTRUST/RK3576TRUST.ini')
ddr = firmware(loader['LOADER_OPTION']['FlashData'])
if loader['CODE471_OPTION']['Path1'] != loader['LOADER_OPTION']['FlashData']:
    raise SystemExit('RK3576 loader DDR inputs disagree')
bl31 = firmware(trust['BL31_OPTION']['PATH'])
bl32 = firmware(trust['BL32_OPTION']['PATH'])
for section in ('BL31_OPTION', 'BL32_OPTION'):
    if trust[section]['SEC'] != '1':
        raise SystemExit(f'Expected enabled {section}')
for section, key in [('LOADER_OPTION', 'FlashBoost'), ('CODE472_OPTION', 'Path1')]:
    firmware(loader[section][key])
for name in ('boot_merger', 'ddrbin_tool.py'):
    firmware('tools/' + name)
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
         'boost': firmware(loader['LOADER_OPTION']['FlashBoost']),
         'usbplug': firmware(loader['CODE472_OPTION']['Path1']),
         'boot_merger': firmware('tools/boot_merger')}
report = {'uboot_branch': 'v5', 'uboot_commit': revision(uboot),
          'rkbin_branch': 'master', 'rkbin_commit': revision(rkbin),
          'firmware': {name: {'path': str(path.relative_to(rkbin.resolve())), 'sha256': digest(path)}
                       for name, path in paths.items()},
          'ddr_output_sha256': digest(build / 'rk3576-ddr-k7-1560.bin'),
          'hardware_validation': 'pending'}
(build / 'k7-rockchip-components.json').write_text(json.dumps(report, indent=2) + '\n')

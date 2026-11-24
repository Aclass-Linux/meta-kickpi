"""准备上游构建用的 DDR、BL31 和共用的 USB 下载 Loader。"""
from pathlib import Path
import shutil
import subprocess
import sys

from k7_rkbin import select_inputs

source, build, inputs = map(Path, sys.argv[1:])
build.mkdir(parents=True, exist_ok=True)
loader, _, original, bl31 = select_inputs(source)
ddr = build / "rk3576-ddr-k7-1560.bin"
subprocess.run([sys.executable, str(inputs / "prepare-k7-ddr.py"),
                str(source), str(inputs / "k7-ddr-1560.params"), str(ddr),
                str(inputs / "rk3576_ddr_lp4_1560MHz_lp5_2736MHz_v1.08.bin"),
                str(original)], check=True)
shutil.copyfile(bl31, build / "bl31.elf")

# c. USB 下载 Loader 只用于建立烧录通信；磁盘启动的 SPL 由上游源码编译。
# 保留 INI 中的厂商 FlashBoot/USBPLUG，替换为相同 K7 DDR 参数。
loader["CODE471_OPTION"]["Path1"] = str(ddr)
loader["LOADER_OPTION"]["FlashData"] = str(ddr)
loader["OUTPUT"]["PATH"] = str(build / "loader.bin")
loader["OUTPUT"]["IDB_PATH"] = str(build / "download-only-idblock.img")
ini = build / "usb-loader.ini"
with ini.open("w") as stream:
    loader.write(stream, space_around_delimiters=False)
subprocess.run([str(source / "tools/boot_merger"), str(ini)], cwd=source, check=True)

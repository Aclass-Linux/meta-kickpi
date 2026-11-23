# a. 独立准备上游 U-Boot 的二进制输入，不编译 U-Boot。
SUMMARY = "K7 DDR、BL31 和 USB 下载固件"
LICENSE = "LicenseRef-Rockchip-rkbin"
LIC_FILES_CHKSUM = "file://LICENSE;md5=e1d83c54b4e0b5e550769dd04f092f6c"
NO_GENERIC_LICENSE[LicenseRef-Rockchip-rkbin] = "LICENSE"
inherit deploy python3native
COMPATIBLE_MACHINE = "^kickpi-k7$"
PACKAGE_ARCH = "${MACHINE_ARCH}"
DEPENDS = "python3-native"

# 与厂商方案使用同一版 rkbin、相同 DDR 参数，便于对照学习。
FILESEXTRAPATHS:prepend := "${THISDIR}/../u-boot/files/rockchip:"
SRC_URI = "git://github.com/rockchip-linux/rkbin.git;protocol=https;branch=master;destsuffix=rkbin \
           file://prepare-k7-ddr.py \
           file://k7-ddr-1560.params \
           file://rk3576_ddr_lp4_1560MHz_lp5_2736MHz_v1.08.bin"
SRCREV = "3e288fe814e059dd06833495f845cab04ac20a5c"
S = "${UNPACKDIR}/rkbin"
B = "${WORKDIR}/build"

python __anonymous() {
    if d.getVar("BUILD_ARCH") != "x86_64":
        raise bb.parse.SkipRecipe("Rockchip boot_merger requires an x86_64 build host")
}

# b. 读取固定版本 INI，修改 DDR 参数并准备 BL31。
do_configure[noexec] = "1"
python do_compile() {
    import configparser
    from pathlib import Path
    import shutil
    import subprocess

    source = Path(d.getVar("S"))
    build = Path(d.getVar("B"))
    inputs = Path(d.getVar("UNPACKDIR"))
    build.mkdir(parents=True, exist_ok=True)
    loader = configparser.ConfigParser()
    loader.optionxform = str
    loader.read(source / "RKBOOT/RK3576MINIALL_V5.ini")
    trust = configparser.ConfigParser()
    trust.optionxform = str
    trust.read(source / "RKTRUST/RK3576TRUST.ini")
    original = source / loader["LOADER_OPTION"]["FlashData"]
    if loader["CODE471_OPTION"]["Path1"] != loader["LOADER_OPTION"]["FlashData"]:
        bb.fatal("RK3576 DDR inputs disagree")
    ddr = build / "rk3576-ddr-k7-1560.bin"
    subprocess.run([d.getVar("PYTHON"), str(inputs / "prepare-k7-ddr.py"),
                    str(source), str(inputs / "k7-ddr-1560.params"), str(ddr),
                    str(inputs / "rk3576_ddr_lp4_1560MHz_lp5_2736MHz_v1.08.bin"),
                    str(original)], check=True)
    shutil.copyfile(source / trust["BL31_OPTION"]["PATH"], build / "bl31.elf")

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
}

# d. U-Boot 通过 do_deploy 任务依赖获取这些文件，不将它们安装进 rootfs。
do_deploy() {
    install -d ${DEPLOYDIR}/k7-mainline-firmware
    install -m 0644 ${B}/bl31.elf ${DEPLOYDIR}/k7-mainline-firmware/
    for suffix in bin json before.txt after.txt reference.txt; do
        install -m 0644 ${B}/rk3576-ddr-k7-1560.$suffix ${DEPLOYDIR}/k7-mainline-firmware/
    done
    install -m 0644 ${B}/loader.bin ${DEPLOYDIR}/k7-mainline-loader.bin
}
do_install[noexec] = "1"
PACKAGES = ""
addtask deploy after do_compile before do_build

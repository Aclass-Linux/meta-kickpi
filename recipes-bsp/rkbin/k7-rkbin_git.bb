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
SRC_URI = "git://github.com/rockchip-linux/rkbin.git;protocol=https;branch=master;destsuffix=rkbin \
           file://prepare-k7-usb-loader.py \
           file://k7_rkbin.py \
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
do_compile() {
    ${PYTHON} ${UNPACKDIR}/prepare-k7-usb-loader.py ${S} ${B} ${UNPACKDIR}
}

# d. U-Boot 通过 do_deploy 任务依赖获取这些文件，不将它们安装进 rootfs。
do_deploy() {
    install -d ${DEPLOYDIR}/k7-mainline-firmware
    install -m 0644 ${B}/bl31.elf ${DEPLOYDIR}/k7-mainline-firmware/
    for suffix in bin json before.txt after.txt reference.txt; do
        install -m 0644 ${B}/rk3576-ddr-k7-1560.$suffix ${DEPLOYDIR}/k7-mainline-firmware/
    done
    install -m 0644 ${B}/loader.bin ${DEPLOYDIR}/k7-mainline-firmware/usb-loader.bin
}
do_install[noexec] = "1"
PACKAGES = ""
addtask deploy after do_compile before do_build

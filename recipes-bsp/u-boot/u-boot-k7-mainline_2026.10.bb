SUMMARY = "Mainline U-Boot for KICKPI K7 serial/MMC bring-up"
HOMEPAGE = "https://u-boot.org/"
LICENSE = "GPL-2.0-or-later"
LIC_FILES_CHKSUM = "file://Licenses/README;md5=2ca5f2c35c8cc335f0a19756634782f1"
require recipes-bsp/u-boot/u-boot.inc

COMPATIBLE_MACHINE = "^kickpi-k7$"
FILESEXTRAPATHS:prepend := "${THISDIR}/files/mainline:${THISDIR}/files:"
SRC_URI = " \
    git://github.com/u-boot/u-boot.git;protocol=https;nobranch=1;name=uboot;destsuffix=u-boot-${PV} \
    git://github.com/rockchip-linux/rkbin.git;protocol=https;branch=master;name=rkbin;destsuffix=rkbin \
    file://rk3576-kickpi-k7.dts \
    file://rk3576-kickpi-k7-u-boot.dtsi \
    file://kickpi-k7-rk3576_defconfig \
    file://rk3576_ddr_lp4_1560MHz_lp5_2736MHz_v1.08.bin \
"
SRCREV_rkbin = "3e288fe814e059dd06833495f845cab04ac20a5c"
SRCREV_uboot = "5508406582f6f7120dce0c4b3059819a41788db2"
SRCREV_FORMAT = "uboot_rkbin"
S = "${UNPACKDIR}/u-boot-${PV}"
B = "${WORKDIR}/build"
DEPENDS += "bc-native dtc-native flex-native bison-native gnutls-native python3-setuptools-native python3-pyelftools-native python3-pyyaml-native"

UBOOT_MACHINE = "kickpi-k7-rk3576_defconfig"
UBOOT_BINARY = "u-boot-rockchip.bin"
UBOOT_INITIAL_ENV = ""
EXTRA_OEMAKE += "BL31=${UNPACKDIR}/rkbin/bin/rk35/rk3576_bl31_v1.25.elf ROCKCHIP_TPL=${UNPACKDIR}/rk3576_ddr_lp4_1560MHz_lp5_2736MHz_v1.08.bin"

do_configure:prepend() {
    install -m 0644 ${UNPACKDIR}/kickpi-k7-rk3576_defconfig ${S}/configs/
    install -m 0644 ${UNPACKDIR}/rk3576-kickpi-k7.dts ${S}/arch/arm/dts/
    install -m 0644 ${UNPACKDIR}/rk3576-kickpi-k7-u-boot.dtsi ${S}/arch/arm/dts/
    test -s ${UNPACKDIR}/rkbin/bin/rk35/rk3576_bl31_v1.25.elf || bbfatal "RK3576 BL31 missing"
}

do_deploy:append() {
    for image in idbloader.img u-boot.itb u-boot.dtb; do
        install -m 0644 ${B}/${image} ${DEPLOYDIR}/${image}
    done
}

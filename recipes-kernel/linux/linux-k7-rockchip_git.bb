SUMMARY = "Rockchip BSP Linux kernel for KICKPI K7"
HOMEPAGE = "https://github.com/rockchip-linux/kernel"
LICENSE = "GPL-2.0-with-Linux-syscall-note"
LIC_FILES_CHKSUM = "file://COPYING;md5=6bc538ed5bd9a7fc9398086aedcd7e46"

inherit kernel
# Scarthgap's Git fetcher unpacks to WORKDIR/git. The kernel class moves
# this checkout to STAGING_KERNEL_DIR before patching and compiling.
S = "${WORKDIR}/git"
COMPATIBLE_MACHINE = "^kickpi-k7$"
FILESEXTRAPATHS:prepend := "${THISDIR}/files/rockchip-k7:"
SRC_URI = "git://github.com/rockchip-linux/kernel.git;protocol=https;branch=develop-6.12 \
           file://rk3576-kickpi-k7.dts"
# Follow the selected Rockchip branch, as requested. Record each built commit.
PV = "6.12+git"
SRCREV = "${AUTOREV}"
LINUX_VERSION = "6.12"
DEPENDS += "openssl-native flex-native"
# The vendor Makefile prefixes this with srctree for includes and embeds it
# in DHD_COMPILED. A command-line relative root fixes both duplicate include
# paths and TMPDIR leakage without changing Rockchip's driver sources.
EXTRA_OEMAKE += "BCMDHD_ROOT=drivers/net/wireless/rockchip_wlan/rkwifi/bcmdhd"

# Use the vendor configuration, then apply only the serial/SD Yocto boot needs.
do_configure:prepend() {
    install -m 0644 ${UNPACKDIR}/rk3576-kickpi-k7.dts ${S}/arch/arm64/boot/dts/rockchip/
    if ! grep -q 'rk3576-kickpi-k7\.dtb' ${S}/arch/arm64/boot/dts/rockchip/Makefile; then
        echo 'dtb-$(CONFIG_ARCH_ROCKCHIP) += rk3576-kickpi-k7.dtb' >> ${S}/arch/arm64/boot/dts/rockchip/Makefile
    fi
    oe_runmake -C ${S} O=${B} rockchip_linux_defconfig
    ${S}/scripts/config --file ${B}/.config \
        --enable DEVTMPFS --enable DEVTMPFS_MOUNT \
        --enable BLK_DEV_INITRD --enable RD_GZIP \
        --enable EFI_PARTITION --enable EXT4_FS \
        --enable SERIAL_8250 --enable SERIAL_8250_CONSOLE --enable SERIAL_8250_DW \
        --enable FHANDLE --enable CGROUPS --enable NAMESPACES --enable AUTOFS_FS \
        --disable ROCKCHIP_FIQ_DEBUGGER --disable LOCALVERSION_AUTO \
        --set-str SYSTEM_TRUSTED_KEYS "" --set-str SYSTEM_REVOCATION_KEYS ""
}

do_deploy:append() {
    git -C ${S} rev-parse HEAD > ${DEPLOYDIR}/k7-rockchip-linux-commit.txt
    install -m 0644 ${B}/.config ${DEPLOYDIR}/k7-rockchip-linux.config
}

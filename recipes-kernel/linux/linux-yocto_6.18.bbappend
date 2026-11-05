# Keep the OE-Core maintained source and kernel-cache revisions unchanged.
COMPATIBLE_MACHINE:kickpi-k7 = "^kickpi-k7$"
FILESEXTRAPATHS:prepend := "${THISDIR}/files/yocto-k7:"
SRC_URI:append:kickpi-k7 = " file://defconfig file://rk3576-kickpi-k7.dts"
KMACHINE:kickpi-k7 = "kickpi-k7"
KCONFIG_MODE:kickpi-k7 = "--alldefconfig"

do_k7_install_dts() {
    install -m 0644 ${UNPACKDIR}/rk3576-kickpi-k7.dts ${S}/arch/arm64/boot/dts/rockchip/
    if ! grep -q 'rk3576-kickpi-k7\.dtb' ${S}/arch/arm64/boot/dts/rockchip/Makefile; then
        echo 'dtb-$(CONFIG_ARCH_ROCKCHIP) += rk3576-kickpi-k7.dtb' >> ${S}/arch/arm64/boot/dts/rockchip/Makefile
    fi
}
addtask k7_install_dts after do_kernel_checkout before do_kernel_metadata

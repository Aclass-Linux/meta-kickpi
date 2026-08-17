# KickPi K7 device-tree sources are kept as readable files in this layer.
FILESEXTRAPATHS:prepend := "${THISDIR}/files/kickpi-k7:"

SRC_URI:append = " \
    file://rk3576-kickpi-k7.dts;subdir=kickpi-k7 \
    file://rk3576-kickpi-k7-cam0.dtsi;subdir=kickpi-k7 \
    file://rk3576-kickpi-k7-cam1.dtsi;subdir=kickpi-k7 \
    file://rk3576-kickpi-k7-cam3.dtsi;subdir=kickpi-k7 \
"

do_patch:append:kickpi-k7() {
    dts_dir="${S}/arch/arm64/boot/dts/rockchip"

    install -m 0644 \
        "${WORKDIR}/kickpi-k7/rk3576-kickpi-k7.dts" \
        "${WORKDIR}/kickpi-k7/rk3576-kickpi-k7-cam0.dtsi" \
        "${WORKDIR}/kickpi-k7/rk3576-kickpi-k7-cam1.dtsi" \
        "${WORKDIR}/kickpi-k7/rk3576-kickpi-k7-cam3.dtsi" \
        "${dts_dir}/"

    if ! grep -q 'rk3576-kickpi-k7\.dtb' "${dts_dir}/Makefile"; then
        echo 'dtb-$(CONFIG_ARCH_ROCKCHIP) += rk3576-kickpi-k7.dtb' \
            >> "${dts_dir}/Makefile"
    fi
}

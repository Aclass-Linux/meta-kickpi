SUMMARY = "Source-built Rockchip U-Boot and SPL for KICKPI K7"
HOMEPAGE = "https://github.com/rockchip-linux/u-boot"
LICENSE = "GPL-2.0-or-later & LicenseRef-Rockchip-rkbin"
LIC_FILES_CHKSUM = "file://Licenses/README;md5=2ca5f2c35c8cc335f0a19756634782f1 \
                    file://../rkbin/LICENSE;md5=e1d83c54b4e0b5e550769dd04f092f6c"
# SPDX 以 S 为基准解析此路径；license.bbclass 也要求
# LIC_FILES_CHKSUM 中包含完全相同的相对路径。
NO_GENERIC_LICENSE[LicenseRef-Rockchip-rkbin] = "../rkbin/LICENSE"
inherit deploy python3native
PROVIDES += "virtual/bootloader"
PACKAGE_ARCH = "${MACHINE_ARCH}"

COMPATIBLE_MACHINE = "^kickpi-k7$"
FILESEXTRAPATHS:prepend := "${THISDIR}/files/rockchip:"
# a. 输入来源：下载 U-Boot、rkbin，并从上述本地搜索路径读取固件、参数和脚本。
# rkbin 提供原始 DDR 固件及修改工具；本地 v1.08 固件仅用于参数参考校验。
# k7-ddr-1560.params 是当前 K7 的 DDR 参数输入，不是 DDR 初始化程序源码。
SRC_URI = "git://github.com/rockchip-linux/u-boot.git;protocol=https;branch=v5;name=uboot;destsuffix=u-boot \
           git://github.com/rockchip-linux/rkbin.git;protocol=https;branch=master;name=rkbin;destsuffix=rkbin \
           file://rk3576_ddr_lp4_1560MHz_lp5_2736MHz_v1.08.bin \
           file://k7-ddr-1560.params \
           file://prepare-k7-ddr.py \
           file://configure-k7-v5.sh \
           file://prepare-k7-firmware.py \
           file://verify-k7-boot.py \
           file://rk3576-kickpi-k7-u-boot.dtsi \
           file://rk3576-kickpi-k7.dts"
PV = "2025.04+git"
# 固定 K7 启动调试所用的源码和固件提交，避免随远端分支更新而变化。
SRCREV_uboot = "18e35f2e75dd2c97c704c8a52ee9bffeef239165"
SRCREV_rkbin = "3e288fe814e059dd06833495f845cab04ac20a5c"
SRCREV_FORMAT = "uboot_rkbin"
S = "${UNPACKDIR}/u-boot"
B = "${S}"
# 厂商 FIT 脚本要求在源码目录内构建，因此 B 与 S 相同。
DEPENDS = "bc-native dtc-native flex-native bison-native openssl-native python3-native"
K7_SPL = "${B}/spl/u-boot-spl.bin"
K7_UBOOT_CFLAGS = "-Wno-error=maybe-uninitialized -Wno-error=misleading-indentation -Wno-error=attributes -Wno-error=address-of-packed-member -Wno-error=array-parameter -Wno-error=enum-int-mismatch"
K7_BL31 = "${B}/bl31.elf"
# DDR 参数处理后的输出路径；后续 Loader 打包使用此文件。
K7_DDR = "${B}/rk3576-ddr-k7-1560.bin"
K7_RKBIN = "${UNPACKDIR}/rkbin"
EXTRA_OEMAKE = 'ARCH=arm CROSS_COMPILE=${TARGET_PREFIX} CC="${TARGET_PREFIX}gcc ${TOOLCHAIN_OPTIONS}" HOSTCC="${BUILD_CC} ${BUILD_CFLAGS} ${BUILD_LDFLAGS}" CFLAGS="${K7_UBOOT_CFLAGS}" KCFLAGS="${K7_UBOOT_CFLAGS}"'

python __anonymous() {
    if d.getVar("BUILD_ARCH") != "x86_64":
        raise bb.parse.SkipRecipe("Rockchip boot_merger requires an x86_64 build host")
}

do_configure() {
    unset CFLAGS CPPFLAGS LDFLAGS
    # b. 准备固件：读取 rkbin 的 RKBOOT/RK3576MINIALL_V5.ini，
    # 由 FlashData 选择原始 DDR bin，并检查 CODE471 的 Path1 与其一致。
    # 脚本调用 prepare-k7-ddr.py，使用 rkbin 的 ddrbin_tool.py
    # 将本地 .params 应用到原始固件副本，生成 K7_DDR 并校验参数。
    # 同时按 RKTRUST/RK3576TRUST.ini 准备 BL31 和 OP-TEE。
    ${PYTHON} ${UNPACKDIR}/prepare-k7-firmware.py ${K7_RKBIN} ${B} ${S} ${UNPACKDIR}
    install -m 0644 ${UNPACKDIR}/rk3576-kickpi-k7.dts ${S}/dts/upstream/src/arm64/rockchip/
    install -m 0644 ${UNPACKDIR}/rk3576-kickpi-k7-u-boot.dtsi ${S}/arch/arm/dts/
    touch ${S}/.scmversion
    oe_runmake -C ${S} rk3576_defconfig
    cd ${S}
    sh ${UNPACKDIR}/configure-k7-v5.sh
    oe_runmake olddefconfig
    if grep -q '^CONFIG_SPL_AB=y$' .config; then
        bbfatal "K7 uses a single rootfs; SPL_AB must remain disabled"
    fi
}

do_compile() {
    unset CFLAGS CPPFLAGS LDFLAGS
    install -d ${B}/k7-host-tools
    ln -sf ${PYTHON} ${B}/k7-host-tools/python
    export PATH="${B}/k7-host-tools:$PATH"
    rm -f ${B}/u-boot.its ${B}/u-boot.itb
    oe_runmake -C ${S} spl/u-boot-spl.bin u-boot.dtb u-boot-nodtb.bin
    # 使用同一份 rkbin Trust 配置提供的 BL32，与厂商 OP-TEE 支持配套。
    cd ${B}
    srctree=. ./arch/arm/mach-rockchip/make_fit_atf.sh -c none -t "$(cat ${B}/k7-rockchip-tee-offset)" > u-boot.its
    oe_runmake -C ${S} U_BOOT_ITS=u-boot.its u-boot.itb
    test -s ${K7_SPL} || bbfatal "Source-built Rockchip SPL missing"
    test "$(fdtget ${B}/u-boot.itb /images/atf-1 os)" = "arm-trusted-firmware" \
        || bbfatal "FIT is missing BL31"
    test "$(fdtget ${B}/u-boot.itb /configurations/conf firmware)" = "atf-1" \
        || bbfatal "FIT does not boot through BL31"
    test "$(fdtget ${B}/u-boot.itb /images/optee os)" = "op-tee" \
        || bbfatal "FIT is missing the vendor OP-TEE firmware"
    cd ${K7_RKBIN}
    rm -f ${B}/k7-rockchip-idbloader.img ${B}/k7-rockchip-loader.bin
    # c. 生成打包配置：Path1 和 FlashData 指向处理后的 DDR bin，
    # FlashBoot 指向源码编译的 SPL，再由 boot_merger 生成 Loader 和 IDBLoader。
    sed -e 's|^Path1=.*rk3576_ddr.*$|Path1=${K7_DDR}|' \
        -e 's|^FlashData=.*$|FlashData=${K7_DDR}|' \
        -e 's|^FlashBoot=.*$|FlashBoot=${K7_SPL}|' \
        -e 's|^PATH=.*$|PATH=${B}/k7-rockchip-loader.bin|' \
        -e 's|^IDB_PATH=.*$|IDB_PATH=${B}/k7-rockchip-idbloader.img|' \
        RKBOOT/RK3576MINIALL_V5.ini > ${B}/k7-rockchip-loader.ini
    ./tools/boot_merger ${B}/k7-rockchip-loader.ini
    test -s ${B}/k7-rockchip-idbloader.img || bbfatal "Rockchip IDB missing"
    test "$(wc -c < ${B}/k7-rockchip-idbloader.img)" -le ${K7_IDB_MAX_BYTES} \
        || bbfatal "IDB overlaps FIT at 8 MiB"
    test "$(wc -c < ${B}/u-boot.itb)" -le ${K7_FIT_MAX_BYTES} \
        || bbfatal "FIT overlaps rootfs at 16 MiB"
    ${PYTHON} ${UNPACKDIR}/verify-k7-boot.py ${B}
}
do_deploy() {
    # d. 导出启动产物、配置及校验报告；DDR 目录同时保存修改前后参数，便于核对。
    install -m 0644 ${B}/.config ${DEPLOYDIR}/k7-rockchip-u-boot.config
    install -m 0644 ${K7_BL31} ${DEPLOYDIR}/k7-rockchip-bl31.elf
    install -m 0644 ${B}/tee.bin ${DEPLOYDIR}/k7-rockchip-bl32.bin
    install -m 0644 ${B}/k7-rockchip-components.json ${DEPLOYDIR}/k7-rockchip-components.json
    install -m 0644 ${B}/k7-rockchip-validation.json ${DEPLOYDIR}/k7-rockchip-validation.json
    install -d ${DEPLOYDIR}/k7-rockchip-ddr
    for suffix in bin json before.txt after.txt reference.txt; do
        install -m 0644 ${B}/rk3576-ddr-k7-1560.$suffix ${DEPLOYDIR}/k7-rockchip-ddr/
    done
    install -m 0644 ${K7_SPL} ${DEPLOYDIR}/k7-rockchip-spl.bin
    install -m 0644 ${B}/k7-rockchip-idbloader.img ${DEPLOYDIR}/k7-rockchip-idbloader.img
    install -m 0644 ${B}/k7-rockchip-loader.bin ${DEPLOYDIR}/k7-rockchip-loader.bin
    install -m 0644 ${B}/k7-rockchip-loader.ini ${DEPLOYDIR}/k7-rockchip-loader.ini
    for image in u-boot.itb u-boot.dtb; do
        install -m 0644 ${B}/$image ${DEPLOYDIR}/k7-rockchip-$image
    done
}

do_install[noexec] = "1"
PACKAGES = ""
addtask deploy after do_compile before do_build

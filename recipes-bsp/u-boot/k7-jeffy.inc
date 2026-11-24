# 固定已验证能编译 RK3576 的版本，复用 JeffyCN 的构建和打包任务。
FILESEXTRAPATHS:prepend := "${THISDIR}/files/official:"
SRCREV = "1c535d65b8509f388d09e49fb6961f49fda35a1d"
SRCREV_rkbin = "3e288fe814e059dd06833495f845cab04ac20a5c"
SRC_URI = "git://github.com/rockchip-linux/u-boot.git;protocol=https;branch=next-dev \
           git://github.com/rockchip-linux/rkbin.git;protocol=https;branch=master;name=rkbin;destsuffix=rkbin \
           file://rk3576-kickpi-k7.dts"
SRC_URI:remove = "file://${PATCHPATH}/0002-HACK-Support-python3-for-dtoc.patch"

UBOOT_MACHINE = "kickpi-k7-rk3576_defconfig"
RK_SOC_FAMILY = "rk3576"
RK_UBOOT_CFG = "kickpi-k7-rk3576"
RK_UBOOT_SPL = "1"
K7_FIT_SECTOR = "${@hex(int(d.getVar('K7_FIT_OFFSET_KIB')) * 2)}"
do_configure[depends] += "k7-rkbin:do_deploy"

do_configure:prepend() {
    # 从同次源码的 RK3576 defconfig 派生当前单 rootfs 配置，磁盘偏移沿用机器配置。
    sed -e 's/^CONFIG_DEFAULT_DEVICE_TREE=.*/CONFIG_DEFAULT_DEVICE_TREE="rk3576-kickpi-k7"/' -e '/^CONFIG_SPL_AB=/d' -e '/^CONFIG_SYS_MMCSD_RAW_MODE_U_BOOT_/d' \
        ${S}/configs/rk3576_defconfig > ${S}/configs/${UBOOT_MACHINE}
    cat >> ${S}/configs/${UBOOT_MACHINE} <<END_CONFIG
# CONFIG_SPL_AB is not set
# CONFIG_SYS_MMCSD_RAW_MODE_U_BOOT_USE_PARTITION is not set
CONFIG_SYS_MMCSD_RAW_MODE_U_BOOT_USE_SECTOR=y
CONFIG_SYS_MMCSD_RAW_MODE_U_BOOT_SECTOR=${K7_FIT_SECTOR}
CONFIG_CMD_PXE=y
END_CONFIG
    install -m 0644 ${UNPACKDIR}/rk3576-kickpi-k7.dts ${S}/arch/arm/dts/
    echo '/ { chosen { u-boot,spl-boot-order = &sdmmc, &sdhci; }; };' >> ${S}/arch/arm/dts/rk3576-kickpi-k7.dts
    # 该版本的默认 bootcmd 在板级头文件定义；直接运行发行版的 extlinux 扫描。
    sed -i 's/^#define CONFIG_BOOTCOMMAND[[:space:]]*RKIMG_BOOTCOMMAND/#define CONFIG_BOOTCOMMAND "run distro_bootcmd"/' ${S}/include/configs/evb_rk3576.h
    # 使用普通 MINIALL.ini（2017.09），使下载与磁盘 IDB 都使用同一份 K7 DDR。
    install -m 0644 ${DEPLOY_DIR_IMAGE}/k7-mainline-firmware/rk3576-ddr-k7-1560.bin ${UNPACKDIR}/rkbin/bin/rk35/k7-ddr.bin
    sed -i -e 's|^Path1=.*rk3576_ddr.*|Path1=bin/rk35/k7-ddr.bin|' \
           -e 's|^FlashData=.*|FlashData=bin/rk35/k7-ddr.bin|' ${UNPACKDIR}/rkbin/RKBOOT/RK3576MINIALL.ini
}

do_compile:prepend() {
    export srctree=.
    grep -qx 'CONFIG_SYS_MMCSD_RAW_MODE_U_BOOT_SECTOR=${K7_FIT_SECTOR}' ${B}/.config || bbfatal "Wrong K7 FIT sector"
    grep -qx '# CONFIG_SPL_AB is not set' ${B}/.config || bbfatal "K7 SPL A/B enabled"
    grep -qx 'CONFIG_CMD_PXE=y' ${B}/.config || bbfatal "Missing sysboot for extlinux"
}

do_deploy:append() {
    # uboot.img 是厂商重复打包的 FIT；WIC 写入同次打包生成的单份 FIT。
    test "$(fdtget ${B}/fit/uboot.itb /images/atf-1 os)" = arm-trusted-firmware || bbfatal "K7 FIT missing BL31"
    for artifact in idblock.img loader.bin fit/uboot.itb; do
        test -s ${B}/$artifact || bbfatal "Missing K7 image: $artifact"
    done
    test "$(wc -c < ${B}/${RK_IDBLOCK_IMG})" -le ${K7_IDB_MAX_BYTES} || bbfatal "IDB overlaps FIT"
    test "$(wc -c < ${B}/fit/uboot.itb)" -le ${K7_FIT_MAX_BYTES} || bbfatal "FIT overlaps rootfs"
    install -m 0644 ${B}/${RK_IDBLOCK_IMG} ${DEPLOYDIR}/k7-jeffy-idbloader.img
    install -m 0644 ${B}/${RK_LOADER_BIN} ${DEPLOYDIR}/k7-jeffy-loader.bin
    install -m 0644 ${B}/fit/uboot.itb ${DEPLOYDIR}/k7-jeffy-u-boot.itb
    install -m 0644 ${B}/spl/u-boot-spl.bin ${DEPLOYDIR}/k7-jeffy-spl.bin
    install -m 0644 ${B}/.config ${DEPLOYDIR}/k7-jeffy-u-boot.config
}

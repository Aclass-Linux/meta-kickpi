SUMMARY = "KICKPI K7 official Armbian SPL and U-Boot"
HOMEPAGE = "https://github.com/radxa/u-boot"
LICENSE = "GPL-2.0-or-later"
LIC_FILES_CHKSUM = "file://Licenses/README;md5=a2c678cfd4a4d97135585cad908541c6"

PROVIDES = "virtual/bootloader"
COMPATIBLE_MACHINE = "^kickpi-k7$"
PACKAGE_ARCH = "${MACHINE_ARCH}"
inherit deploy python3native

python __anonymous() {
    if d.getVar("BUILD_ARCH") != "x86_64":
        raise bb.parse.SkipRecipe("The official boot_merger tool requires an x86_64 build host")
}

FILESEXTRAPATHS:prepend := "${THISDIR}/files/official:"
SRC_URI = " \
    git://github.com/radxa/u-boot.git;protocol=https;branch=next-dev-v2024.10;name=uboot;destsuffix=u-boot \
    git://github.com/armbian/rkbin.git;protocol=https;branch=master;name=rkbin;destsuffix=rkbin \
    file://rk3566-Add-rk3566-to-soc-name.patch \
    file://0001-fit-require-bl31-python3.patch \
    file://rk3576-kickpi-k7.dts \
    file://kickpi-k7-rk3576_defconfig \
    file://rk3576_ddr_lp4_1560MHz_lp5_2736MHz_v1.08.bin \
"
# Fixed vendor revision already recorded in the K7 BSP baseline. The branch
# name contains 2024.10, but this vendor tree reports U-Boot 2017.09.
SRCREV_uboot = "39cd993e5d6296635438e84f4576b3a9bf76f86e"
SRCREV_rkbin = "452f49a987097d7ca675811e51a873b42ebd101f"
SRCREV_FORMAT = "uboot_rkbin"
S = "${UNPACKDIR}/u-boot"
# Match the official framework's in-tree build, including vendor FIT scripts.
B = "${S}"

DEPENDS = "bc-native bison-native flex-native dtc-native openssl-native python3-pyelftools-native swig-native"
K7_RKBIN = "${UNPACKDIR}/rkbin"
K7_DDR = "rk3576_ddr_lp4_1560MHz_lp5_2736MHz_v1.08.bin"
K7_BL31 = "${K7_RKBIN}/rk35/rk3576_bl31_v1.04.elf"
# These warning exceptions are the official framework's uboot_cflags_array,
# including its GCC >= 11 case. Do not disable -Werror globally.
K7_UBOOT_CFLAGS = "-Wno-error=maybe-uninitialized -Wno-error=misleading-indentation -Wno-error=attributes -Wno-error=address-of-packed-member -Wno-error=array-parameter"
EXTRA_OEMAKE = 'CROSS_COMPILE=${TARGET_PREFIX} ARCH=arm V=1 CC="${TARGET_PREFIX}gcc ${TOOLCHAIN_OPTIONS}" HOSTCC="${BUILD_CC} ${BUILD_CFLAGS} ${BUILD_LDFLAGS}" BL31=${K7_BL31} CFLAGS="${K7_UBOOT_CFLAGS}" KCFLAGS="${K7_UBOOT_CFLAGS}"'

do_configure() {
    unset CFLAGS CPPFLAGS LDFLAGS
    install -m 0644 ${UNPACKDIR}/rk3576-kickpi-k7.dts ${S}/arch/arm/dts/
    install -m 0644 ${UNPACKDIR}/kickpi-k7-rk3576_defconfig ${S}/configs/
    install -m 0644 ${UNPACKDIR}/${K7_DDR} ${K7_RKBIN}/rk35/
    for input in ${K7_BL31} ${K7_RKBIN}/rk35/RK3576MINIALL.ini \
                 ${K7_RKBIN}/rk35/rk3576_boost_v1.02.bin \
                 ${K7_RKBIN}/rk35/rk3576_usbplug_v1.03.bin \
                 ${K7_RKBIN}/tools/boot_merger; do
        test -s "$input" || bbfatal "Official K7 boot input missing: $input"
    done
    touch ${S}/.scmversion
    oe_runmake -C ${S} kickpi-k7-rk3576_defconfig
}

do_compile() {
    unset CFLAGS CPPFLAGS LDFLAGS
    # Exact spl-blobs target set from rockchip64_common.inc.
    # Regenerate FIT even when an earlier invocation left an incomplete image.
    rm -f ${B}/u-boot.its ${B}/u-boot.itb
    oe_runmake -C ${S} spl/u-boot-spl.bin u-boot.dtb u-boot.itb
    test "$(fdtget ${B}/u-boot.itb /images/atf-1 os)" = "arm-trusted-firmware" \
        || bbfatal "FIT is missing its BL31 firmware node"
    test "$(fdtget ${B}/u-boot.itb /configurations/conf firmware)" = "atf-1" \
        || bbfatal "FIT configuration does not select BL31"
    cd ${B}
    cp ${K7_RKBIN}/rk35/RK3576MINIALL.ini rk3576.ini
    sed -i \
        -e 's|FlashBoost=.*$|FlashBoost=${K7_RKBIN}/rk35/rk3576_boost_v1.02.bin|' \
        -e 's|Path1=.*rk3576_ddr.*$|Path1=${K7_RKBIN}/rk35/${K7_DDR}|' \
        -e 's|Path1=.*rk3576_usbplug.*$|Path1=${K7_RKBIN}/rk35/rk3576_usbplug_v1.03.bin|' \
        -e 's|FlashData=.*$|FlashData=${K7_RKBIN}/rk35/${K7_DDR}|' \
        -e 's|FlashBoot=.*$|FlashBoot=./spl/u-boot-spl.bin|' \
        -e 's|IDB_PATH=.*$|IDB_PATH=idbloader.img|' rk3576.ini
    ${K7_RKBIN}/tools/boot_merger rk3576.ini
    test -s idbloader.img || bbfatal "Official boot_merger did not produce idbloader.img"
    test -s u-boot.itb || bbfatal "Official build did not produce u-boot.itb"
}

do_install[noexec] = "1"
PACKAGES = ""

do_deploy() {
    for image in idbloader.img u-boot.itb u-boot.dtb rk3576.ini; do
        install -Dm0644 ${B}/$image ${DEPLOYDIR}/$image
    done
    install -Dm0644 ${B}/spl/u-boot-spl.bin ${DEPLOYDIR}/u-boot-spl.bin
}
addtask deploy after do_compile before do_build

SUMMARY = "Official K7 boot script and Yocto kernel boot files"
LICENSE = "MIT & GPL-2.0-only"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302 \
                    file://${COMMON_LICENSE_DIR}/GPL-2.0-only;md5=801f80980d171dd6425610833a22dbe6"

require conf/include/k7-boot-layout.inc
COMPATIBLE_MACHINE = "^kickpi-k7$"
PACKAGE_ARCH = "${MACHINE_ARCH}"
DEPENDS = "u-boot-mkimage-native"
RDEPENDS:${PN} += "kernel-image"
SRC_URI = "file://boot.cmd file://armbianEnv.txt.in"
S = "${UNPACKDIR}"
K7_INITRAMFS_LINK = "kickpi-initramfs-${MACHINE}.cpio.gz"

do_compile[depends] += "kickpi-initramfs:do_image_complete"
do_compile() {
    mkimage -C none -A arm -T script -d ${S}/boot.cmd ${B}/boot.scr
    mkimage -A arm64 -O linux -T ramdisk -C gzip -n 'K7 Yocto initramfs' \
        -d ${DEPLOY_DIR_IMAGE}/${K7_INITRAMFS_LINK} ${B}/uInitrd
}

do_install[depends] += "virtual/kernel:do_deploy"
do_install() {
    install -d ${D}/boot/dtb/rockchip
    dtb="${DEPLOY_DIR_IMAGE}/${KERNEL_DEVICETREE}"
    [ -f "$dtb" ] || dtb="${DEPLOY_DIR_IMAGE}/${@os.path.basename(d.getVar('KERNEL_DEVICETREE'))}"
    install -m 0644 "$dtb" ${D}/boot/dtb/${KERNEL_DEVICETREE}
    install -m 0644 ${S}/boot.cmd ${D}/boot/boot.cmd
    install -m 0644 ${B}/boot.scr ${D}/boot/boot.scr
    install -m 0644 ${B}/uInitrd ${D}/boot/uInitrd
    sed -e 's|@CONSOLE_DEVICE@|${K7_CONSOLE_DEVICE}|g' \
        -e 's|@CONSOLE_BAUD@|${K7_CONSOLE_BAUD}|g' \
        -e 's|@ROOT_PARTUUID@|${K7_ROOT_PARTUUID}|g' \
        -e 's|@ROOT_FSTYPE@|${K7_ROOT_FSTYPE}|g' \
        -e 's|@KERNEL_DTB@|${KERNEL_DEVICETREE}|g' \
        ${S}/armbianEnv.txt.in > ${D}/boot/armbianEnv.txt
}

FILES:${PN} = "/boot/boot.cmd /boot/boot.scr /boot/uInitrd /boot/armbianEnv.txt /boot/dtb"

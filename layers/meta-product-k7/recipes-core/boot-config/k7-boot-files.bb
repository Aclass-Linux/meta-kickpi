SUMMARY = "K7 standard extlinux boot configuration and initramfs"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"
require conf/include/k7-boot-layout.inc
COMPATIBLE_MACHINE = "^kickpi-k7$"
PACKAGE_ARCH = "${MACHINE_ARCH}"
RDEPENDS:${PN} += "kernel-image"
SRC_URI = "file://extlinux.conf.in"
S = "${UNPACKDIR}"
K7_INITRAMFS_LINK = "kickpi-initramfs-${MACHINE}.cpio.gz"
do_install[depends] += "virtual/kernel:do_deploy kickpi-initramfs:do_image_complete"
do_install() {
    install -d ${D}/boot/extlinux ${D}/boot/dtb/rockchip
    dtb="${DEPLOY_DIR_IMAGE}/${KERNEL_DEVICETREE}"
    [ -f "$dtb" ] || dtb="${DEPLOY_DIR_IMAGE}/${@os.path.basename(d.getVar('KERNEL_DEVICETREE'))}"
    install -m 0644 "$dtb" ${D}/boot/dtb/${KERNEL_DEVICETREE}
    install -m 0644 ${DEPLOY_DIR_IMAGE}/${K7_INITRAMFS_LINK} ${D}/boot/initramfs.cpio.gz
    sed -e 's|@CONSOLE_DEVICE@|${K7_CONSOLE_DEVICE}|g' \
        -e 's|@CONSOLE_BAUD@|${K7_CONSOLE_BAUD}|g' \
        -e 's|@ROOT_PARTUUID@|${K7_ROOT_PARTUUID}|g' \
        -e 's|@ROOT_FSTYPE@|${K7_ROOT_FSTYPE}|g' \
        -e 's|@KERNEL_DTB@|${KERNEL_DEVICETREE}|g' \
        ${S}/extlinux.conf.in > ${D}/boot/extlinux/extlinux.conf
}
FILES:${PN} = "/boot/extlinux /boot/initramfs.cpio.gz /boot/dtb"

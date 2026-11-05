SUMMARY = "K7 device tree and extlinux configuration for the packaged kernel"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

require conf/include/k7-boot-layout.inc
COMPATIBLE_MACHINE = "^kickpi-k7$"
PACKAGE_ARCH = "${MACHINE_ARCH}"
RDEPENDS:${PN} += "kernel-image"
SRC_URI = "file://extlinux.conf.in"
S = "${UNPACKDIR}"

do_install[depends] += "virtual/kernel:do_deploy"
do_install() {
    install -d ${D}/boot/extlinux
    dtb="${DEPLOY_DIR_IMAGE}/${KERNEL_DEVICETREE}"
    [ -f "$dtb" ] || dtb="${DEPLOY_DIR_IMAGE}/${@os.path.basename(d.getVar('KERNEL_DEVICETREE'))}"
    install -m 0644 "$dtb" ${D}/boot/${@os.path.basename(d.getVar('KERNEL_DEVICETREE'))}
    sed -e 's|@KERNEL_IMAGE@|${KERNEL_IMAGETYPE}|g' \
        -e 's|@KERNEL_DTB@|${@os.path.basename(d.getVar("KERNEL_DEVICETREE"))}|g' \
        -e 's|@CONSOLE_DEVICE@|${K7_CONSOLE_DEVICE}|g' \
        -e 's|@CONSOLE_BAUD@|${K7_CONSOLE_BAUD}|g' \
        -e 's|@ROOT_PARTUUID@|${K7_ROOT_PARTUUID}|g' \
        -e 's|@ROOT_FSTYPE@|${K7_ROOT_FSTYPE}|g' \
        ${UNPACKDIR}/extlinux.conf.in > ${D}/boot/extlinux/extlinux.conf
}

FILES:${PN} = "/boot/extlinux/extlinux.conf /boot/${@os.path.basename(d.getVar('KERNEL_DEVICETREE'))}"

SUMMARY = "KICKPI K7 mainline U-Boot serial/MMC bring-up image"
LICENSE = "MIT"
require recipes-core/images/core-image-minimal.bb
COMPATIBLE_MACHINE = "^kickpi-k7$"
IMAGE_INSTALL:append = " kernel-modules"
IMAGE_FSTYPES = "ext4 wic"
WKS_FILE = "kickpi-k7-mainline.wks.in"
WKS_FILE_DEPENDS = "${WKS_FILE_DEPENDS_DEFAULT}"
K7_WKS_DIR := "${THISDIR}/../../wic"
WKS_SEARCH_PATH:append = ":${K7_WKS_DIR}"
# This image deliberately does not inherit rockchip-image: its Android-style
# boot.img/uboot.img/update.img layout is incompatible with this boot flow.
do_rootfs[depends] += "virtual/kernel:do_deploy virtual/bootloader:do_deploy"
do_image_wic[depends] += "virtual/bootloader:do_deploy"
ROOTFS_POSTPROCESS_COMMAND:append = " k7_install_boot_files;"
k7_install_boot_files() {
    install -d ${IMAGE_ROOTFS}/boot/extlinux
    install -m 0644 ${DEPLOY_DIR_IMAGE}/Image ${IMAGE_ROOTFS}/boot/Image
    dtb="${DEPLOY_DIR_IMAGE}/rk3576-kickpi-k7.dtb"
    [ -f "$dtb" ] || dtb="${DEPLOY_DIR_IMAGE}/rockchip/rk3576-kickpi-k7.dtb"
    install -m 0644 "$dtb" ${IMAGE_ROOTFS}/boot/rk3576-kickpi-k7.dtb
    cat > ${IMAGE_ROOTFS}/boot/extlinux/extlinux.conf <<'CFG'
default k7
timeout 30
label k7
    linux /boot/Image
    fdt /boot/rk3576-kickpi-k7.dtb
    append earlycon console=ttyS0,1500000 root=PARTUUID=614e0000-0000-4b53-8000-1d28000054a9 rootfstype=ext4 rootwait rw
CFG
}

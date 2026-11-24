SUMMARY = "Early root filesystem for K7 extlinux boot"
LICENSE = "MIT"

inherit core-image

COMPATIBLE_MACHINE = "^kickpi-k7$"
IMAGE_FSTYPES = "cpio.gz"
IMAGE_NAME_SUFFIX = ""
IMAGE_LINK_NAME = "${PN}-${MACHINE}"
IMAGE_FEATURES = ""
IMAGE_LINGUAS = ""
IMAGE_INSTALL = ""
PACKAGE_INSTALL = "initramfs-framework-base initramfs-module-udev initramfs-module-rootfs busybox udev base-passwd ${ROOTFS_BOOTSTRAP_INSTALL}"
PACKAGE_EXCLUDE = "kernel-image-*"
IMAGE_ROOTFS_SIZE = "8192"
IMAGE_ROOTFS_EXTRA_SPACE = "0"

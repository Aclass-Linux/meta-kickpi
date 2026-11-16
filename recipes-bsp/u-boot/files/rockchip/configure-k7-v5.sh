#!/bin/sh
set -eu
./scripts/config --file .config \
    --set-str DEFAULT_DEVICE_TREE rockchip/rk3576-kickpi-k7 \
    --set-str DEFAULT_FDT_FILE rockchip/rk3576-kickpi-k7.dtb \
    --disable SYS_MMCSD_RAW_MODE_U_BOOT_USE_PARTITION \
    --set-val SYS_MMCSD_RAW_MODE_U_BOOT_SECTOR 0x4000 \
    --set-val SPL_PAD_TO 0 \
    --enable BOOTSTD --enable BOOTSTD_DEFAULTS --enable BOOTSTD_FULL \
    --enable BOOTMETH_EXTLINUX --enable CMD_BOOTFLOW \
    --enable CMD_BOOTI --enable LEGACY_IMAGE_FORMAT --enable SUPPORT_RAW_INITRD \
    --disable FIT_SIGNATURE

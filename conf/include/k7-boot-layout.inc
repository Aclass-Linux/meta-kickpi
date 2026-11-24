# K7 的默认启动布局，由 WIC 和 extlinux.conf 共用。
# 磁盘偏移契约（K7_LOADER_OFFSET_KIB / K7_FIT_OFFSET_KIB /
# K7_ROOT_ALIGNMENT_KIB）已统一定义在 BSP 层 conf/machine/kickpi-k7.conf，
# WIC 与两套 U-Boot 方案共同引用；本文件仅保留共用的根分区参数。
K7_ROOT_PARTUUID = "614e0000-0000-4b53-8000-1d28000054a9"
K7_ROOT_FSTYPE = "ext4"

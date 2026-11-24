SUMMARY = "Base runtime packages for the KICKPI K7 product image"
LICENSE = "MIT"

PACKAGE_ARCH = "${MACHINE_ARCH}"
COMPATIBLE_MACHINE = "^kickpi-k7$"

inherit packagegroup

# 保留 OE-Core 选定的基本启动环境与 K7 的 Linux 启动文件。
# 产品服务、运行库和板上验证过的 kernel-module-* 包可在此按需添加。
RDEPENDS:${PN} = " \
    packagegroup-core-boot \
    k7-boot-files \
"

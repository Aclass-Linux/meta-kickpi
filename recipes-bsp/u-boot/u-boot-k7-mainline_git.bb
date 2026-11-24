# K7 上游（mainline）U-Boot 学习方案。
# 与厂商方案 u-boot-k7-rockchip_git.bb 的主要区别：
#   - U-Boot/SPL 从上游源码编译，idbloader.img 和 FIT 由上游 binman 生成，
#     不再使用厂商 boot_merger 和 INI 打包；
#   - DDR 初始化固件和 BL31 由独立配方 k7-rkbin 准备（见第 4 节）；
#   - FIT 只含 U-Boot + DTB + BL31，不含厂商方案的 OP-TEE（BL32）；
#   - 部署产物统一带 k7-mainline- 前缀，与厂商方案并存、互不覆盖。
# 在 local.conf 设置 K7_BOOT_STACK = "mainline" 即可切换，
# 选择逻辑见 conf/machine/kickpi-k7.conf。

# --- 1. 复用 OE-Core 的通用构建逻辑 -------------------------------------
# require 在当前位置展开指定文件，找不到会报错，按从上到下顺序处理。
# 注意：require 两个 .inc 不等于继承 u-boot_<版本>.bb，
# 版本选择、SRCREV 和补丁清单都要由本配方自己维护；
# 而 .inc 里的构建逻辑会随当前 OE-Core 检出版本变化。
#   u-boot-common.inc：许可证等元数据、源码默认值、B = "${WORKDIR}/build"；
#   u-boot.inc：PROVIDES virtual/bootloader、交叉编译参数，以及
#               do_configure/do_compile/do_install/do_deploy 任务。
# 变量覆盖规则：普通 = 赋值后者覆盖前者，所以下面的 PV、SRCREV、SRC_URI
# 会替换 common.inc 中的默认值；未重新赋值的变量继续沿用。
# （?=、??=、+=、:append 等语义不同，不能套用这条规则。）
require recipes-bsp/u-boot/u-boot-common.inc
require recipes-bsp/u-boot/u-boot.inc

SUMMARY = "从上游源码构建 K7 U-Boot 和 SPL（学习方案）"
# 适用范围：解析阶段与 MACHINE 做正则匹配，机器不符则整个配方被 skip；
# 与 u-boot.inc 的 PACKAGE_ARCH = "${MACHINE_ARCH}" 配套，防止跨机器复用产物。
COMPATIBLE_MACHINE = "^kickpi-k7$"

# --- 2. 固定源码版本 -----------------------------------------------------
# PV 只是版本标签，不会自动跟随上游最新版；
# 真正锁定源码的是 SRCREV（提交号）和 SRC_URI 中的 tag=v${PV}。
# 若想跟随 master 学习：去掉 ;tag=v${PV}，SRCREV 改为 "${AUTOREV}"，
# PV 改成开发版本标识（如 "2026.01+git"）。代价：
#   - AUTOREV 可能在解析阶段联网；
#   - 上游变化可能要求同步调整设备树、defconfig 和补丁；
#   - 下面的 CVE 补丁需先确认是否已合入上游，再决定去留。
PV = "2026.01"
SRCREV = "127a42c7257a6ffbbd1575ed1cbaa8f5408a44b3"
# git 段用 = 覆盖 u-boot-common.inc 的默认值，把源码参数锚定在本配方；
# 板级输入用 :append 追加，git 源码与本地文件两层分工一目了然。
# 注意：= 覆盖后必须重写 git 段本身，否则配方就没有 fetch 源了；
# :append 不会自动补空格，追加值开头要自带一个空格。
SRC_URI = "git://source.denx.de/u-boot/u-boot.git;protocol=https;branch=master;tag=v${PV}"
SRC_URI:append = " file://CVE-2026-46728.patch"
FILESEXTRAPATHS:prepend := "${COREBASE}/meta/recipes-bsp/u-boot/files:"
# OE-Core 将 Git 解包到 UNPACKDIR/BP，沿用与其匹配的默认 S。
DEPENDS += "bc-native dtc-native gnutls-native python3-pyelftools-native"

K7_UPSTREAM_PREFIX = "k7-mainline"
require recipes-bsp/u-boot/k7-upstream-board.inc

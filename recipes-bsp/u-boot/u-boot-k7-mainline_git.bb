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
SRC_URI:append = " file://kickpi-k7-rk3576_defconfig \
                   file://rk3576-kickpi-k7.dts \
                   file://rk3576-kickpi-k7-u-boot.dtsi \
                   file://CVE-2026-46728.patch"
# 文件搜索路径：板级文件在本层 files/mainline/；
# CVE-2026-46728.patch（FIT 签名校验修复）来自 OE-Core 的 files 目录。
# 因为本配方没有扩展 u-boot_2026.01.bb，必须手动把 OE-Core 的路径加进来，
# 才能复用上游针对同一版本提供的修复。
FILESEXTRAPATHS:prepend := "${THISDIR}/files/mainline:${COREBASE}/meta/recipes-bsp/u-boot/files:"
# 新版 OE 解包布局：git 源码位于 UNPACKDIR/git。
# B 沿用 common.inc 的 WORKDIR/build，即源码树外构建；
# 厂商方案因 FIT 脚本限制必须 B = S（树内构建），可对照理解。
S = "${UNPACKDIR}/git"
DEPENDS += "bc-native dtc-native gnutls-native python3-pyelftools-native"

# --- 3. 构建目标与产物形式 -----------------------------------------------
# 使用本地 defconfig（由第 5 节的 do_configure:prepend 装入源码树）。
UBOOT_MACHINE = "kickpi-k7-rk3576_defconfig"
# u-boot.inc 的 install/deploy 只搬运此主产物；RK3576 产出 FIT 而非 u-boot.bin，
# 必须覆盖。idbloader.img 不归它管，由 do_deploy:append 手动部署。
UBOOT_BINARY = "u-boot.itb"
# 产物后缀，拼进部署文件名（UBOOT_IMAGE/SYMLINK），默认 bin。
UBOOT_SUFFIX = "itb"
# make 目标，与默认相同、显式可读；all 由 binman 产出 itb 和 idbloader 两个镜像。
UBOOT_MAKE_TARGET = "all"
# 置空表示不生成、不部署 u-boot-initial-env。
UBOOT_INITIAL_ENV = ""

# --- 4. 二进制固件输入：DDR 与 BL31 --------------------------------------
# 任务级依赖（不同于 DEPENDS 的配方级依赖）：保证 do_compile 开始前，
# k7-rkbin 已把固件部署到 DEPLOY_DIR_IMAGE。k7-rkbin 与厂商方案使用
# 同一版 rkbin、同一份 DDR 参数，便于对照；DDR 初始化仍是厂商二进制。
# 下面两行向 make 传上游要求的固件路径；文件名与 k7-rkbin 的部署名
# 是手动契约，改动需两边同步：
#   ROCKCHIP_TPL → rk3576-ddr-k7-1560.bin：DDR 初始化阶段（其后才是源码
#                  编译的 SPL），binman 将 TPL+SPL 合成 idbloader.img；
#   BL31         → bl31.elf：ARM Trusted Firmware，由 binman 嵌入 FIT。
# 两者对应上游 binman 的外部 blob 槽位；同文件还有可选槽位 tee-os
# （变量 TEE）未喂，所以 FIT 不含 OP-TEE，这是与厂商方案的差异点。
do_compile[depends] += "k7-rkbin:do_deploy"
EXTRA_OEMAKE += "ROCKCHIP_TPL=${DEPLOY_DIR_IMAGE}/k7-mainline-firmware/rk3576-ddr-k7-1560.bin"
EXTRA_OEMAKE += "BL31=${DEPLOY_DIR_IMAGE}/k7-mainline-firmware/bl31.elf"

# --- 5. 任务定制 ----------------------------------------------------------
# :prepend 在 OE-Core 的 do_configure（make ${UBOOT_MACHINE}）之前执行：
# 先把本地文件装入源码树，defconfig 和设备树才能被 make 找到。
# 三个文件均为新增（非修改上游文件），直接 install 比维护补丁干净；
# install 是部署式复制：-m 0644 把权限统一为 rw-r--r--，不随源文件变化，
# 保证同一配方构建结果一致。${UNPACKDIR}（file:// 解包处）→ ${S} 各归其位：
#   defconfig → configs/，供 make ${UBOOT_MACHINE} 读取；
#   dts → arch/arm/dts/，按 CONFIG_DEFAULT_DEVICE_TREE 找同名文件编译 dtb；
#   -u-boot.dtsi → 同目录，构建规则自动 include，无需显式引用。
# 本地设备树目前仅适配串口、SD/eMMC 和 USB，其余外设需另行验证。
do_configure:prepend() {
    install -m 0644 ${UNPACKDIR}/kickpi-k7-rk3576_defconfig ${S}/configs/
    install -m 0644 ${UNPACKDIR}/rk3576-kickpi-k7.dts ${S}/arch/arm/dts/
    install -m 0644 ${UNPACKDIR}/rk3576-kickpi-k7-u-boot.dtsi ${S}/arch/arm/dts/
}

# :append 在 OE-Core 的 do_compile 之后执行，只做校验，不写盘：
# 产物写入镜像由 WIC 完成（kickpi-k7-rockchip.wks.in）。
# 尺寸上限引用机器配置推导的常量（kickpi-k7.conf：布局偏移 → 字节数），
# 改布局只需改机器配置一处，WIC 与两套 U-Boot 方案同步生效。
do_compile:append() {
    test -s ${B}/idbloader.img || bbfatal "Mainline IDBLoader missing"
    test -s ${B}/u-boot.itb || bbfatal "Mainline FIT missing"
    test "$(wc -c < ${B}/idbloader.img)" -le ${K7_IDB_MAX_BYTES} || bbfatal "IDB overlaps FIT"
    test "$(wc -c < ${B}/u-boot.itb)" -le ${K7_FIT_MAX_BYTES} || bbfatal "FIT overlaps rootfs"
    # K7 只有单个 rootfs，不允许启用 A/B 启动。
    if grep -q '^CONFIG_SPL_AB=y$' ${B}/.config; then
        bbfatal "K7 requires SPL AB disabled"
    fi
}

# 以 k7-mainline- 前缀部署，避免覆盖厂商方案产物；
# 基础 do_deploy（u-boot.inc）还会部署 u-boot.itb 及通用符号链接。
# USB 烧录用 loader（k7-mainline-loader.bin）由 k7-rkbin 独立导出，不在此处。
# 四个产物的角色（前两个进镜像，后两个只留档）：
#   idbloader.img：TPL(厂商 DDR 固件)+SPL(binman 合成)，BootROM 在 LBA64 读取，
#                  TPL 先初始化 DDR，SPL 负责加载下一级；
#   u-boot.itb：FIT 容器(U-Boot 主体+DTB+BL31)，SPL 在 LBA16384 读取，
#               先启动 BL31 再交给 U-Boot proper，之后按 extlinux 加载内核；
#   spl.bin：idbloader 的打包原料，WIC 不用，仅供调试核对尺寸或手动组装，
#            不能替代 USB 烧录的 loader.bin（其外层另套下载协议头）；
#   config：构建期 .config 快照，审计依据（WORKDIR 会清理，部署目录才持久）。
do_deploy:append() {
    install -m 0644 ${B}/idbloader.img ${DEPLOYDIR}/k7-mainline-idbloader.img
    install -m 0644 ${B}/u-boot.itb ${DEPLOYDIR}/k7-mainline-u-boot.itb
    install -m 0644 ${B}/spl/u-boot-spl.bin ${DEPLOYDIR}/k7-mainline-spl.bin
    install -m 0644 ${B}/.config ${DEPLOYDIR}/k7-mainline-u-boot.config
}
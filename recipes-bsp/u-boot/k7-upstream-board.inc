# 方案 b 和 c 共用 K7 板级输入、固件依赖、尺寸校验和部署规则。
FILESEXTRAPATHS:prepend := "${THISDIR}/files/mainline:"
SRC_URI:append = " file://kickpi-k7-rk3576_defconfig file://rk3576-kickpi-k7.dts file://rk3576-kickpi-k7-u-boot.dtsi"

# --- 3. 构建目标与产物形式 -----------------------------------------------
# 使用本地 defconfig（由第 5 节的 do_configure:prepend 装入源码树）。
UBOOT_MACHINE = "kickpi-k7-rk3576_defconfig"
# 上游部署任务用此后缀命名 FIT；主产物名和初始环境策略由机器配置提供。
UBOOT_SUFFIX = "itb"

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

# 以各自的方案前缀部署，避免覆盖厂商方案产物；
# 基础 do_deploy（u-boot.inc）还会部署 u-boot.itb 及通用符号链接。
# USB 烧录用 loader（k7-mainline-firmware/usb-loader.bin）由 k7-rkbin 独立导出，不在此处。
# 四个产物的角色（前两个进镜像，后两个只留档）：
#   idbloader.img：TPL(厂商 DDR 固件)+SPL(binman 合成)，BootROM 在 LBA64 读取，
#                  TPL 先初始化 DDR，SPL 负责加载下一级；
#   u-boot.itb：FIT 容器(U-Boot 主体+DTB+BL31)，SPL 在 LBA16384 读取，
#               先启动 BL31 再交给 U-Boot proper，之后按 extlinux 加载内核；
#   spl.bin：idbloader 的打包原料，WIC 不用，仅供调试核对尺寸或手动组装，
#            不能替代 USB 烧录的 loader.bin（其外层另套下载协议头）；
#   config：构建期 .config 快照，审计依据（WORKDIR 会清理，部署目录才持久）。
do_deploy:append() {
    install -m 0644 ${B}/idbloader.img ${DEPLOYDIR}/${K7_UPSTREAM_PREFIX}-idbloader.img
    install -m 0644 ${B}/u-boot.itb ${DEPLOYDIR}/${K7_UPSTREAM_PREFIX}-u-boot.itb
    install -m 0644 ${B}/spl/u-boot-spl.bin ${DEPLOYDIR}/${K7_UPSTREAM_PREFIX}-spl.bin
    install -m 0644 ${B}/.config ${DEPLOYDIR}/${K7_UPSTREAM_PREFIX}-u-boot.config
}

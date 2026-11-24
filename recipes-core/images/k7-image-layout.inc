# K7 镜像共用的磁盘布局和 RKDevTool 导出任务。
require conf/include/k7-boot-layout.inc
COMPATIBLE_MACHINE = "^kickpi-k7$"

# 验证镜像沿用原目录，产品镜像指定自己的目录，避免固定文件名相互覆盖。
K7_USB_FLASH_DIR ?= "usb-flash-k7"

# a. 镜像格式与磁盘布局
# ext4 是独立的根文件系统镜像，wic 是包含分区表和引导固件的整盘镜像。
IMAGE_FSTYPES = "ext4 wic"
# WIC 根据模板把 IDBLoader 和 U-Boot FIT 写到固定偏移，再创建根分区。
# 根分区的 PARTUUID 与 extlinux.conf 中的 root=PARTUUID 共用产品配置。
WKS_FILE = "kickpi-k7-rockchip.wks.in"
# 使用 OE-Core 提供的默认 WIC 构建工具依赖。
WKS_FILE_DEPENDS = "${WKS_FILE_DEPENDS_DEFAULT}"
# 当前镜像使用 extlinux 和 WIC 布局。
# [depends] 声明跨配方任务依赖，WIC 组盘前要等当前 U-Boot 提供者部署产物。
do_image_wic[depends] += "virtual/bootloader:do_deploy"

# b. 镜像完成后导出 RKDevTool 所需文件
# 此扩展同样直接读取 U-Boot 的部署产物，因此也显式声明任务依赖。
# :append 把下面的 Python 代码接到 OE-Core 的 do_image_complete 任务末尾。
do_image_complete[depends] += "virtual/bootloader:do_deploy"
python do_image_complete:append() {
    from pathlib import Path
    import shutil

    # DEPLOY_DIR_IMAGE 存放引导产物，IMGDEPLOYDIR 是本次镜像的暂存目录。
    # K7_BOOT_PREFIX 为当前方案选择对应文件，例如 oe 方案使用 k7-oe 前缀。
    deploy = Path(d.getVar("DEPLOY_DIR_IMAGE"))
    staged = Path(d.getVar("IMGDEPLOYDIR"))
    prefix = d.getVar("K7_BOOT_PREFIX")
    config = (deploy / (prefix + "-u-boot.config")).read_text()
    # 校验实际构建出的 U-Boot 配置。厂商路线要求显式关闭 SPL A/B 探测，
    # 上游路线允许配置中没有该选项。发现启用时用 bb.fatal 终止导出。
    lines = config.splitlines()
    if "CONFIG_SPL_AB=y" in lines or (
            d.getVar("K7_BOOT_STACK") in ("rockchip", "jeffy") and
            "# CONFIG_SPL_AB is not set" not in lines):
        bb.fatal("K7 USB export requires CONFIG_SPL_AB disabled")
    # 读取 SPL 二进制并检查临时调试标记，防止把带诊断打印的版本交付烧录。
    spl = (deploy / (prefix + "-spl.bin")).read_bytes()
    if b"[K7TRACE" in spl:
        bb.fatal("K7 USB export still contains temporary SPL diagnostics")

    # 烧录文件先写入镜像暂存目录，随后随镜像一起发布到部署目录。
    output = staged / d.getVar("K7_USB_FLASH_DIR")
    output.mkdir(parents=True, exist_ok=True)
    # rockchip 和 jeffy 使用包含本次构建 SPL 的 USB Loader。
    # mainline 和 oe 共用 k7-rkbin 配方准备的 USB Loader。
    loader = (prefix + "-loader.bin" if d.getVar("K7_BOOT_STACK") in ("rockchip", "jeffy")
              else "k7-mainline-firmware/usb-loader.bin")
    # 用固定名称导出两个文件。Loader 用于 USB 下载，整盘 WIC 用于写入 eMMC。
    # .img 文件直接复制自 .wic，保留原有分区表和磁盘内容。
    shutil.copyfile(deploy / loader,
                    output / "k7-rockchip-loader.bin")
    shutil.copyfile(staged / (d.getVar("IMAGE_NAME") + ".wic"),
                    output / "kickpi-k7-emmc.img")
}

SUMMARY = "KICKPI K7 Yocto image with selectable bootloader"
LICENSE = "MIT"
require recipes-core/images/core-image-minimal.bb
require conf/include/k7-boot-layout.inc
COMPATIBLE_MACHINE = "^kickpi-k7$"
IMAGE_INSTALL:append = " kernel-modules k7-boot-files"
IMAGE_FSTYPES = "ext4 wic"
WKS_FILE = "kickpi-k7-rockchip.wks.in"
WKS_FILE_DEPENDS = "${WKS_FILE_DEPENDS_DEFAULT}"
# 不继承 rockchip-image；其 Android 镜像布局不符合当前 extlinux/WIC 启动流程。
do_image_wic[depends] += "virtual/bootloader:do_deploy"

# 只导出 RKDevTool 所需的两个文件；切换方案后对外文件名保持不变。
do_image_complete[depends] += "virtual/bootloader:do_deploy"
python do_image_complete:append() {
    from pathlib import Path
    import shutil

    deploy = Path(d.getVar("DEPLOY_DIR_IMAGE"))
    staged = Path(d.getVar("IMGDEPLOYDIR"))
    prefix = d.getVar("K7_BOOT_PREFIX")
    config = (deploy / (prefix + "-u-boot.config")).read_text()
    # 厂商配置必须明确关闭 SPL_AB；上游可能没有该符号，但不能启用它。
    lines = config.splitlines()
    if "CONFIG_SPL_AB=y" in lines or (
            d.getVar("K7_BOOT_STACK") == "rockchip" and
            "# CONFIG_SPL_AB is not set" not in lines):
        bb.fatal("K7 USB export requires CONFIG_SPL_AB disabled")
    spl = (deploy / (prefix + "-spl.bin")).read_bytes()
    if b"[K7TRACE" in spl:
        bb.fatal("K7 USB export still contains temporary SPL diagnostics")

    output = staged / "usb-flash-k7"
    output.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(deploy / (prefix + "-loader.bin"),
                    output / "k7-rockchip-loader.bin")
    shutil.copyfile(staged / (d.getVar("IMAGE_NAME") + ".wic"),
                    output / "kickpi-k7-emmc.img")
}

# a. 配方信息与适用范围
# LICENSE 声明许可证，LIC_FILES_CHKSUM 用于校验 OE-Core 提供的 MIT 许可证文本。
SUMMARY = "K7 standard extlinux boot configuration and initramfs"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

# 引入 WIC 和 extlinux 共用的根分区 PARTUUID 与文件系统类型。
require conf/include/k7-boot-layout.inc
COMPATIBLE_MACHINE = "^kickpi-k7$"
# 包含板级 DTB 和启动参数，按机器区分软件包。
PACKAGE_ARCH = "${MACHINE_ARCH}"

# b. 启动文件的来源与任务依赖
# 安装本包时同时安装 kernel-image，由它提供 /boot/Image。
RDEPENDS:${PN} += "kernel-image"
# files/ 中的 extlinux 模板解包到 UNPACKDIR，S 指向模板所在目录。
SRC_URI = "file://extlinux.conf.in"
S = "${UNPACKDIR}"

# 使用 initramfs 镜像部署时生成的固定名称链接。
K7_INITRAMFS_LINK = "kickpi-initramfs-${MACHINE}.cpio.gz"
# 安装前等待内核部署 DTB，并等待 initramfs 镜像生成完毕。
do_install[depends] += "virtual/kernel:do_deploy kickpi-initramfs:do_image_complete"

# c. 安装到软件包暂存目录
# ${D} 是安装暂存目录。软件包进入镜像后，这里的 /boot 路径即为目标机路径。
do_install() {
    install -d ${D}/boot/extlinux ${D}/boot/dtb/rockchip

    # 优先查找带 rockchip/ 子路径的 DTB，兼容直接部署在顶层的文件。
    dtb="${DEPLOY_DIR_IMAGE}/${KERNEL_DEVICETREE}"
    [ -f "$dtb" ] || dtb="${DEPLOY_DIR_IMAGE}/${@os.path.basename(d.getVar('KERNEL_DEVICETREE'))}"
    install -m 0644 "$dtb" ${D}/boot/dtb/${KERNEL_DEVICETREE}
    install -m 0644 ${DEPLOY_DIR_IMAGE}/${K7_INITRAMFS_LINK} ${D}/boot/initramfs.cpio.gz

    # 将串口、根分区和 DTB 参数写入模板，生成 U-Boot 要读取的配置。
    sed -e 's|@CONSOLE_DEVICE@|${K7_CONSOLE_DEVICE}|g' \
        -e 's|@CONSOLE_BAUD@|${K7_CONSOLE_BAUD}|g' \
        -e 's|@ROOT_PARTUUID@|${K7_ROOT_PARTUUID}|g' \
        -e 's|@ROOT_FSTYPE@|${K7_ROOT_FSTYPE}|g' \
        -e 's|@KERNEL_DTB@|${KERNEL_DEVICETREE}|g' \
        ${S}/extlinux.conf.in > ${D}/boot/extlinux/extlinux.conf
}

# d. 指定主包收录的路径，供镜像通过 k7-boot-files 安装。
FILES:${PN} = "/boot/extlinux /boot/initramfs.cpio.gz /boot/dtb"

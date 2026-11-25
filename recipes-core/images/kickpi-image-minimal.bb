# a. 配方说明与公共配置
# SUMMARY 和 LICENSE 分别描述镜像配方的用途与许可证。
SUMMARY = "KICKPI K7 BSP validation image"
LICENSE = "MIT"
# require 在解析时引入必需文件，找不到时会报错。
# 复用 OE-Core 的最小系统镜像。
require recipes-core/images/core-image-minimal.bb

# b. 根文件系统的软件包
# :append 在基础镜像的包列表末尾追加内容，字符串开头的空格用来分隔包名。
# kernel-modules 拉入已打包的内核模块。k7-boot-files 安装 extlinux.conf、
# Linux DTB 和 initramfs，并通过运行时依赖将 kernel-image 拉入根文件系统。
IMAGE_INSTALL:append = " kernel-modules k7-boot-files"
require recipes-core/images/k7-image-features.inc

# c. 两种 K7 镜像共用磁盘布局与烧录文件导出逻辑。
require recipes-core/images/k7-image-layout.inc

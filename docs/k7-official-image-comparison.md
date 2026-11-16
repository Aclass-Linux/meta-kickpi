# 当前 Yocto 镜像与官方实际镜像来源核对

本次依据实际二进制，不将 armbian-k7 项目默认配置当作已发布镜像的组成。
官方 SD 回读：`/home/ryan/Yocto/official-sd-first32MiB.bin`。
官方完整更新包：`/home/ryan/Yocto/update-rk3576-kickpi-k7-linux-ubuntu2404-multi-20261110-114800.img`。
当前 WIC：`kickpi-image-minimal-kickpi-k7.rootfs-20261115142237.wic`。
更新包内 uboot.img 已读取前 2 MiB，与 SD 回读对应位置完全一致；其中全部 FIT payload
均位于该范围，全部 SHA-256 校验通过。

| 组件 | 当前来源/组成 | 官方实际镜像 |
| --- | --- | --- |
| BootROM | RK3576 内部 | 同芯片 |
| Boost | Rockchip rkbin v1.03 | 精确匹配官方 rkbin v1.03 二进制 |
| DDR | 官方普通 v1.13，工具配置为 K7 v1.08 共同参数 | v1.09，build 2f85f4b2d4，LP4 1560；LP5 参数为 1968 |
| SPL | 上游 U-Boot v2026.10，本地交叉编译 | 厂商 2017.09，Nov 10 2026 11:45:07；镜像不提供源码提交身份 |
| U-Boot 本体 | 同一上游 v2026.10 源码 | 2017.09，Nov 10 2026 11:45:07 +0800 |
| BL31 | 上游 TF-A v2.15.0 本地编译 | Rockchip v1.20，内部字符串 TF-A v2.3-931-g4d7e811c6 |
| BL32 | 未打包 | OP-TEE v1.06，内部版本 3.13.0-1004-g667b54418 |
| U-Boot DTB | 上游 RK3576 + 本地 K7 最小 DTS | model 为 Rockchip RK3576 Evaluation board |
| Linux | linux-yocto 6.18.52，本地交叉编译 | 6.1.75，#65，2026-11-09，hcm@kickpi，GCC 10.3.1 |
| Linux DTB | 上游 RK3576 + 本地 K7 DTS | model 为 Rockchip RK3576 KICKPI K7 Board |
| Linux 启动 | extlinux + Image + 原始 gzip initramfs | boot.img FIT 内 fdt/kernel/resource；无独立 ramdisk 节点 |
| rootfs | Yocto/OpenEmbedded core-image-minimal 派生 | 更新包 rootfs.img；Ubuntu 24.04 仅由文件名标识，本次未提取 os-release |

## 当前源码固定版本

- U-Boot：github.com/u-boot/u-boot，5508406582f6f7120dce0c4b3059819a41788db2。
- TF-A：github.com/ARM-software/arm-trusted-firmware，da738d5eae93af342fdc4995dd3c05acb4c9d757。
- DDR/Boost/boot_merger/USB plug：github.com/rockchip-linux/rkbin，3e288fe814e059dd06833495f845cab04ac20a5c。
- Linux：git.yoctoproject.org/linux-yocto.git，machine 364c4b44ab2dcc7dfff16076522b62827bcb0e7d；
  kernel-cache 2184786cc3deed04926e1cca6c320ed9314da9da。
- 板级 DTS、defconfig、extlinux、initramfs 及 rootfs 包选择来自本地 meta-kickpi，
  不属于未经修改的上游 K7 板级支持。

## 布局与加载地址

两者 SD IDB 在 32 KiB，U-Boot FIT 在 8 MiB；我们由官方 boot_merger 打包 IDB，
上游 binman 生成 FIT。官方文件能确认格式，不能仅凭二进制确认当时使用的打包工具版本。
当前 U-Boot 加载地址 0x40800000，官方为 0x40200000。
当前 BL31 分段加载到 0x3fe70000 / 0x40040000 / 0x4005d000；
官方为 0x40060000 / 0x400f0000 / 0x3fe70000，OP-TEE 位于 0x48400000。

官方更新包是 RKFW/RKAF 容器，不是可直接等同 WIC 的裸整盘镜像。
其 parameter 定义：uboot 8 MiB、misc 12 MiB、boot 16 MiB、recovery 80 MiB、
backup 208 MiB、rootfs 240 MiB。当前 WIC 在 16 MiB 即开始 ext4 rootfs。

官方 v1.09 与当前 v1.13 配置回读的共同字段，仅发现 lp5_freq 与 LP4/LP4X F2 不同：
官方 F2 为 1068 MHz，当前按 K7 v1.08 对齐为 1560 MHz。这再次说明参考项目默认配置
不等同官方这份发布镜像；不能要求同时与 v1.08 参考文件及 v1.09 实际文件全部一致。

本次仅分析并记录，没有更改镜像方案。官方 SPL/U-Boot 的准确源码仓库、完整提交和补丁集
不能从版本字符串证明，需要该版本发布清单或源码映射。

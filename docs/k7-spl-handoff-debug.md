# K7：SPL 无 A/B 配置与烧录

## 当前配置

当前镜像使用单个 rootfs。`configure-k7-v5.sh` 固定关闭 `CONFIG_SPL_AB`，
配方在 Kconfig 配置完成后检查该选项，若重新启用则构建失败。
临时诊断打印补丁和自动打包脚本已删除，保留启动组件自身的正常输出。
旧版 `u-boot-k7-official` 配方及其两个兼容补丁也已移除；当前 BSP 不再维护
本地 `.patch` 文件。构建目录中五个 SPL 源文件的诊断改动已恢复为上游源码。

## 构建与固定文件输出

```bash
cd /home/ryan/Yocto/workspace
source sources/openembedded-core/oe-init-build-env build-k7
bitbake kickpi-image-minimal
```

默认配方固定此前使用的 U-Boot 与 rkbin 提交，无 A/B 设置直接由配置脚本应用，
不需要额外的 `.inc`、`-R` 参数或修改 `local.conf`。后续需要 A/B 时再统一调整
分区布局、SPL 配置和镜像导出检查。
镜像完成任务会检查 SPL 不含临时诊断标记、构建配置关闭 A/B，然后自动复制
完整镜像和 Loader。固定目录为：

```text
/home/ryan/Yocto/workspace/build-k7/tmp/deploy/images/kickpi-k7/usb-flash-k7/
├── k7-rockchip-loader.bin
└── kickpi-k7-emmc.img
```

此目录只导出两个真实文件，不生成 ZIP、随机目录名、版本后缀或附带清单。
每次成功构建更新这两个文件。若构建失败，不使用旧目录判断本次构建结果。

## 烧录

将这两个文件复制到 Windows。使用此前成功的 RKDevTool 配置，
Loader 行选择 `k7-rockchip-loader.bin`，Image 行选择 `kickpi-k7-emmc.img`，
地址为 0，勾选按地址写入，两行均勾选。
完整磁盘镜像写入会覆盖原系统及数据，不使用“升级固件”的解包入口。

烧录成功后拔掉 SD 卡、松开启动按键，彻底断电上电。
串口设置为 1500000、8N1、无流控，日志不应再出现临时诊断前缀。
旧诊断包保持原样，不要误选旧目录里的文件。

## 定位记录与验证边界

此前硬件日志显示 FIT 校验、复制和设备树记录完成，随后停在 SPL A/B 探测
对 `misc` 分区的严格查找调用中。当前单 rootfs 方案不需要该探测。
关闭此路径不能单独证明 GPT 解析或底层块读取中的具体根因。

已检查配置脚本语法及固定文件导出逻辑。当前执行环境限制 BitBake 服务
创建 Unix socket，完整重建和清理后的硬件启动仍需在本机终端验证。

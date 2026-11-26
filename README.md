# meta-kickpi

KICKPI K7（RK3576）的自有 Yocto BSP。Linux 跟踪 rockchip-linux/kernel 的 develop-6.12。
Scarthgap 分支支持厂商 v5、固定上游版本和 JeffyCN 配方三条 U-Boot/SPL 路线。
启动路线默认值见 `conf/machine/kickpi-k7.conf`，在构建环境中运行
`bitbake-getvar K7_BOOT_STACK` 可查看当前选择。DDR 和 BL31 使用 rkbin 固件。
Yocto 负责根文件系统、应用和 WIC 镜像。

已启动 Linux 的历史厂商基线保存在本地 `baseline/k7-vendor-linux-boot`（`0a1f895`）。
Scarthgap 开发分支为 `k7-scarthgap-local`。
当前 RK BSP 组合已完成完整镜像构建；实机与外设验收需按交付版本执行。

## 设计与操作入口

- [新电脑复现、构建与 USB 烧录](docs/new-computer.md)

- [BSP/产品层拆分与构建步骤](docs/product-layer.md)
- [当前 Rockchip BSP 构建、来源与验收方案](docs/k7-rockchip-boot.md)
- [社区与 JeffyCN BSP 选型比较](docs/meta-rockchip-comparison.md)
- [官方 K7 引导链、清理清单与构建步骤](docs/k7-official-boot.md)

## 当前代码

```text
conf/machine/kickpi-k7.conf            # 独立 CPU/板卡与 provider 选择
recipes-bsp/u-boot/                   # U-Boot 配方、K7 DTS 和 defconfig
recipes-bsp/rkbin/files/              # 共用 DDR 参数、v1.08 参考固件和处理脚本
recipes-kernel/linux/
  linux-k7-rockchip_git.bb            # RK develop-6.12 内核
  files/rockchip-k7/                  # 当前 Linux 板级设备树
  linux-yocto_6.18.bbappend            # 未选中的历史上游方案
conf/templates/k7-bsp/              # 只加载核心层和 BSP 的验证模板
recipes-core/images/                # BSP 验证镜像、initramfs 和共用镜像配置
recipes-core/boot-config/            # extlinux、DTB 和 initramfs 安装
wic/                                # K7 默认 GPT/ext4 分区布局
layers/meta-product-k7/             # 产品 layer（与 BSP 同仓库，单独启用）
  conf/templates/k7-upstream/       # 产品开发构建模板
  recipes-core/images/              # kickpi-image-product
  recipes-core/packagegroups/       # 产品装机软件清单
docs/                               # 最终方案、来源清单与基础教材
```

Linux 与 U-Boot 的设备树分别维护，不能因同名就互换。

从独立的 `~/Yocto/workspace-scarthgap` 初始化到 `build-k7`。
当前 WIC 使用官方 IDB/FIT 偏移；不使用 Android update.img 布局。

修改前检查 Git 状态，repo detached HEAD 应先创建开发分支。
仓库远端：https://github.com/Aclass-Linux/meta-kickpi.git

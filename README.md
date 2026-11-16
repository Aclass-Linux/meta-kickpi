# meta-kickpi

KICKPI K7（RK3576）的自有 Yocto BSP。当前采用 Rockchip BSP 优先策略：
Linux 跟踪 rockchip-linux/kernel 的 develop-6.12，U-Boot/SPL 跟踪
rockchip-linux/u-boot 的 v5，DDR/BL31/BL32/打包工具跟踪 rkbin/master。
均使用 AUTOREV，固定分支、跟随最新提交；实际版本记录随产物部署。
Yocto 负责根文件系统、应用和 WIC 镜像。

已启动 Linux 的历史厂商基线保存在本地 `baseline/k7-vendor-linux-boot`（`0a1f895`）。
当前工作分支名称 `bringup/k7-upstream-boot` 沿用历史命名，构建内容已改为 RK BSP。
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
recipes-bsp/u-boot/                   # 官方 U-Boot、K7 DTS/defconfig、固件输入
recipes-kernel/linux/
  linux-k7-rockchip_git.bb            # RK develop-6.12 内核
  files/rockchip-k7/                  # 当前 Linux 板级设备树
  linux-yocto_6.18.bbappend            # 未选中的历史上游方案
layers/meta-product-k7/              # 产品 layer（与 BSP 同仓库，单独启用）
  conf/include/                     # GPT/extlinux 共用启动布局
  conf/templates/k7-upstream/        # 开发构建配置模板
  recipes-core/images/              # kickpi-image-minimal
  recipes-core/boot-config/         # extlinux、DTB、原始 initramfs 文件包
  wic/                              # GPT/ext4 分区布局
docs/                               # 最终方案、来源清单与基础教材
```

Linux 与 U-Boot 的设备树分别维护，不能因同名就互换。

操作均从 /home/ryan/Yocto/workspace 初始化到 build-k7。配置和验证结果见上述当前入口；
当前 WIC 使用官方 IDB/FIT 偏移；不使用 Android update.img 布局。

修改前检查 Git 状态，repo detached HEAD 应先创建开发分支。
仓库远端：https://github.com/Aclass-Linux/meta-kickpi.git

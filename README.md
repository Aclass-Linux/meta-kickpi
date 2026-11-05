# meta-kickpi

KICKPI K7（RK3576）的自有 Yocto BSP。当前开发基线是 Wrynose、Yocto 维护的 Linux 6.18.52
和官方 U-Boot 2026.10。厂商 Armbian SDK 用于硬件参考；社区 meta-rockchip 是通用代码的复用目标，尚未加入当前构建。

当前已完成 U-Boot、Linux 内核、ext4/WIC 镜像和标准 SDK 构建。WIC 布局和启动文件已离线核对；SDK 安装、应用编译和真机启动尚未验收。

## 设计与操作入口

- [BSP/产品层拆分与构建步骤](docs/product-layer.md)
- [代码设计方案与分阶段改造清单](docs/code-design.md)
- [社区与 JeffyCN BSP 选型比较](docs/meta-rockchip-comparison.md)
- [当前 Linux 6.18 构建入口](docs/k7-linux-yocto.md)
- [当前主线 U-Boot 构建与验证](docs/k7-mainline-uboot.md)
- [历史迁移记录与 SDK 基础教材](docs/k7-yocto-build.md)

## 当前代码

```text
conf/machine/kickpi-k7.conf            # 独立 CPU/板卡与 provider 选择
recipes-bsp/u-boot/                   # 官方 U-Boot、K7 DTS/defconfig、固件输入
recipes-kernel/linux/
  linux-yocto_6.18.bbappend            # 扩展 OE-Core 维护的内核
  files/yocto-k7/                     # 当前 Linux DT 和内核配置
  files/kickpi-k7/                    # 旧厂商参考文件，当前不应用
layers/meta-product-k7/              # 产品 layer（与 BSP 同仓库，单独启用）
  conf/include/                     # GPT/extlinux 共用启动布局
  conf/templates/k7-upstream/        # 开发构建配置模板
  recipes-core/images/              # kickpi-image-minimal
  recipes-core/boot-config/         # DTB/extlinux 文件包，依赖内核镜像包
  wic/                              # GPT/ext4 分区布局
docs/                               # 设计、操作、验证与历史资料
```

旧厂商追加配方存放在 docs/legacy/vendor-bsp/*.disabled，不参加构建。
Linux 与 U-Boot 的设备树分别维护，不能因同名就互换。

操作均从 workspace-k7 初始化到 build-k7。配置和验证结果见上述当前入口；
不要将历史文档中的厂商烧录偏移应用到主线镜像。

修改前检查 Git 状态，repo detached HEAD 应先创建开发分支。
仓库远端：https://github.com/Aclass-Linux/meta-kickpi.git

# meta-kickpi

KICKPI K7（RK3576）的自有 Yocto BSP。当前开发基线是 Wrynose、Yocto 维护的 Linux 6.18.52
和 KICKPI 官方 Armbian 的配套 SPL/U-Boot 引导链。社区 meta-rockchip 尚未加入当前构建。

用户已烧录当前 WIC 并进入 Linux/OpenEmbedded 用户态；外设完整性与稳定性仍待验收。当前版本保持不变。

## 设计与操作入口

- [BSP/产品层拆分与构建步骤](docs/product-layer.md)
- [社区与 JeffyCN BSP 选型比较](docs/meta-rockchip-comparison.md)
- [官方 K7 引导链、清理清单与构建步骤](docs/k7-official-boot.md)

## 当前代码

```text
conf/machine/kickpi-k7.conf            # 独立 CPU/板卡与 provider 选择
recipes-bsp/u-boot/                   # 官方 U-Boot、K7 DTS/defconfig、固件输入
recipes-kernel/linux/
  linux-yocto_6.18.bbappend            # 扩展 OE-Core 维护的内核
  files/yocto-k7/                     # 当前 Linux DT 和内核配置
layers/meta-product-k7/              # 产品 layer（与 BSP 同仓库，单独启用）
  conf/include/                     # GPT/armbianEnv 共用启动布局
  conf/templates/k7-upstream/        # 开发构建配置模板
  recipes-core/images/              # kickpi-image-minimal
  recipes-core/boot-config/         # 官方 boot.scr、DTB、uInitrd 与 armbianEnv 文件包
  wic/                              # GPT/ext4 分区布局
docs/                               # 最终方案、来源清单与基础教材
```

Linux 与 U-Boot 的设备树分别维护，不能因同名就互换。

操作均从 /home/ryan/Yocto/workspace 初始化到 build-k7。配置和验证结果见上述当前入口；
当前 WIC 使用官方 IDB/FIT 偏移；不使用 Android update.img 布局。

修改前检查 Git 状态，repo detached HEAD 应先创建开发分支。
仓库远端：https://github.com/Aclass-Linux/meta-kickpi.git

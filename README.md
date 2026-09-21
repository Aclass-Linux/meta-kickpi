# meta-kickpi

以 KickPi K7（RK3576）为对象的 Yocto、设备树和 Linux 启动链学习仓库。
当前使用 Scarthgap 与 meta-rockchip 的 Linux 6.1 配方。

## 学习入口

从 [从零学习 K7 Linux 系统开发](LEARNING.md) 开始，将本仓库作为新项目学习，
不按已有代码或历史构建进度跳过内容。唯一主线为：

**rootfs → Kernel → 完整启动链 → 系统集成 → SDK**

完整启动链涵盖 BootROM、DDR 初始化、早期 loader、可信固件和 U-Boot，
具体组件按 K7 实际方案核实。

[工程入门教材](docs/getting-started.md) 用于按需查阅 repo、manifest、layer、
machine 和 BitBake 的基础概念，不作为另一套学习路径。

操作参考：

- [Layer 配置](docs/layer.md)
- [Machine 配置与 BitBake 环境解析](docs/machine.md)
- [设备树注入、patch 生成与模式选择](docs/device-tree.md)

## 目录

```text
meta-kickpi/
├── README.md
├── LEARNING.md
├── docs/
├── conf/
│   ├── layer.conf
│   └── machine/kickpi-k7.conf
└── recipes-kernel/linux/
    ├── linux-rockchip_6.1.bbappend
    └── files/kickpi-k7/           # 四个可读 DTS/DTSI
```

设备树来源：工作区的 `kickpi-armbian/patch/kernel/rk35xx-vendor-6.1/dt/`。
Linux 与 U-Boot 的同名 DTS 需分别处理。

## 实验约定

files 与 patch 是独立学习实验，允许内容不同；每次构建只使用一种。
当前已接入 files 路径，patch 文件和互斥模式开关尚未实现。
解析成功不等于设备树编译成功，更不等于硬件已验证。

文档保存在 `sources/meta-kickpi` Git 仓库。工作区根目录 README 是本文件的
软链接；若编辑器按根目录解析相对链接，请直接打开
`sources/meta-kickpi/LEARNING.md`。

仓库远端：https://github.com/Aclass-Linux/meta-kickpi.git

开始修改前检查 Git 状态；repo 检出的 detached HEAD 应先建立开发分支。

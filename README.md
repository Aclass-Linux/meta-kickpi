# meta-kickpi

以 KickPi K7（RK3576）为对象的 Yocto、设备树和 Linux 启动链学习仓库。
当前使用 Scarthgap 与 meta-rockchip 的 Linux 6.1 配方。

## 学习入口

首次接触本工程，先读 [从零认识当前 K7 Yocto 工程](docs/getting-started.md)：
结合实际文件讲解 repo、manifest、layer、machine 和 BitBake，可独立阅读。

从 [学习计划](LEARNING.md) 开始。学习顺序、实验任务和完成标准统一在该文档
维护，原 A–I 和 L0–L9 两套计划已删除。

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

# K7 设备树维护

当前方案和验收状态见 [可启动方案](k7-official-boot.md)。

## 当前输入

- U-Boot：`recipes-bsp/u-boot/files/official/rk3576-kickpi-k7.dts`，原样官方 K7 文件，继承厂商 SoC DTSI。
- Linux：`recipes-kernel/linux/files/yocto-k7/rk3576-kickpi-k7.dts`，继承 linux-yocto 的 rk3576.dtsi。bbappend 将 DTS 安装到内核源树并加入 DTB 构建列表。
- 产品：k7-boot-files 将 Linux DTB 安装到 `/boot/dtb/rockchip/rk3576-kickpi-k7.dtb`，由 armbianEnv 的 fdtfile 指定。

同名不表示二者可互换。厂商 6.1 的 DTS 不能直接覆盖当前 Linux DTS：节点、绑定和驱动支持需要逐项核对。

## 修改原则

板级 DTS 描述实际走线、供电、外设与状态；SoC DTSI 由对应上游维护。当前精简 DTS 保留串口、MMC 和部分 USB 节点，不代表所有外设已经可用。增加功能前应核对原理图和内核绑定，再执行 DT 编译及可用的 dtbs_check，最后上板验证。不要修改 tmp 下源码作为持久修复，也不要为了消除日志而任意禁用电源域或设备依赖。

串口当前使用 uart0/ttyS0，1500000 baud。SD 50 MHz 和 eMMC 52 MHz 是现有 Linux 基线；升级高速模式前需验证电源、IO 电压切换和信号条件。

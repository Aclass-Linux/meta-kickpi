> 历史记录。当前方案以 [RK BSP 构建方案](k7-rockchip-boot.md) 为准。

> 已被统一瑞芯微方案取代：当前配置与构建步骤见 [k7-rockchip-boot.md](k7-rockchip-boot.md)。以下内容仅作历史记录。

# K7：瑞芯微源码 SPL 与上游 U-Boot

## 当前组成

| 组件 | 来源 |
| --- | --- |
| DDR | 官方 v1.09 按四项频率参数生成，567 项共同参数对齐 v1.08 |
| Boost | 原有固定 rkbin 的 v1.03，不变 |
| SPL | rockchip-linux/u-boot，1c535d65b8509f388d09e49fb6961f49fda35a1d |
| U-Boot 主程序 | 上游 v2026.10，5508406582f6f7120dce0c4b3059819a41788db2 |
| BL31 | 原有源码编译 TF-A 2.15.0，不变 |
| IDB | 原有 RK boot_merger，FlashBoot 改用源码编译的厂商 SPL |

固定 SPL 到本轮下载的官方 next-dev 分支头（2026-07-28）。2017.09 是其基础版本号，不是最后更新日期；不保证与 rkbin 某个预编译 SPL 版本二进制一致。
官方另有基于 2025.04 的 v5 分支，当前未切换。

配方从瑞芯微官方仓库 next-dev 分支获取，固定提交 `1c535d65b8509f388d09e49fb6961f49fda35a1d` 已在本机下载的官方镜像中确认存在，且属于 next-dev 分支。
此前误用了 Radxa 镜像的 `220414809636db78ffd5ddad0f61f39dc2d49036`，官方远端没有该对象，已纠正；该补丁在官方仓库中的对应提交为 `13ceb2afdcb6f5114908e39f0d2453728eb24e0f`；当前选择更新的 next-dev 分支头，包含此修复。

## 构建衔接

`rk-spl-k7` 单独编译 `spl/u-boot-spl.bin`，安装到 sysroot 的 `/usr/share/k7-rk-spl/`。
`u-boot-k7-upstream` 依赖该配方，传给 boot_merger 的 FlashBoot 只指向这个文件。
上游构建仍可能生成自身 SPL 和 binman 镜像作为其默认构建产物，但 WIC 使用的 IDB 已不包含它。

defconfig 基于当前官方 rk3576_defconfig，改选 K7 板级 DTS，并将 MMC 加载位置设为固定扇区 `0x4000`、关闭按分区查找，以匹配现有 WIC 的 FIT 8 MiB 偏移。DTS 沿用 K7 厂商参考。
厂商 FIT 加载实现能解析 firmware/loadables、外置数据与加载地址；这只是静态兼容性检查，不能替代实机验证。

## 验证状态

- 官方仓库固定提交存在、属于 next-dev 分支，并包含计时器适配。
- BitBake fetcher 在禁止联网时成功从本机官方仓库缓存获取该配方全部输入。
- 相关配方解析通过。
- 新源码编译与 IDB 验证见 k7-rk-spl-validation.json，完整 WIC 和板上启动仍需验证。
- GCC 15 的 enum-int-mismatch 采用单项 -Wno-error，不关闭其他错误检查。

## 构建与验收

```bash
cd /home/ryan/Yocto/workspace
source sources/openembedded-core/oe-init-build-env build-k7
bitbake kickpi-image-minimal
```

部署目录会有 `k7-rk-u-boot-spl.bin`、`k7-upstream-idbloader.img` 与新的 WIC。
单独构建 `bitbake rk-spl-k7 -c deploy` 还可部署 ELF 与实际配置，便于符号定位。
预期串口 SPL 版本由上游的 2026.10 变为厂商源码的 2017.09；U-Boot 主程序仍为 2026.10。
用户指定当前测试 DDR v1.09 + 厂商源码 SPL，结果待验证。此前离线 IDB 打包验证使用的是 v1.08，不能当作新组合的实机结果。

## 厂商补丁与主线

厂商补丁不自动进入主线，需单独投稿、评审并适配主线配置方式。
没有查到这份具体补丁未合并的评审结论，不能认定是被拒绝或遗漏。
当前主线配置和启动汇编仍有写入 24 MHz 的路径，这是我们本地可核对的事实；是否导致本板故障尚待实测。

## 当前构建机代理

`build-k7/conf/local.conf` 引入本机 `k7-proxy.inc`，将已有 Git 代理 `http://127.0.0.1:7897` 设置为 http_proxy/https_proxy 及大写同名变量，由 fetcher 显式传递，避免全局 export 影响普通任务签名。
BitBake fetcher 会将这些变量传入下载子进程，无需关闭 TLS 验证。
`git://github.com/rockchip-linux/u-boot.git;protocol=https` 是 BitBake 的 Git-over-HTTPS 语法，实际 Git 地址是 `https://github.com/rockchip-linux/u-boot.git`。

更改配置后可先验证：

```bash
bitbake rk-spl-k7 -c fetch -f
bitbake kickpi-image-minimal
```

代理进程必须在执行 BitBake 的主机/容器网络环境中监听该地址。当前代理连通性尚未验证；代理设置不代表固定提交一定能从官方远端获取。

## 代理导致的 basehash 变化修复

从用户报错的 do_populate_sysroot 原签名 `06428f86…` 加入四个代理值及 export 标记，精确重算出报错的新签名 `c2eb260f…`。全局 export 使普通 shell 任务都依赖代理，构建过程中更新配置可造成旧解析与 worker 重解析不一致。
已去掉 k7-proxy.inc 中的全局 export，代理值仍由 bb.fetch2.get_fetcher_environment 主动传给下载命令。检查确认四个代理均可获取且不全局导出；两次独立解析的任务 basehash 一致。
恢复构建先退出失败的 BitBake 进程，再执行 `bitbake -m` 重启常驻解析服务，随后运行 `bitbake kickpi-image-minimal`。不需要清空 tmp/sstate，也不关闭确定性检查。

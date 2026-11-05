# 社区与 JeffyCN meta-rockchip 选型对比

调研日期：2026-11-05。本轮只调研选型，没有继续切换活动 BSP 或启动完整内核编译。

## 比较对象及边界

- 社区：git.yoctoproject.org/meta-rockchip，Wrynose，实际检出提交 9d02575bfd9ca5e87c394f593f2bbdbdee914d4e（2026-04-08）。源码在 /tmp/k7-meta-rockchip-community，未加入 BBLAYERS。
- JeffyCN：github.com/JeffyCN/meta-rockchip，本项目固定提交 f2fa9fd14571b855bb59a51da11616904a65f36d，Wrynose。
- 比较的是上述提交，不将本地快照的默认版本当作 JeffyCN 所有分支的最新状态。

二者的 layer collection 都叫 rockchip，不应未经处理同时放进 BBLAYERS。
社区仓库位于 Yocto 服务上，不代表 Rockchip 对 K7 提供官方量产支持或 SLA。
JeffyCN BSP 有 Rockchip 厂商生态代码与贡献，也不等于这个 GitHub 地址本身承诺商业支持。

## 实际源码差异

| 维度 | 社区 Wrynose | 本项目 JeffyCN 快照 |
| --- | --- | --- |
| 目标 | README 明确尽可能使用 upstream sources | 集成 Rockchip BSP、工具和多媒体组件 |
| 内核 | 扩展 OE-Core linux-yocto 配方 | 独立 linux-rockchip 6.1/6.12 配方，当前默认 6.1 |
| linux-yocto.inc | 经 OE-Core 配方使用 | 直接 require，复用构建机制，源码仍是厂商树 |
| U-Boot | 扩展 OE-Core u-boot 配方 | 自有 u-boot-rockchip 和 make.sh 打包任务 |
| firmware | DDR、TF-A、OP-TEE 等按配方划分 | rkbin/厂商打包与启动工具集成 |
| 镜像 | Rockchip GPT/WIC、FIT 配置及 RAUC 动态层 | rockchip-image、boot.img/uboot.img、parameter/update.img |
| 图形/多媒体 | 上游集成为主，有 Mesa/Qt 等追加配置 | libmali、rkaiq/rkisp、v4l-rkmpp 等厂商组件 |
| 依赖 | core、meta-arm；README 还要求 meta-arm-toolchain | core、meta-openembedded/meta-oe |
| RK3576 | 本次检出的配置和配方中未找到 RK3576 支持 | 提供 rk3576.inc 和 RK3576 EVB machine |
| K7 | 无现成 machine | 也不是 K7 的完整交付，需自有 meta-kickpi |
| 动态版本 | 本次未在非补丁文本中检出 AUTOREV | Xorg/Weston 追加配方有 AUTOREV，固定 manifest 仍不足以锁全源码 |

社区源码关键证据：

- README：声明尽可能使用上游源码。
- conf/layer.conf：LAYERDEPENDS_rockchip 为 core meta-arm。
- recipes-kernel/linux/linux-yocto_%.bbappend：引入本层 linux-rockchip.inc。
- conf/machine/include/rockchip-defaults.inc：默认 kernel provider 为 linux-yocto，默认启用 RK_KERNEL_FITIMAGE。
- recipes-bsp/u-boot/u-boot_%.bbappend：在 OE-Core u-boot 上添加固件和环境适配。

社区的 linux-rockchip.inc 文件名不表示另有厂商内核 recipe。判断源码应追踪最终 SRC_URI/SRCREV。
社区也不是完全无二进制固件，部分 SoC 仍需 rkbin DDR/BL31。

JeffyCN 本地源码关键证据：

- recipes-kernel/linux/linux-rockchip_6.1.bb：require linux-yocto.inc，但 SRC_URI 指向 JeffyCN/mirrors 的厂商内核分支。
- conf/machine/include/rockchip-common.inc：默认 linux-rockchip 6.1 和 6.1-custom libc headers。
- conf/machine/include/mali.inc：选择 rockchip-libmali 图形 provider。
- classes/rockchip-image.bbclass：提供厂商镜像组装。

## 公司公开实践

### Seeed Studio：明确选择厂商内核的 Rockchip BSP

公开 meta-seeed-rockchip 支持 reComputer RK3576/RK3588，README 说明固定
armbian/linux-rockchip 的 rk-6.1-rkr7.2 提交和 radxa/u-boot 提交，同时与 Rockchip
摄像头运行时配方集成。它把产品分区、OTA 和容器集成留给上层发行版。
这证明“企业项目可以选择厂商内核并固定版本”，不能据此推断所有企业都采用同一路线，
也不能将 README 的 CI 描述视为对 K7 的量产认证。

来源：https://github.com/Seeed-Studio/meta-seeed-rockchip

### Toradex：按产品提供上游和下游变体

官方文档同时说明 upstream 内核方案与 SoC 厂商 downstream 方案，BSP 8 进一步将内核/
厂商 BSP 变体与发行版策略分离。具体可选项受模块和支持矩阵约束。
可借鉴的是硬件能力、版本组合、支持责任和验证矩阵，而非假定其代码能直接用于 K7。

来源：
- https://developer.toradex.com/linux-bsp/
- https://developer.toradex.com/linux-bsp/8/bsp-layers-and-reference-images/bsp-layers-and-reference-images-overview/

### Collabora：以开发资源推进上游能力

公开文章说明 RK3588/RK3576 VDPU 解码器支持及相应用户态接口向上游集成。
它是企业投资上游适配的例子，不是对本项目 linux-yocto 6.18 或 K7 全外设的支持声明。
较新主线合并的功能未必已经回移到本项目固定的 LTS 内核。

来源：https://www.collabora.com/news-and-blog/news-and-events/rk3588-and-rk3576-video-decoders-support-merged-in-the-upstream-linux-kernel.html

## 企业项目如何选

以下是基于源码与公开实践的工程建议，不是市场占有率统计。

| 项目目标 | 优先考察的路线 | 必须承担的工作 |
| --- | --- | --- |
| 工业网关/控制器，串口、存储、网络为主，重长期维护 | 社区 BSP + linux-yocto/上游内核 + 自有板级层 | K7/RK3576 缺口适配、PMIC/PHY、回归测试 |
| 摄像头/ISP、NPU、厂商编解码能力是交付关键，时间紧 | 厂商验证过的 BSP 组合，可基于 JeffyCN 整理 | 版本锁定、CVE 回移、补丁/许可证审查、供应商支持 |
| 自有硬件平台，团队有内核与启动链维护能力 | 上游优先，自有 BSP，选择性复用社区层 | 承担板级硬件支持，不重复维护成熟通用组件 |

上游路线通常减少长期分叉，但若关键驱动未支持，前期适配成本会更高。
厂商路线通常更贴近 SDK 硬件能力，但这只提高集成起点，不能代替自己对 K7 的验证。

企业发布至少应包含：明确功能清单、固定源码/固件与构建环境、CI 和硬件测试、升级恢复、
SDK 配套、漏洞维护责任和发布支持期。选择某个仓库名称本身不能保证这些条件。

## 针对当前 K7 的建议

你的当前目标是串口/SD/eMMC 启动和逐步自维护，尚未承诺摄像头/NPU 产品能力。
建议保留正在配置的 linux-yocto 6.18 与主线 U-Boot 2026.10 作为上游验证路线。
社区 meta-rockchip 可作为通用 BSP 的复用目标，但 Wrynose 本次提交没有 RK3576 支持，
不能只替换 URL；先比较其通用启动/固件代码与已有实现，选择性引入，再做完整构建和真机验收。

厂商原版保留作硬件参考和独立对照。若未来确定必须交付主线上缺失的 ISP/NPU 能力，
再设独立 vendor 配置/分支和构建目录，不把两个同名 rockchip layer 混入一个默认构建。
当前仍有 linux-yocto 配置阶段的开发改动，没有量产验收结论。

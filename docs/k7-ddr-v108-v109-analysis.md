# K7 DDR v1.08 → v1.09：源码调查

## 实机与生成结果

用户确认：厂商 v1.08 可启动；官方 v1.08 经四项频率修改后与厂商文件逐字节相同，并且重新构建后可启动。v1.09 相同四项修改、567 项共同参数匹配后，用户报告启动失败；本轮 v1.09 完整串口日志未提供，不能认定它与较早 v1.13 故障机制完全一致。

当前默认仍是可启动的自行生成 v1.08，没有修改默认 SPL、时钟或 DDR 输入。

## 官方变更证据

rkbin 提交 `08d92ffe436e34eacfcfa6b36307e48ece54c431`：

- enable hptimer hardware adjust mode
- add support RK3576J and RK3576M
- let rk3576 ddr schedule determined by loader parameter
- let pagematch_limit=1

公开仓库只更新训练固件二进制，没有提供对应 dram_init 实现 diff。

## 找到厂商配套的计时器修复

本地 Radxa U-Boot 镜像中存在瑞芯微作者提交：

`220414809636db78ffd5ddad0f61f39dc2d49036`，2024-11-06，
`rockchip: Add arch timer 1Ghz support`。

该提交是原先厂商基线 `39cd993e5d6296635438e84f4576b3a9bf76f86e` 的祖先，已经包含在厂商基线中。

关键改动：

1. `include/configs/rk3576_common.h` 将 `#define COUNTER_FREQUENCY 24000000` 改为 `#undef COUNTER_FREQUENCY`。
2. `arch/arm/mach-rockchip/rk3576/rk3576.c` 仅在 COUNTER_FREQUENCY 被定义时写 CNTFRQ_EL0。
3. SPL 和 U-Boot 的 board_init_f_boot_flags 从 CNTFRQ_EL0 记录实际频率到 gd->arch.timer_rate_hz。

本地查看：

```bash
git --git-dir=build-k7/downloads/git2/github.com.radxa.u-boot.git show \
    220414809636db78ffd5ddad0f61f39dc2d49036
```

## 当前上游 SPL 的不同点

实际构建配置：`CONFIG_COUNTER_FREQUENCY=24000000`。

`arch/arm/cpu/armv8/start.S` 在 CONFIG_COUNTER_FREQUENCY 非零时，且处于最高异常级时，直接写 CNTFRQ_EL0。
随后 RK3576 rockchip_stimer_init 在 HP Timer 已启用时返回，不能恢复前级留下的频率描述。
`arch/arm/cpu/armv8/generic_timer.c` 的 get_tbclk 读取 CNTFRQ_EL0；usec2ticks 用该频率计算延时。

因此，仅检查 get_tbclk 是否动态读取寄存器是不够的：寄存器可能已在更早的启动汇编中被覆盖。

这是实际代码差异，但尚未测量两版 DDR 的真实计数频率、入口 CNTFRQ_EL0 和 CurrentEL，不能据此断言已经找到 SHA256 失败根因。

若计数实际为 1 GHz 而描述为 24 MHz，按微秒计算的延时可能约缩短到预期的 1/41.7。只写 CNTFRQ_EL0 不会把硬件计数器本身降到 24 MHz。

## 调度参数调查边界

当前 ddrbin_tool 和已导出的参数没有 pagematch_limit 字段；pageclose 不能视为同一参数。
在当前厂商 U-Boot 的 RK3576 实现中未找到 pagematch_limit 或明确的调度参数写入代码。不能直接把其他芯片的 DDR_PCTL2_SCHED 地址用于 RK3576。
需要与 RK3576 控制器匹配的寄存器定义/TRM，或追踪两版训练固件的相关写寄存器指令，才能安全确定读出地址。

## 下一轮受控实验

优先验证计时器交接，暂不改 DDR 电气参数：

1. 在 SPL 入口、频率可能被覆盖前记录 CurrentEL、CNTFRQ_EL0；在串口初始化后输出保存值、当前 CNTFRQ_EL0 与 HP Timer 控制寄存器。
2. 保留可启动 v1.08，生成同一 SPL 下的 v1.08/v1.09 对照镜像。
3. 对实验配置关闭启动汇编固定频率覆盖，并在 RK3576 stimer 初始化中保留独立的 24 MHz 冷初始化后备值，避免直接把 CONFIG_COUNTER_FREQUENCY=0 写入寄存器。
4. 比较频率记录、MMC 读取和 FIT 校验结果。若仍失败，再定位调度寄存器和 DMA 行为。

不能直接将频率统一改成 1 GHz：这可能破坏 v1.08，且没有验证实际硬件频率。

## 来源

- https://github.com/rockchip-linux/rkbin/commit/08d92ffe436e34eacfcfa6b36307e48ece54c431
- https://github.com/rockchip-linux/rkbin/blob/master/doc/release/RK3576_EN.md
- https://github.com/radxa/u-boot/commit/220414809636db78ffd5ddad0f61f39dc2d49036
- 本地固定上游 U-Boot `5508406582f6f7120dce0c4b3059819a41788db2`。

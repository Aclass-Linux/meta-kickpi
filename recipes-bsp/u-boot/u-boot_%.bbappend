# c. 基础配方来自 OE-Core，版本和补丁随当前 OE-Core 检出。
# 仅 K7 选择 oe 时展开板级片段，其他机器继续使用原配方。
K7_UPSTREAM_PREFIX = "k7-oe"
require ${@'recipes-bsp/u-boot/k7-upstream-board.inc' if d.getVar('MACHINE') == 'kickpi-k7' and d.getVar('K7_BOOT_STACK') == 'oe' else ''}

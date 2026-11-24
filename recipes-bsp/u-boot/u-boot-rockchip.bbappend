# 仅为 K7 的 jeffy 路线加载板级适配。
require ${@'recipes-bsp/u-boot/k7-jeffy.inc' if d.getVar('MACHINE') == 'kickpi-k7' and d.getVar('K7_BOOT_STACK') == 'jeffy' else ''}

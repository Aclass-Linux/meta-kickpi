# 只在调试串口的 root 交互式登录 Shell 中运行。
case $- in
    *i*)
        if [ "$(id -u)" = "0" ] &&
           [ "$(tty 2>/dev/null)" = "/dev/@K7_CONSOLE_DEVICE@" ] &&
           [ ! -e /var/lib/k7/root-ssh-provisioned ]; then
            @SBINDIR@/k7-enable-root-ssh || printf '%s\n' 'SSH 公钥未配置，可从串口重试 k7-enable-root-ssh。' >&2
        fi
        ;;
esac

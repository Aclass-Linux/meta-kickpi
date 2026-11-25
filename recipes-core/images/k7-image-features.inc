# 两个 K7 镜像默认安装 OpenSSH，可在 local.conf 中覆盖。
K7_SSH_IMAGE_FEATURE ?= "ssh-server-openssh"
IMAGE_FEATURES:append = " ${K7_SSH_IMAGE_FEATURE}"

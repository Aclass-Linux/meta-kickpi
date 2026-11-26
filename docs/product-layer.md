# K7 BSP 与产品层

仓库提供两个 Yocto layer。BSP 提供独立可构建的板级验证镜像，产品层在 BSP 上定义出厂镜像。两层分别在 `BBLAYERS` 启用。

| 层 | 构建目标 | 职责 |
| --- | --- | --- |
| `meta-kickpi` | `kickpi-image-minimal` | 提供 Linux、U-Boot、固件和默认启动布局。验证镜像安装完整内核模块集，便于检查外设。 |
| `meta-product-k7` | `kickpi-image-product` | 通过 `packagegroup-k7-product` 定义产品装机包和所需内核模块。产品镜像过滤开发配置里的免密登录选项。 |

## 独立构建 BSP 镜像

从 `workspace-scarthgap` 创建与 `sources` 同级的新构建目录。该模板启用 OE-Core、Poky 和 K7 BSP。先按 [新电脑配置](new-computer.md#3-初始化与构建) 把独立检出的 BitBake 加入 `PATH`。

```bash
TEMPLATECONF="$PWD/sources/meta-kickpi/conf/templates/k7-bsp" \
    source sources/openembedded-core/oe-init-build-env build-k7-bsp
bitbake-layers show-layers
bitbake kickpi-image-minimal
```

开发模板允许空密码登录，适用于板级验证环境。BSP 镜像继续使用原有构建目标，已启用两层的 `build-k7` 也可直接构建它。

## 构建产品镜像

在启用了 `meta-product-k7` 的 `build-k7` 中运行 `bitbake kickpi-image-product`。新建产品构建目录时使用 `layers/meta-product-k7/conf/templates/k7-upstream`。需要交叉开发 SDK 时，运行 `bitbake kickpi-image-product -c populate_sdk`。模板只初始化缺失的配置。

## SSH 远程访问

BSP 验证镜像和产品镜像都引用 `recipes-core/images/k7-image-features.inc`，默认通过 `K7_SSH_IMAGE_FEATURE` 启用 OpenSSH。需要覆盖时，在 `build-k7/conf/local.conf` 选择以下配置之一。

```bitbake
# 仅 BSP 验证镜像改用 Dropbear，产品镜像继续使用 OpenSSH。
#K7_SSH_IMAGE_FEATURE:pn-kickpi-image-minimal = "ssh-server-dropbear"

# 关闭两个镜像通过共用配置添加的 SSH 服务。
#K7_SSH_IMAGE_FEATURE = ""
```

产品镜像使用 OpenSSH 时，会安装 `k7-serial-ssh`。第一次以 root 登录调试串口，登录钩子会提示粘贴开发机的 SSH 公钥。如果设备已预置 `/etc/k7/maintenance.pub`，钩子会读取该文件。公钥安装成功后，后续串口登录会跳过初始化。

在开发机可运行 `ssh-keygen -t ed25519` 生成新密钥，或者使用已有的公钥。只把 `.pub` 文件的整行内容粘贴到串口。直接回车可暂时跳过，下次串口登录会再次提示，也可在当前串口运行 `k7-enable-root-ssh` 重试。授权成功后，在 K7 串口运行 `ip addr show` 查看 IP，再从开发机执行 `ssh root@板子IP`。root 远程登录使用公钥认证。

产品镜像为学习环境预设 root 串口密码 `199612`。在 `build-k7/conf/local.conf` 设置 `_K7_ROOT_PASSWORD:pn-kickpi-image-product` 可覆盖默认值，设为空字符串可跳过预设密码。正式部署应覆盖该学习密码或将其清空。

产品软件和运行库添加到产品包组。首次接入外设时可先用 BSP 镜像验证硬件，再把对应 `kernel-module-*` 软件包装入产品镜像。

## 板载网口默认地址

两个镜像都使用 BSP 的 `recipes-core/images/k7-network-config.inc` 生成 systemd-networkd 配置，并使用同一组默认地址，掩码均为 `255.255.255.0`。

| 网口 | 默认 IPv4 |
| --- | --- |
| GMAC0 | `192.168.133.10/24` |
| GMAC1 | `192.168.133.20/24` |

地址按 GMAC 的平台设备路径匹配。从串口运行 `networkctl status` 和 `ip addr show` 检查实际网口与地址。

## 发布配置

开发时可沿用默认配置。交付时，在独立构建目录的 `conf/local.conf` 固定已验收的内核提交并覆盖学习用的登录及网络设置。内核提交从上一轮验证产物 `tmp/deploy/images/kickpi-k7/k7-rockchip-linux-commit.txt` 取值。

```bitbake
# 下方 SHA 来自一次产品构建。交付时用实际通过上板验收的提交替换。
K7_KERNEL_SRCREV = "470f9dccbdc42e7b8a824d0a5c5640a10e9457d2"

# 跳过学习密码，交付前由部署流程配置能实际登录的维护账户或公钥。
_K7_ROOT_PASSWORD:pn-kickpi-image-product = ""

# 交回系统默认的有线网络配置。由站点 DHCP 或逐台设备配置地址。
K7_GMAC0_IPV4:pn-kickpi-image-product = ""
K7_GMAC1_IPV4:pn-kickpi-image-product = ""
```

同一次发布用同一份构建配置生成产品镜像和 SDK。发布前在目标板上检查网口地址、预期的维护入口以及 SDK 编译的应用能否运行。

## 构建与发布产品 SDK

### 基础配置

`kickpi-image-product.bb` 通过 `core-image` → `image` → `populate_sdk_base` 获得标准 SDK 任务。OE-Core 用下表的配置分别组装开发主机和 K7 目标机的 sysroot。 [Yocto 变量参考](https://docs.yoctoproject.org/ref-manual/variables.html#term-SDKMACHINE)

| 配置 | 当前来源与作用 |
| --- | --- |
| `MACHINE`、`SDKMACHINE` | `build-k7/conf/local.conf` 选择 `kickpi-k7` 和 `x86_64`。前者决定 AArch64 目标架构和板级配置，后者决定工具链运行在 x86_64 Linux 开发机上。新建构建目录的默认值来自产品模板的 `local.conf.sample`。 |
| `TOOLCHAIN_HOST_TASK` | OE-Core 默认加入 `nativesdk-packagegroup-sdk-host` 和 `packagegroup-cross-canadian-${MACHINE}`。前者提供 CMake、pkg-config 等主机工具，后者提供 GCC、Binutils 和 GDB 等交叉工具。 |
| `TOOLCHAIN_TARGET_TASK` | OE-Core 默认加入基础 SDK 目标包组，包括 C/C++ 运行库和开发文件。`image.bbclass` 还把 `PACKAGE_INSTALL` 加进来，因此产品包组的运行依赖会参与目标 sysroot 的安装。 |
| `SDKIMAGE_FEATURES` | OE-Core 默认启用 `dev-pkgs`、`dbg-pkgs`、`src-pkgs`，在发行版启用 `api-documentation` 时还加入 `doc-pkgs`。这些选项为 SDK 目标端补充对应的开发、调试、源码或文档包。 |
| `SDK_VERSION`、`SDKPATHINSTALL` | 分别控制 Yocto 安装器版本和默认安装路径。发布脚本的 `--version` 单独控制外层交付包版本。 |

### 修改产品 SDK 内容

把需要长期保留的配置写在产品层 `layers/meta-product-k7/recipes-core/images/kickpi-image-product.bb` 中。下面各行按实际需要选用，`:append` 的值开头保留一个空格，以便与已有包名分隔。 [Yocto 标准 SDK 构建文档](https://docs.yoctoproject.org/sdk-manual/appendix-obtain.html)

```bitbake
# a. 给目标 sysroot 增加 libcurl 的头文件和链接文件。
TOOLCHAIN_TARGET_TASK:append = " curl-dev"

# b. 给 x86_64 开发主机增加 ccache 命令。
TOOLCHAIN_HOST_TASK:append = " nativesdk-ccache"

# c. 精简 SDK，保留默认的开发包，取消自动补充调试包和源码包。
SDKIMAGE_FEATURES:remove = "dbg-pkgs src-pkgs"

# d. 可选，设置安装器显示的版本和默认安装目录。
SDK_VERSION = "0.1.0"
SDKPATHINSTALL = "/opt/kickpi-k7-sdk/${SDK_VERSION}"
```

如果用 libcurl 编译的应用要在 K7 上运行，产品镜像也需要提供 `libcurl` 运行库。若应用已由 Yocto 配方打包，正常链接的共享库通常会自动生成运行依赖。若是用 SDK 编译后手动将应用复制到板上，可以在 `packagegroup-k7-product.bb` 中显式加入运行库。

```bitbake
RDEPENDS:${PN}:append = " libcurl"
```

临时验证可在 `build-k7/conf/local.conf` 中用 `:pn-kickpi-image-product` 限定到产品镜像。

```bitbake
TOOLCHAIN_HOST_TASK:append:pn-kickpi-image-product = " nativesdk-ccache"
```

如需更换 SDK 开发主机架构，在 `local.conf` 修改 `SDKMACHINE`。产品模板只在初始化构建目录时提供默认值。当前发布脚本按 x86_64 主机校验，新增架构的发布包时也要更新该校验规则。

### 查看生效值并重新生成

在 `build-k7` 的 BitBake 环境中查询变量。`bitbake-getvar` 会显示最终值及其配置来源，重新生成后可用同名的 `.host.manifest` 和 `.target.manifest` 核实实际装入的包。

```bash
bitbake-getvar SDKMACHINE
bitbake-getvar -r kickpi-image-product TOOLCHAIN_HOST_TASK
bitbake-getvar -r kickpi-image-product TOOLCHAIN_TARGET_TASK
bitbake-getvar -r kickpi-image-product SDKIMAGE_FEATURES
```

交付镜像和 SDK 时，使用同一份构建配置和源码版本。仅修改 SDK 包清单时可单独重新运行 `populate_sdk`。

```bash
bitbake kickpi-image-product
bitbake kickpi-image-product -c populate_sdk
ls tmp/deploy/sdk/*kickpi-image-product*.sh
```

### 打包与验收

将下方 `SDK_INSTALLER` 指定为本次构建生成的 `.sh`。同版本发布包生成后保留原件，重新交付时使用新的发布版本。

```bash
SDK_INSTALLER='tmp/deploy/sdk/替换为实际安装器文件名.sh'
python3 ../sources/meta-kickpi/scripts/package-k7-sdk.py \
    --installer "$SDK_INSTALLER" --version 0.1.0-rc.1
```

发布包为 `tmp/deploy/sdk-release/kickpi-k7-product-sdk-0.1.0-rc.1-x86_64.tar`。包内含 `install.sh`、主机和目标包清单、`release.json`、`SHA256SUMS`、中文使用说明及 C 示例。脚本用 OE-Core 生成的元数据校验镜像和架构，并保留原始安装器内容。发布版本由交付方指定，Yocto 生成的 `SDK_VERSION` 另行记录。

发布验收按包内 `README.md` 执行。先在干净的 x86_64 Linux 环境校验并安装，再交叉编译示例，最后在装有配套产品镜像的 K7 上运行。随发布记录保存产品镜像与 SDK 的校验值、仓库版本和验收结果。对外分发时，依据实际包清单准备适用的许可证及源码材料。

## 共用启动布局与独立烧录产物

BSP 的 `recipes-core/images/k7-image-layout.inc` 提供默认 WIC 布局和烧录导出任务。两张镜像都使用 BSP 的 `k7-boot-files` 安装 extlinux 配置、Linux DTB 和 initramfs，并由 `kernel-image` 包提供内核。WIC 和 extlinux 共用 `conf/include/k7-boot-layout.inc` 中的根分区 PARTUUID。使用同一默认 PARTUUID 的 SD 卡和 eMMC 同时在线时，应核对板子实际选用的根分区。

在各自构建目录的 `tmp/deploy/images/kickpi-k7/` 下，BSP 镜像导出到 `usb-flash-k7/`，产品镜像导出到 `usb-flash-k7-product/`。两个目录都使用固定文件名 `k7-rockchip-loader.bin` 和 `kickpi-k7-emmc.img`。

验收时先确认对应镜像成功构建，再检查冷启动串口日志、根分区以及所需外设。

# Layer 配置参考

返回 [学习计划](../LEARNING.md)。

## 调整 layer 元数据

先检查 `meta-rockchip` 声明的 collection。当前命令在 `build` 目录执行：

```bash
grep -E 'BBFILE_COLLECTIONS|LAYERSERIES_COMPAT' \
    ../sources/meta-rockchip/conf/layer.conf
```

当前仓库的实际输出为：

```bitbake
BBFILE_COLLECTIONS += "rockchip"
LAYERSERIES_COMPAT_rockchip = "scarthgap"
```

因此 `meta-rockchip` 的 collection 名是 `rockchip`。collection 是 BitBake
识别 layer 时使用的内部名称，不一定等于目录名或 Git 仓库名。

然后确认 `sources/meta-kickpi/conf/layer.conf` 至少包含：

```bitbake
BBPATH .= ":${LAYERDIR}"

BBFILES += "${LAYERDIR}/recipes-*/*/*.bb \
            ${LAYERDIR}/recipes-*/*/*.bbappend"

BBFILE_COLLECTIONS += "kickpi"
BBFILE_PATTERN_kickpi = "^${LAYERDIR}/"
BBFILE_PRIORITY_kickpi = "6"

LAYERDEPENDS_kickpi = "core rockchip"
LAYERSERIES_COMPAT_kickpi = "scarthgap"
```

### `${LAYERDIR}`：当前 layer 根目录

`${LAYERDIR}` 由 BitBake 自动设置，表示 `layer.conf` 所在 layer 的根目录。
这里相当于 `/home/ryan/Documents/yocto/sources/meta-kickpi`。不要改成固定绝对
路径，否则移动工作区后配置将失效。

### `BBPATH`：配置和 class 搜索路径

```bitbake
BBPATH .= ":${LAYERDIR}"
```

将当前 layer 加入 BitBake 的搜索路径，用于查找 `.conf`、`.bbclass` 以及被
`include` 或 `require` 的文件。它不负责发现普通 recipe。

### `BBFILES`：recipe 和 bbappend 的位置

```bitbake
BBFILES += "${LAYERDIR}/recipes-*/*/*.bb \
            ${LAYERDIR}/recipes-*/*/*.bbappend"
```

告诉 BitBake 在 `recipes-*` 目录中查找完整 recipe（`.bb`）和对其他 recipe
进行追加修改的文件（`.bbappend`），例如：

```text
recipes-bsp/u-boot/u-boot_%.bbappend
recipes-kernel/linux/linux-rockchip_%.bbappend
recipes-core/images/kickpi-image.bb
```

### `BBFILE_COLLECTIONS`：layer 的内部名称

```bitbake
BBFILE_COLLECTIONS += "kickpi"
```

将当前 layer 注册为 `kickpi` collection。以下变量的 `_kickpi` 后缀必须与
该名称完全一致：

```bitbake
BBFILE_PATTERN_kickpi
BBFILE_PRIORITY_kickpi
LAYERDEPENDS_kickpi
LAYERSERIES_COMPAT_kickpi
```

### `BBFILE_PATTERN_kickpi`：collection 的文件范围

```bitbake
BBFILE_PATTERN_kickpi = "^${LAYERDIR}/"
```

这是正则表达式：`^` 表示从路径开头匹配，`${LAYERDIR}/` 表示文件必须位于
当前 layer 目录内。匹配的文件会被归入 `kickpi` collection。

### `BBFILE_PRIORITY_kickpi`：layer 优先级

```bitbake
BBFILE_PRIORITY_kickpi = "6"
```

多个 layer 提供同名或相互竞争的 recipe 时，BitBake 会参考该优先级，数值
较高者优先。它不能替代 recipe 版本和 provider 选择；板级修改应优先采用
`.bbappend`，必要时再配置 `PREFERRED_VERSION` 或 `PREFERRED_PROVIDER`。

### `LAYERDEPENDS_kickpi`：layer 依赖关系

```bitbake
LAYERDEPENDS_kickpi = "core rockchip"
```

声明 `meta-kickpi` 依赖 `core` 和 `rockchip` collection。这里必须填写
collection 名，不能填写目录名 `meta-rockchip`。缺少任意依赖时，BitBake
会在配置解析阶段报错。因此应先添加依赖层：

```bash
bitbake-layers add-layer ../sources/meta-rockchip
bitbake-layers add-layer ../sources/meta-kickpi
```

### `LAYERSERIES_COMPAT_kickpi`：Yocto 系列兼容性

```bitbake
LAYERSERIES_COMPAT_kickpi = "scarthgap"
```

声明该 layer 已适配 Yocto Project 5.0 Scarthgap。这只是兼容性声明，不会
自动切换 Poky 或 `meta-rockchip` 分支；各仓库仍需由 repo manifest 固定到
经过验证的分支或 revision。

### 常用赋值符号

- `=`：设置变量值。
- `+=`：以空格分隔并追加，适合列表变量。
- `.=`：直接连接字符串，不自动增加空格；这里用于向 `BBPATH` 追加 `:路径`。

名称对应关系：

| Git 仓库或目录 | BitBake collection |
| --- | --- |
| `meta-rockchip` | `rockchip` |
| `meta-kickpi` | `kickpi` |

# 两个镜像共用板载网口的静态 IPv4 默认值，使用 CIDR 格式。
# 留空时由 systemd-networkd 的默认有线配置管理该网口。
K7_GMAC0_IPV4 ??= "192.168.133.10/24"
K7_GMAC1_IPV4 ??= "192.168.133.20/24"

ROOTFS_POSTPROCESS_COMMAND += "k7_write_static_network"

python k7_write_static_network() {
    from ipaddress import IPv4Interface
    from pathlib import Path

    network_dir = Path(d.getVar("IMAGE_ROOTFS") + d.getVar("sysconfdir")) / "systemd/network"
    # 按 GMAC 的平台设备路径匹配，避免依赖网卡枚举产生的接口名称。
    for index, controller in enumerate(("2a220000", "2a230000")):
        variable = f"K7_GMAC{index}_IPV4"
        address = d.getVar(variable)
        if not address:
            continue
        try:
            if "/" not in address:
                raise ValueError
            address = IPv4Interface(address)
        except ValueError:
            bb.fatal(f"{variable} must be an IPv4 address with a prefix, e.g. 192.168.133.10/24")

        network_dir.mkdir(parents=True, exist_ok=True)
        (network_dir / f"10-k7-gmac{index}.network").write_text(
            f"[Match]\nPath=platform-{controller}.ethernet\n\n"
            f"[Network]\nDHCP=no\nAddress={address}\n",
            encoding="utf-8",
        )
}

# 变量名由上面的循环生成，显式声明以便改地址时重建根文件系统。
k7_write_static_network[vardeps] += "K7_GMAC0_IPV4 K7_GMAC1_IPV4"

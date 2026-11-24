"""两条启动路线共用的 RK3576 固件输入检查。"""
import configparser


def firmware(root, relative):
    path = (root / relative).resolve()
    if not path.is_relative_to(root.resolve()) or not path.is_file():
        raise ValueError(f"Missing or invalid Rockchip firmware: {relative}")
    return path


def read_ini(root, relative):
    config = configparser.ConfigParser()
    config.optionxform = str
    with firmware(root, relative).open() as stream:
        config.read_file(stream)
    return config


def select_inputs(root):
    loader = read_ini(root, "RKBOOT/RK3576MINIALL_V5.ini")
    trust = read_ini(root, "RKTRUST/RK3576TRUST.ini")
    ddr_path = loader["LOADER_OPTION"]["FlashData"]
    if loader["CODE471_OPTION"]["Path1"] != ddr_path:
        raise ValueError("RK3576 loader DDR inputs disagree")
    if trust["BL31_OPTION"]["SEC"] != "1":
        raise ValueError("Expected enabled BL31_OPTION")
    return loader, trust, firmware(root, ddr_path), firmware(root, trust["BL31_OPTION"]["PATH"])

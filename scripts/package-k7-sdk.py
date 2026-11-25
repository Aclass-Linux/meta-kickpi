#!/usr/bin/env python3
"""将 K7 产品镜像的标准 SDK 整理为版本化发布归档。"""

import argparse
import hashlib
import json
import re
import shutil
import tarfile
from pathlib import Path
from tempfile import TemporaryDirectory


def package_sdk(installer, version, output_dir):
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+(?:-[a-zA-Z0-9]+(?:[.-][a-zA-Z0-9]+)*)?", version):
        raise ValueError("发布版本须为 X.Y.Z，可带 -rc.1 等预发布后缀")
    if installer.suffix != ".sh":
        raise ValueError("请指定 populate_sdk 生成的 .sh 安装器")

    stem = installer.stem
    metadata_path = installer.with_name(f"{stem}.testdata.json")
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    expected = {
        "PN": "kickpi-image-product",
        "MACHINE": "kickpi-k7",
        "SDK_ARCH": "x86_64",
        "TARGET_ARCH": "aarch64",
        "TOOLCHAIN_OUTPUTNAME": stem,
    }
    for key, value in expected.items():
        if metadata.get(key) != value:
            raise ValueError(f"SDK 元数据 {key} 应为 {value}，实际为 {metadata.get(key)!r}")

    files = {
        "install.sh": installer,
        "manifests/host.manifest": installer.with_name(f"{stem}.host.manifest"),
        "manifests/target.manifest": installer.with_name(f"{stem}.target.manifest"),
    }
    for path in files.values():
        if not path.is_file() or path.stat().st_size == 0:
            raise ValueError(f"缺少有效的 SDK 文件 {path}")

    name = f"kickpi-k7-product-sdk-{version}-x86_64"
    output = output_dir / f"{name}.tar"
    if output.exists():
        raise FileExistsError(f"发布包已存在 {output}")

    output_dir.mkdir(parents=True, exist_ok=True)
    assets = Path(__file__).resolve().with_name("sdk-release")
    # testdata 包含完整构建环境，只交付明确列出的字段。
    release = {"release_version": version, "original_installer": installer.name}
    for key in ("PN", "MACHINE", "SDK_ARCH", "TARGET_ARCH", "SDK_VERSION", "DISTRO",
                "DISTRO_VERSION", "TUNE_PKGARCH", "K7_BOOT_STACK"):
        if key in metadata:
            release[key] = metadata[key]

    with TemporaryDirectory(dir=output_dir) as staging:
        root = Path(staging) / name
        (root / "manifests").mkdir(parents=True)
        (root / "examples").mkdir()
        for destination, source in files.items():
            shutil.copyfile(source, root / destination)
        (root / "install.sh").chmod(0o755)
        shutil.copyfile(assets / "README.md", root / "README.md")
        shutil.copyfile(assets / "hello.c", root / "examples/hello.c")
        (root / "release.json").write_text(
            json.dumps(release, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
        )
        checksums = []
        for path in sorted(root.rglob("*")):
            if path.is_file():
                digest = hashlib.sha256()
                with path.open("rb") as payload:
                    for chunk in iter(lambda: payload.read(1024 * 1024), b""):
                        digest.update(chunk)
                checksums.append(f"{digest.hexdigest()}  {path.relative_to(root).as_posix()}\n")
        (root / "SHA256SUMS").write_text("".join(checksums), encoding="utf-8")

        # 安装器已经含压缩载荷，外层 tar 只负责归档。完成后原子发布且避免覆盖。
        archive = Path(staging) / output.name
        with tarfile.open(archive, "w") as bundle:
            bundle.add(root, arcname=name)
        output.hardlink_to(archive)
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--installer", required=True, type=Path, help="产品 SDK 的 .sh 路径")
    parser.add_argument("--version", required=True, help="发布版本，例如 0.1.0-rc.1")
    parser.add_argument("--output-dir", type=Path, default=Path("tmp/deploy/sdk-release"))
    args = parser.parse_args()
    try:
        print(package_sdk(args.installer, args.version, args.output_dir).resolve())
    except (OSError, ValueError) as error:
        parser.exit(1, f"打包失败：{error}\n")


if __name__ == "__main__":
    main()

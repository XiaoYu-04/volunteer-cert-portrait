#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""为 nginx gzip_static 预生成 .gz 文件（部署辅助脚本，入库版本）。

遍历前端 dist 目录下的 .js/.css/.html/.svg/.json 文件，用 Python gzip 模块
（compresslevel=9）生成同名 <原文件>.gz，原文件保留不动。
nginx 开启 gzip_static 后，客户端支持 gzip 时直接发送 .gz，省去运行时压缩。

为什么需要它：Vite 产物是「带 hash 的大 JS/CSS」，运行时 gzip 每次请求都要压一遍；
预压缩把成本挪到发版一次，实测本仓库 dist 1,059.0 KiB → 377.5 KiB（35.6%）。

可重复执行（幂等）：
  * 默认跳过“已存在且比源文件新”的 .gz，只压缩新增/变更的文件；
  * 加 --force 强制全部重压；
  * 源文件被删除后，对应的孤儿 .gz 会被清理（dist 每次构建文件名带 hash，
    不清理会越积越多）。

用法（在仓库根目录）：
    python deploy/precompress.py
    python deploy/precompress.py --force
    python deploy/precompress.py --dist <dist目录>

发版三连：npm run build → python deploy/precompress.py → nginx -s reload
"""

from __future__ import annotations

import argparse
import gzip
import sys
from pathlib import Path

# 需要预压缩的扩展名（对应 nginx.conf 里 gzip_types / gzip_static 的覆盖面）
EXTENSIONS = {".js", ".css", ".html", ".svg", ".json"}

# 低于该体积的文件不生成 .gz（gzip 头开销会让 .gz 比原文件还大，无收益）
MIN_SIZE = 512


def repo_root() -> Path:
    # 本脚本位于 <repo>/deploy/precompress.py
    return Path(__file__).resolve().parents[1]


def default_dist() -> Path:
    return repo_root() / "volunteer-cert-portrait-web" / "dist"


def compress_one(src: Path, force: bool) -> str:
    """返回 'skip' | 'write'。"""
    dst = src.with_name(src.name + ".gz")
    if src.stat().st_size < MIN_SIZE:
        if dst.exists():
            dst.unlink()
        return "skip"
    if not force and dst.exists() and dst.stat().st_mtime >= src.stat().st_mtime:
        return "skip"
    # mtime=0：输出可复现，不随压缩时刻变化
    with open(src, "rb") as fin, open(dst, "wb") as fout:
        with gzip.GzipFile(fileobj=fout, mode="wb", compresslevel=9, mtime=0) as gz:
            while True:
                chunk = fin.read(1024 * 1024)
                if not chunk:
                    break
                gz.write(chunk)
    return "write"


def main() -> int:
    parser = argparse.ArgumentParser(description="为 nginx gzip_static 预生成 .gz")
    parser.add_argument("--dist", type=Path, default=default_dist(), help="前端 dist 目录")
    parser.add_argument("--force", action="store_true", help="无条件重压所有文件")
    args = parser.parse_args()

    dist: Path = args.dist.resolve()
    if not dist.is_dir():
        print(f"[ERR] dist 目录不存在：{dist}", file=sys.stderr)
        return 1

    written = skipped = 0
    raw_total = gz_total = 0
    for src in sorted(dist.rglob("*")):
        if not src.is_file() or src.suffix.lower() not in EXTENSIONS:
            continue
        result = compress_one(src, args.force)
        if result == "write":
            written += 1
        else:
            skipped += 1
        gz = src.with_name(src.name + ".gz")
        if gz.exists():
            raw_total += src.stat().st_size
            gz_total += gz.stat().st_size

    # 清理孤儿 .gz（源文件已不存在）
    removed = 0
    for gz in dist.rglob("*.gz"):
        if not gz.name.endswith(".gz"):
            continue
        raw = gz.with_name(gz.name[: -len(".gz")])
        if not raw.exists():
            gz.unlink()
            removed += 1

    ratio = (gz_total / raw_total * 100) if raw_total else 0.0
    print(
        f"precompress: 新生成 {written} 个，跳过 {skipped} 个，清理孤儿 {removed} 个；"
        f"已压缩 {raw_total / 1024:.1f} KiB → {gz_total / 1024:.1f} KiB（{ratio:.1f}%）"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

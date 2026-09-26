#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_citations.py —— 校验《论文》与答辩材料里的 `文件:行号` 引用是否仍指向正确内容。

背景（为什么需要这个脚本）：论文各章写就后，docs/ 下的总账文件仍在增长
（会话 11/12 的 B31、学号登录等改动往 `docs/待办清单.md` 等文件里插了新行），
凡引用插入点之后的行号都会整体漂移。行号引用是论文的「证据指针」，指错了
答辩时一翻就对不上。本脚本按「每个引用方文件最后一次提交时目标文件的样子
→ 当前工作区」用 difflib 逐行建立映射，把漂移的引用找出来；再对每条引用做
内容校验（目标行附近的窗口里应出现该引用行声称的数字 / 状态码），抓出
「写的时候就指错了」的个别条目（实测：mock 口径那条写时就差了 13 行）。

与仓库其它验证脚本同一口径：只读可复跑（check 模式），改动可复核（fix 模式
只改引用里的行号数字，不动任何正文；改完 git diff 一眼可查）。

用法：
    python docs/论文/check_citations.py           # 只报告，不改文件
    python docs/论文/check_citations.py --fix     # 按映射 + OVERRIDES 表改写引用

注意：`docs/_handoff/` 是一次性交接存档，不在校验范围（里面的行号反映当时状态）。
"""

import argparse
import difflib
import os
import re
import subprocess
import sys

# Windows 控制台默认 GBK，而文档里满是 `⚠` / `✅`，默认编码下打印到「引用方行」
# 那一步会直接 UnicodeEncodeError 崩掉（实测 python 3.12.10）。把标准输出/错误
# 显式切到 UTF-8，脚本才在 Windows 上跑得完；输出被重定向到管道时同样生效。
for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, 'reconfigure'):
        _stream.reconfigure(encoding='utf-8', errors='replace')

# 本脚本在 docs/论文/ 下，向上三层才是仓库根
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.chdir(ROOT)

# 论文正文 11 块 + 答辩材料 5 份（.pptx 是二进制，行号引用不适用，跳过）
SCAN_FILES = (
    [f'docs/论文/{n}.md' for n in (
        '00-摘要与关键词', '绪论', '01-需求分析', '02-概要设计', '03-详细设计',
        '04-系统实现', '05-系统测试', '结论与展望', '参考文献', '致谢', '附录')]
    + [f'docs/答辩材料/{n}.md' for n in (
        '成员分工', '技术亮点', '数据库说明', '演示脚本', '演示视频分镜')]
)

# 引用形如 `docs/待办清单.md:847-849`、`sql/09_consistency_check.sql:29-35`、`UserServiceImpl.java:294`
FILE_RE = re.compile(
    r'((?:[\w.\-]+/)*[\w.\-]+\.(?:md|sql|java|js|vue|ps1|py|mjs|cjs)):(\d+)(?:([-~])(\d+))?')
# 同行内文件名已出现后的延续引用，形如 `:283-291`、`:197`~`:200`
BARE_RE = re.compile(r':(\d+)(?:([-~])(\d+))?')

# 行号映射只对这两类文件自动改写；代码类文件只报告不建议自动改（语义锚定弱）
AUTO_FIX_EXT = ('.md', '.sql')

CAND_PREFIXES = ('', 'docs/', 'sql/', 'volunteer-cert-portrait-web/',
                 'volunteer-cert-portrait-server/')

# 人工裁定的改写表：(目标文件名, 旧起行, 旧止行) -> (新起行, 新止行, 理由)
# 用于「写的时候就指错了」的条目 —— 机械映射治不了作者当时的笔误，只能按内容重新锚定。
# 空表 = 全部按机械映射处理。
OVERRIDES = {}


def run_git(args):
    return subprocess.run(['git'] + args, capture_output=True).stdout.decode('utf-8', 'replace')


def blob_lines(commit, path):
    return run_git(['show', f'{commit}:{path}']).splitlines()


def worktree_lines(path):
    with open(path, 'rb') as f:
        return f.read().decode('utf-8').splitlines()


def last_commit(path):
    return run_git(['log', '-1', '--format=%h', '--', path]).strip() or 'HEAD'


def resolve(name):
    for p in CAND_PREFIXES:
        if p and os.path.exists(p + name):
            return p + name
        if not p and (os.path.exists(name) or '/' in name):
            return name if os.path.exists(name) else None
    return None


_MAPS = {}


def get_map(cF, gpath):
    """cF 时点的 gpath → 当前工作区 gpath 的 0 基行号映射；无差异返回 None。"""
    key = (cF, gpath)
    if key in _MAPS:
        return _MAPS[key]
    old = blob_lines(cF, gpath)
    new = worktree_lines(gpath)
    m = None
    if old != new:
        m = {}
        sm = difflib.SequenceMatcher(None, old, new, autojunk=False)
        for tag, i1, i2, j1, j2 in sm.get_opcodes():
            if tag == 'equal' or (tag == 'replace' and i2 - i1 == j2 - j1):
                for i in range(i1, i2):
                    m[i] = j1 + (i - i1)
            elif tag in ('replace', 'delete'):
                for i in range(i1, i2):
                    m[i] = j1  # 被删行钳到插入点，交由内容校验裁决
    _MAPS[key] = m
    return m


def map_num(m, x):
    if m is None:
        return x
    return m.get(x - 1, x - 1) + 1


def claim_tokens(text):
    """从引用行提取「该引用声称的事实」的可检索特征。

    特征取三类（都做宽度归一）：① 4 位以上数字（去逗号）——看板数字、错误码；
    ② 带单位数字的「数 + 汉字单位」（30 分钟 / 1.5 倍 / 16 张表）；③ 大写状态码。
    先剔除引用片段本身 —— 否则被引的行号（如 `:192`）会被当成论断特征，
    而目标行里当然不会出现「192」，造成成片误报。
    单位汉字表按本仓库实际出现过的取：分/时/倍/项/条/张/表/页/位/次/秒/类/人/个/份。
    """
    text = FILE_RE.sub(' ', text)
    text = BARE_RE.sub(' ', text)
    toks = set()
    for n in re.findall(r'\d[\d,]*(?:\.\d+)?[一-鿿]', text):
        # 带单位的数：连同后面的单位汉字一起收录，如「30分钟」「1.5倍」
        toks.add(re.sub(r'[,]', '', n))
    for n in re.findall(r'\d[\d,]*(?:\.\d+)?', text):
        n2 = n.replace(',', '')
        digits = n2.replace('.', '')
        if len(digits) >= 4:
            toks.add(n2)
    toks.update(re.findall(r'[A-Z][A-Z0-9_]{3,}', text))
    return toks


def content_hit(new_lines, na, nb, toks):
    if not toks:
        return None
    lo = max(0, na - 4)
    hi = min(len(new_lines), (nb or na) + 3)
    win = ' '.join(new_lines[lo:hi]).replace(',', '')
    return any(t in win for t in toks)


def scan():
    """返回引用清单：每条 (文件, 行号, 目标路径, a, b, span, 全匹配文本, 延续引用?)"""
    cites = []
    for f in SCAN_FILES:
        if not os.path.exists(f):
            print(f'!! 缺文件：{f}')
            continue
        cF = last_commit(f)
        cur = worktree_lines(f)
        base = blob_lines(cF, f)
        if cur != base:
            print(f'!! {f} 工作区与最后提交 {cF} 不一致，按工作区扫描（映射基线仍是 {cF}）')
        for li, line in enumerate(cur, 1):
            spans = [(m.start(), m.end()) for m in FILE_RE.finditer(line)]
            for m in FILE_RE.finditer(line):
                cites.append(dict(f=f, li=li, g=m.group(1), a=int(m.group(2)),
                                  b=int(m.group(4)) if m.group(4) else None,
                                  span=(m.start(), m.end()), text=m.group(0),
                                  sep=m.group(3) or '-', cont=False))
            for m in BARE_RE.finditer(line):
                if any(s <= m.start() < e for s, e in spans):
                    continue  # 已被 FILE_RE 覆盖
                # 延续引用：往前找同行最近的文件名
                ctx = None
                for fm in FILE_RE.finditer(line):
                    if fm.end() <= m.start():
                        ctx = fm.group(1)
                if ctx is None:
                    continue  # 无上下文的裸行号不动、仅计数
                cites.append(dict(f=f, li=li, g=ctx, a=int(m.group(1)),
                                  b=int(m.group(3)) if m.group(3) else None,
                                  span=(m.start(), m.end()), text=m.group(0),
                                  sep=m.group(2) or '-', cont=True))
    return cites


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--fix', action='store_true')
    args = ap.parse_args()

    cites = scan()
    print(f'共扫到引用 {len(cites)} 条（含延续引用）\n')

    drift, suspect, unresolved, unchecked = [], [], 0, 0
    for c in cites:
        gpath = resolve(c['g'])
        if gpath is None:
            print(f"  ? 无法解析目标：{c['f']}:{c['li']} -> {c['g']}")
            continue
        ext = os.path.splitext(gpath)[1]
        cF = last_commit(c['f'])
        m = get_map(cF, gpath)
        na, nb = map_num(m, c['a']), (map_num(m, c['b']) if c['b'] else None)
        if nb is not None and nb < na:
            suspect.append((c, gpath, na, nb, '区间倒置'))
            continue
        moved = (na != c['a']) or (nb != c['b'])
        if moved:
            new_lines = worktree_lines(gpath)
            ok = content_hit(new_lines, na, nb, claim_tokens(
                worktree_lines(c['f'])[c['li'] - 1]))
            drift.append((c, gpath, na, nb, ok))
        # 未漂移的引用也做内容抽查（抓「写时就错」的笔误）。
        # 三态要分开：None = 该引用行提不出可检索特征（如只写了个 `7.0.9` ——
        # 数字位不足 4 位、后面又紧跟 `*` 而非汉字），属「查不了」；False 才是
        # 「查了不通」。混为一谈时实测会刷出 747 条假阳性，真正要人工裁定的
        # 条目全被埋掉。
        elif ext in AUTO_FIX_EXT:
            new_lines = worktree_lines(gpath)
            ok = content_hit(new_lines, c['a'], c['b'], claim_tokens(
                worktree_lines(c['f'])[c['li'] - 1]))
            if ok is False:
                suspect.append((c, gpath, c['a'], c['b'], '未漂移但内容不匹配'))
            elif ok is None:
                unchecked += 1

    print(f'—— 漂移引用 {len(drift)} 条 ——')
    for c, gpath, na, nb, ok in sorted(drift, key=lambda x: (x[0]['f'], x[0]['li'])):
        flag = {True: '内容OK', False: '内容不匹配', None: '无特征可查'}[ok]
        rng = f"{na}-{nb}" if nb else str(na)
        print(f"  {c['f']}:{c['li']}  {c['text']}  ->  {rng}  [{flag}]")

    print(f'\n—— 内容失配（需人工裁定）{len(suspect)} 条 ——')
    if unchecked:
        print(f'（另有 {unchecked} 条未漂移引用提不出可检索特征，判为「查不了」已跳过，'
              f'不计入上面的「失配」。特征取法见 claim_tokens 的说明。）')
    for c, gpath, a, b, why in suspect:
        rng = f"{a}-{b}" if b else str(a)
        line = worktree_lines(c['f'])[c['li'] - 1]
        print(f"  {c['f']}:{c['li']}  {c['g']}:{rng}  [{why}]")
        print(f"      引用方行：{line.strip()[:110]}")

    if not args.fix:
        return

    # ——fix：机械映射 + OVERRIDES，只改行号数字
    ov = {(g, a, b): (na, nb) for g, a, b, na, nb, _ in
          [(k[0], k[1], k[2], v[0], v[1], v[2]) for k, v in OVERRIDES.items()]}
    edits = {}  # f -> {(li): [(span, new_text), ...]}
    fixed = 0
    for c, gpath, na, nb, ok in drift + suspect:
        gname = c['g']
        key = (gname, c['a'], c['b'])
        if key in OVERRIDES:
            na, nb = OVERRIDES[key][0], OVERRIDES[key][1]
        elif (gname, c['a'], c['b']) not in ov and c in [s[0] for s in suspect]:
            continue  # 未经裁定的失配不自动改
        if os.path.splitext(gpath)[1] not in AUTO_FIX_EXT:
            continue
        if (na, nb) == (c['a'], c['b']):
            continue
        head = c['text'][:c['text'].index(':') + 1]
        new_text = head + str(na) + (f"{c['sep']}{nb}" if nb else '')
        edits.setdefault(c['f'], {}).setdefault(c['li'], []).append((c['span'], new_text))
        fixed += 1

    for f, per_line in edits.items():
        lines = worktree_lines(f)
        for li, repls in per_line.items():
            s = lines[li - 1]
            for (start, end), txt in sorted(repls, key=lambda x: -x[0][0]):
                s = s[:start] + txt + s[end:]
            lines[li - 1] = s
        with open(f, 'wb') as fh:
            fh.write('\n'.join(lines).encode('utf-8'))
    print(f'\n已改写 {fixed} 条引用，涉及 {len(edits)} 个文件；复跑本脚本（无 --fix）应余 0 漂移。')


if __name__ == '__main__':
    sys.exit(main())

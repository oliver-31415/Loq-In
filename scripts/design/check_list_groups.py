#!/usr/bin/env python3
"""Report ListGroupLayouts whose direct children aren't rows (TextView, Button, ImageView, ...).

A grouped list draws every direct child as a rounded segment, so a plain text or a button placed
directly inside one is almost always a mis-converted content card. Usage: check_list_groups.py files...
"""
import re
import sys

GROUP = "com.oliver.loqin.ui.widgets.ListGroupLayout"
ROW_TAGS = ("LinearLayout", "androidx.constraintlayout.widget.ConstraintLayout", "RelativeLayout",
            "FrameLayout", "include", "com.google.android.material.card.MaterialCardView")


def find_close(text, start, tag):
    token = re.compile(r'<' + re.escape(tag) + r'\b[^>]*?(/?)>|</' + re.escape(tag) + r'\s*>', re.S)
    depth = 0
    for m in token.finditer(text, start):
        if m.group(0).startswith('</'):
            depth -= 1
            if depth == 0:
                return m.end()
        elif m.group(1) == '/':
            if depth == 0:
                return m.end()
        else:
            depth += 1
    raise ValueError(tag)


bad = 0
for path in sys.argv[1:]:
    text = open(path, encoding='utf-8').read()
    for m in re.finditer(r'<' + re.escape(GROUP) + r'\b[^>]*?(/?)>', text, re.S):
        if m.group(1) == '/':
            continue
        end = find_close(text, m.start(), GROUP)
        body = text[m.end():end]
        i = 0
        while True:
            c = re.compile(r'<([\w.]+)\b').search(body, i)
            if not c:
                break
            k = body.rfind('<!--', 0, c.start())
            if k != -1 and body.find('-->', k) > c.start():
                i = body.find('-->', k) + 3
                continue
            tag = c.group(1)
            if tag == GROUP.split('.')[-1] or tag.endswith('ListGroupLayout'):
                break
            ce = find_close(body, c.start(), tag)
            if tag not in ROW_TAGS:
                line = text.count('\n', 0, m.end() + c.start()) + 1
                print(f"{path}:{line}: <{tag}> directly inside a ListGroupLayout")
                bad += 1
            i = ce
sys.exit(1 if bad else 0)

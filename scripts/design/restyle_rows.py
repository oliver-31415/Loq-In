#!/usr/bin/env python3
"""Restyle rows inside <ListGroupLayout> blocks to the Expressive list-row spec.

For each direct child row of a ListGroupLayout:
  - minHeight 52/56/60/64dp -> 72dp; paddingStart -> 20dp; paddingTop/Bottom -> 14dp
For TextViews inside those rows (any depth):
  - bold title (textStyle="bold", textSize 14-17sp, no textAppearance) -> RowTitle appearance
  - secondary summary (textColor textColorSecondary/colorOnSurfaceVariant, textSize 12-14sp) -> RowSummary
For trailing chevrons (src keyboard_arrow_right_24): 24dp, tint colorOnSurfaceVariant, no alpha.

Usage: restyle_rows.py [--dry-run] layout.xml [...]
"""
import re
import sys

GROUP = "com.oliver.loqin.ui.widgets.ListGroupLayout"


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


def set_attr(tag_text, name, value):
    if re.search(r'\b' + re.escape(name) + r'="', tag_text):
        return re.sub(r'(\b' + re.escape(name) + r'=")[^"]*(")', lambda m: m.group(1) + value + m.group(2), tag_text)
    indent = re.search(r'\n([ \t]+)[\w:]+="', tag_text)
    ind = indent.group(1) if indent else ' '
    return re.sub(r'(\s*/?>)$', lambda m: '\n' + ind + f'{name}="{value}"' + m.group(1), tag_text, count=1)


def drop_attr(tag_text, name):
    return re.sub(r'\s*\b' + re.escape(name) + r'="[^"]*"', '', tag_text)


def restyle_textview(tag):
    if 'android:textAppearance=' in tag or 'style=' in tag:
        return tag, None
    size = re.search(r'android:textSize="(\d+)sp"', tag)
    size = int(size.group(1)) if size else None
    if 'android:textStyle="bold"' in tag and size and 14 <= size <= 17:
        tag = drop_attr(drop_attr(tag, 'android:textStyle'), 'android:textSize')
        return set_attr(tag, 'android:textAppearance', '@style/TextAppearance.LoqIn.RowTitle'), 'title'
    if re.search(r'android:textColor="\?(android:)?attr/(textColorSecondary|colorOnSurfaceVariant)"', tag) and size and 12 <= size <= 14:
        tag = drop_attr(drop_attr(tag, 'android:textColor'), 'android:textSize')
        return set_attr(tag, 'android:textAppearance', '@style/TextAppearance.LoqIn.RowSummary'), 'summary'
    return tag, None


def restyle_row(row, counts):
    head_end = re.match(r'<[\w.]+\b[^>]*?>', row, re.S).end()
    head = row[:head_end]
    if re.search(r'android:minHeight="(48|52|56|60|64)dp"', head):
        head = set_attr(head, 'android:minHeight', '72dp')
    if 'android:paddingStart=' in head:
        head = set_attr(head, 'android:paddingStart', '20dp')
    for p in ('android:paddingTop', 'android:paddingBottom'):
        if re.search(p + r'="(8|10|12)dp"', head):
            head = set_attr(head, p, '14dp')
    body = row[head_end:]

    def tv(m):
        new, kind = restyle_textview(m.group(0))
        if kind:
            counts[kind] += 1
        return new
    body = re.sub(r'<TextView\b[^>]*?/>', tv, body, flags=re.S)

    def chevron(m):
        t = m.group(0)
        if 'keyboard_arrow_right_24' not in t:
            return t
        counts['chevron'] += 1
        t = set_attr(t, 'android:layout_width', '24dp')
        t = set_attr(t, 'android:layout_height', '24dp')
        t = drop_attr(t, 'android:alpha')
        return set_attr(t, 'app:tint', '?attr/colorOnSurfaceVariant')
    body = re.sub(r'<ImageView\b[^>]*?/>', chevron, body, flags=re.S)
    return head + body


def children(body):
    i = 0
    while True:
        m = re.compile(r'<([\w.]+)\b').search(body, i)
        if not m:
            return
        c = body.rfind('<!--', 0, m.start())
        if c != -1 and body.find('-->', c) > m.start():
            i = body.find('-->', c) + 3
            continue
        end = find_close(body, m.start(), m.group(1))
        yield m.start(), end
        i = end


def convert(text, counts):
    out, i = [], 0
    for m in re.finditer(r'<' + re.escape(GROUP) + r'\b[^>]*>', text, re.S):
        if m.start() < i:
            continue
        end = find_close(text, m.start(), GROUP)
        close = '</' + GROUP + '>'
        body = text[m.end():end - len(close)]
        pieces, j = [], 0
        for s, e in children(body):
            pieces.append(body[j:s])
            pieces.append(restyle_row(body[s:e], counts))
            j = e
        pieces.append(body[j:])
        out.append(text[i:m.end()] + ''.join(pieces) + close)
        i = end
    out.append(text[i:])
    return ''.join(out)


def main(argv):
    dry = '--dry-run' in argv
    for path in [a for a in argv if not a.startswith('--')]:
        text = open(path, encoding='utf-8').read()
        counts = {'title': 0, 'summary': 0, 'chevron': 0}
        new = convert(text, counts)
        print(f"{path}: {counts}")
        if not dry and new != text:
            open(path, 'w', encoding='utf-8').write(new)


if __name__ == '__main__':
    main(sys.argv[1:])

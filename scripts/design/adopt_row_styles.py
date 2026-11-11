#!/usr/bin/env python3
"""Move hand-written list-row attributes onto the shared row styles (docs/EXPRESSIVE_DESIGN.md,
"Cohesion rules").

For each direct child row (LinearLayout without a style) of a ListGroupLayout:
  row        minHeight 72/56dp + horizontal + center_vertical + 20/16/14/14dp padding
             -> style LoqIn.ListRow (LoqIn.ListRow.OneLine for 56dp)
  icon       first ImageView tinted ?attr/colorPrimary -> style LoqIn.RowIcon
  text       vertical LinearLayout, 0dp + weight 1 -> style LoqIn.RowText
  title      TextView with TextAppearance.LoqIn.RowTitle -> style LoqIn.RowTitle
  summary    first TextView with TextAppearance.LoqIn.RowSummary -> style LoqIn.RowSummary
  status     later RowSummary TextViews in the same column -> style LoqIn.RowStatus
  chevron    ImageView with keyboard_arrow_right_24 -> style LoqIn.RowChevron
Attributes that the style now provides are dropped; ids, text, visibility and anything unusual stay.
Rows that don't match the default anatomy are left alone: review the diff and finish by hand.

Usage: adopt_row_styles.py [--dry-run] layout.xml [...]
"""
import re
import sys

GROUP = "com.oliver.loqin.ui.widgets.ListGroupLayout"
ROW_ATTRS = {
    'android:orientation': 'horizontal',
    'android:gravity': 'center_vertical',
    'android:paddingStart': '20dp',
    'android:paddingEnd': '16dp',
    'android:paddingTop': '14dp',
    'android:paddingBottom': '14dp',
}


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


def attr(tag, name):
    m = re.search(r'\s' + re.escape(name) + r'="([^"]*)"', tag)
    return m.group(1) if m else None


def drop(tag, name):
    return re.sub(r'\n?[ \t]*\b' + re.escape(name) + r'="[^"]*"', '', tag)


def add_style(tag, style):
    """Insert style= as the first attribute after the id (or the element name)."""
    m = re.match(r'(<[\w.]+)(\s+)', tag)
    ws = m.group(2)
    idm = re.search(r'\sandroid:id="[^"]*"', tag)
    pos = idm.end() if idm else m.end(1)
    return tag[:pos] + ws + f'style="@style/{style}"' + tag[pos:]


def has_style(tag):
    return re.search(r'\sstyle="', tag) is not None


def restyle_row(row, counts):
    head_end = re.match(r'<[\w.]+\b[^>]*?>', row, re.S).end()
    head, body = row[:head_end], row[head_end:]
    if not head.startswith('<LinearLayout') or has_style(head):
        return row
    min_h = attr(head, 'android:minHeight')
    if min_h not in ('72dp', '56dp') or any(attr(head, k) != v for k, v in ROW_ATTRS.items()):
        return row
    for k in list(ROW_ATTRS) + ['android:minHeight']:
        head = drop(head, k)
    head = add_style(head, 'LoqIn.ListRow' if min_h == '72dp' else 'LoqIn.ListRow.OneLine')
    counts['row'] += 1

    state = {'icon': False}

    def image(m):
        t = m.group(0)
        if has_style(t):
            return t
        if 'keyboard_arrow_right_24' in t:
            t = drop(drop(drop(t, 'android:src'), 'app:tint'), 'android:layout_marginStart')
            counts['chevron'] += 1
            return add_style(t, 'LoqIn.RowChevron')
        if not state['icon'] and attr(t, 'app:tint') == '?attr/colorPrimary' \
                and attr(t, 'android:layout_width') == '24dp':
            state['icon'] = True
            counts['icon'] += 1
            return add_style(drop(t, 'app:tint'), 'LoqIn.RowIcon')
        return t
    body = re.sub(r'<ImageView\b[^>]*?/>', image, body, flags=re.S)

    def column(m):
        t = m.group(0)
        if has_style(t) or attr(t, 'android:layout_width') != '0dp' \
                or attr(t, 'android:layout_weight') != '1' or attr(t, 'android:orientation') != 'vertical':
            return t
        gap = attr(t, 'android:layout_marginStart')
        t = drop(drop(t, 'android:layout_weight'), 'android:orientation')
        if gap in ('14dp', '16dp'):
            t = add_style(drop(t, 'android:layout_marginStart'), 'LoqIn.RowText')
        elif gap is None:
            t = add_style(t, 'LoqIn.RowText.NoIcon')
        else:
            return m.group(0)
        counts['text'] += 1
        return t
    body = re.sub(r'<LinearLayout\b[^>]*?[^/]>', column, body, flags=re.S)

    summaries = {'n': 0}

    def text(m):
        t = m.group(0)
        if has_style(t) or 'android:textColor=' in t or 'android:textSize=' in t:
            return t
        ap = attr(t, 'android:textAppearance')
        if ap == '@style/TextAppearance.LoqIn.RowTitle' and attr(t, 'android:layout_width') == 'match_parent':
            counts['title'] += 1
            return add_style(drop(t, 'android:textAppearance'), 'LoqIn.RowTitle')
        if ap == '@style/TextAppearance.LoqIn.RowSummary':
            summaries['n'] += 1
            style = 'LoqIn.RowSummary' if summaries['n'] == 1 else 'LoqIn.RowStatus'
            counts['summary' if summaries['n'] == 1 else 'status'] += 1
            t = drop(drop(t, 'android:textAppearance'), 'android:layout_marginTop')
            return add_style(t, style)
        return t
    body = re.sub(r'<TextView\b[^>]*?/>', text, body, flags=re.S)
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
        counts = dict.fromkeys(('row', 'icon', 'text', 'title', 'summary', 'status', 'chevron'), 0)
        new = convert(text, counts)
        print(f"{path}: {counts}")
        if not dry and new != text:
            open(path, 'w', encoding='utf-8').write(new)


if __name__ == '__main__':
    main(sys.argv[1:])

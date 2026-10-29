#!/usr/bin/env python3
"""Convert "card of rows" blocks in a layout into Expressive grouped lists.

A block qualifies when a <MaterialCardView style="@style/LoqIn.Card"> has exactly one child, a
vertical LinearLayout, whose children are rows and MaterialDividers. The card + inner LinearLayout
become one <com.oliver.loqin.ui.widgets.ListGroupLayout style="@style/LoqIn.ListGroup">: the card's
layout/visibility attributes and the inner layout's id are kept, dividers are dropped and
row backgrounds of ?attr/selectableItemBackground are removed (the group draws segment ripples).

Cards with an android:id are skipped unless --allow-card-id is given (Kotlin may treat them as a
MaterialCardView). Prints what it changed and what it skipped.

Usage: to_list_group.py [--dry-run] [--allow-card-id] layout.xml [...]
"""
import re
import sys

CARD = "com.google.android.material.card.MaterialCardView"
DIVIDER_RE = re.compile(r'[ \t]*<com\.google\.android\.material\.divider\.MaterialDivider\b[^>]*?/>[ \t]*\n', re.S)
DIVIDER_VIEW_RE = re.compile(r'[ \t]*<View\b(?=[^>]*?(@style/LoqIn\.Divider|layout_height="(0\.5|1|1\.5|2)dp"|layout_height="1px"))[^>]*?/>[ \t]*\n', re.S)
ROW_TAGS = ("LinearLayout", "androidx.constraintlayout.widget.ConstraintLayout", "RelativeLayout",
            "FrameLayout", "include")
KEEP_CARD_ATTRS = ("android:id", "android:layout_width", "android:layout_height", "android:layout_margin",
                   "android:layout_marginTop", "android:layout_marginBottom", "android:layout_marginStart",
                   "android:layout_marginEnd", "android:layout_marginHorizontal", "android:layout_marginVertical",
                   "android:layout_weight", "android:visibility", "tools:visibility", "android:layout_gravity",
                   "app:layout_constraint", "android:layout_below", "android:layout_above")


def find_close(text, start, tag):
    """Index just past the closing tag matching the element that starts at `start`."""
    open_re = re.compile(r'<' + re.escape(tag) + r'\b')
    pos = start
    depth = 0
    token = re.compile(r'<' + re.escape(tag) + r'\b[^>]*?(/?)>|</' + re.escape(tag) + r'\s*>', re.S)
    for m in token.finditer(text, pos):
        if m.group(0).startswith('</'):
            depth -= 1
            if depth == 0:
                return m.end()
        else:
            if m.group(1) == '/':
                if depth == 0:
                    return m.end()
            else:
                depth += 1
    raise ValueError("unbalanced " + tag)


def attrs_of(start_tag):
    return re.findall(r'([\w:]+)="([^"]*)"', start_tag)


def top_level_children(body):
    """Yield (tag, start, end) for direct child elements of an element body."""
    i = 0
    while True:
        m = re.compile(r'<([\w.]+)\b').search(body, i)
        if not m:
            return
        if body.startswith('<!--', m.start() - 0) or m.group(0).startswith('<!'):
            i = m.end()
            continue
        # skip comments
        c = body.rfind('<!--', 0, m.start())
        if c != -1 and body.find('-->', c) > m.start():
            i = body.find('-->', c) + 3
            continue
        tag = m.group(1)
        end = find_close(body, m.start(), tag)
        yield tag, m.start(), end
        i = end


def convert(text, allow_card_id, log):
    out = []
    i = 0
    card_re = re.compile(r'<' + re.escape(CARD) + r'\b([^>]*)>', re.S)
    while True:
        m = card_re.search(text, i)
        if not m:
            out.append(text[i:])
            return ''.join(out)
        start_tag = m.group(0)
        end = find_close(text, m.start(), CARD)
        if 'style="@style/LoqIn.Card"' not in start_tag or start_tag.rstrip().endswith('/>'):
            out.append(text[i:m.end()])
            i = m.end()
            continue
        card_attrs = attrs_of(start_tag)
        card_id = dict(card_attrs).get('android:id')
        body = text[m.end():end - len('</' + CARD + '>')]
        kids = list(top_level_children(body))
        line = text.count('\n', 0, m.start()) + 1
        if card_id and not allow_card_id:
            log.append(f"  skip line {line}: card has id {card_id}")
            out.append(text[i:m.end()]); i = m.end(); continue
        if len(kids) != 1 or kids[0][0] != 'LinearLayout':
            log.append(f"  skip line {line}: card child is not a single LinearLayout")
            out.append(text[i:m.end()]); i = m.end(); continue
        tag, cs, ce = kids[0]
        inner = body[cs:ce]
        inner_start = re.match(r'<LinearLayout\b[^>]*>', inner, re.S).group(0)
        inner_attrs = dict(attrs_of(inner_start))
        if inner_attrs.get('android:orientation') != 'vertical':
            # The card's only child is itself one row: the group holds that single row.
            inner_body = '\n' + inner + '\n'
            inner_attrs = {}
        else:
            inner_body = inner[len(inner_start):inner.rfind('</LinearLayout>')]
        rows = [k for k in top_level_children(DIVIDER_VIEW_RE.sub('', inner_body)) if not k[0].endswith('MaterialDivider')]
        not_rows = sorted({k[0].split('.')[-1] for k in rows if k[0] not in ROW_TAGS})
        if not_rows:
            # Text, buttons or images directly in the card: it's a content card, not a list of rows.
            log.append(f"  skip line {line}: content card (has {', '.join(not_rows)})")
            out.append(text[i:m.end()]); i = m.end(); continue
        if len(rows) < 1:
            log.append(f"  skip line {line}: no rows")
            out.append(text[i:m.end()]); i = m.end(); continue
        new_body = DIVIDER_RE.sub('', inner_body)
        new_body = DIVIDER_VIEW_RE.sub('', new_body)
        # drop row backgrounds on direct children only (first attribute block of each child)
        def strip_bg(match):
            return ''
        pieces = []
        j = 0
        for ktag, ks, ke in top_level_children(new_body):
            pieces.append(new_body[j:ks])
            child = new_body[ks:ke]
            head_end = re.match(r'<[\w.]+\b[^>]*?>', child, re.S).end()
            head = re.sub(r'\s*android:background="\?attr/selectableItemBackground"', '', child[:head_end])
            pieces.append(head + child[head_end:])
            j = ke
        pieces.append(new_body[j:])
        new_body = ''.join(pieces)
        indent = re.search(r'([ \t]*)$', text[:m.start()]).group(1)
        keep = [(k, v) for k, v in card_attrs if k.startswith(KEEP_CARD_ATTRS) and k != 'android:id']
        gid = card_id or inner_attrs.get('android:id')
        attr_lines = ['style="@style/LoqIn.ListGroup"']
        if gid:
            attr_lines.insert(0, f'android:id="{gid}"')
        attr_lines += [f'{k}="{v}"' for k, v in keep]
        if 'android:layout_width' not in dict(keep):
            attr_lines.append('android:layout_width="match_parent"')
        if 'android:layout_height' not in dict(keep):
            attr_lines.append('android:layout_height="wrap_content"')
        open_tag = '<com.oliver.loqin.ui.widgets.ListGroupLayout\n' + '\n'.join(indent + '    ' + a for a in attr_lines) + '>'
        # de-indent the rows by 4 spaces (one nesting level removed)
        new_body = re.sub(r'\n    ', '\n', new_body)
        out.append(text[i:m.start()])
        out.append(open_tag + new_body.rstrip() + '\n' + indent + '</com.oliver.loqin.ui.widgets.ListGroupLayout>')
        log.append(f"  converted line {line}: {len(rows)} rows")
        i = end


def main(argv):
    dry = '--dry-run' in argv
    allow = '--allow-card-id' in argv
    files = [a for a in argv if not a.startswith('--')]
    for path in files:
        text = open(path, encoding='utf-8').read()
        log = []
        new = convert(text, allow, log)
        print(path)
        print('\n'.join(log) or '  nothing to convert')
        if not dry and new != text:
            open(path, 'w', encoding='utf-8').write(new)


if __name__ == '__main__':
    main(sys.argv[1:])

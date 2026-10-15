"""Render icons from the registries into a preview sheet: python preview.py <out.png> name [name ...]"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from forge import sheet  # noqa: E402
import registry  # noqa: E402


def main():
    out = sys.argv[1]
    names = sys.argv[2:]
    reg = registry.all_icons()
    if not names or names == ['*']:
        names = list(reg)
    entries = []
    for n in names:
        if n.endswith('*'):
            entries += [(k, reg[k]().render()) for k in reg if k.startswith(n[:-1])]
        else:
            entries.append((n, reg[n]().render()))
    cols = 4 if len(entries) > 2 else len(entries)
    zoom = int(os.environ.get('ZOOM', '8'))
    sheet(entries, out, zoom=zoom, cols=max(1, cols), title=os.path.basename(out))
    print(out, len(entries))


if __name__ == '__main__':
    main()

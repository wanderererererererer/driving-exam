import json, re, subprocess, sys, html
import xml.etree.ElementTree as ET

pdf = sys.argv[1] if len(sys.argv) > 1 else '综合课程设计报告.pdf'
out = sys.argv[2] if len(sys.argv) > 2 else 'toc_entries.json'
raw = subprocess.check_output(['pdftohtml', '-xml', '-i', '-stdout', pdf], stderr=subprocess.DEVNULL)
root = ET.fromstring(raw)

H1 = {
    '摘 要', '摘要', 'Abstract', '1 绪论', '2 关键技术', '3 需求分析', '4 总体设计',
    '5 详细设计', '6 系统实现', '7 系统测试', '8 安全性、可靠性与可扩展性分析',
    '9 总结与展望', '致 谢', '致谢', '参考文献',
}
entries = []
global_fonts = {}
for page in root.findall('page'):
    pno = int(page.attrib['number'])
    for fs in page.findall('fontspec'):
        global_fonts[fs.attrib['id']] = float(fs.attrib['size'])
    fonts = global_fonts
    runs = []
    for t in page.findall('text'):
        size = fonts.get(t.attrib.get('font'), 0)
        if size >= 20:
            txt = html.unescape(''.join(t.itertext())).strip()
            if txt:
                runs.append((float(t.attrib.get('top', 0)), float(t.attrib.get('left', 0)), size, txt))
    # group runs that belong to the same visual line
    runs.sort(key=lambda x: (x[0], x[1]))
    lines = []
    for run in runs:
        top, left, size, txt = run
        if lines and abs(top - lines[-1]['top']) <= 7:
            lines[-1]['runs'].append(run)
            lines[-1]['top'] = min(lines[-1]['top'], top)
        else:
            lines.append({'top': top, 'runs': [run]})
    for line in lines:
        line['runs'].sort(key=lambda x: x[1])
        text = ' '.join(r[3] for r in line['runs'])
        text = re.sub(r'\s+', ' ', text).strip()
        text = re.sub(r'(?<=[一-鿿])\s+(?=[一-鿿])', '', text)
        text = re.sub(r'\s*([、，。；：])\s*', r'\1', text)
        text = re.sub(r'\s*—\s*', '—', text)
        text = re.sub(r'(?<=[一-鿿])\s+(?=[）】])', '', text)
        text = re.sub(r'(?<=[（【])\s+(?=[一-鿿])', '', text)
        text = text.replace('附录 A ', '附录A  ').replace('附录 B ', '附录B  ').replace('附录 C ', '附录C  ')
        level = 1 if any(r[2] >= 23 for r in line['runs']) else 2
        if level == 1:
            if text not in H1 and not text.startswith(('附录A', '附录B', '附录C')):
                continue
        else:
            if not re.match(r'^\d+\.\d+ ', text):
                continue
        entries.append({'text': text, 'level': level, 'page': max(1, pno - 3)})

# De-duplicate, keeping the first occurrence after the front matter.
seen = set()
unique = []
for e in entries:
    key = (e['text'], e['level'])
    if key in seen:
        continue
    seen.add(key)
    unique.append(e)

with open(out, 'w', encoding='utf-8') as f:
    json.dump(unique, f, ensure_ascii=False, indent=2)
print('entries', len(unique))
for e in unique:
    print(e['level'], e['page'], e['text'])

from docx import Document
from docx.shared import Pt, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_TAB_ALIGNMENT, WD_TAB_LEADER
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL, WD_ROW_HEIGHT_RULE
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.enum.style import WD_STYLE_TYPE
import os
import json


class ReportBuilder:
    def __init__(self, cover_path, toc_entries=None):
        self.doc = Document(cover_path)
        self.pending_break = False
        self.toc_entries = toc_entries or []
        self._setup_section()
        self._setup_styles()

    def _setup_section(self):
        section = self.doc.add_section(WD_SECTION.NEW_PAGE)
        section.top_margin = Cm(2.5)
        section.bottom_margin = Cm(2.5)
        section.left_margin = Cm(2.5)
        section.right_margin = Cm(2.0)
        section.footer.is_linked_to_previous = False
        p = section.footer.paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r = p.add_run('第 ')
        self._font(r, '宋体', 9)
        begin = OxmlElement('w:fldChar'); begin.set(qn('w:fldCharType'), 'begin')
        instr = OxmlElement('w:instrText'); instr.set(qn('xml:space'), 'preserve'); instr.text = ' PAGE '
        sep = OxmlElement('w:fldChar'); sep.set(qn('w:fldCharType'), 'separate')
        t = OxmlElement('w:t'); t.text = '1'
        end = OxmlElement('w:fldChar'); end.set(qn('w:fldCharType'), 'end')
        for el in (begin, instr, sep, t, end):
            r._r.append(el)
        r2 = p.add_run(' 页')
        self._font(r2, '宋体', 9)
        pgnum = section._sectPr.findall(qn('w:pgNumType'))
        if pgnum:
            pgnum[-1].set(qn('w:start'), '1')
        self.section = section

    def _setup_styles(self):
        styles = self.doc.styles
        body = styles.add_style('ReportBody', WD_STYLE_TYPE.PARAGRAPH)
        body.base_style = styles['Normal']
        body.font.name = '宋体'
        body.font.size = Pt(12)
        body._element.rPr.rFonts.set(qn('w:eastAsia'), '宋体')
        body.paragraph_format.line_spacing = 1.5
        body.paragraph_format.space_after = Pt(0)
        body.paragraph_format.first_line_indent = Pt(24)
        body.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY

        caption = styles.add_style('ReportCaption', WD_STYLE_TYPE.PARAGRAPH)
        caption.base_style = styles['Normal']
        caption.font.name = '宋体'
        caption.font.size = Pt(10.5)
        caption._element.rPr.rFonts.set(qn('w:eastAsia'), '宋体')
        caption.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.CENTER
        caption.paragraph_format.space_after = Pt(6)

        abstract = styles.add_style('ReportAbstractBody', WD_STYLE_TYPE.PARAGRAPH)
        abstract.base_style = styles['Normal']
        abstract.font.name = '宋体'
        abstract.font.size = Pt(11.5)
        abstract._element.rPr.rFonts.set(qn('w:eastAsia'), '宋体')
        abstract.paragraph_format.line_spacing = 1.3
        abstract.paragraph_format.space_after = Pt(0)
        abstract.paragraph_format.first_line_indent = Pt(23)
        abstract.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY

        ref = styles.add_style('ReportReference', WD_STYLE_TYPE.PARAGRAPH)
        ref.base_style = styles['Normal']
        ref.font.name = '宋体'
        ref.font.size = Pt(10)
        ref._element.rPr.rFonts.set(qn('w:eastAsia'), '宋体')
        ref.paragraph_format.line_spacing = 1.2
        ref.paragraph_format.space_after = Pt(2)
        ref.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.LEFT

        toc = styles.add_style('ReportTOC', WD_STYLE_TYPE.PARAGRAPH)
        toc.base_style = styles['Normal']
        toc.font.name = '宋体'
        toc.font.size = Pt(11)
        toc._element.rPr.rFonts.set(qn('w:eastAsia'), '宋体')
        toc.paragraph_format.line_spacing = 1.0
        toc.paragraph_format.space_after = Pt(0)

        en = styles.add_style('ReportEnglishBody', WD_STYLE_TYPE.PARAGRAPH)
        en.base_style = styles['Normal']
        en.font.name = 'Times New Roman'
        en.font.size = Pt(11)
        en._element.rPr.rFonts.set(qn('w:eastAsia'), 'Times New Roman')
        en.paragraph_format.line_spacing = 1.3
        en.paragraph_format.space_after = Pt(0)
        en.paragraph_format.first_line_indent = Pt(22)
        en.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY

        for name, size in [('Heading 1', 16), ('Heading 2', 15), ('Heading 3', 14)]:
            st = styles[name]
            st.font.name = '黑体'
            st.font.size = Pt(size)
            st.font.bold = True
            st.font.color.rgb = RGBColor(0, 0, 0)
            st._element.rPr.rFonts.set(qn('w:eastAsia'), '黑体')
            st.paragraph_format.space_before = Pt(12)
            st.paragraph_format.space_after = Pt(6)
            pPr = st.element.pPr
            if pPr is not None:
                for numPr in pPr.findall(qn('w:numPr')):
                    pPr.remove(numPr)

    @staticmethod
    def _font(run, name='宋体', size=12, bold=False):
        run.font.name = name
        run.font.size = Pt(size)
        run.bold = bold
        run._element.rPr.rFonts.set(qn('w:eastAsia'), name)

    def h(self, text, level=1):
        p = self.doc.add_paragraph(style=f'Heading {level}')
        if self.pending_break:
            p.paragraph_format.page_break_before = True
            self.pending_break = False
        p.paragraph_format.keep_with_next = True
        r = p.add_run(text)
        self._font(r, '黑体', {1: 16, 2: 15, 3: 14}[level], True)
        return p

    def body(self, text, indent=True, en=False):
        text = text.replace('`', '')
        p = self.doc.add_paragraph(style='ReportEnglishBody' if en else 'ReportBody')
        if not indent:
            p.paragraph_format.first_line_indent = None
        r = p.add_run(text)
        self._font(r, 'Times New Roman' if en else '宋体', 11 if en else 12, False)
        return p

    def abstract_body(self, text):
        text = text.replace('`', '')
        p = self.doc.add_paragraph(style='ReportAbstractBody')
        r = p.add_run(text)
        self._font(r, '宋体', 11.5, False)
        return p

    def kv(self, label, value):
        p = self.doc.add_paragraph(style='ReportBody')
        p.paragraph_format.first_line_indent = None
        r = p.add_run(label)
        self._font(r, '宋体', 12, True)
        r2 = p.add_run(value)
        self._font(r2, '宋体', 12, False)
        return p

    def caption(self, text):
        p = self.doc.add_paragraph(style='ReportCaption')
        r = p.add_run(text)
        self._font(r, '宋体', 10.5, False)
        return p

    def figure(self, path, width_cm, cap):
        p = self.doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.space_before = Pt(6)
        p.paragraph_format.space_after = Pt(3)
        p.add_run().add_picture(path, width=Cm(width_cm))
        self.caption(cap)

    def _shade(self, cell, fill='D9E2F3'):
        tcPr = cell._tc.get_or_add_tcPr()
        shd = OxmlElement('w:shd')
        shd.set(qn('w:val'), 'clear')
        shd.set(qn('w:fill'), fill)
        tcPr.append(shd)

    def _reorder_tblpr(self, tblPr):
        order = [
            'tblStyle', 'tblpPr', 'tblOverlap', 'bidiVisual',
            'tblStyleRowBandSize', 'tblStyleColBandSize', 'tblW', 'jc',
            'tblCellSpacing', 'tblInd', 'tblBorders', 'shd', 'tblLayout',
            'tblCellMar', 'tblLook', 'tblCaption', 'tblDescription', 'tblPrChange',
        ]
        rank = {name: i for i, name in enumerate(order)}
        children = list(tblPr)
        for child in children:
            tblPr.remove(child)
        for child in sorted(children, key=lambda c: rank.get(c.tag.split('}')[-1], 99)):
            tblPr.append(child)

    def table(self, headers, rows, widths=None, font_size=9.0, cap=None):
        if cap:
            cp = self.caption(cap)
            cp.paragraph_format.keep_with_next = True
        t = self.doc.add_table(rows=1, cols=len(headers))
        t.autofit = False
        tblPr = t._tbl.tblPr
        if widths:
            for i, width in enumerate(widths):
                t.columns[i].width = Cm(width)
        borders = OxmlElement('w:tblBorders')
        for edge in ('top', 'left', 'bottom', 'right', 'insideH', 'insideV'):
            el = OxmlElement(f'w:{edge}')
            el.set(qn('w:val'), 'single')
            el.set(qn('w:sz'), '4')
            el.set(qn('w:space'), '0')
            el.set(qn('w:color'), '000000')
            borders.append(el)
        tblPr.append(borders)
        t.alignment = WD_TABLE_ALIGNMENT.CENTER
        t.autofit = False
        layout = tblPr.get_or_add_tblLayout()
        layout.set(qn('w:type'), 'fixed')
        self._reorder_tblpr(tblPr)
        hdr = t.rows[0]
        trPr = hdr._tr.get_or_add_trPr()
        tblHeader = OxmlElement('w:tblHeader'); tblHeader.set(qn('w:val'), 'true')
        trPr.append(tblHeader)
        for i, text in enumerate(headers):
            cell = hdr.cells[i]
            if widths:
                cell.width = Cm(widths[i])
            cell.text = ''
            p = cell.paragraphs[0]
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            r = p.add_run(str(text))
            self._font(r, '宋体', font_size, True)
            self._shade(cell)
        for row in t.rows:
            trPr = row._tr.get_or_add_trPr()
            trPr.append(OxmlElement('w:cantSplit'))
        for row in rows:
            cells = t.add_row().cells
            trPr = t.rows[-1]._tr.get_or_add_trPr()
            trPr.append(OxmlElement('w:cantSplit'))
            for i, val in enumerate(row):
                if widths:
                    cells[i].width = Cm(widths[i])
                cells[i].text = ''
                p = cells[i].paragraphs[0]
                p.paragraph_format.space_after = Pt(0)
                p.paragraph_format.line_spacing = 1.1
                p.alignment = WD_ALIGN_PARAGRAPH.LEFT
                r = p.add_run('' if val is None else str(val).replace('`', ''))
                self._font(r, '宋体', font_size, False)
        self.doc.add_paragraph()
        return t

    def page_break(self):
        self.pending_break = True

    def toc(self):
        if not self.toc_entries:
            self.body('（目录将在生成后自动填充。）', indent=False)
            return
        for entry in self.toc_entries:
            level = int(entry.get('level', 1))
            p = self.doc.add_paragraph(style='ReportTOC')
            p.paragraph_format.left_indent = Cm(0.0 if level == 1 else 0.75)
            p.paragraph_format.space_before = Pt(4 if level == 1 else 0)
            p.paragraph_format.tab_stops.add_tab_stop(
                Cm(16.5), WD_TAB_ALIGNMENT.RIGHT, WD_TAB_LEADER.DOTS
            )
            r = p.add_run(str(entry.get('text', '')))
            self._font(r, '宋体', 11.5 if level == 1 else 10.5, level == 1)
            r2 = p.add_run('\t' + str(entry.get('page', '—')))
            self._font(r2, '宋体', 10.5, False)

    def screenshot_placeholder(self, title, description, fig_no, height_cm=9.0,
                               note='建议尺寸 1600×900（16:9）；插入后删除提示文字，保留图题。'):
        p = self.doc.add_paragraph(style='ReportBody')
        p.paragraph_format.first_line_indent = None
        if self.pending_break:
            p.paragraph_format.page_break_before = True
            self.pending_break = False
        p.paragraph_format.keep_with_next = True
        r = p.add_run(title)
        self._font(r, '宋体', 12, True)
        self.body(description)
        t = self.doc.add_table(rows=1, cols=1)
        t.autofit = False
        t.alignment = WD_TABLE_ALIGNMENT.CENTER
        tblPr = t._tbl.tblPr
        borders = OxmlElement('w:tblBorders')
        for edge in ('top', 'left', 'bottom', 'right', 'insideH', 'insideV'):
            el = OxmlElement(f'w:{edge}')
            el.set(qn('w:val'), 'dashed')
            el.set(qn('w:sz'), '8')
            el.set(qn('w:space'), '0')
            el.set(qn('w:color'), 'A3ADBB')
            borders.append(el)
        tblPr.append(borders)
        layout = tblPr.get_or_add_tblLayout()
        layout.set(qn('w:type'), 'fixed')
        self._reorder_tblpr(tblPr)
        cell = t.rows[0].cells[0]
        cell.width = Cm(16.0)
        t.columns[0].width = Cm(16.0)
        t.rows[0].height = Cm(height_cm)
        t.rows[0].height_rule = WD_ROW_HEIGHT_RULE.EXACTLY
        trPr = t.rows[0]._tr.get_or_add_trPr()
        trPr.append(OxmlElement('w:cantSplit'))
        self._shade(cell, 'F7F8FA')
        cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
        cp = cell.paragraphs[0]
        cp.alignment = WD_ALIGN_PARAGRAPH.CENTER
        cr = cp.add_run(f'【待插入实际系统截图：{title}】')
        self._font(cr, '宋体', 11, True)
        cr.font.color.rgb = RGBColor(0xC9, 0x6A, 0x6A)
        cp.add_run().add_break()
        cn = cp.add_run(note)
        self._font(cn, '宋体', 9, False)
        cn.font.color.rgb = RGBColor(0x68, 0x75, 0x89)
        c = self.caption(f'{fig_no}  {title}')
        c.paragraph_format.keep_with_next = False

    def save(self, path):
        self.doc.save(path)


def load_toc(path):
    if os.path.exists(path):
        with open(path, encoding='utf-8') as f:
            return json.load(f)
    return []

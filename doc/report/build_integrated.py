import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from report_lib import ReportBuilder, load_toc
import content_front
import content_ch1_3
import content_ch4_6
import content_ch7_9

BASE = os.path.dirname(os.path.abspath(__file__))
COVER = '/sessions/sweet-eloquent-thompson/mnt/outputs/report-build/cover.docx'
TOC = os.path.join(BASE, 'toc_entries_integrated.json')
OUT = os.path.join(BASE, '综合课程设计报告-整合完善版.docx')

ctx = ReportBuilder(COVER, load_toc(TOC))
content_front.build(ctx)
content_ch1_3.build(ctx)
content_ch4_6.build(ctx)
content_ch7_9.build(ctx)
ctx.save(OUT)
print('SAVED', OUT)

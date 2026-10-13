"""Use the packaged DOCX rasterizer after a read-only native Word PDF export."""
import importlib.util, sys, uuid
from pathlib import Path
from functools import partial

ROOT=Path(__file__).resolve().parents[3]
OUT=ROOT/'docs/project-governance/audit'
SKILL=Path(r'C:/Users/C-Linglong/.codex/plugins/cache/openai-primary-runtime/documents/26.909.12148/skills/documents')
POPPLER=r'C:/Users/C-Linglong/.cache/codex-runtimes/codex-primary-runtime/dependencies/native/poppler/Library/bin'
pdf=OUT/'assignment-reviewed.pdf'
if not pdf.is_file() or not pdf.stat().st_size:raise RuntimeError('Word PDF export required')
source=ROOT/'docs/project-governance/多维数据分析平台开发模块与人员分工（修订版）.docx'
if pdf.stat().st_mtime_ns<source.stat().st_mtime_ns:raise RuntimeError('Word PDF export is older than DOCX; re-export first')
spec=importlib.util.spec_from_file_location('packaged_renderer',SKILL/'render_docx.py')
renderer=importlib.util.module_from_spec(spec);spec.loader.exec_module(renderer)
renderer.convert_to_pdf=lambda *a,**kw:(str(pdf),'Read-only native Microsoft Word export; bundled LibreOffice unavailable on Windows')
renderer.convert_from_path=partial(renderer.convert_from_path,poppler_path=POPPLER)
renderer.pdfinfo_from_path=partial(renderer.pdfinfo_from_path,poppler_path=POPPLER)
class RetainTemp:
 def __init__(self,prefix='tmp',**kw):
  self.name=str(OUT/'render-temp'/(prefix+uuid.uuid4().hex));Path(self.name).mkdir(parents=True,exist_ok=True)
 def __enter__(self):return self.name
 def __exit__(self,*args):return False
renderer.tempfile.TemporaryDirectory=RetainTemp
sys.argv=[str(SKILL/'render_docx.py'),str(source),'--output_dir',str(OUT/'word-render')]
renderer.main()
# Remove only stale page PNGs from an earlier, longer review of this document.
page_count=len(renderer.PdfReader(str(pdf)).pages) if hasattr(renderer,'PdfReader') else None
if page_count is None:
 from pypdf import PdfReader
 page_count=len(PdfReader(pdf).pages)
render_dir=(OUT/'word-render').resolve()
for page in render_dir.glob('page-*.png'):
 if page.parent.resolve()==render_dir and page.stem[5:].isdigit() and int(page.stem[5:])>page_count:
  page.unlink()

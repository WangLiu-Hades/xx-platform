from __future__ import annotations
import collections, hashlib, json, os, sys, zipfile
from pathlib import Path
from datetime import datetime, timezone
from docx import Document
from docx.oxml.ns import qn
from docx.table import Table
from docx.text.paragraph import Paragraph

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / 'docs' / 'project-governance' / 'audit'
OUT.mkdir(parents=True, exist_ok=True)

def digest(path):
    h=hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda:stream.read(1024*1024),b''): h.update(block)
    return h.hexdigest()

def extract_docx(path):
    doc=Document(path); lines=[]
    for item in doc.element.body.iterchildren():
        if item.tag==qn('w:p'):
            text=Paragraph(item,doc).text
            if text: lines.append(text)
        elif item.tag==qn('w:tbl'):
            for row in Table(item,doc).rows:
                lines.append(' | '.join(cell.text.replace('\n',' / ') for cell in row.cells))
    return '\n'.join(lines)

def classify(relative):
    parts=relative.parts; s=relative.as_posix()
    if '.git' in parts or 'analysis-platform.git' in parts:return 'git_metadata'
    if parts and parts[0]=='local-only':return 'local_preserved_material'
    if 'node_modules' in parts:return 'third_party_dependency'
    if '.npm-cache' in parts:return 'dependency_cache'
    if 'prototype-v1-baseline' in parts or 'prototype-migration-assessment' in parts or relative.name=='prototype-audit-baseline.html':return 'historical_prototype'
    if 'dist' in parts:return 'generated_frontend'
    if 'audit' in parts or 'evidence' in parts or any('render' in p for p in parts):return 'verification_evidence'
    if s.startswith('archive/'):return 'historical_prototype'
    if '/src/api/' in s and relative.name in ['schema.ts','contract.json','validation-contract.json','operations.ts']:return 'generated_contract'
    if 'vendor' in parts:return 'vendored_dependency'
    if 'apps/web/src/' in s:return 'active_frontend_source'
    if 'apps/web/tests/' in s:return 'active_frontend_test' if relative.name not in ['audit.e2e.cjs'] else 'historical_test'
    if relative.suffix in ['.md','.mmd','.yaml','.sql']:return 'design_or_governance'
    return 'project_support'

files=[];errors=[];texts=[];json_errors=[];docs=[]
# Snapshot all paths before writing output; current audit output is separately listed.
paths=[]
for directory,subdirs,names in os.walk(ROOT,followlinks=False):
    for name in names:
        path=Path(directory)/name
        if OUT in path.parents:continue
        paths.append(path)
for path in sorted(paths):
    rel=path.relative_to(ROOT);category=classify(rel)
    entry={'path':rel.as_posix(),'category':category}
    try:
        stat=path.stat();entry.update(size=stat.st_size,mtime_ns=stat.st_mtime_ns,sha256=digest(path))
        if category not in ['git_metadata','third_party_dependency','dependency_cache','generated_frontend','vendored_dependency','local_preserved_material']:
            if path.suffix.lower() in ['.md','.mmd','.yaml','.sql','.ts','.vue','.js','.cjs','.mjs','.py','.ps1','.json','.html','.css','.txt'] or path.name.startswith('.env'):
                text=path.read_text('utf-8-sig');entry['text_chars']=len(text)
                if '\ufffd' in text:entry['replacement_characters']=text.count('\ufffd')
                if path.suffix.lower()=='.json':
                    try:json.loads(text)
                    except Exception as e:json_errors.append({'path':rel.as_posix(),'error':str(e)})
                if category in ['design_or_governance','project_support','active_frontend_source','active_frontend_test','contract_work_reference']:
                    texts.append('\n\n===== '+rel.as_posix()+' =====\n'+text)
            elif path.suffix.lower()=='.docx':
                text=extract_docx(path);docs.append({'path':rel.as_posix(),'text':text});entry['text_chars']=len(text)
        entry['read_status']='hashed'
    except Exception as e:
        entry['read_status']='error';entry['error']=str(e);errors.append(entry.copy())
    files.append(entry)
external=[]
for index,arg in enumerate(sys.argv[1:]):
    path=Path(arg);entry={'path':str(path),'sha256':digest(path)}
    if path.suffix.lower()=='.docx':
        text=extract_docx(path);entry['text_chars']=len(text)
        (OUT/f'external-docx-{index}.txt').write_text(text,encoding='utf-8')
        entry['extraction']=f'external-docx-{index}.txt'
    external.append(entry)
(OUT/'workspace-files.jsonl').write_text(''.join(json.dumps(row,ensure_ascii=False)+'\n' for row in files),encoding='utf-8')
(OUT/'first-party-text.txt').write_text(''.join(texts),encoding='utf-8')
(OUT/'workspace-docx-text.json').write_text(json.dumps(docs,ensure_ascii=False,indent=2),encoding='utf-8')
summary={'time':datetime.now(timezone.utc).isoformat(),'root':str(ROOT),'files':len(files),'bytes':sum(f.get('size',0) for f in files),'categories':dict(collections.Counter(f['category'] for f in files)),'read_errors':errors,'json_errors':json_errors,'external_sources':external,'exclusions':['current docs/project-governance/audit output; final deliverables verified separately'],'method':'All enumerated regular files read and SHA-256 hashed; owned text extracted; dependency/cache/compiled bytes inventoried, not individually semantically reviewed.'}
(OUT/'workspace-inventory-summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(summary,ensure_ascii=False))

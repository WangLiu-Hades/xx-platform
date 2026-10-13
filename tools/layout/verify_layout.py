"""Portable layout check; --integrity also verifies all preserved local originals."""
from pathlib import Path
from urllib.parse import unquote
import argparse, json, re, hashlib, sys, csv
from datetime import datetime, timezone

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'reports/path-optimization'
def sha(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b)
 return h.hexdigest()
def save(p,data):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n','utf-8')
def run():
 parser=argparse.ArgumentParser();parser.add_argument('--integrity',action='store_true');args=parser.parse_args()
 checks=[]
 def check(name,ok,detail=None):checks.append({'check':name,'passed':bool(ok),'detail':detail})
 required=['README.md','docs/START_HERE.md','docs/team/README.md','docs/team/DEV-01-backend.md','docs/team/DEV-02-frontend.md','docs/team/DEV-03-data.md','docs/team/DEV-04-quality.md','apps/web/package.json','apps/web/package-lock.json','apps/web/src/main.ts','apps/api/README.md','workers/data/README.md','workers/delivery/README.md','contracts/api/openapi.yaml','contracts/database/schema.sql','docs/requirements/source/平台功能设计（原始）.pdf','infra/README.md','tests/README.md','reports/path-optimization/path-map.csv','.gitignore','.github/workflows/frontend.yml']
 check('required_entrypoints',all((ROOT/p).is_file() for p in required),[p for p in required if not (ROOT/p).is_file()])
 check('single_active_vue_project',(ROOT/'apps/web/vite.config.ts').exists() and not (ROOT/'package.json').exists())
 own=json.loads((ROOT/'docs/team/ownership.json').read_text('utf-8'))
 check('four_role_owners',set(own['owners'])=={'DEV-01','DEV-02','DEV-03','DEV-04'})
 check('shared_repository',own['repository']=='https://github.com/WangLiu-Hades/xx-platform')
 check('no_invented_github_reviewers',not (ROOT/'.github/CODEOWNERS').exists())
 for path in ['apps/api','workers/data','workers/delivery','infra','tests']:
  check('future_status:'+path,any(x in (ROOT/path/'README.md').read_text('utf-8') for x in ['待实现','尚无','尚待','预留','未实现']))
 # Only actionable documents; legacy sources and report provenance are not runbooks.
 broken=[];links=0;markdown=[]
 for prefix in ['apps','contracts','docs','tools','infra','tests','workers','.github']:
  for p in (ROOT/prefix).rglob('*.md'):
   rel=p.relative_to(ROOT).as_posix()
   if any(s in p.parts for s in ['node_modules','dist','vendor']):continue
   if '/audit/' in rel and p.name!='README.md':continue
   markdown.append(p)
 markdown += [ROOT/'README.md',OUT/'README.md',OUT/'RESULT.md',ROOT/'archive/README.md']
 for p in markdown:
  body=re.sub(r'```[\s\S]*?```','',p.read_text('utf-8-sig'))
  for m in re.finditer(r'\[[^\]\n]+\]\((<[^>]+>|[^\n]+?)\)',body):
   ref=m.group(1).strip('<>')
   if re.match(r'^[a-z][a-z0-9+.-]*:|^#',ref,re.I):continue
   ref=unquote(ref.split('#',1)[0])
   if not ref:continue
   links+=1;dest=(p.parent/ref).resolve()
   if not dest.is_relative_to(ROOT) or not dest.exists():broken.append({'file':p.relative_to(ROOT).as_posix(),'link':ref})
 check('active_markdown_links',not broken,{'files':len(markdown),'links':links,'broken':broken})
 generator=(ROOT/'apps/web/scripts/generate-api.mjs').read_text('utf-8')
 check('api_generator_new_path','../../contracts/api/openapi.yaml' in generator)
 if args.integrity:check('openapi_business_bytes_preserved',sha(ROOT/'contracts/api/openapi.yaml')=='f10fc158252bad97cb48d9d223e7878618dda403dca5fa863a54cdf6b8aa54cd')
 if args.integrity:check('ddl_business_bytes_preserved',sha(ROOT/'contracts/database/schema.sql')=='7d3534b536e46a5879d338df08cc70a02012ef86d0efe423d25747cf37a26ab1')
 plan=json.loads((OUT/'before-and-mapping.json').read_text('utf-8'))
 with (OUT/'path-map.csv').open(encoding='utf-8-sig',newline='') as f:rows=list(csv.DictReader(f))
 check('complete_unique_mapping',len(rows)==plan['filesBefore'] and len({r['old'].lower() for r in rows})==len(rows) and len({r['new'].lower() for r in rows})==len(rows))
 check('mapping_matches_snapshot',all(a['old']==b['old'] and a['new']==b['new'] and a['sha256']==b['sha256'] and int(a['size'])==b['size'] for a,b in zip(rows,plan['files'])))
 missing=[];mismatch=[];preserved=0;updated=0
 if args.integrity:
  from concurrent.futures import ThreadPoolExecutor
  def verify_one(f):
   p=ROOT/f['new'];b=ROOT/'local-only/path-optimization/original-content'/f['new']
   try:
    if not p.is_file():return 'missing',f['new']
    if p.stat().st_size==f['size'] and sha(p)==f['sha256']:return 'preserved',f['new']
    if b.is_file() and b.stat().st_size==f['size'] and sha(b)==f['sha256']:return 'updated',f['new']
    return 'mismatch',f['new']
   except OSError as e:return 'mismatch',f['new']+': '+str(e)
  with ThreadPoolExecutor(max_workers=8) as pool:
   for i,(status,path) in enumerate(pool.map(verify_one,plan['files'])):
    if status=='preserved':preserved+=1
    elif status=='updated':updated+=1
    elif status=='missing':missing.append(path)
    else:mismatch.append(path)
    if (i+1)%20000==0:print(f'Integrity {i+1}/{len(plan["files"])}',flush=True)
  check('original_files_all_preserved',not missing and not mismatch,{'originalFiles':len(plan['files']),'byteIdenticalAtMappedPath':preserved,'updatedWithExactOriginalBackup':updated,'missing':missing,'unrecoverableChanges':mismatch,'deleted':len(missing)})
 result={'checkedAt':datetime.now(timezone.utc).isoformat(),'scope':'local full preservation and portable navigation' if args.integrity else 'portable navigation, critical contracts and mapping','checks':checks,'passed':sum(c['passed'] for c in checks),'failed':sum(not c['passed'] for c in checks)}
 save(OUT/('integrity-checks.json' if args.integrity else 'layout-checks.json'),result)
 print(json.dumps(result,ensure_ascii=False,indent=2),flush=True)
 return 1 if result['failed'] else 0
if __name__=='__main__':sys.exit(run())

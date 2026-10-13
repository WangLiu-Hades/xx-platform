"""Preview/copy Git-eligible platform files into a clean clone. Never commit or push."""
from pathlib import Path
import argparse,subprocess,tempfile,json,shutil,hashlib,os,sys
from datetime import datetime

def git(*args,cwd=None):
 p=subprocess.run(['git',*map(str,args)],cwd=cwd,capture_output=True)
 if p.returncode:raise RuntimeError(p.stderr.decode('utf-8',errors='replace').strip())
 return p.stdout.decode('utf-8')
def sha(p):
 h=hashlib.sha256()
 with p.open('rb') as stream:
  for block in iter(lambda:stream.read(1048576),b''):h.update(block)
 return h.hexdigest()
def main():
 parser=argparse.ArgumentParser(description=__doc__)
 parser.add_argument('--source',type=Path,required=True)
 parser.add_argument('--destination',type=Path,required=True)
 parser.add_argument('--report',type=Path,required=True)
 parser.add_argument('--apply',action='store_true')
 parser.add_argument('--allow-overwrite',action='store_true')
 args=parser.parse_args();src=args.source.resolve();dest=args.destination.resolve();report=args.report.resolve()
 if src==dest or src.is_relative_to(dest) or dest.is_relative_to(src):raise RuntimeError('Source and destination must be separate directories, not nested')
 required=['.gitignore','README.md','apps/web/package.json','contracts/api/openapi.yaml','docs/team/ownership.json']
 if not all((src/p).is_file() for p in required):raise RuntimeError('Source is not the optimized platform root')
 if not dest.is_dir():raise RuntimeError('Destination must be an existing clean Git clone')
 if Path(git('rev-parse','--show-toplevel',cwd=dest).strip()).resolve()!=dest:raise RuntimeError('Destination must be the clone root')
 if git('status','--porcelain','--untracked-files=all',cwd=dest).strip():raise RuntimeError('Destination has local changes; use a new clean clone')
 if report.is_relative_to(src) or report.is_relative_to(dest):raise RuntimeError('Report must be outside both projects; use your TEMP directory')
 # A temporary empty index lets Git itself apply every .gitignore without changing source .git.
 with tempfile.TemporaryDirectory(prefix='xx-platform-export-') as temp:
  meta=Path(temp).resolve()
  if not meta.is_relative_to(Path(tempfile.gettempdir()).resolve()):raise RuntimeError('Unexpected temporary metadata path')
  git('init','--bare',meta)
  names=git('--git-dir='+str(meta),'--work-tree='+str(src),'ls-files','--others','--exclude-standard','-z').split('\0')
 names=sorted(p for p in names if p)
 conflicts=[];files=[]
 forbidden={'node_modules','.npm-cache','.next','.vinext','.sites-runtime','.git','dist','build'}
 for rel in names:
  origin=src/rel;target=dest/rel
  if any(part in forbidden or part.startswith('.wrangler') for part in Path(rel).parts):raise RuntimeError('Ignored runtime file leaked: '+rel)
  if rel.startswith('local-only/') and rel!='local-only/README.md':raise RuntimeError('Local-only material leaked: '+rel)
  if not origin.resolve().is_relative_to(src) or not target.resolve().is_relative_to(dest):raise RuntimeError('Path escapes project: '+rel)
  for parent in [origin,*list(origin.parents)[:len(origin.relative_to(src).parts)-1],target,*list(target.parents)[:len(target.relative_to(dest).parts)-1]]:
   if parent.is_symlink() or getattr(parent,'is_junction',lambda:False)():raise RuntimeError('Linked path requires manual review: '+str(parent))
  if not origin.is_file():raise RuntimeError('Not a regular file: '+rel)
  if origin.stat().st_size>100*1024*1024:raise RuntimeError('File exceeds 100MiB: '+rel)
  if target.exists() and not target.is_file():raise RuntimeError('Destination type conflict: '+rel)
  state='new' if not target.exists() else 'same' if sha(origin)==sha(target) else 'overwrite'
  if state=='overwrite':conflicts.append(rel)
  files.append({'path':rel,'size':origin.stat().st_size,'state':state})
 oldTracked=git('ls-files','-z',cwd=dest).split('\0')
 extras=[p for p in oldTracked if p and p not in names]
 result={'source':str(src),'destination':str(dest),'files':len(files),'bytes':sum(f['size'] for f in files),'overwrites':conflicts,'destinationOnlyTrackedFiles':extras,'fileList':files,'applied':False,'backup':None}
 report.parent.mkdir(parents=True,exist_ok=True);report.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n','utf-8')
 print(json.dumps({k:v for k,v in result.items() if k!='fileList'},ensure_ascii=False,indent=2))
 if not args.apply:return
 if conflicts and not args.allow_overwrite:raise RuntimeError('Overwrites require review and --allow-overwrite; no files copied')
 # The current layout intentionally has no root package.json. Do not silently overlay another application.
 if 'package.json' in extras:raise RuntimeError('Destination has another root application; archive/resolve it before importing')
 if (dest/'local-only').exists() and ((dest/'local-only').is_symlink() or getattr(dest/'local-only','is_junction',lambda:False)()):raise RuntimeError('Backup path is linked')
 backup=dest/'local-only/github-import-backup'/datetime.now().strftime('%Y%m%d-%H%M%S-%f')
 for rel in conflicts:
  b=backup/rel
  if not b.resolve().is_relative_to(dest):raise RuntimeError('Unsafe backup path')
  b.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(dest/rel,b)
 for f in files:
  if f['state']=='same':continue
  target=dest/f['path'];target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(src/f['path'],target)
 result['applied']=True;result['backup']=str(backup) if conflicts else None
 report.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n','utf-8')
 print('Copied files. No commit/push performed. Review git status before staging.')

if __name__=='__main__':
 try:main()
 except (OSError,RuntimeError) as e:print('ERROR: '+str(e),file=sys.stderr);sys.exit(1)

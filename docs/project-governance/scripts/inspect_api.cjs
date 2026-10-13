const fs=require('fs'),path=require('path'),crypto=require('crypto');
const root=path.resolve(__dirname,'../../..'),proto=path.join(root,'apps/web'),out=process.env.LAYOUT_API_OUT?path.resolve(root,process.env.LAYOUT_API_OUT):path.join(root,'docs/project-governance/audit');
const ts=require(path.join(proto,'node_modules/typescript')),yaml=require(path.join(proto,'node_modules/yaml'));
const raw=fs.readFileSync(path.join(root,'contracts/api/openapi.yaml'),'utf8'),api=yaml.parse(raw),contract=JSON.parse(fs.readFileSync(path.join(proto,'src/api/contract.json'),'utf8'));
fs.mkdirSync(out,{recursive:true});
const operations=[];
for(const [p,item] of Object.entries(api.paths))for(const [method,op] of Object.entries(item))if(['get','post','put','patch','delete','head','options'].includes(method))operations.push({id:op.operationId,path:p,method:method.toUpperCase(),op});
const ids=new Set(operations.map(o=>o.id)),references=new Map([...ids].map(id=>[id,[]])),calls=[],unresolved=[];
function walk(dir){return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(path.join(dir,e.name)):[path.join(dir,e.name)]);}
for(const file of walk(path.join(proto,'src')).filter(p=>/\.(ts|vue)$/.test(p)&&!/(schema|operations)\.ts$/.test(p))){
 let content=fs.readFileSync(file,'utf8'),offset=0;
 if(file.endsWith('.vue')){for(const match of content.matchAll(/'([A-Za-z][A-Za-z0-9]+)'/g)){if(ids.has(match[1])&&content.slice(0,match.index).includes('</script>'))references.get(match[1]).push({file:path.relative(root,file).replaceAll('\\','/'),line:content.slice(0,match.index).split('\n').length,kind:'vue_template_literal'});}const m=/<script\b[^>]*>([\s\S]*?)<\/script>/.exec(content);if(!m)continue;offset=content.slice(0,m.index+ m[0].indexOf(m[1])).split('\n').length-1;content=m[1];}
 const source=ts.createSourceFile(file,content,ts.ScriptTarget.Latest,true,ts.ScriptKind.TS),variables=new Map();
 function collect(n){if(ts.isVariableDeclaration(n)&&ts.isIdentifier(n.name)&&n.initializer){if(!variables.has(n.name.text))variables.set(n.name.text,[]);variables.get(n.name.text).push(n.initializer);}if(ts.isParameter(n)&&ts.isIdentifier(n.name)&&n.type){const literals=[];function types(t){if(ts.isLiteralTypeNode(t))literals.push(t.literal);ts.forEachChild(t,types);}types(n.type);if(literals.length)variables.set(n.name.text,[...(variables.get(n.name.text)||[]),...literals]);}ts.forEachChild(n,collect);}collect(source);
 function values(n,seen=new Set()) {if(!n)return [];if(ts.isStringLiteralLike(n))return [n.text];if(ts.isConditionalExpression(n))return [...values(n.whenTrue,seen),...values(n.whenFalse,seen)];if(ts.isParenthesizedExpression(n)||ts.isAsExpression(n))return values(n.expression,seen);if(ts.isIdentifier(n)&&!seen.has(n.text)){const next=new Set([...seen,n.text]);return (variables.get(n.text)||[]).flatMap(v=>values(v,next));}return [];}
 function visit(n){const line=source.getLineAndCharacterOfPosition(n.getStart(source)).line+1+offset,rel=path.relative(root,file).replaceAll('\\','/');
  if(ts.isStringLiteralLike(n)&&ids.has(n.text)&&!ts.isLiteralTypeNode(n.parent)){references.get(n.text).push({file:rel,line,kind:'runtime_literal'});}
  if(ts.isCallExpression(n)&&ts.isIdentifier(n.expression)&&['request','downloadBinary'].includes(n.expression.text)){const found=[...new Set(values(n.arguments[0]).filter(id=>ids.has(id)))];calls.push({file:rel,line,callee:n.expression.text,expression:n.arguments[0]?.getText(source),operations:found});if(!found.length)unresolved.push(calls.at(-1));else for(const id of found)references.get(id).push({file:rel,line,kind:'request_call'});}
  ts.forEachChild(n,visit);
 }visit(source);
}
function deref(ref){return ref.slice(2).split('/').reduce((o,k)=>o?.[k],api);}
function pending(obj,seen=new Set()){if(!obj||typeof obj!=='object')return [];let result=[];if(obj['x-design-status']==='pending')result.push(...String(obj['x-pending-reason']||'').match(/P-\d{3}/g)||[]);if(obj.$ref&&!seen.has(obj.$ref)){seen.add(obj.$ref);result.push(...pending(deref(obj.$ref),seen));}for(const [k,v] of Object.entries(obj))if(k!=='example'&&k!=='examples')result.push(...(Array.isArray(v)?v.flatMap(x=>pending(x,seen)):typeof v==='object'?pending(v,seen):[]));return [...new Set(result)].sort();}
function assign(p){
 if(/^\/(health|ready)$/.test(p))return ['M01/M15','API','DEV-04负责探针部署与监测，DEV-01实现健康响应'];
 if(/^\/(auth|me|menus|permissions|dictionaries|client-config|regions|import-templates|files|tasks|contexts|targets|exports|metrics|decrypt-requests|batch-deletions|batch-revocations)/.test(p))return ['M01/M06','API','按领域分派Worker；数据算法由DEV-03、文件与任务底座由DEV-01'];
 if(/^\/ingestion/.test(p))return ['M03','DATA','DEV-03接入适配/标准化/去重/回放；DEV-01公开API与权限编排'];
 if(/^\/dashboard/.test(p))return ['M04','METRIC','DEV-03指标计算；DEV-02首页展示'];
 if(/^\/operations/.test(p))return ['M13','METRIC','DEV-03共用指标；DEV-02运维展示'];
 if(/^\/(captures|faces)/.test(p))return ['M05','DATA','DEV-03检索查询及人像适配，DEV-01公共API与授权'];
 if(/^\/(map|trajectories|statistics)/.test(p))return ['M07/M13','DATA','DEV-03空间/轨迹/统计查询；DEV-02地图交互'];
 if(/^\/analysis-tasks/.test(p))return ['M08','DATA','DEV-03分析计算与结果；DEV-01任务资源与编排'];
 if(/^\/(datasets|set-tasks)/.test(p))return ['M09','DATA','DEV-03成员投影和集合运算；DEV-01资源/版本/范围'];
 if(/^\/(watch-rules|alerts|notification|deliveries)/.test(p))return ['M10/M14','API','DEV-03命中候选计算；DEV-01规则状态、告警及可靠投递'];
 if(/^\/(person-libraries|profiles|trend-models|model-tags)/.test(p))return ['M11','DATA','DEV-03关联/模型/导入解析；DEV-01档案事务、谱系、公开API'];
 if(/^\/(sites|devices|device-groups|device-commands|reports)/.test(p))return ['M12','API','DEV-03遥测标准化/报表计算；DEV-01设备协议命令适配与回执'];
 if(/^\/(users|roles|audit-events)/.test(p))return ['M14','API','DEV-01用户、角色、审计；DEV-04安全反例验证'];
 return ['UNMAPPED','UNMAPPED','需检查'];
}
const rows=operations.map(({id,path:p,method,op})=>{const [module,workstream,handoff]=assign(p),refs=references.get(id),args=(op.parameters||[]).map(v=>v.$ref?deref(v.$ref):v);return {operationId:id,method,path:p,summary:op.summary,module,publicApiOwner:'DEV-01',dataWorkerOwner:['DATA','METRIC'].includes(workstream)?'DEV-03':'按handoff',handoff,frontendStaticReference:refs.length?'REFERENCED':'CONTRACT_ONLY',references:refs,operationPending:op['x-design-status']==='pending',pendingReason:op['x-pending-reason']||'',reachablePendingIds:pending(op),permission:op['x-permission']||op['x-permission-code']||contract.operations[id]?.permission||null,parameters:args.map(a=>({name:a.name,in:a.in,required:!!a.required})),requestSchema:op.requestBody?.content?.['application/json']?.schema||op.requestBody?.content?.['multipart/form-data']?.schema||null,success:Object.fromEntries(Object.entries(op.responses||{}).filter(([s])=>/^2/.test(s)).map(([s,r])=>[s,r.content?.['application/json']?.schema||'binary_or_empty']))};});
let missingRefs=[];function checkRefs(obj){if(!obj||typeof obj!=='object')return;if(obj.$ref&&!deref(obj.$ref))missingRefs.push(obj.$ref);Object.values(obj).forEach(v=>Array.isArray(v)?v.forEach(checkRefs):checkRefs(v));}checkRefs(api);
const summary={generatedAt:new Date().toISOString(),openapiSha256:crypto.createHash('sha256').update(raw).digest('hex'),operations:rows.length,schemas:Object.keys(api.components.schemas).length,operationPending:rows.filter(r=>r.operationPending).length,referenced:rows.filter(r=>r.references.length).length,contractOnly:rows.filter(r=>!r.references.length).length,unmapped:rows.filter(r=>r.module==='UNMAPPED'),unresolvedCalls:unresolved,duplicateIds:rows.length-ids.size,missingRefs:[...new Set(missingRefs)],generatedCatalogMatches:rows.every(r=>contract.operations[r.operationId]?.path===r.path&&contract.operations[r.operationId]?.method===r.method),method:'TS AST runtime string references and request-call literal/conditional resolution; static references do not prove a reachable button or successful real HTTP integration.'};
fs.writeFileSync(path.join(out,'api-inventory.json'),JSON.stringify({summary,rows,calls},null,2)+'\n');
const quote=v=>'"'+String(v??'').replaceAll('"','""')+'"';const fields=['operationId','method','path','summary','module','publicApiOwner','dataWorkerOwner','frontendStaticReference','operationPending','pendingReason','handoff'];
fs.writeFileSync(path.join(out,'api-inventory.csv'),'\uFEFF'+[fields,...rows.map(r=>fields.map(f=>r[f]))].map(r=>r.map(quote).join(',')).join('\r\n')+'\r\n');
fs.writeFileSync(path.join(out,'contract-only-operations.md'),'# 仅有契约、未发现当前前端运行时引用的接口\n\n静态检查结果，不能推定这些接口可删除；健康检查、自动清理、接入及运维能力也可能被其他调用方使用。\n\n| operationId | 方法与路径 | 模块 |\n| --- | --- | --- |\n'+rows.filter(r=>!r.references.length).map(r=>`| ${r.operationId} | ${r.method} ${r.path} | ${r.module} |`).join('\n')+'\n');
console.log(JSON.stringify(summary,null,2));

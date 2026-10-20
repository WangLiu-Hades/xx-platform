/* Local contract/policy integrity checks; never a substitute for backend or UAT. */
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'../../..'),deps=path.join(root,'apps/web/node_modules');
const YAML=require(path.join(deps,'yaml')),Ajv=require(path.join(deps,'ajv')),formats=require(path.join(deps,'ajv-formats'));
const policy=JSON.parse(fs.readFileSync(path.join(root,'docs/project-governance/provisional-policy.json'),'utf8'));
const changes=JSON.parse(fs.readFileSync(path.join(root,'docs/project-governance/provisional-contract-changes.json'),'utf8'));
const raw=fs.readFileSync(path.join(root,'contracts/api/openapi.yaml'),'utf8'),api=YAML.parse(raw);
const generated=JSON.parse(fs.readFileSync(path.join(root,'apps/web/src/api/contract.json'),'utf8'));
const errors=[],record=(name,fn)=>{try{fn();}catch(e){errors.push({name,message:e.message});}};
function deref(ref){return ref.slice(2).split('/').reduce((o,k)=>o?.[k],api);}
function convert(v){
 if(Array.isArray(v))return v.map(convert);if(!v||typeof v!=='object')return v;
 const r={};for(const[k,x]of Object.entries(v)){
  if(['nullable','discriminator','example','examples','description','readOnly','writeOnly'].includes(k)||k.startsWith('x-'))continue;
  r[k]=['properties','definitions','$defs'].includes(k)?Object.fromEntries(Object.entries(x).map(([n,s])=>[n,convert(s)])):k==='$ref'&&typeof x==='string'&&x.startsWith('#/')?'https://contract.invalid/openapi'+x:convert(x);
 }return v.nullable===true?{anyOf:[r,{type:'null'}]}:r;
}
const ajv=new Ajv({strict:false,allErrors:true,validateFormats:true});formats(ajv);
ajv.addSchema({$id:'https://contract.invalid/openapi',components:{schemas:Object.fromEntries(Object.entries(api.components.schemas).map(([n,s])=>[n,convert(s)]))}});
let exampleCount=0,refCount=0,pending=0;
function example(schema,value,label){record(label,()=>{const validate=ajv.compile(convert(schema));if(!validate(value))throw new Error(ajv.errorsText(validate.errors));exampleCount++;});}
for(const[n,s]of Object.entries(api.components.schemas))if('example'in s)example({$ref:'#/components/schemas/'+n},s.example,'schema example '+n);
function walk(v){if(!v||typeof v!=='object')return;
 if(v.$ref){refCount++;record('ref '+v.$ref,()=>assert.ok(deref(v.$ref)));}
 if(v['x-design-status']==='pending')pending++;
 for(const[k,x]of Object.entries(v))if(!['example','examples'].includes(k))walk(x);
}
walk(api);
function approvalExamples(v){if(!v||typeof v!=='object')return;
 if(typeof v.approvalId==='string'&&typeof v.requestDigest==='string'&&typeof v.requesterUserId==='string')record('approval fixture voting',()=>{
  const reviewers=v.decisions.map(d=>d.reviewerUserId);assert.equal(new Set(reviewers).size,reviewers.length);assert.ok(!reviewers.includes(v.requesterUserId)&&!reviewers.includes(v.executorUserId));
  if(v.status==='APPROVED')assert.ok(v.decisions.filter(d=>d.decision==='APPROVE').length>=v.requiredApprovals);
  assert.ok(v.reviewItems.length>0);
 });
 for(const x of Object.values(v))if(x&&typeof x==='object')approvalExamples(x);
}approvalExamples(api);
const operations=[];
for(const[url,item]of Object.entries(api.paths))for(const[method,op]of Object.entries(item))if(op?.operationId){
 operations.push(op);
 record('catalog '+op.operationId,()=>{
  const g=generated.operations[op.operationId];assert.ok(g);assert.equal(g.permission,op['x-permission']||op['x-permission-code']||null);assert.equal(g.path,url);assert.equal(g.method,method.toUpperCase());
  assert.ok(['PUBLIC','AUTHENTICATED',...api.components.schemas.PermissionCode.enum].includes(op['x-permission']));
  if(op['x-design-status']==='provisional'){assert.equal(op['x-client-confirmation'],'pending');assert.equal(g.designStatus,'provisional');assert.equal(g.clientConfirmation,'pending');}
 });
 const body=op.requestBody?.$ref?deref(op.requestBody.$ref):op.requestBody;
 for(const[type,m]of Object.entries(body?.content||{}))if(m.schema){if('example'in m)example(m.schema,m.example,op.operationId+' request '+type);for(const[n,e]of Object.entries(m.examples||{}))if('value'in e)example(m.schema,e.value,op.operationId+' request '+n);}
 for(const[status,response]of Object.entries(op.responses||{})){
  const r=response.$ref?deref(response.$ref):response;
  for(const[type,m]of Object.entries(r?.content||{}))if(m.schema){if('example'in m)example(m.schema,m.example,op.operationId+' '+status+' '+type);for(const[n,e]of Object.entries(m.examples||{}))if('value'in e)example(m.schema,e.value,op.operationId+' '+status+' '+n);}
 }
}
record('coverage and counts',()=>{
 assert.equal(operations.length,198);assert.equal(new Set(operations.map(o=>o.operationId)).size,198);assert.equal(Object.keys(api.components.schemas).length,623);assert.equal(pending,0);
 assert.equal(changes.marked.length,102);assert.equal(changes.newOperations.length,5);assert.equal(policy.coverage.length,62);assert.equal(new Set(policy.coverage.map(x=>x.id)).size,62);assert.equal(Object.keys(policy.rules).length,23);
 for(const x of changes.marked){let n=x.path.reduce((o,k)=>o[k],api);assert.equal(n['x-design-status'],'provisional');assert.equal(n['x-client-confirmation'],'pending');assert.deepEqual(n['x-decision-ids'],x.ids);}
 for(const x of policy.coverage){assert.equal(x.status,'provisional');assert.equal(x.client_confirmation,'pending');assert.ok(x.resolution.length>10);assert.ok(policy.rules[x.group]);}
});
record('confirmed constraints and settings',()=>{
 assert.equal(policy.confirmedConstraints.maxSessions,1);assert.equal(policy.confirmedConstraints.productionInternet,false);assert.equal(policy.confirmedConstraints.roleDataScope,'union_same_type');assert.equal(policy.confirmedConstraints.retention.unit,'calendar_month');
 assert.equal(policy.confirmedConstraints.retention.value,12);assert.equal(policy.settings.retention.unit,'calendar_month');assert.equal(policy.settings.retention.value,12);assert.equal(policy.settings.backup.retentionMonths,12);
 assert.equal(policy.retentionDecision.status,'user_confirmed');assert.equal(policy.settings.retention.annualReportProtection.freezeBeforeSourceExpiry,true);assert.equal(policy.settings.retention.annualReportProtection.protectInProgressInputs,true);assert.equal(policy.settings.retention.annualReportProtection.frozenReportRetentionMonths,12);
 assert.equal(policy.settings.residence.windowCalendarMonths,1);assert.equal(policy.settings.resources.ordinaryQueryMaxDays,31);assert.equal(policy.settings.resources.downloadGrantSeconds,300);assert.equal(policy.settings.resources.cursorSeconds,900);
 assert.equal(policy.settings.auth.idleSeconds,900);assert.equal(policy.settings.auth.absoluteSeconds,28800);assert.equal(policy.settings.resources.contextAccessSeconds,1800);assert.equal(policy.settings.resources.pageMax,100);
 assert.deepEqual(Object.keys(policy.settings.taskClasses).sort(),api.components.schemas.Task.properties.taskType.enum.slice().sort());
 assert.deepEqual(Object.keys(policy.permissionMatrix).sort(),api.components.schemas.PermissionCode.enum.slice().sort());assert.equal(Object.keys(policy.permissionMatrix).length,58);
 assert.equal(policy.menuCatalog.length,28);const source=fs.readFileSync(path.join(root,'apps/web/src/domain.ts'),'utf8');
 const text=source.match(/export const groups[^=]*=\s*(\{[^;]+\})/)[1];const groups=Function('return ('+text+')')();
 assert.deepEqual(policy.menuCatalog.map(m=>m.label),Object.values(groups).flat());
 assert.equal(new Set(policy.menuCatalog.map(m=>m.menuId)).size,28);assert.deepEqual(policy.menuCatalog.map(m=>m.route),Array.from({length:28},(_,i)=>'/page/'+i));
 assert.equal(api.components.schemas.PasswordChangeInput.properties.newPassword.minLength,15);assert.equal(api.components.schemas.PasswordChangeInput.properties.newPassword.maxLength,128);
 assert.equal(api.components.schemas.FaceSearchInput.properties.candidateLimit.maximum,100);assert.equal(api.components.schemas.DecryptionInput.properties.captureIds.maxItems,200);
 assert.deepEqual(policy.settings.metrics.allowedStatuses,api.components.schemas.MetricStatus.enum);assert.equal(policy.settings.notifications.unknownMayResend,false);assert.equal(policy.settings.notifications.successRateMin,0.99);
 assert.equal(policy.sources.length,22);for(const s of policy.sources)assert.ok(s.url.startsWith('https://'));for(const b of policy.externalBindings)assert.equal(b.status,'required_not_verified');
});
const result={policyVersion:policy.policyVersion,scope:'static_contract_and_policy_not_backend_acceptance',openapiSha256:crypto.createHash('sha256').update(raw).digest('hex'),operations:operations.length,schemas:Object.keys(api.components.schemas).length,originalPendingLocationsCovered:changes.marked.length,remainingDesignPending:pending,clientConfirmation:'pending',coverageItems:policy.coverage.length,validatedExamples:exampleCount,checkedRefs:refCount,errors};
const out=path.join(root,'docs/project-governance/checks');fs.mkdirSync(out,{recursive:true});fs.writeFileSync(path.join(out,'contract-policy-check.json'),JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result,null,2));if(errors.length)process.exitCode=1;

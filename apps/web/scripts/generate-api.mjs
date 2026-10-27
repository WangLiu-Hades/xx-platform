import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import crypto from 'node:crypto';
import YAML from 'yaml';
import openapiTS, { astToString } from 'openapi-typescript';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const file = path.resolve(root, '../../contracts/api/openapi.yaml');
const raw = await fs.readFile(file);
const doc = YAML.parse(raw.toString('utf8'));
const directory = path.join(root, 'src/api');
await fs.mkdir(directory, { recursive: true });
// OpenAPI default does not make an optional wire property required.
const ast = await openapiTS(doc, { alphabetize: true, exportType: true, immutable: false, defaultNonNullable: false });
await fs.writeFile(path.join(directory, 'schema.ts'), astToString(ast));
const ops = {};
for (const [url, item] of Object.entries(doc.paths)) {
  for (const [method, op] of Object.entries(item)) {
    if (!['get', 'post', 'put', 'patch', 'delete'].includes(method)) continue;
    const resolve = o => o?.$ref ? doc.components[o.$ref.split('/')[2]][o.$ref.split('/')[3]] : o;
    const requestBody = resolve(op.requestBody);
    const responses = Object.fromEntries(Object.entries(op.responses).map(([code,value]) => [code,resolve(value)]));
    ops[op.operationId] = { method: method.toUpperCase(), path: url, summary:op.summary, parameters:[...(item.parameters||[]),...(op.parameters||[])].map(resolve), requestBody, responses, permission:op['x-permission']||op['x-permission-code']||null, pending:op['x-design-status']==='pending', pendingReason:op['x-pending-reason']||null, designStatus:op['x-design-status']||'baseline', clientConfirmation:op['x-client-confirmation']||null, decisionIds:op['x-decision-ids']||[] };
  }
}
// Axios 路由只需轻量元数据；完整 Schema/样例留在延迟加载的 contract.json，避免首页重复加载。
const routes=Object.fromEntries(Object.entries(ops).map(([id,op])=>[id,{method:op.method,path:op.path,summary:op.summary,permission:op.permission,pending:op.pending,pendingReason:op.pendingReason,designStatus:op.designStatus,clientConfirmation:op.clientConfirmation,decisionIds:op.decisionIds}]));
await fs.writeFile(path.join(directory, 'operations.ts'), '/* Generated from the specified OpenAPI. Do not edit. */\nexport const operationCatalog = '+JSON.stringify(routes,null,2)+' as const;\n');
await fs.writeFile(path.join(directory, 'contract.json'), JSON.stringify({components:doc.components, operations:ops}));
function validationSchema(value){
  if(Array.isArray(value))return value.map(validationSchema);
  if(!value||typeof value!=='object')return value;
  const result={};
  for(const [key,entry] of Object.entries(value)){
    if(['description','example','examples','deprecated','readOnly','writeOnly'].includes(key)||key.startsWith('x-'))continue;
    result[key]=['properties','definitions','$defs'].includes(key)?Object.fromEntries(Object.entries(entry).map(([name,schema])=>[name,validationSchema(schema)])):validationSchema(entry);
  }
  return result;
}
const validationMedia=content=>Object.fromEntries(Object.entries(content||{}).filter(([,media])=>media.schema).map(([mime,media])=>[mime,{schema:validationSchema(media.schema)}]));
const validationOps=Object.fromEntries(Object.entries(ops).map(([id,op])=>[id,{parameters:op.parameters.map(p=>({name:p.name,in:p.in,required:p.required,schema:validationSchema(p.schema)})),...(op.requestBody?{requestBody:{required:op.requestBody.required,content:validationMedia(op.requestBody.content)}}:{}),responses:Object.fromEntries(Object.entries(op.responses).map(([status,response])=>[status,{content:validationMedia(response.content)}]))}]));
await fs.writeFile(path.join(directory,'validation-contract.json'),JSON.stringify({components:{schemas:Object.fromEntries(Object.entries(doc.components.schemas).map(([name,schema])=>[name,validationSchema(schema)]))},operations:validationOps}));
await fs.mkdir(path.join(root,'audit/vue-migration'),{recursive:true});
await fs.writeFile(path.join(root,'audit/vue-migration/openapi-operations.json'),JSON.stringify(ops,null,2));
await fs.writeFile(path.join(root,'audit/vue-migration/openapi-generation.json'),JSON.stringify({source:file,sha256:crypto.createHash('sha256').update(raw).digest('hex'),operations:Object.keys(ops).length,schemas:Object.keys(doc.components.schemas).length,generator:'openapi-typescript',note:'Generated operations/DTOs plus Axios typed facade; no backend implementation.',time:new Date().toISOString()},null,2));
console.log(JSON.stringify({operations:Object.keys(ops).length,schemas:Object.keys(doc.components.schemas).length}));
for(const [id, op] of Object.entries(ops)) console.log(id,op.method,op.path,op.requestBody?.content?.['application/json']?.schema?.$ref?.split('/').at(-1)||'');

import Ajv from 'ajv';
import addFormats from 'ajv-formats';
import contract from './validation-contract.json';
import type {OperationId} from './client';
type Schema = Record<string,unknown>;
type Media = {schema?:Schema};
type ContractOperation = {parameters:{name:string;in:string;required?:boolean;schema:Schema}[];requestBody?:{required?:boolean;content:Record<string,Media>};responses:Record<string,{content?:Record<string,Media>}>};
const document=contract as unknown as {components:{schemas:Record<string,Schema>};operations:Record<OperationId,ContractOperation>};
function convert(value:unknown):unknown{
 if(Array.isArray(value))return value.map(convert);
 if(!value||typeof value!=='object')return value;
 const result:Schema={};
 for(const [key,entry] of Object.entries(value)){
  if(['nullable','discriminator','example','examples','description','deprecated','readOnly','writeOnly'].includes(key)||key.startsWith('x-'))continue;
  if(['properties','definitions','$defs'].includes(key)&&entry&&typeof entry==='object')result[key]=Object.fromEntries(Object.entries(entry).map(([name,schema])=>[name,convert(schema)]));
  else result[key]=key==='$ref'&&typeof entry==='string'&&entry.startsWith('#/')?'https://contract.invalid/openapi'+entry:convert(entry);
 }
 return 'nullable' in value&&value.nullable===true?{anyOf:[result,{type:'null'}]}:result;
}
const ajv=new Ajv({strict:false,allErrors:true,validateFormats:true});addFormats(ajv);ajv.addSchema({$id:'https://contract.invalid/openapi',components:{schemas:Object.fromEntries(Object.entries(document.components.schemas).map(([name,schema])=>[name,convert(schema)]))}});
const validators=new Map<string,ReturnType<typeof ajv.compile>>();
function check(key:string,schema:Schema,value:unknown){let validator=validators.get(key);if(!validator){validator=ajv.compile({$id:'https://contract.invalid/validators/'+key,...convert(schema) as Schema});validators.set(key,validator);}if(!validator(value)){const detail=ajv.errorsText(validator.errors,{separator:'；'});throw new Error('接口结构校验失败：'+detail);}}
export function validateRequest(id:OperationId,path:unknown,query:unknown,body:unknown){
 const op=document.operations[id];for(const place of ['path','query'] as const){const params=op.parameters.filter(p=>p.in===place);const schema={type:'object',properties:Object.fromEntries(params.map(p=>[p.name,p.schema])),required:params.filter(p=>p.required).map(p=>p.name),additionalProperties:false};check(id+'/'+place,schema,path===undefined&&place==='path'||query===undefined&&place==='query'?{}:place==='path'?path:query);}
 const schema=op.requestBody?.content['application/json']?.schema;
 if(schema){if(body===undefined&&!op.requestBody?.required)return;check(id+'/body',schema,body);}
}
export function validateResponse(id:OperationId,status:number,value:unknown){const schema=document.operations[id].responses[String(status)]?.content?.['application/json']?.schema;if(schema)check(id+'/response/'+status,schema,value);}

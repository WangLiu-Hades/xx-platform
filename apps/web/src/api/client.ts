import axios from 'axios';
import type {AxiosAdapter, AxiosRequestConfig} from 'axios';
import type {operations} from './schema';
import {operationCatalog} from './operations';
export type OperationId = keyof operations;
type Parameters<I extends OperationId> = operations[I] extends {parameters:infer P}?P:never;
type Part<P,K extends PropertyKey> = K extends keyof P ? Exclude<P[K],undefined> : Record<string,never>;
export type Body<I extends OperationId> = operations[I] extends {requestBody?:{content:infer C}} ? C extends {'application/json':infer B}?B:C extends {'multipart/form-data':infer B}?B:never:never;
type Responses<I extends OperationId> = operations[I] extends {responses:infer R}?R:never;
type Success<R> = R[Extract<keyof R,200|201|202|204>];
type Content<R> = R extends {content:{'application/json':infer C}} ? C:undefined;
export type Response<I extends OperationId> = Content<Success<Responses<I>>>;
export type Request<I extends OperationId> = {path?:Part<Parameters<I>,'path'>;query?:Part<Parameters<I>,'query'>;body?:Body<I>;etag?:string;signal?:AbortSignal;idempotencyKey?:string;approvalId?:string;upload?:I extends 'uploadFile'?File:never};
export const apiMode = import.meta.env.VITE_API_MODE === 'http' ? 'http' : 'mock';
export class ApiError extends Error {constructor(message:string,public code:string,public traceId:string,public status:number,public details:unknown=null){super(message);this.name='ApiError';}}
let csrfToken='';
let identityEpoch=0;
let onUnauthorized=()=>{};
const base=import.meta.env.VITE_API_BASE_URL||'/api/v1';
if(!base.startsWith('/') || base.startsWith('//'))throw new Error('API 地址须使用本站绝对路径，由代理连接后端');
export const http = axios.create({baseURL:base,withCredentials:true,timeout:20000,headers:{Accept:'application/json'},paramsSerializer:{indexes:null}});
export function setMockAdapter(adapter:AxiosAdapter){if(apiMode==='mock')http.defaults.adapter=adapter;}
export function invalidateRequests(){identityEpoch++;csrfToken='';}
export function clearIdentity(){invalidateRequests();onUnauthorized();}
export const isRequestCanceled=axios.isCancel;
export function bindUnauthorized(fn:()=>void){onUnauthorized=fn;}
export function setCsrfToken(value:string){csrfToken=value;}
/** 会话续期钩子：返回 true 表示已续期，调用方会重放原请求；由 stores/platform 绑定。 */
let renewSessionHook:(()=>Promise<boolean>)|null=null;
let renewal:Promise<boolean>|null=null;
export function bindSessionRenewal(fn:(()=>Promise<boolean>)|null){renewSessionHook=fn;renewal=null;}
async function renewOnce(){if(!renewSessionHook)return false;renewal??=renewSessionHook().finally(()=>{renewal=null;});return renewal;}
/** 把后端错误码翻译成可展示文案：数据范围、版本冲突、上下文过期、能力未开放等需要区别提示。 */
export function describeError(error:unknown):string{
 if(error instanceof ApiError){switch(error.code){
  case 'PERMISSION_DATA_SCOPE':return '超出数据范围：'+error.message;
  case 'PERMISSION_DENIED':return '无操作权限：'+error.message;
  case 'RESOURCE_VERSION_CONFLICT':return error.message+'（数据已被修改，请刷新后重试）';
  case 'CONTEXT_EXPIRED':return error.message+'（查询条件已过期，请重新查询）';
  case 'REQUIREMENT_PENDING':return '该能力尚未开放：'+error.message;
  case 'SYSTEM_UNAVAILABLE':return '服务暂不可用：'+error.message;
  case 'TASK_RESULT_NOT_READY':return '结果尚未就绪：'+error.message;
  default:return error.message;
 }}return error instanceof Error?error.message:String(error);
}
export async function request<I extends OperationId>(id:I,args:Request<I>={}):Promise<Response<I>>{return send(id,args,true);}
async function send<I extends OperationId>(id:I,args:Request<I>,allowRenewal:boolean):Promise<Response<I>>{
 const epoch=identityEpoch;
 const op=operationCatalog[id];const validator=await import('./validation');validator.validateRequest(id,args.path,args.query,args.body);let url:string=op.path;
 for(const [key,value] of Object.entries(args.path||{}))url=url.replace('{'+key+'}',encodeURIComponent(String(value)));
 if(url.includes('{'))throw new ApiError('缺少接口路径参数','CLIENT_VALIDATION','',0);
 const writing=!['GET','HEAD'].includes(op.method);
 const headers:Record<string,string>={};
 // 幂等键在续期重放时必须复用，否则同一次业务重试会被后端当成两次请求
 const idempotencyKey=args.idempotencyKey||crypto.randomUUID();
 if(writing){if(csrfToken)headers['X-CSRF-Token']=csrfToken;headers['Idempotency-Key']=idempotencyKey;}
 if(args.etag)headers['If-Match']=args.etag;
 if(args.approvalId)headers['X-Approval-Id']=args.approvalId;
 try{
   let data:unknown=args.body;
   if(id==='uploadFile'){if(!args.upload)throw new ApiError('请选择上传文件','CLIENT_VALIDATION','',0);const multipart=new FormData();multipart.append('category',String((args.body as Body<'uploadFile'>).category));multipart.append('file',args.upload,args.upload.name);data=multipart;}
   const config:AxiosRequestConfig={method:op.method,url,params:args.query,data,headers,signal:args.signal};
   if(epoch!==identityEpoch)throw new axios.CanceledError('会话已改变，取消旧请求');
   const result=await http.request(config);
   if(epoch!==identityEpoch)throw new axios.CanceledError('会话已改变，丢弃旧响应');
   if(result.status===204)return undefined as Response<I>;
   const envelope:unknown=result.data;
   if(!envelope||typeof envelope!=='object'||!('code' in envelope)||!('traceId' in envelope)||!('data' in envelope))throw new ApiError('接口响应不符合约定','INVALID_RESPONSE','',result.status);
   const e=envelope as {code:string;message:string;data:unknown;traceId:string;details?:unknown};
   if(e.code!=='0')throw new ApiError(e.message||'请求失败',e.code,e.traceId,result.status,e.details);
   validator.validateResponse(id,result.status,envelope);
   return envelope as Response<I>;
 }catch(error){
   if(epoch!==identityEpoch)throw new axios.CanceledError('会话已改变，丢弃旧响应');
   if(axios.isCancel(error))throw error;
   if(error instanceof ApiError)throw error;
   if(axios.isAxiosError(error)){
     const status=error.response?.status||0;const e=error.response?.data as {message?:string;code?:string;traceId?:string;details?:unknown}|undefined;
     // 401：先尝试静默续期一次并重放原请求，续期失败才让身份失效
     if(status===401&&allowRenewal&&id!=='login'&&id!=='renewSession'&&renewSessionHook){
       if(await renewOnce())return send(id,{...args,idempotencyKey},false);
     }
     if(status===401&&id!=='login')clearIdentity();
     throw new ApiError(e?.message||(error.code==='ECONNABORTED'?'请求超时，请重试':navigator.onLine?'后端连接失败':'网络已断开'),e?.code||error.code||'NETWORK_ERROR',e?.traceId||'',status,e?.details);
   }throw error;
 }
}
export async function refreshCsrf(){const r=await request('getCsrfToken');setCsrfToken(r.data.token);}
async function downloadBinary(id:'downloadReport'|'downloadFile',key:'reportId'|'fileAssetId',resourceId:string):Promise<Blob>{
 const epoch=identityEpoch;
 const path={[key]:resourceId};const validator=await import('./validation');validator.validateRequest(id,path,undefined,undefined);
 const url=operationCatalog[id].path.replace('{'+key+'}',encodeURIComponent(resourceId));
 try{const result=await http.get<Blob>(url,{responseType:'blob',headers:{Accept:'application/octet-stream'}});
 if(epoch!==identityEpoch)throw new axios.CanceledError('会话已改变，丢弃旧下载');
 if(result.data.size===0)throw new ApiError('下载文件为空','INVALID_FILE',String(result.headers['x-trace-id']||''),result.status);
 if(result.data.type.includes('json'))throw new ApiError('下载接口未返回文件流','INVALID_FILE',String(result.headers['x-trace-id']||''),result.status);
 return result.data;
 }catch(error){if(epoch!==identityEpoch)throw new axios.CanceledError('会话已改变，丢弃旧下载');if(axios.isCancel(error)||error instanceof ApiError)throw error;if(axios.isAxiosError(error)){if(error.response?.status===401)clearIdentity();throw new ApiError('文件下载失败',error.code||'NETWORK_ERROR',String(error.response?.headers?.['x-trace-id']||''),error.response?.status||0);}throw error;}
}
export const downloadReportBinary=(reportId:string)=>downloadBinary('downloadReport','reportId',reportId);
export const downloadFileBinary=(fileAssetId:string)=>downloadBinary('downloadFile','fileAssetId',fileAssetId);

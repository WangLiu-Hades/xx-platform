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
export type Request<I extends OperationId> = {path?:Part<Parameters<I>,'path'>;query?:Part<Parameters<I>,'query'>;body?:Body<I>;etag?:string;signal?:AbortSignal;idempotencyKey?:string;upload?:I extends 'uploadFile'?File:never};
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
export async function request<I extends OperationId>(id:I,args:Request<I>={}):Promise<Response<I>>{
 const epoch=identityEpoch;
 const op=operationCatalog[id];const validator=await import('./validation');validator.validateRequest(id,args.path,args.query,args.body);let url:string=op.path;
 for(const [key,value] of Object.entries(args.path||{}))url=url.replace('{'+key+'}',encodeURIComponent(String(value)));
 if(url.includes('{'))throw new ApiError('缺少接口路径参数','CLIENT_VALIDATION','',0);
 const writing=!['GET','HEAD'].includes(op.method);
 const headers:Record<string,string>={};
 if(writing){if(csrfToken)headers['X-CSRF-Token']=csrfToken;headers['Idempotency-Key']=args.idempotencyKey||crypto.randomUUID();}
 if(args.etag)headers['If-Match']=args.etag;
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
     if(status===401)clearIdentity();
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

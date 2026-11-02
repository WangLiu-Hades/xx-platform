import {beforeEach,it,expect,vi} from 'vitest';
import type {AxiosResponse,InternalAxiosRequestConfig} from 'axios';
import axios from 'axios';
import contract from '../src/api/contract.json';
import {http,request,invalidateRequests,isRequestCanceled,bindUnauthorized,bindSessionRenewal,setCsrfToken} from '../src/api/client';
const csrf=contract.operations.getCsrfToken.responses['200'].content['application/json'].examples.success.value;
const response=(config:InternalAxiosRequestConfig,data:unknown):AxiosResponse=>({config,data,status:200,statusText:'OK',headers:{}});
beforeEach(()=>{invalidateRequests();bindUnauthorized(()=>{});bindSessionRenewal(null);});
it('discards a response from the previous identity after logout',async()=>{
 let finish:(data:AxiosResponse)=>void=()=>{},config:InternalAxiosRequestConfig|undefined;
 http.defaults.adapter=c=>{config=c;return new Promise(resolve=>{finish=resolve;});};
 const old=request('getCsrfToken');await vi.waitFor(()=>expect(config).toBeDefined());invalidateRequests();finish(response(config!,csrf));await expect(old).rejects.toSatisfy(isRequestCanceled);
});
it('a stale 401 cannot log out the new identity',async()=>{
 const unauthorized=vi.fn();bindUnauthorized(unauthorized);let fail:(e:unknown)=>void=()=>{},config:InternalAxiosRequestConfig|undefined;
 http.defaults.adapter=c=>{config=c;return new Promise((_resolve,reject)=>{fail=reject;});};const old=request('getCsrfToken');await vi.waitFor(()=>expect(config).toBeDefined());invalidateRequests();fail(new axios.AxiosError('old session expired','ERR_BAD_REQUEST',config,undefined,{...response(config!,null),status:401}));await expect(old).rejects.toSatisfy(isRequestCanceled);expect(unauthorized).not.toHaveBeenCalled();
});
it('mutations send the configured CSRF, caller idempotency key and resource version',async()=>{
 let captured:InternalAxiosRequestConfig|undefined;setCsrfToken('0123456789abcdef0123456789abcdef');http.defaults.adapter=async c=>{captured=c;return response(c,contract.operations.updateSite.responses['200'].content['application/json'].examples.success.value);};
 await request('updateSite',{path:{siteId:'00000000-0000-4000-8000-000000000001'},body:{name:'示例场所',regionCode:'MOCK',enabled:true},etag:'"4"',idempotencyKey:'00000000-0000-4000-8000-000000000002'});
 expect(captured!.headers.get('X-CSRF-Token')).toBe('0123456789abcdef0123456789abcdef');expect(captured!.headers.get('If-Match')).toBe('"4"');expect(captured!.headers.get('Idempotency-Key')).toBe('00000000-0000-4000-8000-000000000002');
});
it('a failed login reports the credential error instead of expiring the session',async()=>{
  const unauthorized=vi.fn();bindUnauthorized(unauthorized);let config:InternalAxiosRequestConfig|undefined;
  http.defaults.adapter=c=>{config=c;return Promise.reject(new axios.AxiosError('Request failed with status code 401','ERR_BAD_REQUEST',c,undefined,{...response(c,{code:'AUTH_INVALID_CREDENTIALS',message:'账号或密码错误',traceId:'t'}),status:401}));};
  await expect(request('login',{body:{loginName:'stub.admin',password:'wrong'}})).rejects.toThrow('账号或密码错误');
  expect(unauthorized).not.toHaveBeenCalled();
});

it('renews the session once and replays the original request on 401',async()=>{
  const renew=vi.fn().mockResolvedValue(true);bindSessionRenewal(renew);
  const unauthorized=vi.fn();bindUnauthorized(unauthorized);
  const body={dataType:'FENCE' as const,timeRange:{from:'2026-10-07T00:00:00Z',to:'2026-10-07T02:00:00Z'}};
  let attempts=0;
  http.defaults.adapter=async c=>{attempts++;if(attempts===1)throw new axios.AxiosError('expired','ERR_BAD_REQUEST',c,undefined,{...response(c,{code:'AUTH_SESSION_EXPIRED',message:'会话已过期',traceId:'t'}),status:401});return response(c,contract.operations.searchCaptures.responses['200'].content['application/json'].examples.success.value);};
  const result=await request('searchCaptures',{body});
  expect(renew).toHaveBeenCalledTimes(1);
  expect(attempts).toBe(2);
  expect(unauthorized).not.toHaveBeenCalled();
  expect(result.data.items.length).toBeGreaterThan(0);
});
it('only expires the session when renewal also fails',async()=>{
  const renew=vi.fn().mockResolvedValue(false);bindSessionRenewal(renew);
  const unauthorized=vi.fn();bindUnauthorized(unauthorized);
  http.defaults.adapter=async c=>{throw new axios.AxiosError('expired','ERR_BAD_REQUEST',c,undefined,{...response(c,{code:'AUTH_SESSION_EXPIRED',message:'会话已过期',traceId:'t'}),status:401});};
  await expect(request('getMe')).rejects.toThrow('会话已过期');
  expect(renew).toHaveBeenCalledTimes(1);
  expect(unauthorized).toHaveBeenCalledTimes(1);
});
it('sends the approval as a header without adding it to the frozen proposal body',async()=>{
 let captured:InternalAxiosRequestConfig|undefined;
 http.defaults.adapter=async c=>{captured=c;return response(c,contract.operations.readDecryptionResult.responses['200'].content['application/json'].examples.success.value);};
 await request('readDecryptionResult',{path:{requestId:'00000000-0000-4000-8000-000000000001'},approvalId:'00000000-0000-4000-8000-000000000002'});
 expect(captured!.headers.get('X-Approval-Id')).toBe('00000000-0000-4000-8000-000000000002');
 expect(captured!.data).toBeUndefined();
});

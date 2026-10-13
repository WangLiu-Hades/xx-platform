import {beforeEach,it,expect,vi} from 'vitest';
import type {AxiosResponse,InternalAxiosRequestConfig} from 'axios';
import axios from 'axios';
import contract from '../src/api/contract.json';
import {http,request,invalidateRequests,isRequestCanceled,bindUnauthorized,setCsrfToken} from '../src/api/client';
const csrf=contract.operations.getCsrfToken.responses['200'].content['application/json'].examples.success.value;
const response=(config:InternalAxiosRequestConfig,data:unknown):AxiosResponse=>({config,data,status:200,statusText:'OK',headers:{}});
beforeEach(()=>{invalidateRequests();bindUnauthorized(()=>{});});
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

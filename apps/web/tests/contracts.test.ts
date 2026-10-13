import {describe,it,expect} from 'vitest';
import contract from '../src/api/contract.json';
import {validateRequest,validateResponse} from '../src/api/validation';
import type {OperationId} from '../src/api/client';
describe('specified OpenAPI contract',()=>{
 for(const [id,op] of Object.entries(contract.operations)){
  for(const [status,response] of Object.entries(op.responses)){
   const media=(response as {content?:Record<string,{examples?:Record<string,{value:unknown}>}>}).content?.['application/json'];
   for(const [name,example] of Object.entries(media?.examples||{}))it(id+' '+status+' '+name,()=>validateResponse(id as OperationId,Number(status),example.value));
  }
 }
 it('rejects a business number in UUID path',()=>expect(()=>validateRequest('getDevice',{deviceId:'MOCK-1001'},{},undefined)).toThrow());
 it('rejects unknown capture filter fields',()=>expect(()=>validateRequest('searchCaptures',{}, {},{dataType:'PLATE',timeRange:{from:'2026-10-07T00:00:00Z',to:'2026-10-08T00:00:00Z'},imsi:'460000000000001'})).toThrow());
 it('rejects a missing required body',()=>expect(()=>validateRequest('login',{}, {},undefined)).toThrow());
 it('rejects malformed response instead of silently using fixtures',()=>expect(()=>validateResponse('getMe',200,{code:'0',data:{},traceId:'x'})).toThrow());
});

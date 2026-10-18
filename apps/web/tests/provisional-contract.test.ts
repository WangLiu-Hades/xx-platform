import {expect,it} from 'vitest';
import {validateRequest} from '../src/api/validation';
const id='00000000-0000-4000-8000-000000000001';
it('accepts the typed decryption proposal and rejects arbitrary operations or payload fields',()=>{
 const proposal={operationId:'readDecryptionResult',pathParameters:{requestId:id}};
 validateRequest('createActionApproval',{}, {},{proposal,reason:'独立复核合成测试用途说明'});
 expect(()=>validateRequest('createActionApproval',{}, {},{proposal:{...proposal,operationId:'runArbitrarySql'},reason:'独立复核合成测试用途说明'})).toThrow();
 expect(()=>validateRequest('createActionApproval',{}, {},{proposal:{...proposal,sql:'SELECT 1'},reason:'独立复核合成测试用途说明'})).toThrow();
});
it('requires reauthentication for an approval decision',()=>{
 expect(()=>validateRequest('decideActionApproval',{approvalId:id},{},{decision:'APPROVE',reason:'独立复核合成测试用途说明'})).toThrow();
});
it('rejects an undersized new password without preventing old-password login',()=>{
 expect(()=>validateRequest('changeMyPassword',{}, {},{currentPassword:'old-pass',newPassword:'short-pass'})).toThrow();
 validateRequest('changeMyPassword',{}, {},{currentPassword:'old-pass',newPassword:'Example-password-2026!'});
 validateRequest('login',{}, {},{loginName:'synthetic-test',password:'old-pass'});
});
it('bounds face candidates and decryption requests rather than silently truncating',()=>{
 const face={imageAssetId:id,timeRange:{from:'2026-10-17T00:00:00Z',to:'2026-10-18T00:00:00Z'},candidateLimit:100};
 validateRequest('createFaceSearchTask',{}, {},face);
 expect(()=>validateRequest('createFaceSearchTask',{}, {},{...face,candidateLimit:101})).toThrow();
 const captureIds=Array.from({length:201},(_,n)=>`00000000-0000-4000-8000-${String(n+1).padStart(12,'0')}`);
 validateRequest('requestDecryption',{}, {},{contextId:id,purpose:'独立复核合成测试用途说明',captureIds:captureIds.slice(0,200)});
 expect(()=>validateRequest('requestDecryption',{}, {},{contextId:id,purpose:'独立复核合成测试用途说明',captureIds})).toThrow();
});
it('bounds map point counts and zoom before sending a request',()=>{
 const query={bbox:'116,39,117,40',zoom:12,limit:2000};
 validateRequest('getMapFeatures',{},query,undefined);
 expect(()=>validateRequest('getMapFeatures',{}, {...query,limit:2001},undefined)).toThrow();
 expect(()=>validateRequest('getMapFeatures',{}, {...query,zoom:2},undefined)).toThrow();
});

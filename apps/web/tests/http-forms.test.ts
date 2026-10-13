import {beforeEach,describe,it,expect,vi} from 'vitest';
import contract from '../src/api/contract.json';
import {validateRequest} from '../src/api/validation';
import type {OperationId,Request} from '../src/api/client';
import type {Field,Database,Capture} from '../src/domain';
const mock=vi.hoisted(()=>({request:vi.fn()}));
vi.mock('../src/api/client',async importOriginal=>({...await importOriginal<typeof import('../src/api/client')>(),request:mock.request}));
import {remote,resetRemote} from '../src/api/gateway';
import {openNotification,openWatch,openModel,openConfig,openAnalysis} from '../src/api/httpForms';
import type {FormHost} from '../src/api/httpForms';
import {submitRemote,deviceChanges,modelParameters,createContext} from '../src/api/mutations';
import type {RemoteIntent} from '../src/api/mutations';

const uuid=(n=1)=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
function example(id:OperationId):any{const op=contract.operations[id];const response=Object.entries(op.responses).find(([code])=>Number(code)>=200&&Number(code)<300)?.[1] as any;return structuredClone(response.content['application/json'].examples.success.value);}
const empty=():Database=>({sites:[],devices:[],captures:[],people:[],analysis:{},libraries:[],datasets:[],watches:[],configs:[],reports:[],notifications:[],models:[],roles:[],users:[],logs:[],sms:[],messages:[]});
function host(){const state={title:'',fields:[] as Field[],model:{} as Record<string,unknown>,intent:undefined as RemoteIntent|undefined,save:undefined as ((m:Record<string,unknown>)=>void|Promise<void>)|undefined,step:-1};const api:FormHost={open:(title,fields,model,save)=>Object.assign(state,{title,fields,model,save,intent:undefined}),bind:intent=>{state.intent=intent;},step:step=>{state.step=step;}};return {state,api};}
beforeEach(()=>{
 resetRemote();mock.request.mockReset();remote.me=example('getMe').data;remote.config=example('getClientConfig').data;remote.config!.features=(['NOTIFICATION_SEND','WATCH_ACTIVATION','DEVICE_COMMAND','RESIDENCE_MODEL'] as const).map(featureCode=>({featureCode,enabled:true,pendingReason:null}));
 remote.config!.features.push(...(['COMPANION_ANALYSIS','COLLISION_ANALYSIS','REGION_STATISTICS'] as const).map(featureCode=>({featureCode,enabled:true,pendingReason:null})));
 mock.request.mockImplementation(async(id:OperationId,args:Request<OperationId>={})=>{validateRequest(id,args.path,args.query,args.body);const value=example(id);if(id==='listNotificationTemplates')value.data.items=[{...value.data.items[0],templateCode:'DEVICE_STATUS_V1',purpose:'DEVICE_STATUS',channels:['SMS','EMAIL']},{...value.data.items[0],templateCode:'WATCH_ALERT_V1',purpose:'WATCH_ALERT',channels:['SMS','EMAIL']}];return value;});
});
describe('HTTP form integration boundaries',()=>{
 it.each([['围栏检索','M05'],['车牌检索','M05'],['MAC检索','M05'],['人像检索','M05'],['地图研判','M07'],['伴随任务','M08'],['碰撞任务','M08'],['区域统计','M08'],['数据运算','M09'],['综合布控','M10'],['图码档案','M11'],['车码档案','M11'],['数据态势','M11'],['设备分布','M13']])('context from %s uses the current engineering module %s',async(page,module)=>{
  await createContext(page,{start:'2026-10-07T10:00',end:'2026-10-08T10:00'},[],false,empty());expect(mock.request.mock.calls.find(c=>c[0]==='createContext')![1].body.sourceModule).toBe(module);
 });
 it('[PDF-191,PDF-192,PDF-193] notification form obtains approved templates and submits a valid daily plan',async()=>{
  const h=host();await openNotification(h.api);expect(h.state.intent?.kind).toBe('notification');expect(h.state.fields.find(f=>f.key==='channel')?.options).toEqual(['短信','邮件']);
  Object.assign(h.state.model,{name:'通知测试',templateCode:'DEVICE_STATUS_V1',recipient:'+8613800000000'});await submitRemote(h.state.intent!,h.state.model,empty(),'运维通知',{});
  const call=mock.request.mock.calls.find(c=>c[0]==='createNotificationTask')!;expect(call[1].body.targets).toEqual([{channel:'SMS',address:'+8613800000000'}]);expect(call[1].body.platformUserIds).toEqual([remote.me!.user.userId]);expect(call[1].body.schedule.timeZone).toBe('Asia/Shanghai');
 });
 it('does not resend a masked address while editing a notification',async()=>{
  const h=host();await openNotification(h.api,uuid());expect(h.state.model.recipient).toBe('');expect(h.state.fields.find(f=>f.key==='recipient')?.label).toContain('完整地址');
  await expect(submitRemote(h.state.intent!,{...h.state.model,recipient:'138****0000',templateCode:'DEVICE_STATUS_V1'},empty(),'运维通知',{})).rejects.toThrow('电话号码');expect(mock.request.mock.calls.some(c=>c[0]==='updateNotificationTask')).toBe(false);
 });
 it('rejects a channel/template mismatch before submitting a notification',async()=>{
  remote.templates=[{...example('listNotificationTemplates').data.items[0],templateCode:'sms',purpose:'DEVICE_STATUS',channels:['SMS']}];await expect(submitRemote({kind:'notification'},{channel:'邮件',recipient:'test@example.com',templateCode:'sms',platformUserIds:[uuid()]},empty(),'运维通知',{})).rejects.toThrow('通知模板');
 });
 it('a pending service blocks submission without a local success or remote mutation',async()=>{
  remote.config!.features[0]!.enabled=false;await expect(submitRemote({kind:'notification'},{},empty(),'运维通知',{})).rejects.toThrow();expect(mock.request).not.toHaveBeenCalled();
 });
 it('[PDF-114,PDF-115] watch handoff retains proxy target IDs and the explicit site selection',async()=>{
  const db=empty(),h=host(),capture:Capture={id:uuid(2),target:'460***001',targetId:uuid(3),targetType:'IMSI',type:'电围',time:'2026-10-07 10:00:00',site:'mock-example',device:'',deviceId:uuid(),region:'',imei:'',person:'',score:0};
  await openWatch(h.api,db,undefined,[capture]);Object.assign(h.state.model,{name:'布控测试',start:'2026-10-07T10:00',end:'2026-10-08T10:00',siteIds:[uuid()],templateCode:'WATCH_ALERT_V1',recipient:'13800000000'});
  const create=example('createContext');mock.request.mockImplementation(async(id:OperationId,args:any={})=>{validateRequest(id,args.path,args.query,args.body);if(id==='createContext'){create.data.targets=args.body.targets;return create;}return example(id);});
  await submitRemote(h.state.intent!,h.state.model,db,'围栏检索',{});const call=mock.request.mock.calls.find(c=>c[0]==='createWatchRule')!;expect(call[1].body.conditions.targets[0].target.targetId).toBe(uuid(3));expect(call[1].body.conditions.siteIds).toEqual([uuid()]);expect(mock.request.mock.calls.some(c=>c[0]==='resolveTargets')).toBe(false);
 });
 it('[PDF-118] editing a watch retains authorized targets instead of resolving display text',async()=>{
  const db=empty(),h=host();await openWatch(h.api,db,uuid());Object.assign(h.state.model,{start:'2026-10-07T10:00',end:'2026-10-08T10:00',siteIds:[uuid()],templateCode:'WATCH_ALERT_V1',recipient:'13800000000'});const targets=structuredClone(h.state.intent!.watchRule!.conditions.targets);
  await submitRemote(h.state.intent!,h.state.model,db,'综合布控',{});const call=mock.request.mock.calls.find(c=>c[0]==='updateWatchRule')!;expect(call[1].body.conditions.targets).toEqual(targets);expect(call[1].etag).toMatch(/^"\d+"$/);expect(mock.request.mock.calls.some(c=>c[0]==='resolveTargets')).toBe(false);
 });
 it('[PDF-169,PDF-170,PDF-171] configuration wizard submits only shared writable manufacturer parameters',async()=>{
  const h=host(),db=empty();const fallback=mock.request.getMockImplementation()!;
  mock.request.mockImplementation(async(id:OperationId,args:any={})=>{const value=await fallback(id,args);if(id==='getDeviceParameters')value.data.items=[{parameterCode:'INTERVAL',label:'采集间隔',valueType:'INTEGER',integerValue:60,readOnly:false,updatedAt:'2026-10-07T02:00:00Z'},{parameterCode:'SERIAL',label:'序列号',valueType:'TEXT',textValue:'secret',readOnly:true,updatedAt:'2026-10-07T02:00:00Z'}];return value;});
  await openConfig(h.api,db);expect(h.state.step).toBe(0);await h.state.save!({...h.state.model,name:'参数配置',region:db.sites[0]!.region,groupId:uuid()});expect(h.state.step).toBe(1);await h.state.save!({...h.state.model,deviceIds:[uuid()]});expect(h.state.step).toBe(2);expect(h.state.fields.map(f=>f.key)).toEqual(['parameter_INTERVAL']);
  await submitRemote(h.state.intent!,{...h.state.model,parameter_INTERVAL:30},db,'参数配置',{});const call=mock.request.mock.calls.find(c=>c[0]==='createDeviceCommand')!;expect(call[1].body.parameterChanges).toEqual([{parameterCode:'INTERVAL',valueType:'INTEGER',integerValue:30}]);
 });
 it('rejects a device group outside the selected region before loading devices',async()=>{
  const h=host();await openConfig(h.api,empty());await expect(h.state.save!({...h.state.model,name:'配置',region:'其他区域',groupId:uuid()})).rejects.toThrow('不属于该区域');expect(mock.request.mock.calls.some(c=>c[0]==='listDeviceGroupMembers')).toBe(false);
 });
 it('[PDF-173] editing configuration uses the revision endpoint and current resource version',async()=>{
  remote.versions[uuid()]=3;await submitRemote({kind:'config',id:uuid(),parameterItems:[{parameterCode:'INTERVAL',label:'采集间隔',valueType:'INTEGER',integerValue:60,readOnly:false,updatedAt:'2026-10-07T02:00:00Z'}]},{name:'配置修订',region:'MOCK',groupId:uuid(),deviceIds:[uuid()],parameter_INTERVAL:30},empty(),'参数配置',{});
  const call=mock.request.mock.calls.find(c=>c[0]==='reviseDeviceCommand')!;expect(call[1].path.taskId).toBe(uuid());expect(call[1].etag).toBe('"3"');expect(mock.request.mock.calls.some(c=>c[0]==='createDeviceCommand')).toBe(false);
 });
 it('[PDF-130,PDF-132] model metadata preserves valid-only filters and parameter values zero/false',async()=>{
  const db=empty(),h=host(),fallback=mock.request.getMockImplementation()!;
  mock.request.mockImplementation(async(id:OperationId,args:any={})=>{const value=await fallback(id,args);if(id==='getTrendModel')value.data.parameterDefinitions=[{parameterCode:'COUNT',label:'最少次数',valueType:'INTEGER',required:true,minimum:0,maximum:100,description:''},{parameterCode:'SWITCH',label:'开关',valueType:'BOOLEAN',required:true,description:''}];return value;});
  await openModel(h.api,db,uuid());await submitRemote(h.state.intent!,{...h.state.model,label:'常驻',parameter_COUNT:0,parameter_SWITCH:false},db,'数据态势',{});const metadata=mock.request.mock.calls.find(c=>c[0]==='updateTrendModel')!;expect(metadata[1].body.effectiveDataFilter.captureStatuses).toEqual(['VALID']);const params=mock.request.mock.calls.find(c=>c[0]==='createModelParameterVersion')![1].body.parameters;expect(params).toContainEqual({parameterCode:'COUNT',valueType:'INTEGER',integerValue:0});expect(params).toContainEqual({parameterCode:'SWITCH',valueType:'BOOLEAN',booleanValue:false});
 });
 it('rejects fractional integer device values and out-of-range model parameters',()=>{
  expect(()=>deviceChanges([{parameterCode:'COUNT',label:'次数',valueType:'INTEGER',integerValue:1,readOnly:false,updatedAt:'2026-10-07T02:00:00Z'}],{parameter_COUNT:1.5})).toThrow('整数');const model=example('getTrendModel').data;model.parameterDefinitions=[{parameterCode:'DAYS',label:'天数',valueType:'INTEGER',minimum:1,maximum:365,required:true,description:''}];expect(()=>modelParameters(model,{parameter_DAYS:366})).toThrow('范围');
 });
 it('[PDF-079,PDF-082] companion editing retains the server target and parameters without resolving masked values',async()=>{
  const h=host();const old=example('getAnalysisTask').data;expect(old.kind).toBe('COMPANION');await openAnalysis(h.api,empty(),'伴随任务',uuid());expect(h.state.model.timeWindowSeconds).toBe(old.parameters.timeWindowSeconds);expect(h.state.model.target).toBe('保留已授权目标');await submitRemote(h.state.intent!,{...h.state.model,name:'伴随修订'},empty(),'伴随任务',{});
  const call=mock.request.mock.calls.find(c=>c[0]==='reviseAnalysisTask')!;expect(call[1].body.parameters.target).toEqual(old.parameters.target);expect(call[1].etag).toMatch(/^"\d+"$/);expect(mock.request.mock.calls.some(c=>c[0]==='resolveTargets'||c[0]==='createContext')).toBe(false);
 });
 it('[PDF-088,PDF-091] collision input uses the selected rule and requires multiple resolved target IDs',async()=>{
  const h=host(),fallback=mock.request.getMockImplementation()!;mock.request.mockImplementation(async(id:OperationId,args:any={})=>{const value=await fallback(id,args);if(id==='resolveTargets')value.data.items=[{...value.data.items[0],target:{targetId:uuid(2),targetType:'IMSI'}},{...value.data.items[0],target:{targetId:uuid(3),targetType:'IMSI'}}];if(id==='createContext')value.data.targets=args.body.targets;return value;});
  await openAnalysis(h.api,empty(),'碰撞任务');await submitRemote(h.state.intent!,{...h.state.model,name:'碰撞测试',target:'460000000000001,460000000000002',ruleCode:'APPROVED_RULE_V1'},empty(),'碰撞任务',{});const call=mock.request.mock.calls.find(c=>c[0]==='createAnalysisTask')!;expect(call[1].body.parameters.ruleCode).toBe('APPROVED_RULE_V1');expect(call[1].body.parameters.targets.map((t:any)=>t.targetId)).toEqual([uuid(2),uuid(3)]);expect(mock.request.mock.calls.find(c=>c[0]==='createContext')![1].body.sourceModule).toBe('M08');
 });
 it('[PDF-096,PDF-100] region analysis sends WGS84 polygons and an AREA context with no manual target resolution',async()=>{
  const h=host();await openAnalysis(h.api,empty(),'区域统计');const polygon={type:'Polygon',coordinates:[[[116,39],[117,39],[117,40],[116,40],[116,39]]]};await submitRemote(h.state.intent!,{...h.state.model,name:'选区统计',polygons:[polygon]},empty(),'区域统计',{});const context=mock.request.mock.calls.find(c=>c[0]==='createContext')![1].body;expect(context.targets).toEqual([]);expect(context.spatialRange).toEqual({selectionType:'AREA',coordinateSystem:'EPSG:4326',geometry:polygon});const task=mock.request.mock.calls.find(c=>c[0]==='createAnalysisTask')![1].body;expect(task.parameters.regions).toEqual([polygon]);expect(task.parameters.dataTypes).toEqual(['FENCE','PLATE','MAC']);expect(mock.request.mock.calls.some(c=>c[0]==='resolveTargets')).toBe(false);
 });
 it('region selection cannot be submitted empty, and companion windows cannot be fractional or negative',async()=>{
  const h=host();await openAnalysis(h.api,empty(),'区域统计');await expect(submitRemote(h.state.intent!,{...h.state.model,name:'空选区'},empty(),'区域统计',{})).rejects.toThrow('框选');expect(mock.request.mock.calls.some(c=>c[0]==='createContext')).toBe(false);await expect(submitRemote({kind:'analysis',analysisKind:'伴随任务'},{start:'2026-10-07T10:00',end:'2026-10-08T10:00',timeWindowSeconds:1.5,spatialWindowMeters:0},empty(),'伴随任务',{})).rejects.toThrow('窗口');
 });
});

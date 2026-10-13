import {request} from './client';
import type {Body,Response} from './client';
import {remote,feature,etag,codes,analysisKinds,reportCodes,timeRange,value,ids,searchInput} from './gateway';
import type {components} from './schema';
import type {Database,Filter,Capture} from '../domain';
import {iso} from '../domain';
type S=components['schemas'];
export type RemoteIntent={kind:'site'|'device'|'library'|'person'|'role'|'user'|'watch'|'analysis'|'report'|'notification'|'config'|'parameter'|'dataset'|'model'|'password'|'decrypt';id?:string;libraryId?:string;analysisKind?:string;rows?:Capture[];watchRule?:Response<'getWatchRule'>['data'];analysis?:Response<'getAnalysisTask'>['data'];model?:Response<'getTrendModel'>['data'];sourceAnalysis?:{analysisTaskId:string;resultIds?:string[]};parameterItems?:Response<'getDeviceParameters'>['data']['items']};
const deletionTypes={sites:'SITE',devices:'DEVICE',libraries:'PERSON_LIBRARY',datasets:'DATASET',watches:'WATCH_RULE',configs:'DEVICE_COMMAND',reports:'REPORT',notifications:'NOTIFICATION_TASK',roles:'ROLE',users:'USER'} as const;
export async function deleteRemote(scope:keyof typeof deletionTypes|'analysis',resourceIds:string[]){
 const resourceType=scope==='analysis'?'ANALYSIS_TASK':deletionTypes[scope];
 const items=resourceIds.map(resourceId=>{const version=remote.versions[resourceId];if(version===undefined)throw new Error('部分记录缺少版本，请刷新列表或详情');return {resourceId,version};});
 const r=await request('deleteResourcesBatch',{body:{resourceType,items,reason:'用户在前端删除所选记录'}});
 return r.data;
}
export async function revokeRemote(scope:'watches'|'notifications',resourceIds:string[]){
 const items=resourceIds.map(resourceId=>{const version=remote.versions[resourceId];if(version===undefined)throw new Error('部分任务缺少版本，请刷新列表或详情');return {resourceId,version};});
 const r=await request('revokeResourcesBatch',{body:{resourceType:scope==='watches'?'WATCH_RULE':'NOTIFICATION_TASK',items,reason:'用户在前端撤销所选任务'}});
 return r.data;
}
type Form=Record<string,unknown>;
const text=(m:Form,key:string)=>String(m[key]??'').trim();
const list=(m:Form,key:string)=>Array.isArray(m[key])?(m[key] as unknown[]).map(String):[];
const reason=(m:Form)=>text(m,'reason')||'前端平台业务操作';
function validateRecipient(m:Form,purpose:'WATCH_ALERT'|'DEVICE_STATUS'){
 const channel=text(m,'channel')==='短信'?'SMS':'EMAIL',address=text(m,'recipient');
 if(channel==='SMS'&&!/^\+?\d{7,15}$/.test(address))throw new Error('请输入完整、有效的电话号码');
 if(channel==='EMAIL'&&!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(address))throw new Error('请输入完整、有效的邮件地址');
 const template=remote.templates.find(t=>t.templateCode===text(m,'templateCode'));
 if(!template?.enabled||template.purpose!==purpose||!template.channels.includes(channel))throw new Error('通知模板不支持当前通知方式，请重新选择');
 if(!list(m,'platformUserIds').length)throw new Error('请选择平台消息接收人');
 return {channel,address} as S['NotificationTarget'];
}
export function deviceChanges(items:Response<'getDeviceParameters'>['data']['items'],m:Form):S['DeviceParameterChange'][] {
 return items.filter(item=>!item.readOnly).map(item=>{const value=m['parameter_'+item.parameterCode];if(item.valueType==='INTEGER'){if(!Number.isInteger(Number(value)))throw new Error(item.label+'必须为整数');return {parameterCode:item.parameterCode,valueType:'INTEGER',integerValue:Number(value)};}if(item.valueType==='NUMBER'){if(!Number.isFinite(Number(value)))throw new Error(item.label+'必须为数字');return {parameterCode:item.parameterCode,valueType:'NUMBER',numberValue:Number(value)};}if(item.valueType==='BOOLEAN')return {parameterCode:item.parameterCode,valueType:'BOOLEAN',booleanValue:!!value};return {parameterCode:item.parameterCode,valueType:'TEXT',textValue:String(value??'')};});
}
export function modelParameters(model:Response<'getTrendModel'>['data'],m:Form):S['ModelParameterValue'][] {
 return model.parameterDefinitions.filter(d=>d.required||m['parameter_'+d.parameterCode]!==undefined).map(d=>{const value=m['parameter_'+d.parameterCode],base={parameterCode:d.parameterCode};if(d.valueType==='TEXT')return {...base,valueType:'TEXT',textValue:String(value??'')};if(d.valueType==='BOOLEAN')return {...base,valueType:'BOOLEAN',booleanValue:!!value};const number=Number(value);if(!Number.isFinite(number)||number<(d.minimum??-Infinity)||number>(d.maximum??Infinity))throw new Error(d.label+'超出允许范围');if(d.valueType==='INTEGER'||d.valueType==='DURATION_SECONDS'){if(!Number.isInteger(number))throw new Error(d.label+'必须为整数');return {...base,valueType:d.valueType,integerValue:number};}return {...base,valueType:d.valueType,numberValue:number};});
}
const scope=()=>{if(!remote.me)throw new Error('尚未加载用户授权范围');return {siteScope:remote.me.siteScope,dataScope:remote.me.dataScope};};
export function targetType(type:string):S['TargetType']{return type==='电围'?'IMSI':type==='车牌'?'PLATE':type==='MAC'?'MAC':'FACE';}
export async function resolveTargets(type:string,targets:string[],registerMissing=false){const r=await request('resolveTargets',{body:{items:targets.map(value=>({targetType:targetType(type),value})),registerMissing,purpose:'前端分析或布控选择'}});if(r.data.items.some(item=>!item.target))throw new Error('部分目标未解析成功，请核对目标及权限');return r.data.items.map(item=>item.target).filter((target):target is S['ContextTarget']=>!!target);}
export async function createContext(page:string,filter:Filter,rows:Capture[]=[],snapshot=false,db?:Database,sourceAnalysis?:RemoteIntent['sourceAnalysis']){
 const targets:S['ContextTarget'][]=[];for(const row of rows){const record=remote.captures[row.id];const targetId=record?.targetId||row.targetId,targetType=record?.targetType||row.targetType;if(!targetId||!targetType)throw new Error('缺少目标代理 ID；不能使用展示号码代替');if(!targets.some(t=>t.targetId===targetId&&t.targetType===targetType))targets.push({targetId,targetType:targetType as S['TargetType']});}
 const type=rows[0]?.type||value(filter,'type')||({'围栏检索':'电围','车牌检索':'车牌','MAC检索':'MAC','人像检索':'人像'} as Record<string,string>)[page]||'电围';
 const dataTypes=type==='综合'?['FENCE','MAC','PLATE','FACE'] as S['DataType'][]:[codes[type as keyof typeof codes]||'FENCE'];
 const sourceModule:Body<'createContext'>['sourceModule']=['围栏检索','车牌检索','MAC检索','人像检索'].includes(page)?'M05':page==='地图研判'?'M07':page==='数据运算'?'M09':['伴随任务','碰撞任务','区域统计'].includes(page)?'M08':page==='综合布控'||page==='告警结果'||page==='运维通知'?'M10':['人员库','图码档案','车码档案','数据态势'].includes(page)?'M11':page==='设备分布'||page==='运维首页'?'M13':page==='系统首页'?'M04':['场所管理','设备管理','参数配置','运维报表'].includes(page)?'M12':'M06';
 const searchPage=['围栏检索','车牌检索','MAC检索','人像检索'].includes(page);
 const captureSearch=searchPage?searchInput(filter,type,db):null;
 const captureFilter=captureSearch?{...captureSearch}:null;
 if(captureFilter){delete (captureFilter as Record<string,unknown>).pageSize;delete (captureFilter as Record<string,unknown>).sortBy;delete (captureFilter as Record<string,unknown>).sortOrder;delete (captureFilter as Record<string,unknown>).cursor;}
 const body:Body<'createContext'>={sourceModule,targets:snapshot?[]:targets,filter:sourceAnalysis?{filterType:'ANALYSIS_RESULTS',filterVersion:1,analysisTaskId:sourceAnalysis.analysisTaskId,...sourceAnalysis.resultIds?{resultIds:sourceAnalysis.resultIds}:{}}:captureFilter?{filterType:'CAPTURE_SEARCH',filterVersion:1,captureFilter:captureFilter as S['CaptureSearchFilter']}:{filterType:'GENERAL',filterVersion:1,dataTypes,timeRange:timeRange(filter),...ids(filter,'sites').length?{siteIds:ids(filter,'sites')}:{},...ids(filter,'devices').length?{deviceIds:ids(filter,'devices')}:{},...rows.length&&!snapshot?{sourceCaptureIds:rows.filter(row=>!!remote.captures[row.id]).map(row=>row.id)}:{}}};
 const r=await request('createContext',{body});remote.context=r.data;return r.data.contextId;
}
async function contextForManualTargets(page:string,m:Form){const type=text(m,'type')||'电围',targets=await resolveTargets(type,text(m,'target').split(/[,，\n]/).map(t=>t.trim()).filter(Boolean),true);const r=await request('createContext',{body:{sourceModule:page==='综合布控'?'M10':'M08',targets,filter:{filterType:'GENERAL',filterVersion:1,dataTypes:[codes[type as keyof typeof codes]||'FENCE'],timeRange:timeRange({start:text(m,'start'),end:text(m,'end')})}}});remote.context=r.data;return r.data;}
export async function submitRemote(intent:RemoteIntent,m:Form,db:Database,page:string,filter:Filter):Promise<{taskId?:string;resourceId?:string}>{
 const id=intent.id;
 if(intent.kind==='site'){const body:Body<'createSite'>={name:text(m,'name'),regionCode:text(m,'region'),address:text(m,'address'),enabled:true};const r=id?await request('updateSite',{path:{siteId:id},body,etag:etag(id)}):await request('createSite',{body});remote.versions[r.data.siteId]=r.data.version;return {resourceId:r.data.siteId};}
 if(intent.kind==='device'){const siteId=db.sites.find(s=>s.name===text(m,'site'))?.id;if(!siteId)throw new Error('请选择有效场所');const groups=remote.groups.filter(g=>g.name===text(m,'group')&&g.siteId===siteId).map(g=>g.groupId);const body:Body<'createDevice'>={name:text(m,'name'),serialNumber:text(m,'serial'),siteId,category:text(m,'type'),groupIds:groups};const r=id?await request('updateDevice',{path:{deviceId:id},body,etag:etag(id)}):await request('createDevice',{body});remote.versions[r.data.deviceId]=r.data.version;return {resourceId:r.data.deviceId};}
 if(intent.kind==='library'){const body:Body<'createPersonLibrary'>={name:text(m,'name'),category:text(m,'category'),...(id&&remote.libraries[id]?{siteScope:remote.libraries[id]!.siteScope,dataScope:remote.libraries[id]!.dataScope}:scope())};const r=id?await request('updatePersonLibrary',{path:{libraryId:id},body,etag:etag(id)}):await request('createPersonLibrary',{body});remote.versions[r.data.libraryId]=r.data.version;return {resourceId:r.data.libraryId};}
 if(intent.kind==='person'){const person:S['PersonLibraryPersonInfoInput']={name:text(m,'name'),gender:text(m,'sex')==='男'?'MALE':text(m,'sex')==='女'?'FEMALE':text(m,'sex')||'UNKNOWN',ethnicity:text(m,'ethnic'),phone:text(m,'phone'),identityNumber:text(m,'identity'),residenceRegionCode:text(m,'resident'),suspectedLandingText:text(m,'landing'),appearanceDescription:text(m,'appearance')};if(id){const correction:S['PersonProfileCorrection']={};const keys={name:'name',sex:'gender',ethnic:'ethnicity',phone:'phone',identity:'identityNumber',resident:'residenceRegionCode',landing:'suspectedLandingText',appearance:'appearanceDescription'} as const;for(const [key,field] of Object.entries(keys))if(text(m,key)){if(['name','phone','identity'].includes(key)&&/[＊*]/.test(text(m,key)))throw new Error('不能提交脱敏值，请填写完整更正值');correction[field]=text(m,key);}if(!Object.keys(correction).length)throw new Error('请填写至少一项更正值');const r=await request('createProfileCorrection',{path:{profileId:id},body:{profileType:'PERSON',person:correction,supportingEvidenceIds:list(m,'supportingEvidenceIds'),reason:reason(m)}});return {resourceId:r.data.revisionId};}if(!intent.libraryId)throw new Error('缺少人员库');const r=await request('createLibraryPerson',{path:{libraryId:intent.libraryId},body:{person,identifiers:[{targetType:'IMSI',value:text(m,'target')}],reason:reason(m)}});return {resourceId:r.data.member.memberId};}
 if(intent.kind==='role'){const assignedSites=list(m,'sites').map(name=>db.sites.find(s=>s.name===name)?.id).filter((id):id is string=>!!id),allSites=!!m.allSites;const original=id?remote.roles[id]:null;if(id&&!original)throw new Error('请先加载角色详情');const selectedMenus=list(m,'menuIds');if(text(m,'name')==='测试'&&selectedMenus.some(menuId=>remote.menus.find(menu=>menu.menuId===menuId)?.label==='系统设置'))throw new Error('测试角色不能获得系统设置菜单');const body:Body<'createRole'>={roleCode:text(m,'roleCode'),name:text(m,'name'),permissionCodes:list(m,'permissionCodes') as S['PermissionCode'][],menuIds:selectedMenus,siteScope:{allSites,siteIds:allSites?[]:assignedSites},dataScope:{...(original?.dataScope||scope().dataScope),allSites,siteIds:allSites?[]:assignedSites}};const r=id?await request('updateRole',{path:{roleId:id},body,etag:etag(id)}):await request('createRole',{body});remote.versions[r.data.roleId]=r.data.version;return {resourceId:r.data.roleId};}
 if(intent.kind==='user'){const roleId=text(m,'role');if(!roleId)throw new Error('请选择角色');const inherited=id?remote.users[id]:null;const scopes=inherited?{siteScope:inherited.siteScope,dataScope:inherited.dataScope}:scope();const r=id?await request('updateUser',{path:{userId:id},body:{displayName:text(m,'name'),roleIds:[roleId],...scopes},etag:etag(id)}):await request('createUser',{body:{userCode:text(m,'userCode')||text(m,'id'),displayName:text(m,'name'),loginName:text(m,'account'),initialPassword:text(m,'password'),roleIds:[roleId],...scopes}});remote.versions[r.data.userId]=r.data.version;if(id&&text(m,'password'))await request('changeUserPassword',{path:{userId:id},body:{newPassword:text(m,'password'),reason:reason(m)},etag:etag(id)});if((r.data.status==='ACTIVE')!==!!m.enabled){const action=m.enabled?'enableUser':'disableUser';const state=await request(action,{path:{userId:r.data.userId},body:{reason:reason(m)},etag:etag(r.data.userId)});if('version' in state.data&&typeof state.data.version==='number')remote.versions[r.data.userId]=state.data.version;}return {resourceId:r.data.userId};}
 if(intent.kind==='report'){feature('REPORT_GENERATION');const mode=text(m,'mode')==='多报表'?'BUNDLE':'SINGLE',types=mode==='BUNDLE'?list(m,'subtypes'):[text(m,'subtype')],date=text(m,'date'),period=text(m,'period');const definitions:Body<'createReport'>['definitions']=types.map(type=>{const code=reportCodes[type as keyof typeof reportCodes];if(!code)throw new Error('未知报表类型');return {reportType:code,granularity:period==='年'?'YEAR':period==='月'?'MONTH':'DAY',periodStart:period==='年'?date.slice(0,4)+'-01-01':period==='月'?date.slice(0,7)+'-01':date,regionCode:text(m,'region')==='全部'?'ALL':text(m,'region')};});const r=await request('createReport',{body:{name:text(m,'name'),mode,definitions,reason:reason(m)}});return {taskId:r.data.taskId,resourceId:r.data.reportId};}
 if(intent.kind==='notification'){feature('NOTIFICATION_SEND');const recipient=validateRecipient(m,'DEVICE_STATUS'),templateCode=text(m,'templateCode');if(!templateCode)throw new Error('请选择后端通知模板');const body:Body<'createNotificationTask'>={name:text(m,'name'),targets:[recipient],templateCode,schedule:{scheduleType:'DAILY',localTime:text(m,'time'),timeZone:'Asia/Shanghai',startDate:text(m,'startDate')},platformUserIds:list(m,'platformUserIds')};const r=id?await request('updateNotificationTask',{path:{notificationTaskId:id},body,etag:etag(id)}):await request('createNotificationTask',{body});return {resourceId:r.data.notificationTaskId};}
 if(intent.kind==='watch'){
  if(m.activate)feature('WATCH_ACTIVATION');
  const recipient=validateRecipient(m,'WATCH_ALERT');
  const old=intent.watchRule;
  let contextId=old?.contextId;
  let targets=old?.conditions.targets;
  if(!old){if(intent.rows?.length){contextId=await createContext(page,{start:text(m,'start'),end:text(m,'end'),type:text(m,'type')},intent.rows,false,db);targets=remote.context?.targets.map(target=>({target}));}else{const context=await contextForManualTargets('综合布控',m);contextId=context.contextId;targets=context.targets.map(target=>({target}));}}
  if(!targets?.length)throw new Error('布控目标不能为空');if(!old&&!contextId)throw new Error('布控上下文未建立');
  const conditions:Body<'createWatchRule'>['conditions']={...old?.conditions,targets,siteIds:list(m,'siteIds'),dataTypes:old?.conditions.dataTypes||[codes[text(m,'type') as keyof typeof codes]||'FENCE']};
  const notification:Body<'createWatchRule'>['notification']={templateCode:text(m,'templateCode'),platformUserIds:list(m,'platformUserIds'),targets:[recipient]};
  const body={name:text(m,'name'),effectiveFrom:iso(text(m,'start')),effectiveUntil:iso(text(m,'end')),conditions,notification};
  if(body.effectiveFrom>=body.effectiveUntil)throw new Error('结束时间必须晚于开始时间');
  const r=id?await request('updateWatchRule',{path:{ruleId:id},body,etag:etag(id)}):await request('createWatchRule',{body:{...body,contextId:contextId!}});
  remote.versions[r.data.ruleId]=r.data.version;
  if(m.activate){try{const active=await request('activateWatchRule',{path:{ruleId:r.data.ruleId},body:{effectiveFrom:body.effectiveFrom,effectiveUntil:body.effectiveUntil},etag:etag(r.data.ruleId)});remote.versions[r.data.ruleId]=active.data.version;}catch(e){throw new Error('布控规则已保存（'+r.data.ruleId+'），启用未成功，请从列表查看并启用：'+(e instanceof Error?e.message:String(e)));}}
  return {resourceId:r.data.ruleId};
 }
 if(intent.kind==='config'){
  feature('DEVICE_COMMAND');const changes=deviceChanges(intent.parameterItems||[],m);
  if(!changes.length)throw new Error('未选择可修改的设备参数');
  const body:Body<'createDeviceCommand'>={commandType:'APPLY_PARAMETERS',name:text(m,'name'),regionCode:text(m,'region'),groupId:text(m,'groupId'),deviceIds:list(m,'deviceIds'),parameterChanges:changes,reason:reason(m)};
  const r=id?await request('reviseDeviceCommand',{path:{taskId:id},body,etag:etag(id)}):await request('createDeviceCommand',{body});
  return {taskId:r.data.taskId};
 }
 if(intent.kind==='model'){
  if(m.enabled)feature('RESIDENCE_MODEL');
  const tags=text(m,'label').split(/[,，]/).map(t=>t.trim()).filter(Boolean);
  const parameters=intent.model?modelParameters(intent.model,m):[];
  const body:Body<'createTrendModel'>={code:text(m,'code'),name:text(m,'name'),modelType:'RESIDENCE',tagCodes:tags,enabled:!!m.enabled,effectiveDataFilter:{...intent.model?.effectiveDataFilter,dataTypes:list(m,'dataTypes') as S['DataType'][],siteIds:list(m,'siteIds'),captureStatuses:['VALID']}};
  const r=id?await request('updateTrendModel',{path:{modelId:id},body,etag:etag(id)}):await request('createTrendModel',{body});remote.versions[r.data.modelId]=r.data.version;
  if(parameters.length){try{await request('createModelParameterVersion',{path:{modelId:r.data.modelId},body:{parameters,matchingRules:intent.model?.matchingRules||[],reason:reason(m)},etag:etag(r.data.modelId)});}catch(e){throw new Error('模型信息已保存，参数版本保存失败，请刷新后重试：'+(e instanceof Error?e.message:String(e)));}}
  return {resourceId:r.data.modelId};
 }
 if(intent.kind==='dataset'){const selected=intent.rows||[];const contextId=selected.length?await createContext(page,filter,selected,true,db,intent.sourceAnalysis):intent.sourceAnalysis?await createContext(page,filter,[],true,db,intent.sourceAnalysis):null;const r=await request('createDataset',{body:{name:text(m,'name'),datasetKind:'SINGLE',elementType:'CAPTURE'}});remote.versions[r.data.datasetId]=r.data.version;if(!contextId)return {resourceId:r.data.datasetId};const task=await request('importDatasetItems',{path:{datasetId:r.data.datasetId},body:{source:'CONTEXT',contextId},etag:etag(r.data.datasetId)});return {resourceId:r.data.datasetId,taskId:task.data.taskId};}
 if(intent.kind==='password'){if(text(m,'password')!==text(m,'repeat'))throw new Error('两次密码不一致');await request('changeMyPassword',{body:{currentPassword:text(m,'old'),newPassword:text(m,'password'),reason:reason(m)}});return {};}
 if(intent.kind==='decrypt'){feature('DECRYPTION');const selected=intent.rows||[];if(!selected.length)throw new Error('请勾选采集记录');const purpose=text(m,'purpose');if(purpose.length<10)throw new Error('申请用途至少 10 字');const contextId=await createContext(page,filter,selected,false,db);const r=await request('requestDecryption',{body:{contextId,purpose,captureIds:[...new Set(selected.map(row=>row.id))]}});return {resourceId:r.data.requestId};}
 if(intent.kind==='parameter'){feature('DEVICE_COMMAND');if(!id||remote.parameters?.deviceId!==id)throw new Error('请先读取设备参数');const changes:S['DeviceParameterChange'][]=remote.parameters.items.filter(item=>!item.readOnly).map(item=>{const raw=m['parameter_'+item.parameterCode];if(item.valueType==='INTEGER')return {parameterCode:item.parameterCode,valueType:'INTEGER',integerValue:Number(raw)};if(item.valueType==='NUMBER')return {parameterCode:item.parameterCode,valueType:'NUMBER',numberValue:Number(raw)};if(item.valueType==='BOOLEAN')return {parameterCode:item.parameterCode,valueType:'BOOLEAN',booleanValue:!!raw};return {parameterCode:item.parameterCode,valueType:'TEXT',textValue:String(raw??'')};});if(!changes.length)throw new Error('此设备没有可修改参数');const r=await request('updateDeviceParameters',{path:{deviceId:id},body:{changes,reason:reason(m)},etag:etag(id)});return {taskId:r.data.taskId};}
 if(intent.kind==='analysis'){
  const kind=analysisKinds[(intent.analysisKind||page) as keyof typeof analysisKinds];if(!kind)throw new Error('未知分析类型');feature(kind==='COMPANION'?'COMPANION_ANALYSIS':kind==='COLLISION'?'COLLISION_ANALYSIS':'REGION_STATISTICS');
  const old=intent.analysis,range=timeRange({start:text(m,'start'),end:text(m,'end')});
  if(old&&old.kind!==kind)throw new Error('任务类型与参数快照不匹配');
  const base={timeRange:range,...m.threshold!==undefined&&m.threshold!==''?{minResultScore:Number(m.threshold)/100}:{},regionCodes:list(m,'regions')};
  if(kind==='COMPANION'&&(!Number.isInteger(Number(m.timeWindowSeconds))||Number(m.timeWindowSeconds)<1||!Number.isFinite(Number(m.spatialWindowMeters))||Number(m.spatialWindowMeters)<0))throw new Error('请填写有效的伴随时间和空间窗口');
  if(kind==='COLLISION'&&!text(m,'ruleCode'))throw new Error('请选择或填写碰撞匹配规则');
  const polygons=Array.isArray(m.polygons)?m.polygons as S['GeoPolygon'][]:[];
  if(kind==='REGION_STATISTICS'&&!polygons.length)throw new Error('请在地图框选至少一个任务区域');
  let contextId=old?.contextId,targets:S['ContextTarget'][]=old?.kind==='COMPANION'?[old.parameters.target]:old?.kind==='COLLISION'?old.parameters.targets:[];
  if(!old){if(kind==='REGION_STATISTICS'){
    const geometry:S['GeoPolygon']|S['GeoMultiPolygon']=polygons.length===1?polygons[0]!:{type:'MultiPolygon',coordinates:polygons.map(p=>p.coordinates)};
    const context=(await request('createContext',{body:{sourceModule:'M08',targets:[],filter:{filterType:'GENERAL',filterVersion:1,timeRange:range,dataTypes:list(m,'dataTypes') as S['DataType'][]},spatialRange:{selectionType:'AREA',coordinateSystem:'EPSG:4326',geometry}}})).data;
    remote.context=context;contextId=context.contextId;
  }else if(intent.rows?.length){contextId=await createContext(intent.analysisKind||page,{start:text(m,'start'),end:text(m,'end'),type:text(m,'type')},intent.rows,false,db);targets=remote.context?.targets||[];}else{const context=await contextForManualTargets(intent.analysisKind||page,m);contextId=context.contextId;targets=context.targets;}}
  if(!contextId)throw new Error('分析上下文未建立');
  let body:Body<'createAnalysisTask'>;
  if(kind==='COMPANION'){const target=targets[0];if(!target||targets.length!==1)throw new Error('伴随仅允许单个已授权目标');body={kind,name:text(m,'name'),contextId,parameters:{...old?.parameters,...base,target,timeWindowSeconds:Number(m.timeWindowSeconds),spatialWindowMeters:Number(m.spatialWindowMeters),siteIds:list(m,'siteIds')}};}
  else if(kind==='COLLISION'){if(targets.length<2)throw new Error('碰撞接口需要至少两个已授权目标');body={kind,name:text(m,'name'),contextId,parameters:{...old?.parameters,...base,targets,ruleCode:text(m,'ruleCode')}};}
  else body={kind,name:text(m,'name'),contextId,parameters:{...old?.parameters,...base,regions:polygons,dataTypes:list(m,'dataTypes') as S['RegionAnalysisDataType'][]}};
  if(id){const revision:Body<'reviseAnalysisTask'>=body.kind==='COMPANION'?{kind:body.kind,name:body.name,parameters:body.parameters,reason:reason(m)}:body.kind==='COLLISION'?{kind:body.kind,name:body.name,parameters:body.parameters,reason:reason(m)}:{kind:body.kind,name:body.name,parameters:body.parameters,reason:reason(m)};const r=await request('reviseAnalysisTask',{path:{analysisTaskId:id},body:revision,etag:etag(id)});return {taskId:r.data.taskId,resourceId:r.data.analysisTaskId};}
  const r=await request('createAnalysisTask',{body});return {taskId:r.data.taskId,resourceId:r.data.analysisTaskId};
 }
 throw new Error('不支持的表单类型：'+intent.kind);
}

import type {Database,Field,Capture} from '../domain';
import {localTime} from '../domain';
import {request} from './client';
import {remote,labels,analysisKinds} from './gateway';
import type {RemoteIntent} from './mutations';

type Form=Record<string,unknown>;
export async function dictionaryOptions(fieldKey:string){const binding=remote.config?.catalogBindings.find(b=>b.fieldKey===fieldKey);if(!binding)return undefined;const dictionary=(await request('getDictionary',{path:{dictionaryCode:binding.dictionaryCode}})).data;return dictionary.items.filter(item=>item.enabled).sort((a,b)=>a.order-b.order).map(item=>({label:item.label,value:item.code}));}
export type FormHost={open:(title:string,fields:Field[],model:Form,save?:(model:Form)=>void|Promise<void>,submit?:string)=>void;bind:(intent:RemoteIntent)=>void;step:(step:number)=>void};
const field=(key:string,label:string,required=true):Field=>({key,label,required});
const today=()=>new Date().toLocaleDateString('sv-SE',{timeZone:'Asia/Shanghai'});
const dateInput=(value?:string|null)=>value?localTime(value).replace(' ','T').slice(0,16):'';
const parameterValue=(item:Record<string,unknown>)=>item.textValue??item.integerValue??item.numberValue??item.booleanValue??item.secondsValue??item.metersValue;
const parameterField=(item:{parameterCode:string;label:string;valueType:string;required?:boolean;minimum?:number;maximum?:number;readOnly?:boolean;unit?:string}):Field=>({key:'parameter_'+item.parameterCode,label:item.label+(item.unit?'（'+item.unit+'）':''),type:item.valueType==='BOOLEAN'?'boolean':item.valueType==='TEXT'?'text':'number',required:item.required,readonly:item.readOnly,min:item.minimum,max:item.maximum});
export async function ensureSites(db:Database){

 let page=1;const first=(await request('listSites',{query:{page,pageSize:100}})).data;const items=[...first.items];
 while(items.length<first.total){const next=(await request('listSites',{query:{page:++page,pageSize:100}})).data;if(!next.items.length)throw new Error('场所目录分页不完整，请稍后重试');items.push(...next.items);}
 db.sites=items.map(site=>({id:site.siteId,name:site.name,region:site.regionCode,address:''}));
}

async function notificationFields(purpose:'WATCH_ALERT'|'DEVICE_STATUS',_channel:string):Promise<Field[]>{
 remote.templates=(await request('listNotificationTemplates')).data.items;
 const templates=remote.templates.filter(t=>t.enabled&&t.purpose===purpose&&t.channels.some(c=>c==='EMAIL'||c==='SMS'));
 return [{key:'templateCode',label:'通知模板',options:templates.map(t=>({label:t.name,value:t.templateCode})),required:true},{key:'platformUserIds',label:'平台消息接收人',options:remote.me?[{label:remote.me.user.displayName,value:remote.me.user.userId}]:[],multiple:true,required:true}];
}

export async function openNotification(host:FormHost,id?:string){
 const old=id?(await request('getNotificationTask',{path:{notificationTaskId:id}})).data:null;
 if(old)remote.versions[old.notificationTaskId]=old.version;
 const channel=old?.targets[0]?.channel==='EMAIL'?'邮件':'短信';
 // 服务端仅返回掩码，编辑时要求重新填写地址，不得把掩码当成真实收件人。
 const fields:Field[]=[field('name','通知任务名称'),{key:'channel',label:'通知方式',options:['短信','邮件'],required:true},{key:'time',label:'通知时间',type:'time',required:true},{key:'startDate',label:'开始日期',type:'date',required:true},field('recipient',old?'通知对象（重新填写完整地址）':'通知对象'),...await notificationFields('DEVICE_STATUS',channel)];
 const model:Form={name:old?.name||'',channel,time:old?.schedule.scheduleType==='DAILY'?old.schedule.localTime:'10:00',startDate:old?.schedule.scheduleType==='DAILY'?old.schedule.startDate:today(),recipient:'',templateCode:old?.templateCode||'',platformUserIds:old?.platformUserIds||[remote.me?.user.userId].filter(Boolean)};
 host.open(old?'编辑通知任务':'新增通知任务',fields,model);host.bind({kind:'notification',id});
}

export async function openWatch(host:FormHost,db:Database,id?:string,rows:Capture[]=[]){
 await ensureSites(db);
 const old=id?(await request('getWatchRule',{path:{ruleId:id}})).data:null;
 if(old){remote.watches[old.ruleId]=old;remote.versions[old.ruleId]=old.version;}
 const channel=old?.notification?.targets[0]?.channel==='EMAIL'?'邮件':'短信';
 const fields:Field[]=[field('name','布控名称'),{key:'start',label:'开始时间',type:'datetime-local',required:true},{key:'end',label:'结束时间',type:'datetime-local',required:true},{key:'type',label:'数据类型',options:['电围','车牌','MAC','人像'],readonly:!!old||!!rows.length,required:true},{...field('target','目标标识'),readonly:!!old||!!rows.length},{key:'siteIds',label:'布控场所',options:db.sites.map(s=>({label:s.name,value:s.id})),multiple:true,required:true},{key:'channel',label:'通知方式',options:['短信','邮件'],required:true},field('recipient',old?'通知对象（重新填写完整地址）':'通知对象'),...await notificationFields('WATCH_ALERT',channel),{key:'activate',label:'保存后启用布控',type:'boolean'}];
 host.open(old?'编辑布控信息':'新增布控',fields,{name:old?.name||'',start:dateInput(old?.effectiveFrom),end:dateInput(old?.effectiveUntil),type:old?labels[old.conditions.dataTypes[0]||'FENCE']:rows[0]?.type||'电围',target:old?'保留已授权目标':rows.map(r=>r.target).filter((v,i,a)=>a.indexOf(v)===i).join(','),siteIds:old?.conditions.siteIds||[],channel,recipient:'',templateCode:old?.notification?.templateCode||'',platformUserIds:old?.notification?.platformUserIds||[remote.me?.user.userId].filter(Boolean),activate:false});
 host.bind({kind:'watch',id,rows,watchRule:old||undefined});
}

export async function openModel(host:FormHost,db:Database,id?:string){
 await ensureSites(db);
 const model=id?(await request('getTrendModel',{path:{modelId:id}})).data:null;
 if(model)remote.versions[model.modelId]=model.version;
 const version=model?.currentParameterVersionId?(await request('getModelParameterVersion',{path:{modelId:model.modelId,parameterVersionId:model.currentParameterVersionId}})).data:null;
 const fields:Field[]=[field('code','模型编号'),field('name','模型名称'),field('label','数据标签（逗号分隔）'),{key:'dataTypes',label:'有效数据类型',options:['FENCE','PLATE','MAC','FACE'].map(value=>({label:labels[value]||({'FENCE':'电围','PLATE':'车牌','MAC':'MAC','FACE':'人像'} as Record<string,string>)[value]||value,value})),multiple:true,required:true},{key:'siteIds',label:'有效数据场所',options:db.sites.map(s=>({label:s.name,value:s.id})),multiple:true},{key:'enabled',label:'启用模型',type:'boolean'},...(model?.parameterDefinitions.map(parameterField)||[])];
 host.open(model?'常驻数据模型参数':'创建常驻数据模型',fields,{code:model?.code||'',name:model?.name||'',label:model?.tagCodes.join(',')||'',dataTypes:model?.effectiveDataFilter.dataTypes||['FENCE'],siteIds:model?.effectiveDataFilter.siteIds||[],enabled:model?.enabled||false,...Object.fromEntries((version?.parameters||[]).map(item=>['parameter_'+item.parameterCode,parameterValue(item)]))});
 host.bind({kind:'model',id,model:model||undefined});
}

export async function openConfig(host:FormHost,db:Database,id?:string){
 await ensureSites(db);
 const old=id?(await request('getDeviceCommand',{path:{taskId:id}})).data:null;
 if(old)remote.versions[old.taskId]=old.version;
 const groups=(await request('listDeviceGroups',{query:{page:1,pageSize:100}})).data;let groupPage=1;
 while(groups.items.length<groups.total){const next=(await request('listDeviceGroups',{query:{page:++groupPage,pageSize:100}})).data;if(!next.items.length)throw new Error('设备分组目录分页不完整');groups.items.push(...next.items);}remote.groups=groups.items;
 const record:Form={name:old?.name||'',region:old?.requestSnapshot.regionCode||'',groupId:old?.requestSnapshot.groupId||'',deviceIds:[]};
 const first:Field[]=[field('name','任务名称'),{key:'region',label:'场所区域',options:[...new Set(db.sites.map(s=>s.region))],required:true},{key:'groupId',label:'设备分组',options:groups.items.map(g=>({label:g.name,value:g.groupId})),required:true}];
 host.open(id?'编辑配置任务':'新增配置任务',first,record,async m=>{
  Object.assign(record,m);const group=groups.items.find(g=>g.groupId===m.groupId),site=db.sites.find(s=>s.id===group?.siteId);
  if(!group||site?.region!==m.region)throw new Error('所选分组不属于该区域，请重新选择');
  const members=(await request('listDeviceGroupMembers',{path:{groupId:group.groupId},query:{page:1,pageSize:100}})).data;
  let memberPage=1;while(members.items.length<members.total){const next=(await request('listDeviceGroupMembers',{path:{groupId:group.groupId},query:{page:++memberPage,pageSize:100}})).data;if(!next.items.length)throw new Error('设备成员目录分页不完整');members.items.push(...next.items);}
  host.open('选择配置设备',[{key:'deviceIds',label:'选择设备',options:members.items.map(d=>({label:d.name,value:d.deviceId})),multiple:true,required:true}],{...record,deviceIds:old?.requestSnapshot.deviceIds||[]},async m2=>{
   Object.assign(record,m2);const deviceIds=m2.deviceIds as string[];
   if(deviceIds.some(deviceId=>!members.items.some(d=>d.deviceId===deviceId)))throw new Error('部分设备已不在所选分组');
   const snapshots=await Promise.all(deviceIds.map(deviceId=>request('getDeviceParameters',{path:{deviceId}})));
   const items=(snapshots[0]?.data.items||[]).filter(item=>!item.readOnly&&snapshots.every(snapshot=>snapshot.data.items.some(other=>other.parameterCode===item.parameterCode&&other.valueType===item.valueType&&!other.readOnly)));
   if(!items.length)throw new Error('所选设备没有共同可修改参数，请重新选择');
   host.open('配置设备参数',items.map(parameterField),{...record,...Object.fromEntries(items.map(item=>['parameter_'+item.parameterCode,parameterValue(item)]))},undefined,'提交配置');host.step(2);host.bind({kind:'config',id,parameterItems:items});
  },'下一步');host.step(1);
 },'下一步');host.step(0);
}

export async function openAnalysis(host:FormHost,db:Database,kind:string,id?:string,rows:Capture[]=[]){
 if(!(kind in analysisKinds))throw new Error('未知分析任务类型');await ensureSites(db);
 const old=id?(await request('getAnalysisTask',{path:{analysisTaskId:id}})).data:null;
 if(old){if(old.kind!==analysisKinds[kind as keyof typeof analysisKinds])throw new Error('分析任务类型不匹配');remote.versions[old.analysisTaskId]=old.version;}
 const times=rows.map(row=>row.time.replace(' ','T')).sort();const stamp=(date:Date)=>date.toLocaleString('sv-SE',{timeZone:'Asia/Shanghai'}).replace(' ','T').slice(0,16);
 const oldTarget=old?.kind==='COMPANION'?old.parameters.target:old?.kind==='COLLISION'?old.parameters.targets[0]:undefined;
 const type=oldTarget?oldTarget.targetType==='PLATE'?'车牌':oldTarget.targetType==='MAC'?'MAC':oldTarget.targetType==='FACE'?'人像':'电围':rows[0]?.type||'电围';
 const fields:Field[]=[field('name','任务名称'),...(kind==='区域统计'?[]:[{key:'type',label:'目标数据类型',options:['电围','车牌','MAC','人像'],readonly:!!old||!!rows.length,required:true},{...field('target',kind==='伴随任务'?'伴随目标（单目标）':'碰撞目标（逗号分隔）'),readonly:!!old||!!rows.length}]),{key:'start',label:'开始时间',type:'datetime-local',required:true},{key:'end',label:'结束时间',type:'datetime-local',required:true},{key:'threshold',label:'最低关联度 / 匹配度（%）',type:'number',min:0,max:100},{key:'regions',label:'任务区域',options:[...new Set([...db.sites.map(s=>s.region),...old?.parameters.regionCodes||[]])],multiple:true},...(kind==='伴随任务'?[{key:'timeWindowSeconds',label:'伴随时间窗口（秒）',type:'number',min:1,required:true},{key:'spatialWindowMeters',label:'伴随空间窗口（米）',type:'number',min:0,required:true},{key:'siteIds',label:'场所',options:db.sites.map(s=>({label:s.name,value:s.id})),multiple:true}]:kind==='碰撞任务'?[field('ruleCode','碰撞匹配规则')]:[{key:'polygons',label:'任务选区',type:'region-picker',required:true},{key:'dataTypes',label:'统计数据类型',options:['FENCE','PLATE','MAC'].map(value=>({value,label:value==='FENCE'?'电围':value==='PLATE'?'车牌':'MAC'})),multiple:true,required:true}])];
 host.open((old?'编辑':'新增')+kind.replace('任务','分析'),fields,{name:old?.name||'',type,target:old?'保留已授权目标':[...new Set(rows.map(row=>row.target))].join(','),start:old?dateInput(old.parameters.timeRange.from):times[0]?.slice(0,16)||stamp(new Date(Date.now()-86400000)),end:old?dateInput(old.parameters.timeRange.to):times.length?stamp(new Date(new Date(times.at(-1)!+'+08:00').getTime()+60000)):stamp(new Date()),threshold:old?.parameters.minResultScore==null?undefined:old.parameters.minResultScore*100,regions:old?.parameters.regionCodes||[...new Set(rows.map(row=>row.region))],...(old?.kind==='COMPANION'?{timeWindowSeconds:old.parameters.timeWindowSeconds,spatialWindowMeters:old.parameters.spatialWindowMeters,siteIds:old.parameters.siteIds||[]}:old?.kind==='COLLISION'?{ruleCode:old.parameters.ruleCode}:old?.kind==='REGION_STATISTICS'?{polygons:old.parameters.regions,dataTypes:old.parameters.dataTypes}:kind==='区域统计'?{polygons:[],dataTypes:['FENCE','PLATE','MAC']}:{})});
 host.bind({kind:'analysis',id,analysisKind:kind,rows,analysis:old||undefined});
}

export async function openPerson(host:FormHost,libraryId:string,id?:string){
 const evidence=id?(await request('listProfileEvidence',{path:{profileId:id},query:{pageSize:100}})).data.items:[];
 const fields:Field[]=[field('name',id?'更正姓名（留空保留）':'姓名',!id),{key:'sex',label:'性别',options:await dictionaryOptions('PersonProfileCorrection.gender')},{key:'ethnic',label:'民族',options:await dictionaryOptions('PersonProfileCorrection.ethnicity')},field('phone',id?'更正手机号码（完整号码，留空保留）':'手机号码',false),field('identity',id?'更正身份证号码（完整号码，留空保留）':'身份证号码',false),...id?[]:[field('target','IMSI / 特征码')],field('resident','常驻地区',false),field('landing','疑似落脚点',false),field('appearance','外貌特征',false),field('reason','填写 / 更正用途')];
 // 普通人员库响应带脱敏数据，不能将这些值复制到更正请求。
 if(id)fields.push({key:'supportingEvidenceIds',label:'更正依据（授权证据）',options:evidence.map(e=>({label:e.source+' · '+localTime(e.collectedAt),value:e.evidenceId})),multiple:true,required:true});
 host.open(id?'更正人员档案':'新增人员',fields,{name:'',sex:'',ethnic:'',phone:'',identity:'',resident:'',landing:'',appearance:'',reason:'',supportingEvidenceIds:[]});host.bind({kind:'person',id,libraryId});
}

export async function openLibrary(host:FormHost,id?:string){const old=id?(await request('getPersonLibrary',{path:{libraryId:id}})).data:null;if(old){remote.libraries[old.libraryId]=old;remote.versions[old.libraryId]=old.version;}host.open(old?'编辑库信息':'新增人员库',[field('name','库名'),{key:'category',label:'分类',options:await dictionaryOptions('PersonLibraryInput.category'),required:true}],{name:old?.name||'',category:old?.category||''});host.bind({kind:'library',id});}

export const dataTypes = ['电围','车牌','MAC','人像'] as const;
export type DataType = typeof dataTypes[number];
export const groups: Record<string,string[]> = {'系统首页':['系统首页'],'数据检索':['围栏检索','车牌检索','MAC检索','人像检索','人员库'],'综合分析':['地图研判'],'数据研判':['伴随任务','碰撞任务','区域统计','数据运算'],'布控预警':['综合布控','告警结果'],'数据模型':['图码档案','车码档案','数据态势'],'运维服务':['运维首页','场所管理','设备管理','设备分布','参数配置','运维报表','运维通知'],'系统设置':['用户管理','角色管理','操作日志','短信日志'],'共享入口':['任务中心']};
export const reportTypes = ['设备实时在线率','设备实时状态','电围运营商数据统计','电围区域采集数据统计','电围设备采集数据统计','MAC数据统计','车牌数据统计','系统组件状态'];
export type Params = {power:string;interval:string;mode:string};
export type Site = {id:string;name:string;region:string;address:string};
export type Device = {id:string;name:string;serial:string;site:string;region:string;type:string;status:string;x:number;y:number;count:number;group:string;params:Params;resetCount:number;longitude?:number|null;latitude?:number|null};
export type Capture = {id:string;type:string;target:string;imei:string;site:string;region:string;device:string;deviceId:string;time:string;person:string;score:number;targetId?:string;targetType?:string};
export type Person = {id:string;name:string;sex:string;ethnic:string;phone:string;identity:string;resident:string;landing:string;target:string;appearance:string;image:string};
export type Analysis = {id:string;name:string;date:string;start:string;end:string;target:string;regions:string[];sites:string[];status:string;threshold:number;account:string;sourceIds?:string[]};
export type Library = {id:string;name:string;category:string;people:string[];memberCount?:number};
export type Dataset = {id:string;name:string;items:string[];elementType?:'CAPTURE'|'TARGET';itemCount?:number};
export type Watch = {id:string;name:string;account:string;start:string;end:string;type:string;target:string;person:string;status:string;channel:string;recipient:string;hits:string[]};
export type Config = {id:string;name:string;region:string;group:string;deviceIds:string[];params:Params;status:string};
export type Report = {id:string;name:string;date:string;mode:string;type:string;subtype?:string;subtypes?:string[];period:string;region:string;status:string};
export type Notification = {id:string;name:string;channel:string;time:string;recipient:string;status:string;last:string};
export type Model = {id:string;name:string;days:number;min:number;label:string;enabled:boolean};
export type Role = {id:string;code?:string;name:string;menus:string[];sites:string[];secret:boolean};
export type User = {id:string;code?:string;name:string;account:string;role:string;enabled:boolean;password?:string};
export type Log = {id:string;user:string;name:string;action:string;time:string};
export type Sms = {id:string;name:string;recipient:string;time:string;content:string;status:string};
export type Message = {id:string;title:string;content:string;time:string;read:boolean};
export type Database = {sites:Site[];devices:Device[];captures:Capture[];people:Person[];analysis:Record<string,Analysis[]>;libraries:Library[];datasets:Dataset[];watches:Watch[];configs:Config[];reports:Report[];notifications:Notification[];models:Model[];roles:Role[];users:User[];logs:Log[];sms:Sms[];messages:Message[]};
export type Entity = Site|Device|Capture|Person|Analysis|Library|Dataset|Watch|Config|Report|Notification|Model|Role|User|Log|Sms;
export type Scalar = string|number|boolean|null;
export type Row = Record<string,unknown> & {id:string};
export type Filter = Record<string,string|string[]>;
export type Field = {key:string;label:string;type?:string;options?:string[]|{label:string;value:string}[];multiple?:boolean;required?:boolean;min?:number;max?:number;readonly?:boolean};
export const uid = () => crypto.randomUUID();
export function localTime(value:string) { if(!value)return '';const date=new Date(value.includes('T')?value:value.replace(' ','T')+'+08:00');return Number.isNaN(date.getTime())?value:date.toLocaleString('sv-SE',{timeZone:'Asia/Shanghai'}); }
export function iso(value:string){ if(!value)throw new Error('时间不能为空');const input=value.replace(' ','T');const zoned=/Z$|[+-]\d\d:\d\d$/.test(input)?input:input+(input.length===16?':00':'')+'+08:00';const d=new Date(zoned);if(Number.isNaN(d.getTime()))throw new Error('时间格式错误');return d.toISOString(); }
export function filterRows<T>(rows:T[],filters:Filter,bindings:Record<string,(row:T)=>unknown>){return rows.filter(row=>Object.entries(bindings).every(([key,read])=>{const input=filters[key];if(!input||input==='全部'||Array.isArray(input)&&!input.length)return true;const value=String(read(row)??'');if(key==='start')return value.replace(' ','T')>=String(input);if(key==='end')return value.replace(' ','T')<String(input)+(String(input).length===10?'T23:59:59.999':'');return (Array.isArray(input)?input:String(input).split(/[,，\n]/)).some(q=>value.toLowerCase().includes(q.trim().toLowerCase()));}));}

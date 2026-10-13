import {beforeEach,describe,it,expect,vi} from 'vitest';
import {createPinia,setActivePinia} from 'pinia';
import {nextTick} from 'vue';

const env=vi.hoisted(()=>{
  const memory=new Map<string,string>();
  Object.defineProperty(globalThis,'localStorage',{configurable:true,value:{getItem:(key:string)=>memory.get(key)??null,setItem:(key:string,value:string)=>memory.set(key,value),removeItem:(key:string)=>memory.delete(key),clear:()=>memory.clear()}});
  const clipboard=vi.fn().mockResolvedValue(undefined);
  Object.defineProperty(globalThis,'navigator',{configurable:true,value:{onLine:true,clipboard:{writeText:clipboard}}});
  return {clipboard,message:{success:vi.fn(),warning:vi.fn(),error:vi.fn(),info:vi.fn()}};
});
vi.mock('element-plus',()=>({ElMessage:env.message,ElMessageBox:{confirm:vi.fn().mockResolvedValue('confirm')}}));
vi.mock('../src/utils/files',()=>({exportRows:vi.fn().mockResolvedValue(undefined),download:vi.fn(),workbook:vi.fn().mockResolvedValue(new Blob(['workbook'])),parseImport:vi.fn()}));
import {usePlatform} from '../src/stores/platform';
import {resetMock,statistics} from '../src/mock/repository';
import {groups,filterRows} from '../src/domain';
import type {Capture} from '../src/domain';

let p:ReturnType<typeof usePlatform>;
beforeEach(async()=>{resetMock();setActivePinia(createPinia());p=usePlatform();vi.clearAllMocks();await p.login('demo.admin','demo123');});
describe('PDF frontend business flows',()=>{
  it('[PDF-001] failed or disabled login does not establish a session; success opens home',async()=>{
    await p.logout();await p.login('demo.admin','bad');expect(p.session).toBe('');expect(p.error).toContain('错误');
    await p.login('demo.disabled','demo123');expect(p.session).toBe('');expect(p.error).toContain('禁用');
    await p.login('demo.admin','demo123');expect(p.active).toBe('系统首页');expect(p.allowed('系统首页')).toBe(true);
  });
  it('[PDF-015,PDF-016,PDF-031,PDF-044] combined capture filters and reset preserve the intended type',()=>{
    const rows=p.captureRows('电围');const first=rows[0]!;
    const found=filterRows(rows,{site:first.site,target:first.target,imei:first.imei,start:'2026-10-05T08:00',end:'2026-10-08T00:00'},{site:r=>r.site,target:r=>r.target,imei:r=>r.imei,start:r=>r.time,end:r=>r.time});
    expect(found.length).toBeGreaterThan(0);expect(found.every(r=>r.site===first.site&&r.target===first.target&&r.imei===first.imei)).toBe(true);
    p.navigate('围栏检索',{target:'none',site:first.site});p.reset();expect(p.f).toEqual({});expect(p.captureRows('车牌').every(r=>r.type==='车牌')).toBe(true);
  });
  it('[PDF-020,PDF-034,PDF-047] copying selected records deduplicates the target values',async()=>{
    const rows=p.captureRows('电围').filter(c=>c.target===p.db.captures[0]!.target);await p.dataAction('复制',rows);
    expect(env.clipboard).toHaveBeenCalledWith(rows[0]!.target);
  });
  it('[PDF-021,PDF-035,PDF-048] statistics derive all five indicators without merging data types',async()=>{
    const selected=p.db.captures[0]!;await p.dataAction('统计',[selected]);const s=p.statRows[0]!;
    const all=p.db.captures.filter(c=>c.type===selected.type&&c.target===selected.target);
    expect(s.count).toBe(all.length);expect(s.stations).toBe(new Set(all.map(c=>c.site)).size);expect(s.regions).toBe(new Set(all.map(c=>c.region)).size);expect(s.first).toBe(all.map(c=>c.time).sort()[0]);expect(all.some(c=>c.site===s.landing)).toBe(true);
    const other={...selected,id:'other',type:'MAC'};expect(statistics([selected,other],[selected,other])).toHaveLength(2);
  });
  it('[PDF-023,PDF-027,PDF-036,PDF-040,PDF-049,PDF-053] trajectory handoff carries type, target and a nonempty time range',async()=>{
    for(const type of ['电围','车牌','MAC','人像']){await p.dataAction('轨迹',[p.captureRows(type)[0]!]);expect(p.active).toBe('地图研判');expect(p.f.type).toBe(type);expect(p.f.target).toBeTruthy();expect(String(p.f.end)>String(p.f.start)).toBe(true);expect(p.mapTrajectory).toBe(true);}
  });
  it('[PDF-025,PDF-038,PDF-051] secondary search routes to the corresponding type and fills its target',async()=>{
    for(const [type,page] of [['电围','围栏检索'],['车牌','车牌检索'],['MAC','MAC检索']]){const c=p.captureRows(type!)[0]!;await p.dataAction('检索',[c]);expect(p.active).toBe(page);expect(p.f.target).toBe(c.target);}
  });
  it('[PDF-024,PDF-037,PDF-050] companion creation from selected data retains every time/site record and saves a task',async()=>{
    const first=p.captureRows('电围')[0]!;await p.dataAction('伴随',[first]);const all=p.captureRows('电围').filter(c=>c.target===first.target);
    expect(p.modal.info[0]?.[1]).toContain(all[0]!.time);expect(p.modal.model.sites).toEqual([...new Set(all.map(c=>c.site))]);
    p.modal.model.name='带入时空验收';const before=p.db.analysis['伴随任务']!.length;await p.saveForm();
    expect(p.modal.error).toBe('');expect(p.db.analysis['伴随任务']).toHaveLength(before+1);expect(p.db.analysis['伴随任务']!.at(-1)!.sourceIds).toEqual(all.map(c=>c.id));
  });
  it('[PDF-022] companion is rejected for multiple selected targets',async()=>{await p.dataAction('伴随',p.captureRows('电围').slice(0,2));expect(p.modal.visible).toBe(false);expect(env.message.warning).toHaveBeenCalled();});
  it('[PDF-026,PDF-039,PDF-052,PDF-084] temporary watch creation uses selected target/type',async()=>{const c=p.captureRows('MAC')[0]!;await p.dataAction('布控',[c]);p.modal.model.name='临时布控验收';await p.saveForm();expect(p.db.watches.at(-1)?.target).toBe(c.target);expect(p.db.watches.at(-1)?.type).toBe('MAC');expect(p.active).toBe('综合布控');});
  it('[PDF-028,PDF-041,PDF-054] region creation carries the full source area and saves',async()=>{const rows=p.captureRows('车牌');p.formAnalysis('区域统计',undefined,rows);p.modal.model.name='选区验收';await p.saveForm();expect(p.modal.error).toBe('');expect(p.db.analysis['区域统计']!.at(-1)?.regions).toEqual([...new Set(rows.map(c=>c.region))]);});
  it('[PDF-029,PDF-042,PDF-055] dataset addition preserves all filtered records rather than the visible page',async()=>{const rows=p.captureRows('电围');p.addDataset(rows);p.modal.model.name='全部结果';await p.saveForm();expect(p.db.datasets.at(-1)?.items).toEqual(rows.map(c=>c.id));expect(p.db.datasets.at(-1)?.items.length).toBeGreaterThan(5);});
  it('[PDF-064,PDF-065,PDF-068] library/person creation and edits persist; duplicate names do not create another library',async()=>{
    p.formLibrary();p.modal.model.name='验收库';await p.saveForm();const lib=p.db.libraries.at(-1)!;
    p.formPerson(lib.id);Object.assign(p.modal.model,{name:'验收人员',target:'460000000009999',sex:'男'});await p.saveForm();const person=p.db.people.at(-1)!;expect(lib.people).toContain(person.id);
    p.formPerson(lib.id,person.id);p.modal.model.phone='13800000009';await p.saveForm();expect(person.phone).toBe('13800000009');
    p.formLibrary();p.modal.model.name=lib.name;await p.saveForm();expect(p.modal.error).toContain('存在');
    await p.remove('libraries',[lib.id]);expect(p.db.libraries.some(l=>l.id===lib.id)).toBe(false);
  });
  it('[PDF-067] library import commits only after all rows validate',async()=>{
    const before=p.db.libraries.length;p.importScope('libraries');p.importState.rows=[{name:'先验证库',people:[{name:'合法人员',sex:'男',target:'x'}]},{name:'后错误库',people:[{name:'错误人员',sex:'invalid'}]}];await p.applyImport();expect(p.db.libraries).toHaveLength(before);expect(p.importState.error).toContain('性别');
    p.importState.rows=[{name:'成功库',people:[{name:'成功人员',sex:'女',target:'y'}]}];await p.applyImport();expect(p.db.libraries).toHaveLength(before+1);expect(p.db.people.at(-1)?.name).toBe('成功人员');
  });
  it('[PDF-069,PDF-115] selecting a library person fills the watch target',async()=>{p.formWatch();p.modal.model.person='P1';await nextTick();expect(p.modal.model.target).toBe(p.db.people.find(person=>person.id==='P1')?.target);expect(p.modal.model.type).toBe('电围');});
  for(const [kind,ids] of [['伴随任务','PDF-079,PDF-080,PDF-081,PDF-082,PDF-086'],['碰撞任务','PDF-088,PDF-089,PDF-090,PDF-091,PDF-094'],['区域统计','PDF-096,PDF-097,PDF-098,PDF-099,PDF-100,PDF-103']])it(`[${ids}] ${kind} creates, edits and deletes tasks with valid per-type results`,async()=>{
    p.navigate(kind!);p.formAnalysis(kind!);Object.assign(p.modal.model,{name:'新增验收',target:'460000000000000',regions:p.db.sites.map(s=>s.region),sites:p.db.sites.map(s=>s.name)});await p.saveForm();expect(p.modal.error).toBe('');const task=p.db.analysis[kind!]!.at(-1)!;
    for(const type of kind==='区域统计'?['电围','车牌','MAC']:['电围','车牌','MAC','人像']){const results=p.analysisResults(task,type,kind!);expect(results.length).toBeGreaterThan(0);expect(results.every(r=>r.count>0&&r.score!>=0&&r.score!<=100)).toBe(true);if(kind==='区域统计')for(const r of results)expect(r.score).toBe(Math.round(r.visited.length/task.regions.length*100));}
    p.formAnalysis(kind!,task.id);p.modal.model.name='已编辑验收';await p.saveForm();expect(task.name).toBe('已编辑验收');p.chosen=[task];await p.removeAnalysis();expect(p.db.analysis[kind!]!.some(t=>t.id===task.id)).toBe(false);
  });
  it('[PDF-106] set operations apply intersection, union, difference, parentheses and type protection',async()=>{
    p.operands=['SET0','SET1'];p.expression='A ∩ B';await p.runSets();expect(p.setResult).toEqual(['C0-0']);
    p.expression='A ∪ B';await p.runSets();expect(p.setResult).toHaveLength(6);p.expression='A \\ B';await p.runSets();expect(p.setResult).toHaveLength(2);
    p.expression='(A ∪ B) ∩ A';await p.runSets();expect(p.setResult).toHaveLength(3);p.db.datasets[1]!.elementType='TARGET';await p.runSets();expect(env.message.error).toHaveBeenCalledWith(expect.stringContaining('不同元素类型'));
  });
  it('[PDF-104,PDF-109] direct dataset import rejects unknown capture IDs and dataset deletion is scoped',async()=>{p.importScope('datasets');p.importState.rows=[{name:'非法数据集',items:'unknown'}];const count=p.db.datasets.length;await p.applyImport();expect(p.db.datasets).toHaveLength(count);p.importState.rows=[{name:'合法数据集',items:'C0-0,C0-6'}];await p.applyImport();expect(p.db.datasets.at(-1)?.items).toHaveLength(2);await p.remove('datasets',[p.db.datasets.at(-1)!.id]);expect(p.db.datasets).toHaveLength(count);});
  it('[PDF-114,PDF-116,PDF-118,PDF-119,PDF-120,PDF-121,PDF-212] watch lifecycle separates hits, finish, SMS and platform messages',async()=>{
    p.formWatch();Object.assign(p.modal.model,{name:'命中验收',target:p.db.captures[0]!.target});await p.saveForm();const w=p.db.watches.at(-1)!;
    const sms=p.db.sms.length,msg=p.db.messages.length;p.simulateWatch(w.id);expect(w.hits).toHaveLength(1);expect(p.db.sms).toHaveLength(sms+1);expect(p.db.messages).toHaveLength(msg+1);p.simulateWatch(w.id,true);expect(w.status).toBe('已结束');
    p.formWatch(w.id);p.modal.model.name='命中已编辑';await p.saveForm();expect(w.name).toBe('命中已编辑');p.chosen=[w];await p.revoke('watches');expect(w.status).toBe('已撤销');await p.remove('watches',[w.id]);expect(p.db.watches.some(item=>item.id===w.id)).toBe(false);
  });
  it('[PDF-121,PDF-212] email and platform watch events do not fabricate SMS logs',()=>{const w=p.db.watches[0]!;w.channel='邮件';const before=p.db.sms.length;p.simulateWatch(w.id);expect(p.db.sms).toHaveLength(before);expect(p.db.messages.length).toBeGreaterThan(0);});
  it('[PDF-130] residence model parameter edits and creation persist',async()=>{p.formModel('M0');Object.assign(p.modal.model,{days:4,min:3,label:'参数验收'});await p.saveForm();expect(p.db.models[0]!.days).toBe(4);expect(p.db.models[0]!.label).toBe('参数验收');p.formModel();p.modal.model.name='新常驻模型';await p.saveForm();expect(p.db.models).toHaveLength(2);});
  it('[PDF-144,PDF-145,PDF-146] site rename updates linked captures/devices/role scopes and referenced site deletion is blocked',async()=>{p.formAdmin('sites','S0');p.modal.model.name='中心改名';await p.saveForm();expect(p.db.devices.filter(d=>d.site==='中心改名')).toHaveLength(2);expect(p.db.captures.some(c=>c.site==='中心广场')).toBe(false);await p.remove('sites',['S0']);expect(p.db.sites.some(s=>s.id==='S0')).toBe(true);p.formAdmin('sites');Object.assign(p.modal.model,{name:'空场所',region:'新区'});await p.saveForm();const s=p.db.sites.at(-1)!;await p.remove('sites',[s.id]);expect(p.db.sites.some(item=>item.id===s.id)).toBe(false);});
  it('[PDF-149,PDF-153,PDF-154,PDF-155] device parameters change, reset and selected deletion persist',async()=>{p.deviceOperation('D0','参数');p.modal.model.power=32;await p.saveForm();expect(p.db.devices[0]!.params.power).toBe('32');await p.resetDevices(['D0']);expect(p.db.devices[0]!.resetCount).toBe(1);expect(p.db.devices[0]!.params.power).toBe('20');p.formAdmin('devices');Object.assign(p.modal.model,{name:'新设备',serial:'NEW-DEVICE'});await p.saveForm();const d=p.db.devices.at(-1)!;expect(d.serial).toBe('NEW-DEVICE');await p.remove('devices',[d.id]);expect(p.db.devices.some(item=>item.id===d.id)).toBe(false);});
  it('[PDF-156] device import validates the site/serial before committing',async()=>{p.importScope('devices','D0');p.importState.rows=[{name:'改名设备',site:'不存在'}];await p.applyImport();expect(p.db.devices[0]!.name).toBe('设备 A-01');expect(p.importState.error).toContain('场所');p.importState.rows=[{name:'导入已修改',site:'东区车站'}];await p.applyImport();expect(p.db.devices[0]!.region).toBe('东区');});
  it('[PDF-169,PDF-170,PDF-171,PDF-172,PDF-173] configuration wizard commits all selected device parameters and supports editing',async()=>{p.formConfig();Object.assign(p.modal.model,{name:'配置验收',region:'中心区',group:'核心组'});await p.saveForm();expect(p.modal.step).toBe(1);p.modal.model.deviceIds=['D0','D5'];await p.saveForm();expect(p.modal.step).toBe(2);p.modal.model.power=35;await p.saveForm();const cfg=p.db.configs.at(-1)!;expect(cfg.deviceIds).toEqual(['D0','D5']);expect(p.db.devices.filter(d=>cfg.deviceIds.includes(d.id)).every(d=>d.params.power==='35')).toBe(true);p.formConfig(cfg.id);p.modal.model.name='配置已编辑';await p.saveForm();p.modal.model.deviceIds=['D0'];await p.saveForm();await p.saveForm();expect(cfg.name).toBe('配置已编辑');});
  it('[PDF-175,PDF-176,PDF-177,PDF-178,PDF-189] single/bundle report models retain period, type and selected definitions',async()=>{p.formReport();Object.assign(p.modal.model,{name:'单报表验收',type:'数据报表',subtype:'MAC数据统计',period:'月'});await p.saveForm();expect(p.db.reports.at(-1)?.subtype).toBe('MAC数据统计');expect(p.db.reports.at(-1)?.period).toBe('月');p.formReport();Object.assign(p.modal.model,{name:'多报表验收',mode:'多报表'});expect(p.modal.model.subtypes).toHaveLength(8);await p.saveForm();const report=p.db.reports.at(-1)!;expect(report.mode).toBe('多报表');await p.remove('reports',[report.id]);expect(p.db.reports.some(r=>r.id===report.id)).toBe(false);});
  it('[PDF-191,PDF-192,PDF-193,PDF-194,PDF-195] notifications validate addresses, generate platform messages and revoke/delete',async()=>{for(const [channel,recipient] of [['短信','13800000000'],['邮件','demo@example.com']]){p.formNotification();Object.assign(p.modal.model,{name:channel+'验收',channel,recipient});await p.saveForm();const n=p.db.notifications.at(-1)!;const before=p.db.sms.length;p.simulateNotification(n.id);expect(n.last).toBeTruthy();expect(p.db.sms.length-before).toBe(channel==='短信'?1:0);p.chosen=[n];await p.revoke('notifications');expect(n.status).toBe('已撤销');await p.remove('notifications',[n.id]);}expect(p.db.messages).toHaveLength(2);});
  it('[PDF-197,PDF-198,PDF-199,PDF-200,PDF-201,PDF-202] role permissions take effect, test role cannot obtain settings and referenced roles stay intact',async()=>{p.formAdmin('roles','R3');p.modal.model.menus=[...Object.keys(groups)];await p.saveForm();expect(p.db.roles.find(r=>r.id==='R3')?.menus).not.toContain('系统设置');await p.remove('roles',['R1']);expect(p.db.roles.some(r=>r.id==='R1')).toBe(true);p.formAdmin('roles');Object.assign(p.modal.model,{id:'CUSTOM',name:'自定义角色',menus:['系统首页'],sites:[]});await p.saveForm();expect(p.db.roles.some(r=>r.id==='CUSTOM')).toBe(true);await p.remove('roles',['CUSTOM']);expect(p.db.roles.some(r=>r.id==='CUSTOM')).toBe(false);});
  it('[PDF-204,PDF-205,PDF-206,PDF-207] user role/password edits and disable-before-delete are enforced',async()=>{p.formAdmin('users');Object.assign(p.modal.model,{id:'NEWUSER',name:'新用户',account:'demo.new',password:'new12345',role:'R1'});await p.saveForm();await p.remove('users',['NEWUSER']);expect(p.db.users.some(u=>u.id==='NEWUSER')).toBe(true);p.formAdmin('users','NEWUSER');Object.assign(p.modal.model,{enabled:false,password:'changed123'});await p.saveForm();expect(p.db.users.find(u=>u.id==='NEWUSER')?.password).toBe('changed123');await p.remove('users',['NEWUSER']);expect(p.db.users.some(u=>u.id==='NEWUSER')).toBe(false);p.formAdmin('roles','R0');p.modal.model.name='降级';await p.saveForm();expect(p.modal.error).toContain('最高权限');expect(p.superuser).toBe(true);});
  it('[PDF-208,PDF-210] user operations append attributable logs and role restrictions affect accessible menus',async()=>{const before=p.db.logs.length;await p.query();p.navigate('围栏检索');expect(p.db.logs.length).toBeGreaterThan(before);expect(p.db.logs[0]!.user).toBe('demo.admin');await p.logout();await p.login('demo.test','demo123');expect(p.allowed('用户管理')).toBe(false);expect(p.allowed('围栏检索')).toBe(true);});
});

 it('logout clears dialogs, imported rows, analysis selections and map state before another identity signs in',async()=>{
  p.formWatch();p.importScope('libraries');p.importState.rows=[{name:'sensitive'}];p.mapSelected=['old'];p.mapTrajectory=true;p.operands=['private'];p.setResult=['private'];p.setHasResult=true;p.statSelected=['old'];await p.logout();expect(p.modal.visible).toBe(false);expect(p.modal.model).toEqual({});expect(p.importState.rows).toEqual([]);expect(p.mapSelected).toEqual([]);expect(p.operands).toEqual([]);expect(p.setResult).toEqual([]);expect(p.statSelected).toEqual([]);await p.login('demo.analyst','demo123');expect(p.modal.visible).toBe(false);
 });
 it('[PDF-124,PDF-128] composite profile trajectories carry all capture types into the map',async()=>{
  const person=p.db.people[0]!,rows=p.db.captures.filter(c=>c.person===person.id);expect(new Set(rows.map(c=>c.type)).size).toBe(4);await p.dataAction('综合轨迹',rows);expect(p.f.type).toBe('综合');expect(p.f.target).toBe([...new Set(rows.map(c=>c.target))].join(','));expect(p.mapTrajectory).toBe(true);
 });

 it('[PDF-018,PDF-029,PDF-032,PDF-042,PDF-045,PDF-055,PDF-112] hidden dataset drawer adds the full filtered result rather than the unfiltered type',async()=>{
  for(const [page,type] of [['围栏检索','电围'],['车牌检索','车牌'],['MAC检索','MAC']]){const first=p.captureRows(type!)[0]!;p.navigate(page!,{target:first.target,site:first.site});const expected=p.captureRows(type!).filter(c=>c.target===first.target&&c.site===first.site);expect(p.currentCaptureResults().map(c=>c.id)).toEqual(expected.map(c=>c.id));p.addDataset(p.currentCaptureResults());p.modal.model.name='filtered '+type;await p.saveForm();expect(p.db.datasets.at(-1)!.items).toEqual(expected.map(c=>c.id));}
 });

import type {Database,Person} from '../domain';
import {dataTypes,uid} from '../domain';

const value=(row:Record<string,unknown>,key:string)=>String(row[key]??'').trim();
export function stageImport(database:Database,scope:string,records:Record<string,unknown>[],resourceId='',fileName=''):Database{
  if(!records.length)throw new Error('请先选择文件');
  const staged=structuredClone(database);
  if(scope==='libraries'){
    for(const row of records){
      const name=value(row,'name')||value(row,'库名');
      if(!name)throw new Error('库名不能为空');
      if(staged.libraries.some(l=>l.name===name))throw new Error('库名重复');
      let people:unknown=row.people??row.人员明细??[];
      if(typeof people==='string')people=JSON.parse(people);
      if(!Array.isArray(people))throw new Error('人员明细必须是数组');
      const ids:string[]=[];
      for(const person of people){
        if(!person||typeof person!=='object'||Array.isArray(person))throw new Error('人员资料格式错误');
        const record=person as Record<string,unknown>,sex=value(record,'sex')||'未知';
        if(!['男','女','未知'].includes(sex))throw new Error('性别仅允许男、女、未知');
        if(!value(record,'name'))throw new Error('人员姓名不能为空');
        const item:Person={id:uid(),name:value(record,'name'),sex,ethnic:value(record,'ethnic'),phone:value(record,'phone'),identity:value(record,'identity'),resident:value(record,'resident'),landing:value(record,'landing'),target:value(record,'target'),appearance:value(record,'appearance'),image:''};
        staged.people.push(item);ids.push(item.id);
      }
      staged.libraries.push({id:uid(),name,category:value(row,'category')||value(row,'分类')||'自建库',people:ids});
    }
  }else if(scope==='devices'){
    if(records.length!==1)throw new Error('该入口修改单台设备，文件必须只包含一条设备信息');
    const device=staged.devices.find(d=>d.id===resourceId);
    if(!device)throw new Error('设备不存在');
    const row=records[0]!;
    const serial=value(row,'serial');
    if(serial&&staged.devices.some(d=>d.serial===serial&&d.id!==device.id))throw new Error('序列号重复');
    if(value(row,'type')&&!dataTypes.includes(value(row,'type') as typeof dataTypes[number]))throw new Error('设备分类无效');
    if(value(row,'status')&&!['在线','离线'].includes(value(row,'status')))throw new Error('设备状态仅允许在线或离线');
    for(const key of ['name','serial','type','status','group'] as const)if(value(row,key))device[key]=value(row,key);
    if(value(row,'site')){
      const site=staged.sites.find(s=>s.name===value(row,'site'));
      if(!site)throw new Error('场所不存在');
      device.site=site.name;device.region=site.region;
    }
  }else if(scope==='datasets'){
    const captureRows=records.every(row=>value(row,'id')||value(row,'captureId')||value(row,'sourceCaptureId'));
    const validate=(ids:string[])=>{if(!ids.length)throw new Error('数据集没有采集记录');if(ids.some(id=>!staged.captures.some(c=>c.id===id)))throw new Error('数据集包含未知采集 ID');return [...new Set(ids)];};
    if(captureRows){
      const ids=validate(records.map(row=>value(row,'captureId')||value(row,'sourceCaptureId')||value(row,'id')));
      staged.datasets.push({id:uid(),name:fileName.replace(/\.[^.]+$/,'')||'导入采集数据集',items:ids,elementType:'CAPTURE'});
    }else{
      for(const row of records){const items=Array.isArray(row.items)?row.items.map(String):value(row,'items').split(/[,;\s]+/).filter(Boolean);staged.datasets.push({id:uid(),name:value(row,'name')||'导入集合',items:validate(items),elementType:'CAPTURE'});}
    }
  }else throw new Error('未定义此导入类型');
  return staged;
}

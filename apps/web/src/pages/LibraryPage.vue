<script setup lang="ts">
import {computed,ref,onMounted} from 'vue';
import {ElMessage} from 'element-plus';
import {usePlatform} from '../stores/platform';
import QueryForm from '../components/QueryForm.vue';
import DataTable from '../components/DataTable.vue';
import {exportRows} from '../utils/files';
import {apiMode,request} from '../api/client';
import {dictionaryOptions} from '../api/httpForms';
import {remote} from '../api/gateway';

const p=usePlatform();const categories=ref<{label:string;value:string}[]>();onMounted(async()=>{if(apiMode==='http'){try{const options=await dictionaryOptions('PersonLibraryInput.category');if(options)categories.value=[{label:'全部',value:'全部'},...options];}catch(e){ElMessage.error(e instanceof Error?e.message:String(e));}}});
const library=computed(()=>p.db.libraries.find(l=>l.id===p.detail?.id));
const rows=computed(()=>p.filterRows(p.db.libraries,p.f,{name:l=>l.name,category:l=>l.category}));
const people=computed(()=>p.db.people.filter(person=>library.value?.people.includes(person.id)));

function watchPerson(id:string){if(apiMode==='http'){const member=remote.members[id];if(!member){ElMessage.error('请刷新成员详情');return;}const type=member.target.targetType==='PLATE'?'车牌':member.target.targetType==='MAC'?'MAC':member.target.targetType==='FACE'?'人像':'电围';p.formWatch(undefined,[{id:member.memberId,type,target:member.displayName,targetId:member.target.targetId,targetType:member.target.targetType,time:new Date().toLocaleString('sv-SE',{timeZone:'Asia/Shanghai'}),imei:'',site:'',region:'',device:'',deviceId:'',person:id,score:0}]);}else p.formWatch(undefined,p.captureRows().filter(c=>c.person===id));}
async function removePerson(id:string){
  if(!library.value||!await p.confirm('确认从人员库移除此人员？'))return;
  try{
    if(apiMode==='http'){
      const member=remote.members[id];
      if(!member)throw new Error('成员详情尚未加载，请刷新页面');
      await request('removePersonLibraryMember',{path:{libraryId:library.value.id,memberId:member.memberId}});
      ElMessage.success('成员已移除');
      await p.reload();
      return;
    }
    library.value.people=library.value.people.filter(v=>v!==id);
    p.commit('库内人员已移除');
  }catch(e){ElMessage.error(e instanceof Error?e.message:String(e));}
}

async function exportLibraries(){
  try{
    if(apiMode==='http'){
      const libraryIds=p.chosen.map(row=>String(row.id));
      const r=await request('exportPersonLibraries',{body:{libraryIds,format:'XLSX',reason:'导出选中的人员库'}});
      ElMessage.success('导出任务已提交：'+r.data.taskId+'；完成后到任务中心下载');
      return;
    }
    await exportRows('人员库',p.chosen.map(l=>({库名:l.name,分类:l.category,人员明细:JSON.stringify(p.db.people.filter(person=>(l.people as string[]).includes(person.id)))})));
  }catch(e){ElMessage.error(e instanceof Error?e.message:String(e));}
}
</script>
<template>
  <template v-if="library">
    <section class="card section-head"><h3>{{library.name}}</h3><el-button @click="p.formLibrary(library.id)">编辑库信息</el-button><el-button type="primary" @click="p.formPerson(library.id)">新增人员</el-button></section>
    <DataTable :rows="people" :columns="[{key:'name',label:'姓名',link:true},{key:'sex',label:'性别'},{key:'phone',label:'手机号码'},{key:'identity',label:'身份证号码'},{key:'target',label:'特征码'}]" title="人员详情" @select="p.chosen=$event" @open="row=>p.info('人员信息',Object.entries(row))">
      <template #rowActions="{row}"><el-button size="small" link type="primary" @click="p.formPerson(library.id,String(row.id))">编辑</el-button><el-button size="small" link type="danger" @click="removePerson(String(row.id))">删除</el-button><el-button size="small" link type="primary" @click="watchPerson(String(row.id))">布控</el-button></template>
    </DataTable>
  </template>
  <template v-else>
    <QueryForm :fields="[{key:'name',label:'库名'},{key:'category',label:'分类',options:apiMode==='http'?categories:['全部','重点人员','常驻人员','自建库']}]" :model="p.f" @search="p.query" @reset="p.reset"/>
    <div class="actions"><el-button type="primary" @click="p.formLibrary()">新增</el-button><el-button @click="p.remove('libraries')">删除</el-button></div>
    <DataTable :rows="rows.map(l=>({...l,count:l.memberCount??l.people.length}))" :columns="[{key:'name',label:'库名',link:true},{key:'category',label:'分类'},{key:'count',label:'人员数量'}]" title="人员库" @select="p.chosen=$event" @open="row=>p.openDetail('library',String(row.id))">
      <template #actions><el-button @click="p.importScope('libraries')">导入</el-button><el-button :disabled="!p.chosen.length" @click="exportLibraries">导出</el-button></template>
    </DataTable>
  </template>
</template>

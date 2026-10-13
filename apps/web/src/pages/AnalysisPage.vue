<script setup lang="ts">
import {computed} from 'vue';
import {usePlatform} from '../stores/platform';
import {dataTypes,filterRows} from '../domain';
import type {Capture} from '../domain';
import QueryForm from '../components/QueryForm.vue';
import DataTable from '../components/DataTable.vue';
import {exportRows} from '../utils/files';import {apiMode} from '../api/client';
const p=usePlatform();const task=computed(()=>p.db.analysis[p.active]?.find(t=>t.id===p.detail?.id));
const rows=computed(()=>p.filterRows(p.db.analysis[p.active]||[],p.f,{name:r=>r.name,start:r=>r.date,end:r=>r.date}));
const tabs=computed<readonly string[]>(()=>p.active==='区域统计'?dataTypes.filter(t=>t!=='人像'):dataTypes);
const results=computed(()=>task.value?p.analysisResults(task.value,tabs.value.includes(p.resultTab as typeof dataTypes[number])?p.resultTab:'电围',p.active):[]);
async function exportDetails(){if(!task.value)return;if(apiMode==='http'){await p.exportAnalysis(task.value.id);return;}const selected=new Set(p.chosen.map(row=>String(row.id)));await exportRows(task.value.name,results.value.filter(r=>selected.has(r.id)).map(r=>({目标:r.target,采集总次数:r.count,经过站点:r.stations,经过任务选区:r.visited.join('、'),匹配度:r.score})));}
async function exportTasks(){if(apiMode==='http'){await p.exportAnalysis();return;}await exportRows(p.active,p.chosen);}
</script>
<template><template v-if="task"><section class="card"><div class="section-head"><h3>{{task.name}}</h3><el-button @click="p.formAnalysis(p.active,task.id)">任务编辑</el-button></div><p>目标：{{task.target||'未知目标'}} · {{task.start}} 至 {{task.end}}</p><p>任务选区：{{task.regions.join('、')}} · {{p.active==='区域统计'?'匹配度 = 经过任务选区数 / 任务选区总数':apiMode==='mock'?'关联度 = Mock 满足条件比例':'关联度由后端模型返回'}} · 最低 {{task.threshold}}%</p><el-tabs v-model="p.resultTab"><el-tab-pane v-for="tab in tabs" :key="tab" :label="tab" :name="tab"/></el-tabs></section><DataTable :rows="results" :columns="[{key:'target',label:'目标标识'},{key:'count',label:'采集总次数'},{key:'stations',label:'经过站点'},{key:'visited',label:'经过任务选区'},{key:'score',label:p.active==='区域统计'?'匹配度（%）':'关联度（%）'}]" title="任务结果" @select="p.chosen=$event"><template #actions><el-button :disabled="!p.chosen.length" @click="exportDetails()">导出</el-button></template><template #rowActions="{row}"><el-button v-for="action in ['检索','轨迹','伴随','布控']" :key="action" size="small" link type="primary" @click="apiMode==='http'?p.analysisAction(action,String(row.id)):p.dataAction(action,row.captures as Capture[])">{{action}}</el-button></template></DataTable></template><template v-else><QueryForm :model="p.f" :fields="[{key:'name',label:'任务名称'},{key:'start',label:'分析日期开始',type:'date'},{key:'end',label:'分析日期结束',type:'date'}]" @search="p.query" @reset="p.reset"/><div class="actions"><el-button type="primary" @click="p.formAnalysis(p.active)">{{p.active==='伴随任务'?'新增伴随分析':'新增'}}</el-button><el-button @click="p.removeAnalysis">删除</el-button></div><DataTable :rows="rows" :columns="[{key:'name',label:'任务名称',link:true},{key:'target',label:'目标'},{key:'date',label:'分析日期'},{key:'start',label:'分析开始'},{key:'end',label:'分析结束'},{key:'status',label:'状态'}]" title="任务列表" @select="p.chosen=$event" @open="row=>p.openDetail('analysis',String(row.id))"><template #actions><el-button :disabled="!p.chosen.length" @click="exportTasks()">导出</el-button></template></DataTable></template></template>


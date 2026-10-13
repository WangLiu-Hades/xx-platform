<script setup lang="ts">
import {ref,onMounted,watch,computed} from 'vue';
import {request} from '../api/client';
import {remote,reportCodes} from '../api/gateway';
import {localTime} from '../domain';
import ChartPanel from './ChartPanel.vue';

const props=defineProps<{reportId:string}>();
const error=ref(''),loading=ref(false);
const detail=computed(()=>remote.reports[props.reportId]);
const preview=computed(()=>remote.reportPreview?.reportId===props.reportId?remote.reportPreview.preview:null);
const details=computed(()=>{const p=preview.value;if(!p)return [];
 if(p.previewKind==='ONLINE_RATE')return [['设备总数',p.deviceCount],['在线设备',p.onlineCount],['在线率',(p.onlineRate*100).toFixed(1)+'%'],['区域代码',p.regionCode]];
 if(p.previewKind==='CAPTURE')return [['采集设备数',p.deviceCount],['采集记录数',p.captureCount],['区域代码',p.regionCode]];
 if(p.previewKind==='SYSTEM')return [['系统组件数',p.components.length]];
 return [['设备数',p.items.length],['区域代码',p.regionCode]];
});
const rows=computed<Record<string,unknown>[]>(()=>{const p=preview.value;if(!p)return [];
 if(p.previewKind==='ONLINE_RATE')return p.items.map(item=>({...item,onlineRate:(item.onlineRate*100).toFixed(1)+'%'}));
 if(p.previewKind==='DEVICE_STATUS')return p.items.map(item=>({设备名称:item.name,序列号:item.serialNumber,状态:item.status,场所:item.siteName||item.siteId}));
 if(p.previewKind==='SYSTEM')return p.components.map(item=>({组件:item.label,状态:item.status,检测时间:localTime(item.checkedAt)}));
 return p.metrics.map(item=>({指标:item.label,数值:item.value,单位:item.unit,状态:item.status}));
});
const columns=computed(()=>Object.keys(rows.value[0]||{}));
const chartRows=computed(()=>{const p=preview.value;if(p?.previewKind!=='CAPTURE')return [];
 const dates=[...new Set(p.operatorSeries.flatMap(s=>s.points.map(point=>point.at)))].sort();
 return dates.map(at=>Object.fromEntries([['日期',localTime(at)],...p.operatorSeries.map(s=>[s.operatorLabel,s.points.find(point=>point.at===at)?.value??null])]));
});
const chartLabels=computed(()=>preview.value?.previewKind==='CAPTURE'?preview.value.operatorSeries.map(s=>s.operatorLabel):[]);
const reportLabel=computed(()=>{const code=preview.value?.reportType;return Object.entries(reportCodes).find(([,value])=>value===code)?.[0]||code||'';});
async function load(){loading.value=true;error.value='';try{const r=await request('getReport',{path:{reportId:props.reportId}});remote.reports[props.reportId]=r.data;remote.versions[props.reportId]=r.data.version;remote.reportPreview=null;if(r.data.mode==='SINGLE'&&r.data.status==='SUCCESS')remote.reportPreview=(await request('getReportPreview',{path:{reportId:props.reportId},query:{pageSize:50}})).data;}catch(e){error.value=e instanceof Error?e.message:String(e);}finally{loading.value=false;}}
onMounted(load);watch(()=>props.reportId,load);
</script>
<template><div v-loading="loading"><el-alert v-if="error" type="error" :title="error" :closable="false"/><template v-else-if="detail"><p class="muted">{{detail.name}} · {{detail.mode==='BUNDLE'?'多报表 ZIP':'单报表 XLSX'}} · {{detail.status}} · 任务进度 {{detail.progress}}%</p><el-empty v-if="detail.mode==='BUNDLE'" description="多报表集合无单独预览；完成后下载 ZIP"/><el-empty v-else-if="detail.status!=='SUCCESS'" description="报表尚未生成成功，请稍后刷新"/><template v-else-if="preview"><p class="muted">统计时间 {{localTime(preview.asOf)}} · {{reportLabel}}</p><div class="report-metrics"><span v-for="[label,value] in details" :key="label">{{label}}：{{value}}</span></div><ChartPanel v-if="preview.previewKind==='CAPTURE'&&chartRows.length" title="运营商分时采集" :rows="chartRows" :labels="chartLabels"/><el-table :data="rows" border stripe><el-table-column v-for="key in columns" :key="key" :prop="key" :label="key"/></el-table><p v-if="'nextCursor' in preview&&preview.nextCursor" class="muted">仅显示当前预览页；完整内容请下载报表。</p></template></template></div></template>

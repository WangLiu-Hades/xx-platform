<script setup lang="ts">
import {computed} from 'vue';
import {remote,labels} from '../api/gateway';
import {localTime} from '../domain';
import ChartPanel from './ChartPanel.vue';
import {usePlatform} from '../stores/platform';

const p=usePlatform();
const d=computed(()=>remote.dashboard);
const display=(value:number|null|undefined,unit='')=>value===null||value===undefined?'暂无数据':value.toLocaleString('zh-CN')+unit;
const rate=(value:number|null|undefined)=>value===null||value===undefined?'暂无数据':(value*100).toFixed(1)+'%';
const meta=(value:{asOf:string|null;status:string;unit:string})=>value.status==='READY'?'截至 '+(value.asOf?localTime(value.asOf):'时间未提供')+' · '+value.unit:labels[value.status]||value.status;
type Series={series:{label:string;points:{at:string;value:number|null}[]}[]};
function chartRows(metric:Series|undefined){if(!metric)return [];
 const dates=[...new Set(metric.series.flatMap(series=>series.points.map(point=>point.at)))].sort();
 return dates.map(at=>Object.fromEntries([['日期',localTime(at)],...metric.series.map(series=>[series.label,series.points.find(point=>point.at===at)?.value??null])]));}
const rank=computed(()=>d.value?.topSitesByDataType.find(r=>r.dataType===p.f.rankType)?.ranking||d.value?.topSitesByDataType[0]?.ranking);
const dedup=computed(()=>d.value?.deduplicatedSeries.find(r=>r.dataType===p.f.dedupType&&r.originClass===p.f.dedupClass)?.metric||d.value?.deduplicatedSeries[0]?.metric);
</script>
<template><div v-if="!d" class="card"><el-empty description="正在等待后端首页指标"/></div><div v-else class="dashboard-layout">
 <div class="stack">
  <section class="card"><h3>设备在线数量</h3><p class="muted">{{meta(d.onlineDevices.meta)}}</p><div class="stat">总数 / 在线 <b>{{display(d.onlineDevices.total)}} / {{display(d.onlineDevices.online)}}</b></div><div class="stat">在线率 <b>{{rate(d.onlineDevices.onlineRate)}}</b></div><div v-for="item in d.onlineDevices.byPowerCategory" :key="item.powerCategoryCode" class="stat">{{item.label}}<b>{{display(item.online)}} / {{display(item.total)}} · {{rate(item.onlineRate)}}</b></div></section>
  <ChartPanel title="近 7 天采集数据" :labels="d.lastSevenDaysCapture.series.map(s=>s.label)" :rows="chartRows(d.lastSevenDaysCapture)" :unit="d.lastSevenDaysCapture.meta.unit"/>
  <section class="card"><div class="section-head"><h3>采集场所前五排行</h3><el-select v-model="p.f.rankType" placeholder="设备类型"><el-option v-for="item in d.topSitesByDataType" :key="item.dataType" :value="item.dataType" :label="labels[item.dataType]||item.dataType"/></el-select></div><p class="muted">{{rank?meta(rank.meta):'暂无数据'}}</p><ol class="ranking"><li v-for="(item,i) in rank?.items.slice(0,5)||[]" :key="item.key"><span>{{i+1}}. {{item.label}}</span><b>{{display(item.value)}}</b></li></ol></section>
 </div><div class="stack">
  <section class="card"><h3>当日采集 / 累计采集</h3><p class="muted">{{meta(d.todayAndTotalCapture.meta)}}</p><div class="totals"><div><p>当日采集（源端）</p><strong>{{display(d.todayAndTotalCapture.todayCaptured)}}</strong></div><div><p>累计采集（源端）</p><strong>{{display(d.todayAndTotalCapture.allCaptured)}}</strong></div></div><div class="stat">当日入库 / 累计入库 <b>{{display(d.todayAndTotalCapture.todayIngested)}} / {{display(d.todayAndTotalCapture.allIngested)}}</b></div></section>
  <div class="two-column"><div class="stack"><section class="card"><h3>设备累计采集（已入库）</h3><p class="muted">{{meta(d.deviceCaptureTotals.meta)}}</p><div v-for="item in d.deviceCaptureTotals.items" :key="item.code" class="stat">{{item.label}}<b>{{display(item.count)}}</b></div></section><section class="card"><h3>设备布控告警次数</h3><p class="muted">{{meta(d.deviceAlertCounts.meta)}}</p><div v-for="item in d.deviceAlertCounts.items" :key="item.deviceId" class="stat">{{item.deviceName}}<b>{{display(item.count)}}</b></div></section></div><section class="card"><h3>设备分布</h3><p class="muted">{{meta(d.deviceMap.meta)}}</p><div class="stat">设备 / 场所 <b>{{display(d.deviceMap.deviceCount)}} / {{display(d.deviceMap.siteCount)}}</b></div><el-button @click="p.navigate('设备分布')">打开设备地图</el-button></section></div>
  <section class="card"><div class="section-head"><h3>一周去重后采集数据</h3><el-select v-model="p.f.dedupType" placeholder="数据类型"><el-option v-for="item in d.deduplicatedSeries" :key="item.dataType" :value="item.dataType" :label="labels[item.dataType]||item.dataType"/></el-select><el-select v-model="p.f.dedupClass" placeholder="归属分类"><el-option v-for="item in d.deduplicatedSeries" :key="item.originClass" :value="item.originClass" :label="item.originClass"/></el-select></div><ChartPanel title="去重采集趋势" :labels="dedup?.series.map(s=>s.label)||[]" :rows="chartRows(dedup)" :unit="dedup?.meta.unit||'条'"/></section>
  <section class="card"><h3>数据归属地前十位排行</h3><p class="muted">{{meta(d.originTopTen.meta)}}</p><div class="origin-rank"><span v-for="item in d.originTopTen.items.slice(0,10)" :key="item.key">{{item.label}} <b>{{display(item.value)}}</b></span></div></section>
 </div><div class="stack"><section class="card"><h3>应用入口</h3><div class="app-entries"><el-button v-for="entry in d.applicationEntries" :key="entry.code" @click="p.navigate(entry.label)">{{entry.label}}</el-button></div></section><ChartPanel title="电围分类统计" :labels="d.fenceRadioStatistics.series.map(s=>s.label)" :rows="chartRows(d.fenceRadioStatistics)" :unit="d.fenceRadioStatistics.meta.unit"/><ChartPanel title="一周操作统计" :labels="d.operationStatistics.series.map(s=>s.label)" :rows="chartRows(d.operationStatistics)" :unit="d.operationStatistics.meta.unit"/></div>
</div></template>

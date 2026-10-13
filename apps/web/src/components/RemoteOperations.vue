<script setup lang="ts">
import {computed} from 'vue';
import {remote,labels} from '../api/gateway';
import {localTime} from '../domain';
import ChartPanel from './ChartPanel.vue';
import {usePlatform} from '../stores/platform';

const p=usePlatform();
const d=computed(()=>remote.operations);
const display=(value:number|null|undefined)=>value===null||value===undefined?'暂无数据':value.toLocaleString('zh-CN');
const rate=(value:number|null|undefined)=>value===null||value===undefined?'暂无数据':(value*100).toFixed(1)+'%';
const meta=(value:{asOf:string|null;status:string;unit:string})=>value.status==='READY'?'截至 '+(value.asOf?localTime(value.asOf):'时间未提供')+' · '+value.unit:labels[value.status]||value.status;
type Points={code:string;points:{at:string;value:number|null}[]};
function pointsRows(series:Points[]){const dates=[...new Set(series.flatMap(s=>s.points.map(point=>point.at)))].sort();return dates.map(at=>Object.fromEntries([['日期',localTime(at)],...series.map(s=>[s.code,s.points.find(point=>point.at===at)?.value??null])]));}
function captureRows(series:{label:string;points:{at:string;value:number|null}[]}[]){return pointsRows(series.map(s=>({...s,code:s.label})));}
const modem=computed(()=>{const m=d.value?.smsModem;return m?[['运行时间',m.uptimeSeconds===null?'暂无数据':Math.floor(m.uptimeSeconds/3600)+' 小时'],['信号质量',m.signalQuality??'暂无数据'],['信号强度',m.signalStrength??'暂无数据'],['运营商类型',m.carrier??'暂无数据'],['网络注册数量',display(m.networkRegistrationCount)],['SIM 卡状态',m.simStatus??'暂无数据'],['成功发送条数',display(m.sentSuccessCount)],['发送量总数',display(m.sentTotalCount)],...(m.simBalanceDisplay?[['SIM 卡账户余额',m.simBalanceDisplay]]:[])]:[];});
</script>
<template><div v-if="!d" class="card"><el-empty description="正在等待后端运维指标"/></div><template v-else>
 <div class="operations-top"><section class="card"><h3>设备在线情况</h3><p class="muted">{{meta(d.deviceOnline.meta)}}</p><div class="totals"><div><p>设备总数</p><strong>{{display(d.deviceOnline.total)}}</strong></div><div><p>在线设备</p><strong>{{display(d.deviceOnline.online)}}</strong></div><div><p>总在线率</p><strong>{{rate(d.deviceOnline.onlineRate)}}</strong></div></div><div v-for="item in d.deviceOnline.byDeviceType" :key="item.deviceType" class="stat">{{labels[item.deviceType]||item.deviceType}}<b>{{display(item.online)}} / {{display(item.total)}}</b></div></section>
 <section class="card"><h3>今日采集 / 累计采集</h3><p class="muted">{{meta(d.captureOverview.meta)}}</p><div class="stat">当日采集 / 累计采集 <b>{{display(d.captureOverview.todayCaptured)}} / {{display(d.captureOverview.allCaptured)}}</b></div><div class="stat">当日入库 / 累计入库 <b>{{display(d.captureOverview.todayIngested)}} / {{display(d.captureOverview.allIngested)}}</b></div><div v-for="type in d.byDataType" :key="type.dataType" class="stat">{{labels[type.dataType]||type.dataType}}<b>{{display(type.totals.todayCaptured)}} / {{display(type.totals.allCaptured)}}</b></div></section>
 <section class="card"><h3>短信猫状态</h3><p class="muted">{{meta(d.smsModem.meta)}}</p><dl class="modem"><template v-for="r in modem" :key="r[0]"><dt>{{r[0]}}</dt><dd>{{r[1]}}</dd></template></dl></section></div>
 <section class="card section-head"><h3>数据统计图</h3><div class="spacer"/><span class="muted">时间范围 {{localTime(d.timeRange.from)}} 至 {{localTime(d.timeRange.to)}} · {{labels[d.timeGranularity]||d.timeGranularity}}</span><el-select v-model="p.f.granularity" placeholder="统计时间范围" @change="p.query"><el-option v-for="item in [['WEEK','近一周'],['MONTH','近一个月'],['QUARTER','近一个季度'],['YEAR','近一年']]" :key="item[0]" :value="item[0]" :label="item[1]"/></el-select></section>
 <div v-for="type in d.byDataType" :key="type.dataType" class="stack"><h2 class="type-heading">{{labels[type.dataType]||type.dataType}}数据</h2><div class="two-column"><ChartPanel v-for="series in type.captureSeries" :key="series.metricCode" :title="series.metricCode+'统计图'" :labels="series.metric.series.map(s=>s.label)" :rows="captureRows(series.metric.series)" :unit="series.metric.meta.unit"/><ChartPanel title="异常设备统计图" :labels="type.anomalies.deviceAnomalies.map(s=>s.code)" :rows="pointsRows(type.anomalies.deviceAnomalies)" :unit="type.anomalies.meta.unit"/><ChartPanel title="异常数据统计图" :labels="type.anomalies.dataAnomalies.map(s=>s.code)" :rows="pointsRows(type.anomalies.dataAnomalies)" :unit="type.anomalies.meta.unit"/><ChartPanel title="数据量异常设备统计图" :labels="type.anomalies.volumeAnomalies.map(s=>s.code)" :rows="pointsRows(type.anomalies.volumeAnomalies)" :unit="type.anomalies.meta.unit"/></div></div>
</template></template>

<script setup lang="ts">
import {ref,onMounted,onBeforeUnmount,watch,nextTick} from 'vue';
import * as echarts from 'echarts/core';
import {LineChart,BarChart} from 'echarts/charts';
import {GridComponent,LegendComponent,TooltipComponent,AriaComponent} from 'echarts/components';
import {CanvasRenderer} from 'echarts/renderers';
import {exportRows} from '../utils/files';
import {apiMode} from '../api/client';
echarts.use([LineChart,BarChart,GridComponent,LegendComponent,TooltipComponent,AriaComponent,CanvasRenderer]);
const props=withDefaults(defineProps<{title:string;labels?:string[];count?:number;scale?:number;rows?:Record<string,unknown>[];exportable?:boolean;unit?:string}>(),{labels:()=>['采集量'],count:7,scale:1,unit:'条'});
const host=ref<HTMLElement>(),hidden=ref<string[]>([]);let chart:echarts.ECharts|undefined,resize:ResizeObserver|undefined;
function data(){if(props.rows)return props.rows;if(apiMode==='http')return [];return Array.from({length:props.count},(_,i)=>Object.fromEntries([['日期',props.count===12?'2026-'+String(i+1).padStart(2,'0'):new Date(Date.UTC(2026,9,7-props.count+i+1)).toISOString().slice(0,10)],...props.labels.map((l,j)=>[l,(40+(i*13+j*19)%90)*props.scale])]));}
function render(){
  if(!host.value){resize?.disconnect();chart?.dispose();chart=undefined;return;}
  if(!chart||chart.getDom()!==host.value){resize?.disconnect();chart?.dispose();chart=echarts.init(host.value);resize=new ResizeObserver(()=>chart?.resize());resize.observe(host.value);}
  chart.setOption({aria:{enabled:true},animation:false,tooltip:{trigger:'axis'},legend:{show:false,data:props.labels,selected:Object.fromEntries(props.labels.map(label=>[label,!hidden.value.includes(label)]))},grid:{left:48,right:18,top:24,bottom:42},xAxis:{type:'category',data:data().map(r=>r.日期)},yAxis:{type:'value',name:props.unit},series:props.labels.map(label=>({name:label,type:'line',smooth:true,showSymbol:true,data:data().map(r=>r[label]??null)}))},true);
}
function toggle(label:string){hidden.value=hidden.value.includes(label)?hidden.value.filter(item=>item!==label):[...hidden.value,label];render();}
onMounted(render);watch(()=>[props.labels,props.count,props.scale,props.rows],async()=>{await nextTick();render();},{deep:true});onBeforeUnmount(()=>{resize?.disconnect();chart?.dispose();});
</script>
<template><section class="card chart-card"><div class="section-head"><h3>{{title}}</h3><slot/><div class="spacer"/><el-button v-if="exportable&&apiMode==='mock'" link type="primary" @click="exportRows(title,data())">下载报表</el-button></div><div v-if="labels.length>1" class="chart-legend" role="group" :aria-label="title+'图例'"><button v-for="(label,i) in labels" :key="label" :aria-label="'切换'+label" :aria-pressed="!hidden.includes(label)" :class="{inactive:hidden.includes(label)}" @click="toggle(label)"><span :style="{color:['#5470c6','#91cc75','#fac858','#ee6666'][i%4]}">●</span> {{label}}</button></div><div v-if="apiMode==='http'&&!data().length" class="chart"><el-empty description="指标暂不可用" :image-size="54"/></div><div v-else ref="host" class="chart" role="img" :aria-label="title+'，单位：'+unit"/><p class="muted chart-meta">单位：{{unit}} · {{apiMode==='mock'?'Mock 演示指标':'后端返回指标'}}</p></section></template>
<style scoped>.chart-legend{display:flex;justify-content:center;gap:12px;flex-wrap:wrap;padding-top:10px}.chart-legend button{border:0;background:transparent;color:#526377;font:inherit;font-size:12px;cursor:pointer;padding:3px}.chart-legend button.inactive{opacity:.4}.chart-legend button:focus-visible{outline:2px solid #1769aa;outline-offset:2px}</style>

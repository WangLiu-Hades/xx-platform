<script setup lang="ts">
import {ref,onMounted,onBeforeUnmount,watch,computed} from 'vue';
import Map from 'ol/Map';import View from 'ol/View';import Feature from 'ol/Feature';import Polygon from 'ol/geom/Polygon';import Point from 'ol/geom/Point';import VectorSource from 'ol/source/Vector';import VectorLayer from 'ol/layer/Vector';import DragBox from 'ol/interaction/DragBox';import {defaults as interactions} from 'ol/interaction/defaults';import {fromLonLat} from 'ol/proj';import {Style,Fill,Stroke,Circle} from 'ol/style';
import type {components} from '../api/schema';import {usePlatform} from '../stores/platform';
type Region=components['schemas']['GeoPolygon'];
const props=defineProps<{modelValue:unknown}>(),emit=defineEmits<{ 'update:modelValue':[regions:Region[]]}>();
const platform=usePlatform(),host=ref<HTMLElement>(),drawing=ref(false),regions=computed(()=>Array.isArray(props.modelValue)?props.modelValue as Region[]:[]);
const source=new VectorSource(),devices=new VectorSource();let map:Map|undefined,draw:DragBox|undefined,observer:ResizeObserver|undefined;
function refresh(){source.clear();source.addFeatures(regions.value.map(region=>new Feature(new Polygon(region.coordinates).transform('EPSG:4326','EPSG:3857'))));}
onMounted(()=>{
 const points=platform.db.devices.filter(d=>d.longitude!=null&&d.latitude!=null).map(d=>new Feature(new Point(fromLonLat([d.longitude!,d.latitude!]))));devices.addFeatures(points);
 map=new Map({target:host.value,layers:[new VectorLayer({source:devices,style:new Style({image:new Circle({radius:5,fill:new Fill({color:'#1769aa'})})})}),new VectorLayer({source,style:new Style({fill:new Fill({color:'rgba(23,105,170,.15)'}),stroke:new Stroke({color:'#1769aa',width:2})})})],view:new View({center:fromLonLat([0,0]),zoom:2,minZoom:1,maxZoom:18}),interactions:interactions({dragPan:false})});
 const extent=devices.getExtent();if(points.length&&extent)map.getView().fit(extent,{padding:[30,30,30,30],maxZoom:13});
 draw=new DragBox({condition:()=>drawing.value});draw.setActive(false);map.addInteraction(draw);
 draw.on('boxend',()=>{const polygon=draw?.getGeometry();if(!polygon)return;const geometry=polygon.clone().transform('EPSG:3857','EPSG:4326');emit('update:modelValue',[...regions.value,{type:'Polygon',coordinates:geometry.getCoordinates()}]);drawing.value=false;});
 observer=new ResizeObserver(()=>map?.updateSize());if(host.value)observer.observe(host.value);refresh();
});
watch(regions,refresh,{deep:true});watch(drawing,value=>draw?.setActive(value));onBeforeUnmount(()=>{observer?.disconnect();map?.dispose();});
</script>
<template><div class="region-picker"><div class="actions"><el-button :aria-pressed="drawing" :type="drawing?'primary':'default'" @click="drawing=!drawing">框选任务区域</el-button><el-button :disabled="!regions.length" @click="emit('update:modelValue',[])">清空选区</el-button><span>已选择 {{regions.length}} 个区域</span></div><div ref="host" class="region-map" aria-label="任务区域选取地图" role="img"/><p class="muted">在地图上拖动选择矩形区域；蓝点为已加载的设备。坐标采用 WGS84，底图由后续授权地图服务提供。</p><div class="region-list"><el-tag v-for="(_,i) in regions" :key="i" closable @close="emit('update:modelValue',regions.filter((_,index)=>index!==i))">任务选区 {{i+1}}</el-tag></div></div></template>
<style scoped>.region-picker{width:100%}.region-map{height:300px;background:#eef4f8;border:1px solid var(--el-border-color);border-radius:6px;margin-top:10px}.region-list{display:flex;gap:8px;flex-wrap:wrap}</style>

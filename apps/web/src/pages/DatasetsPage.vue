<script setup lang="ts">
import {computed,ref,onMounted} from 'vue';
import {ElMessage} from 'element-plus';
import {usePlatform} from '../stores/platform';
import DataTable from '../components/DataTable.vue';
import {exportRows} from '../utils/files';
import {apiMode,request} from '../api/client';
import {captureView} from '../api/gateway';
import type {Capture} from '../domain';

const p=usePlatform();
const dataset=computed(()=>p.db.datasets.find(d=>d.id===p.selectedDataset));
const detailRows=ref<Capture[]>([]),nextCursor=ref<string|null>(null),detailError=ref('');let detailEpoch=0;
const rows=computed(()=>apiMode==='http'?detailRows.value:p.captureRows().filter(c=>(p.setHasResult?p.setResult:dataset.value?.items||[]).includes(c.id)));
async function selectDataset(id:string,next=false){
  const epoch=++detailEpoch;p.selectedDataset=id;p.setHasResult=false;if(!next)detailRows.value=[];detailError.value='';
  if(apiMode!=='http')return;
  try{const r=await request('listDatasetItems',{path:{datasetId:id},query:{pageSize:100,cursor:next?nextCursor.value||undefined:undefined}});if(epoch!==detailEpoch||p.selectedDataset!==id)return;const rows=r.data.items.map(item=>item.elementType==='CAPTURE'?captureView(item.capture,p.db):{id:item.itemId,type:item.target.targetType==='PLATE'?'车牌':item.target.targetType==='MAC'?'MAC':item.target.targetType==='FACE'?'人像':'电围',target:item.displayTarget,targetId:item.target.targetId,targetType:item.target.targetType,time:new Date(item.addedAt).toLocaleString('sv-SE',{timeZone:'Asia/Shanghai'}),imei:'',site:'',region:'',device:'',deviceId:'',person:'',score:0});detailRows.value=next?[...detailRows.value,...rows]:rows;nextCursor.value=r.data.nextCursor;}catch(e){if(epoch===detailEpoch){detailError.value=e instanceof Error?e.message:String(e);ElMessage.error(detailError.value);}}
}
onMounted(()=>{if(apiMode==='http'&&p.selectedDataset)void selectDataset(p.selectedDataset);});
function operand(id:string){if(p.operands.length<26)p.operands.push(id);}
function drop(e:DragEvent){const id=e.dataTransfer?.getData('application/x-dataset');if(id&&p.db.datasets.some(d=>d.id===id))operand(id);}
async function exportDataset(){
  try{
    if(apiMode==='http'){
      if(!dataset.value)throw new Error('请先选择数据集');
      const r=await request('exportDataset',{path:{datasetId:dataset.value.id},body:{format:'XLSX'}});
      ElMessage.success('导出任务已提交：'+r.data.taskId+'；完成后到任务中心下载');
      return;
    }
    await exportRows('数据集详情',rows.value);
  }catch(e){ElMessage.error(e instanceof Error?e.message:String(e));}
}
</script>
<template>
  <div class="actions"><el-button type="primary" @click="p.addDataset(apiMode==='http'?[]:p.captureRows())">添加数据集</el-button><el-button @click="p.importScope('datasets')">导入数据集</el-button><el-button @click="p.info('运算说明',[['操作流程','拖动左侧数据集至右侧运算框；键盘用户可点击“加入运算”。'],['运算顺序','交集 ∩、并集 ∪、差集 \\ 或 - 优先级相同，按从左到右计算；括号优先。'],['例子','(A ∩ B) ∪ C；不能混合采集记录与目标数据集。']])">操作说明</el-button></div>
  <el-alert v-if="detailError" type="error" :title="detailError" :closable="false"/><div class="dataset-layout">
    <aside class="card"><h3>数据集列表</h3><article v-for="d in p.db.datasets" :key="d.id" class="dataset-item" draggable="true" @dragstart="$event.dataTransfer?.setData('application/x-dataset',d.id)"><el-button link type="primary" @click="selectDataset(d.id)">{{d.name}}</el-button><span class="muted">{{apiMode==='http'?d.itemCount??'—':d.items.length}} 条</span><div class="actions"><el-button size="small" @click="operand(d.id)">加入运算</el-button><el-button size="small" type="danger" @click="p.remove('datasets',[d.id])">删除</el-button></div></article></aside>
    <div class="stack"><section class="card"><h3>数据集运算</h3><div class="drop-zone" @dragover.prevent @drop.prevent="drop"><span v-if="!p.operands.length">将数据集拖到这里</span><el-tag v-for="(id,i) in p.operands" :key="i" closable @close="p.operands.splice(i,1)">{{String.fromCharCode(65+i)}}：{{p.db.datasets.find(d=>d.id===id)?.name}}</el-tag></div><label>运算表达式<el-input v-model="p.expression" aria-label="运算表达式"/></label><div class="actions"><el-button v-for="op in ['∩','∪','\\']" :key="op" @click="p.expression+= ' '+op+' '">{{op==='∩'?'交集':op==='∪'?'并集':'差集'}}</el-button><el-button type="primary" @click="p.runSets">开始运算</el-button><el-button @click="p.operands=[];p.expression='A ∩ B';p.setHasResult=false">清空</el-button><el-button :disabled="!p.setHasResult" @click="p.addDataset(rows)">保存结果数据集</el-button></div></section><DataTable :rows="rows" :columns="[{key:'time',label:'采集时间'},{key:'target',label:'目标标识'},{key:'type',label:'类型'},{key:'site',label:'场所'},{key:'device',label:'设备'}]" :title="p.setHasResult?'运算结果':dataset?.name||'数据详情'" @select="p.chosen=$event"><template #actions><el-button :disabled="apiMode==='http'&&!dataset" @click="exportDataset">导出</el-button></template><template #rowActions="{row}"><el-dropdown><el-button size="small">二次操作</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item v-for="a in ['复制','检索','轨迹','伴随','布控','位置追踪']" :key="a" @click="p.dataAction(a,[row as Capture])">{{a}}</el-dropdown-item></el-dropdown-menu></template></el-dropdown></template></DataTable><el-button v-if="apiMode==='http'&&nextCursor&&dataset" @click="selectDataset(dataset.id,true)">加载更多数据集记录</el-button></div>
  </div>
</template>

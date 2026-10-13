<script setup lang="ts">
import {computed,ref,watch} from 'vue';
import {apiMode} from '../api/client';
import {remote} from '../api/gateway';
import {usePlatform} from '../stores/platform';
export type Column={key:string;label:string;link?:boolean;width?:number};
const props=withDefaults(defineProps<{rows:Record<string,unknown>[];columns:Column[];title?:string;selectable?:boolean;pageSize?:number}>(),{title:'数据详情',selectable:true,pageSize:5});
const emit=defineEmits<{select:[rows:Record<string,unknown>[]];open:[row:Record<string,unknown>,key:string];context:[event:MouseEvent,row:Record<string,unknown>];page:[page:number];visible:[rows:Record<string,unknown>[]]}>();
const page=ref(1),size=ref(props.pageSize),selection=ref<Record<string,unknown>[]>([]);
const platform=usePlatform();
const current=computed(()=>apiMode==='http'?props.rows:props.rows.slice((page.value-1)*size.value,page.value*size.value));
watch(current,rows=>emit('visible',rows),{immediate:true});
watch(()=>props.rows,()=>{page.value=Math.min(page.value,Math.max(1,Math.ceil(props.rows.length/size.value)));selection.value=[];emit('select',[]);});
const text=(v:unknown)=>Array.isArray(v)?v.join('、'):v===null||v===undefined?'—':typeof v==='object'?JSON.stringify(v):String(v);
function context(row:Record<string,unknown>,_column:unknown,event:MouseEvent){event.preventDefault();emit('context',event,row);}
function selected(rows:Record<string,unknown>[]){selection.value=rows;emit('select',rows);}
</script>
<template><section class="card data-table"><div class="section-head"><h3>{{title}}</h3><span class="muted">{{apiMode==='http'?(remote.total===null?'本页 '+rows.length+' 条':'共 '+remote.total+' 条'):rows.length+' 条'}}</span><div class="spacer"/><slot name="actions"/></div><el-table :data="current" row-key="id" border stripe @selection-change="selected" @row-contextmenu="context"><el-table-column v-if="selectable" type="selection" width="48"/><el-table-column v-for="c in columns" :key="c.key" :prop="c.key" :label="c.label" :width="c.width" :sortable="apiMode==='mock'"><template #default="{row}"><el-button v-if="c.link&&!row.linkDisabled" link type="primary" @click="emit('open',row,c.key)">{{text(row[c.key])}}</el-button><span v-else>{{text(row[c.key])}}</span></template></el-table-column><el-table-column v-if="$slots.rowActions" label="操作" min-width="200"><template #default="{row}"><slot name="rowActions" :row="row"/></template></el-table-column><template #empty><el-empty description="当前条件下无结果" :image-size="60"/></template></el-table><div class="pagination"><span class="muted">已选 {{selection.length}} 条（当前页）</span><el-pagination v-if="apiMode==='mock'" v-model:current-page="page" v-model:page-size="size" :total="rows.length" :page-sizes="[5,10,20,50,100]" layout="sizes,prev,pager,next" @current-change="emit('page',page)"/><el-pagination v-else-if="remote.total!==null" :current-page="remote.page" :page-size="remote.pageSize" :total="remote.total" layout="prev,pager,next" @current-change="platform.changePage($event)"/><div v-else class="actions"><el-button :disabled="remote.page<=1||remote.loading" @click="platform.changePage(remote.page-1)">上一页</el-button><span>第 {{remote.page}} 页</span><el-button :disabled="!remote.nextCursor||remote.loading" @click="platform.changePage(remote.page+1)">下一页</el-button></div></div></section></template>

<script setup lang="ts">
import {ref,watch,onBeforeUnmount} from 'vue';import {request,downloadFileBinary,isRequestCanceled} from '../api/client';
const props=defineProps<{assetId?:string|null;alt:string}>(),url=ref(''),error=ref('');let epoch=0;
function clear(){if(url.value)URL.revokeObjectURL(url.value);url.value='';}
watch(()=>props.assetId,async id=>{const current=++epoch;clear();error.value='';if(!id)return;try{const asset=(await request('getFileAsset',{path:{fileAssetId:id}})).data;if(asset.status!=='ACTIVE')throw new Error('画像文件不可用：'+asset.status);if(!['image/png','image/jpeg','image/webp'].includes(asset.mimeType))throw new Error('画像格式不支持预览');const blob=await downloadFileBinary(id);if(current===epoch)url.value=URL.createObjectURL(blob);}catch(e){if(current===epoch&&!isRequestCanceled(e))error.value=e instanceof Error?e.message:String(e);}},{immediate:true});onBeforeUnmount(()=>{epoch++;clear();});
</script>
<template><img v-if="url" :src="url" :alt="alt" class="remote-portrait"/><div v-else class="portrait-placeholder" :title="error">{{error?'画像暂不可用':'暂无画像'}}</div></template>
<style scoped>.remote-portrait{width:160px;max-height:200px;object-fit:contain;margin-bottom:16px}</style>

<script setup lang="ts">
import type {Field,Filter} from '../domain';
defineProps<{fields:Field[];model:Filter;label?:string}>();defineEmits<{search:[];reset:[]}>();
</script>
<template><el-form class="query-form card" label-position="top" @submit.prevent="$emit('search')"><el-form-item v-for="field in fields" :key="field.key" :label="field.label"><el-select v-if="field.options" v-model="model[field.key]" :multiple="field.multiple" :aria-label="field.label" clearable><el-option v-for="o in field.options" :key="typeof o==='string'?o:o.value" :label="typeof o==='string'?o:o.label" :value="typeof o==='string'?o:o.value"/></el-select><el-input v-else v-model="model[field.key]" :type="field.type||'text'" :aria-label="field.label"/></el-form-item><div class="query-buttons"><el-button type="primary" native-type="submit">{{label||'搜索'}}</el-button><el-button @click="$emit('reset')">重置</el-button><slot/></div></el-form></template>

<script setup lang="ts">
type Suggestion = {label: string; description: string; query: string; icon: string};
defineProps<{
  modelLabel: string;
  online: boolean;
  suggestions: Suggestion[];
  capabilities: string[];
}>();
defineEmits<{(event: 'pick', query: string): void}>();
</script>

<template>
  <section class="welcome" aria-label="智能体欢迎页">
    <div class="orb" aria-hidden="true">
      <svg viewBox="0 0 48 48" width="34" height="34" fill="none">
        <path d="M24 6l3.6 9.4L37 19l-9.4 3.6L24 32l-3.6-9.4L11 19l9.4-3.6L24 6Z" fill="currentColor" opacity=".92"/>
        <path d="M38 28l1.8 4.7L44 34.5l-4.2 1.8L38 41l-1.8-4.7L32 34.5l4.2-1.8L38 28Z" fill="currentColor" opacity=".55"/>
      </svg>
    </div>
    <h2>你好，我是多维数据分析智能体</h2>
    <p class="lead">用一句话告诉我要做什么，我会把你带到对应功能：查看布控任务、新建布控、发起研判或生成报表。对话只负责识别与导航，数据操作仍在原业务界面完成。</p>
    <div class="suggest-grid">
      <button v-for="item in suggestions" :key="item.query" class="suggest-card" type="button" @click="$emit('pick', item.query)">
        <span class="icon" aria-hidden="true">{{ item.icon }}</span>
        <span class="body"><b>{{ item.label }}</b><small>{{ item.description }}</small></span>
        <span class="go" aria-hidden="true">›</span>
      </button>
    </div>
    <div class="capability-row">
      <span class="capability-label">也可以直接问</span>
      <button v-for="item in capabilities" :key="item" type="button" class="capability" @click="$emit('pick', item)">{{ item }}</button>
    </div>
    <p class="model-hint">
      <span class="dot" :class="{online}" aria-hidden="true"></span>
      当前应答通道：<b>{{ modelLabel }}</b>
      <template v-if="!online"> · 本地模型未连接时由内置规则引擎完成识别，功能入口完全可用</template>
    </p>
  </section>
</template>

<style scoped>
.welcome{max-width:780px;margin:0 auto;padding:34px 24px 18px;text-align:center}
.orb{width:64px;height:64px;margin:0 auto 18px;border-radius:20px;display:grid;place-items:center;color:#fff;background:linear-gradient(135deg,#2f8fe0,#1769aa 55%,#0f4c7d);box-shadow:0 12px 28px #1769aa40}
.welcome h2{font-size:24px;margin-bottom:10px;color:#16324a}
.lead{max-width:620px;margin:0 auto 26px;color:#5d7183;font-size:13.5px;line-height:1.85}
.suggest-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-bottom:20px;text-align:left}
.suggest-card{display:flex;align-items:center;gap:12px;padding:14px 16px;border:1px solid #dde6f0;border-radius:12px;background:#fff;box-shadow:0 1px 2px #1832490a;transition:border-color .18s,box-shadow .18s,transform .18s;text-align:left}
.suggest-card:hover{border-color:#a9cbe8;box-shadow:0 8px 22px #1769aa1a;transform:translateY(-1px)}
.suggest-card .icon{flex:none;width:36px;height:36px;border-radius:10px;display:grid;place-items:center;background:#eaf3fc;color:#1769aa;font-weight:700;font-size:14px}
.suggest-card .body{display:flex;flex-direction:column;gap:3px;min-width:0}
.suggest-card b{font-size:13.5px;color:#1d3b57;font-weight:600}
.suggest-card small{color:#75879a;font-size:12px;line-height:1.5}
.suggest-card .go{margin-left:auto;color:#a9b8c7;font-size:20px;line-height:1}
.capability-row{display:flex;flex-wrap:wrap;gap:8px;justify-content:center;align-items:center;margin-bottom:18px}
.capability-label{font-size:12px;color:#8b9bab}
.capability{border:1px solid #dbe5ef;background:#f8fbff;color:#3c5b78;border-radius:999px;padding:5px 12px;font-size:12px;transition:.18s}
.capability:hover{border-color:#9dc6e8;background:#eef6ff;color:#1769aa}
.model-hint{margin:0;font-size:12px;color:#8b9bab;display:flex;align-items:center;justify-content:center;gap:6px;flex-wrap:wrap}
.model-hint b{color:#4a647c;font-weight:600}
.dot{width:7px;height:7px;border-radius:50%;background:#c3ccd6;display:inline-block}
.dot.online{background:#20a565;box-shadow:0 0 0 3px #20a56522}
@media(max-width:1100px){.suggest-grid{grid-template-columns:1fr}}
</style>

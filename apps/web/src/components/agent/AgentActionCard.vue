<script setup lang="ts">
import type {AgentAction} from '../../utils/agentNlu';
defineProps<{actions: AgentAction[]}>();
defineEmits<{(event: 'run', action: AgentAction): void}>();
</script>

<template>
  <div class="action-block">
    <p class="action-title"><span class="line" aria-hidden="true"></span>可执行操作<span class="count">{{ actions.length }}</span></p>
    <div class="action-grid" role="group" aria-label="可执行操作">
      <button
        v-for="action in actions"
        :key="action.id"
        type="button"
        class="action"
        :class="action.tone === 'primary' ? 'primary' : 'default'"
        :data-action-id="action.id"
        @click="$emit('run', action)"
      >
        <span class="badge" aria-hidden="true">{{ action.icon }}</span>
        <span class="text">
          <b>{{ action.label }}</b>
          <small>{{ action.description }}</small>
        </span>
        <span class="arrow" aria-hidden="true">›</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.action-block{margin-top:14px}
.action-title{display:flex;align-items:center;gap:8px;font-size:12px;color:#8296a9;margin:0 0 8px}
.action-title .line{width:14px;height:1px;background:#c7d6e4}
.action-title .count{min-width:18px;height:18px;border-radius:9px;background:#eef3f9;color:#7b8fa3;font-size:11px;display:grid;place-items:center;padding:0 5px}
.action-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px}
.action{display:flex;align-items:center;gap:10px;padding:10px 12px;border-radius:10px;border:1px solid #e0e9f2;background:#fff;text-align:left;transition:.18s;min-width:0}
.action:hover{border-color:#9dc6e8;background:#f7fbff;transform:translateY(-1px);box-shadow:0 6px 16px #1769aa14}
.action.primary{border-color:#bcd9f1;background:linear-gradient(180deg,#f5faff,#eaf4fe)}
.action.primary:hover{border-color:#7fb6e2}
.action .badge{flex:none;width:30px;height:30px;border-radius:8px;display:grid;place-items:center;background:#eaf3fc;color:#1a6aa8;font-weight:700;font-size:12px}
.action.primary .badge{background:#1769aa;color:#fff}
.action .text{display:flex;flex-direction:column;gap:2px;min-width:0;flex:1}
.action b{font-size:13px;color:#1d3b57;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.action small{font-size:11.5px;color:#7b8ea1;line-height:1.45;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.action .arrow{color:#b3c2d0;font-size:17px;line-height:1;flex:none}
@media(max-width:900px){.action-grid{grid-template-columns:1fr}}
</style>

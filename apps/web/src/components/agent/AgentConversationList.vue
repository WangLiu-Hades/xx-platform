<script setup lang="ts">
import {computed, ref} from 'vue';
import {ElMessageBox} from 'element-plus';
import type {AgentConversation} from '../../stores/agent';
const props = defineProps<{conversations: AgentConversation[]; activeId: string; busy: boolean}>();
const emit = defineEmits<{
  (event: 'create'): void;
  (event: 'select', id: string): void;
  (event: 'delete', id: string): void;
}>();
const keyword = ref('');

const day = (offset = 0) => new Date(Date.now() - offset * 86_400_000).toLocaleDateString('sv-SE', {timeZone: 'Asia/Shanghai'});

const filtered = computed(() => {
  const query = keyword.value.trim().toLowerCase();
  return props.conversations.filter(item => !query || item.title.toLowerCase().includes(query) || item.messages.some(message => message.content.toLowerCase().includes(query)));
});

const groups = computed(() => {
  const buckets: {label: string; items: AgentConversation[]}[] = [
    {label: '今天', items: []},
    {label: '昨天', items: []},
    {label: '近 7 天', items: []},
    {label: '更早', items: []},
  ];
  const today = day();
  const yesterday = day(1);
  const weekAgo = day(7);
  for (const item of filtered.value) {
    const date = item.createdAt.slice(0, 10);
    const bucket = date === today ? buckets[0]! : date === yesterday ? buckets[1]! : date >= weekAgo ? buckets[2]! : buckets[3]!;
    bucket.items.push(item);
  }
  return buckets.filter(bucket => bucket.items.length);
});

async function remove(item: AgentConversation) {
  if (props.busy) return;
  try {
    await ElMessageBox.confirm('删除对话「' + item.title + '」？删除后不可恢复。', '删除对话', {confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'});
    emit('delete', item.id);
  } catch {
    /* 用户取消 */
  }
}
</script>

<template>
  <aside class="conversation-list" aria-label="会话列表">
    <div class="list-head">
      <el-button type="primary" class="new-chat" :disabled="busy" @click="$emit('create')">
        <span class="plus" aria-hidden="true">+</span> 新建对话
      </el-button>
      <input v-model="keyword" class="search" type="search" placeholder="搜索对话" aria-label="搜索对话" />
    </div>
    <div class="list-body">
      <template v-for="group in groups" :key="group.label">
        <p class="group-label">{{ group.label }}</p>
        <div
          v-for="item in group.items"
          :key="item.id"
          class="conversation"
          :class="{active: item.id === activeId}"
          role="button"
          tabindex="0"
          :title="item.title"
          @click="$emit('select', item.id)"
          @keydown.enter="$emit('select', item.id)"
        >
          <span class="conversation-main">
            <b>{{ item.title }}</b>
            <small>{{ item.messages.filter(m => m.role === 'user').length }} 轮 · {{ item.createdAt.slice(11, 16) }}</small>
          </span>
          <button type="button" class="remove" aria-label="删除对话" :disabled="busy" @click.stop="remove(item)">×</button>
        </div>
      </template>
      <p v-if="!groups.length" class="empty">{{ keyword ? '没有匹配的对话' : '暂无历史对话' }}</p>
    </div>
    <p class="list-foot">对话保存在本机浏览器，仅当前设备可见</p>
  </aside>
</template>

<style scoped>
.conversation-list{display:flex;flex-direction:column;min-height:0;background:#fff;border:1px solid #e2e8ef;border-radius:12px;box-shadow:0 2px 10px #23374d08;padding:12px 10px 8px}
.list-head{display:flex;flex-direction:column;gap:8px;padding:0 2px 8px}
.new-chat{width:100%;justify-content:flex-start;gap:6px}
.plus{font-size:15px;line-height:1;margin-right:2px}
.search{width:100%;border:1px solid #e1e9f2;border-radius:7px;background:#f8fbfe;padding:6px 10px;font-size:12px;color:#3d5a75;outline:none}
.search:focus{border-color:#9dc6e8;background:#fff}
.list-body{flex:1;overflow:auto;min-height:0;padding:2px}
.group-label{font-size:11px;color:#9aaaba;margin:10px 6px 4px;letter-spacing:.4px}
.conversation{display:flex;align-items:center;gap:4px;padding:8px 8px 8px 10px;border-radius:8px;cursor:pointer;transition:background .15s;outline:none}
.conversation:hover{background:#f4f8fc}
.conversation.active{background:#eaf3fc;box-shadow:inset 2px 0 0 #1769aa}
.conversation-main{display:flex;flex-direction:column;gap:2px;min-width:0;flex:1}
.conversation b{font-size:12.5px;color:#2c4a66;font-weight:500;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.conversation small{font-size:11px;color:#9aaaba}
.remove{flex:none;border:0;background:transparent;color:#b6c3d0;font-size:15px;line-height:1;padding:2px 5px;border-radius:4px;opacity:0;transition:.15s}
.conversation:hover .remove,.conversation:focus-within .remove{opacity:1}
.remove:hover{background:#fdecec;color:#c24545}
.empty{font-size:12px;color:#a5b3c1;text-align:center;padding:26px 0}
.list-foot{margin:6px 4px 0;font-size:10.5px;color:#a9b7c4;line-height:1.5;border-top:1px solid #eef2f7;padding-top:7px}
</style>

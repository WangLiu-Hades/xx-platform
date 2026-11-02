<script setup lang="ts">
import {computed} from 'vue';
import type {AgentMessage} from '../../stores/agent';
import {renderAgentMarkdown} from '../../utils/agentMarkdown';
import AgentActionCard from './AgentActionCard.vue';
import type {AgentAction} from '../../utils/agentNlu';

const props = defineProps<{message: AgentMessage; isLast: boolean; streaming: boolean}>();
defineEmits<{
  (event: 'run', action: AgentAction): void;
  (event: 'retry', id: string): void;
  (event: 'copy', text: string): void;
  (event: 'suggestion', text: string): void;
}>();

const html = computed(() => renderAgentMarkdown(props.message.content));
const thinking = computed(() => props.message.status === 'thinking' || props.message.status === 'streaming');
const stepProgress = computed(() => (props.message.steps ?? []).filter(step => step.state === 'done').length);
</script>

<template>
  <article class="msg" :class="message.role" :data-message-id="message.id">
    <template v-if="message.role === 'user'">
      <div class="bubble user-bubble">{{ message.content }}</div>
      <div class="avatar user-avatar" aria-hidden="true">我</div>
    </template>

    <template v-else>
      <div class="avatar agent-avatar" aria-hidden="true">
        <svg viewBox="0 0 32 32" width="19" height="19" fill="none">
          <path d="M16 4l2.4 6.2L24.6 12l-6.2 2.4L16 20l-2.4-5.6L7.4 12l6.2-1.8L16 4Z" fill="currentColor"/>
          <path d="M24.4 20l1.3 3.3 3.3 1.3-3.3 1.3L24.4 29l-1.3-3.1-3.3-1.3 3.3-1.3L24.4 20Z" fill="currentColor" opacity=".6"/>
        </svg>
      </div>
      <div class="assistant-body">
        <div class="meta">
          <span class="agent-name">多维数据分析智能体</span>
          <span v-if="message.intentTitle" class="intent-tag">{{ message.intentTitle }}</span>
          <span v-if="message.source === 'local-llm'" class="source-tag model">本地模型</span>
          <span v-else-if="message.source === 'rule'" class="source-tag rule">规则引擎</span>
          <span v-if="message.stopped" class="source-tag stop">已停止</span>
        </div>

        <details v-if="message.steps?.length" class="steps" :open="thinking">
          <summary>
            <span class="pulse" :class="{thinking}" aria-hidden="true"></span>
            {{ thinking ? '正在处理' : '处理过程' }} · {{ stepProgress }}/{{ message.steps.length }} 步
          </summary>
          <ol>
            <li v-for="(step, index) in message.steps" :key="index" :class="step.state">
              <span class="mark" aria-hidden="true">{{ step.state === 'done' ? '✓' : step.state === 'active' ? '…' : '·' }}</span>
              <span>{{ step.label }}</span>
            </li>
          </ol>
        </details>

        <div v-if="message.status === 'thinking'" class="thinking-line">
          <span class="typing" aria-hidden="true"><i></i><i></i><i></i></span>
          正在理解问题并匹配平台能力…
        </div>
        <div v-else class="markdown" :class="{typing: message.status === 'streaming'}" v-html="html"></div>

        <p v-if="message.note" class="note">{{ message.note }}</p>

        <AgentActionCard v-if="message.actions?.length" :actions="message.actions" @run="$emit('run', $event)" />

        <div v-if="message.status === 'done' && message.role === 'assistant'" class="msg-tools">
          <button type="button" @click="$emit('copy', message.content)">复制</button>
          <button v-if="isLast && !streaming" type="button" @click="$emit('retry', message.id)">重新生成</button>
          <span v-if="message.time" class="time">{{ message.time }}</span>
        </div>

        <div v-if="isLast && !streaming && message.suggestions?.length && !message.actions?.length" class="suggestions">
          <button v-for="text in message.suggestions" :key="text" type="button" @click="$emit('suggestion', text)">{{ text }}</button>
        </div>
      </div>
    </template>
  </article>
</template>

<style scoped>
.msg{display:flex;gap:10px;margin-bottom:22px;align-items:flex-start}
.msg.user{justify-content:flex-end}
.avatar{flex:none;width:32px;height:32px;border-radius:10px;display:grid;place-items:center;font-size:12px;font-weight:600;user-select:none}
.agent-avatar{background:linear-gradient(135deg,#2f8fe0,#1769aa 60%,#0f4c7d);color:#fff;box-shadow:0 4px 12px #1769aa33}
.user-avatar{background:#e7eef6;color:#4a647c;border:1px solid #d8e3ee}
.bubble{max-width:min(640px,76%);padding:11px 15px;border-radius:14px;font-size:13.5px;line-height:1.75;white-space:pre-wrap;overflow-wrap:anywhere}
.user-bubble{background:#e8f1fb;color:#1c3a56;border:1px solid #d5e5f5;border-top-right-radius:4px}
.assistant-body{flex:1;min-width:0;max-width:800px}
.meta{display:flex;align-items:center;gap:7px;flex-wrap:wrap;margin-bottom:7px}
.agent-name{font-size:12.5px;color:#36556f;font-weight:600}
.intent-tag{font-size:11px;padding:1px 8px;border-radius:999px;background:#eef5fc;color:#2d6f9f;border:1px solid #d8e8f7}
.source-tag{font-size:11px;padding:1px 7px;border-radius:999px;border:1px solid transparent}
.source-tag.model{background:#eaf8f0;color:#147d52;border-color:#cbeadb}
.source-tag.rule{background:#fff7e8;color:#9a6a12;border-color:#f3e2bd}
.source-tag.stop{background:#f4f6f8;color:#7c8b9a;border-color:#e2e8ee}
.steps{margin:0 0 9px;font-size:12px;color:#7b8ea1}
.steps summary{list-style:none;cursor:pointer;display:inline-flex;align-items:center;gap:6px;padding:3px 8px;border-radius:6px;border:1px dashed #dbe4ee;background:#fafcff;user-select:none}
.steps summary::-webkit-details-marker{display:none}
.steps[open] summary{border-style:solid;background:#f4f8fd}
.steps ol{margin:8px 0 2px;padding:9px 12px;list-style:none;border-left:2px solid #e1eaf3;display:flex;flex-direction:column;gap:6px}
.steps li{display:flex;gap:7px;align-items:flex-start;line-height:1.5}
.steps li .mark{flex:none;width:14px;text-align:center;color:#9db0c2}
.steps li.done .mark{color:#20a565}
.steps li.active .mark,.steps li.active{color:#1769aa}
.pulse{width:6px;height:6px;border-radius:50%;background:#bcc9d6}
.pulse.thinking{background:#1769aa;animation:pulse 1.1s infinite}
@keyframes pulse{0%,100%{opacity:1;transform:scale(1)}50%{opacity:.35;transform:scale(.8)}}
.thinking-line{display:flex;align-items:center;gap:8px;color:#7b8ea1;font-size:13px;padding:4px 0}
.typing{display:inline-flex;gap:3px;align-items:flex-end;height:12px}
.typing i{width:4px;height:4px;border-radius:50%;background:#7fa9d0;animation:bounce 1.2s infinite}
.typing i:nth-child(2){animation-delay:.15s}.typing i:nth-child(3){animation-delay:.3s}
@keyframes bounce{0%,60%,100%{transform:translateY(0);opacity:.5}30%{transform:translateY(-3px);opacity:1}}
.markdown{font-size:13.5px;line-height:1.85;color:#273b50;overflow-wrap:anywhere}
.markdown.typing::after{content:'';display:inline-block;width:2px;height:15px;background:#1769aa;vertical-align:-2px;margin-left:2px;animation:blink .9s steps(2) infinite}
@keyframes blink{50%{opacity:0}}
.markdown :deep(p){margin:0 0 8px}
.markdown :deep(p:last-child){margin-bottom:0}
.markdown :deep(ul),.markdown :deep(ol){margin:0 0 8px;padding-left:20px}
.markdown :deep(li){margin:2px 0}
.markdown :deep(strong){color:#16324a;font-weight:600}
.markdown :deep(code){background:#f1f5fa;border:1px solid #e3ebf4;border-radius:4px;padding:1px 5px;font-family:Consolas,Monaco,monospace;font-size:12px}
.markdown :deep(h3),.markdown :deep(h4){font-size:14px;margin:6px 0 6px;color:#1d3b57}
.note{margin:8px 0 0;font-size:11.5px;color:#9a6a12;background:#fffaf0;border:1px solid #f4e6c8;border-radius:6px;padding:5px 9px;line-height:1.6}
.msg-tools{display:flex;align-items:center;gap:4px;margin-top:8px;font-size:11.5px;color:#93a3b3;opacity:0;transition:opacity .15s}
.msg:hover .msg-tools,.msg:focus-within .msg-tools{opacity:1}
.msg-tools button{border:0;background:transparent;color:#7c8fa2;padding:2px 6px;border-radius:4px;font-size:11.5px}
.msg-tools button:hover{background:#eef3f9;color:#1769aa}
.msg-tools .time{margin-left:4px}
.suggestions{display:flex;flex-wrap:wrap;gap:7px;margin-top:10px}
.suggestions button{border:1px solid #dbe6f1;background:#fbfdff;color:#40607e;border-radius:999px;padding:5px 12px;font-size:12px;transition:.16s}
.suggestions button:hover{border-color:#9dc6e8;background:#eef6ff;color:#1769aa}
@media(max-width:900px){.bubble{max-width:84%}}
</style>

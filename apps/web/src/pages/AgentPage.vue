<script setup lang="ts">
import {computed, nextTick, onMounted, ref, watch} from 'vue';
import {ElMessage} from 'element-plus';
import {useAgent} from '../stores/agent';
import type {LocalModelConfig} from '../api/llm';
import type {AgentAction} from '../utils/agentNlu';
import AgentWelcome from '../components/agent/AgentWelcome.vue';
import AgentMessageItem from '../components/agent/AgentMessageItem.vue';
import AgentComposer from '../components/agent/AgentComposer.vue';
import AgentConversationList from '../components/agent/AgentConversationList.vue';
import AgentModelDialog from '../components/agent/AgentModelDialog.vue';

const agent = useAgent();
const scroll = ref<HTMLElement>();
const composer = ref<{focus: () => void}>();
const modelVisible = ref(false);
const quick = ['布控', '新建布控', '查看告警结果'];

const startCards = [
  {label: '布控预警', description: '查看布控任务、新建布控或查看预警结果', query: '布控', icon: '控'},
  {label: '数据检索', description: '按目标号码、车牌或 MAC 检索采集记录', query: '数据检索', icon: '检'},
  {label: '分析研判', description: '发起伴随、碰撞或区域统计分析任务', query: '新建伴随分析', icon: '研'},
  {label: '运维报表', description: '查看设备在线率并生成运维报表', query: '运维报表', icon: '报'},
];
const capabilities = ['布控', '检索 460000000000001 最近24小时', '查看告警结果', '设备在线率', '任务中心'];

const lastAssistantId = computed(() => [...agent.messages].reverse().find(message => message.role === 'assistant')?.id ?? '');
const headModel = computed(() => {
  switch (agent.modelState.status) {
    case 'online':
      return '本地模型已连接';
    case 'checking':
      return '正在检测本地模型';
    case 'offline':
      return '本地模型未连接';
    default:
      return '规则引擎应答';
  }
});

function scrollToBottom(force = false) {
  const element = scroll.value;
  if (!element) return;
  const nearBottom = element.scrollHeight - element.scrollTop - element.clientHeight < 180;
  if (force || nearBottom) element.scrollTop = element.scrollHeight;
}

async function send(value: string) {
  if (agent.streaming) return;
  await agent.send(value);
  await nextTick();
  scrollToBottom(true);
}
function pick(value: string) {
  void send(value);
}
function run(action: AgentAction) {
  if (action.kind === 'settings') {
    modelVisible.value = true;
    return;
  }
  const handled = agent.runAction(action);
  if (handled && action.kind === 'prompt') void nextTick(() => composer.value?.focus());
}
async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text);
    ElMessage.success('已复制到剪贴板');
  } catch {
    ElMessage.warning('浏览器不允许访问剪贴板，请手动选择文字复制');
  }
}
function newChat() {
  agent.createConversation();
  void nextTick(() => composer.value?.focus());
}
function saveModel(config: LocalModelConfig) {
  agent.updateModelConfig(config);
  ElMessage.success('模型配置已保存，正在检测连接');
}

onMounted(() => {
  void agent.ensureModel();
  void nextTick(() => composer.value?.focus());
});
watch(
  () => [agent.messages.length, agent.messages.at(-1)?.content, agent.messages.at(-1)?.status],
  () => void nextTick(() => scrollToBottom()),
);
</script>

<template>
  <div class="agent-page">
    <AgentConversationList :conversations="agent.conversations" :active-id="agent.activeId" :busy="agent.streaming" @create="newChat" @select="agent.selectConversation" @delete="agent.deleteConversation" />

    <section class="chat-panel" aria-label="智能对话">
      <header class="chat-head">
        <div class="agent-identity">
          <div class="avatar" aria-hidden="true">
            <svg viewBox="0 0 32 32" width="20" height="20" fill="none">
              <path d="M16 4l2.4 6.2L24.6 12l-6.2 2.4L16 20l-2.4-5.6L7.4 12l6.2-1.8L16 4Z" fill="currentColor"/>
              <path d="M24.4 20l1.3 3.3 3.3 1.3-3.3 1.3L24.4 29l-1.3-3.1-3.3-1.3 3.3-1.3L24.4 20Z" fill="currentColor" opacity=".6"/>
            </svg>
          </div>
          <div class="name">
            <h2>多维数据分析智能体</h2>
            <p :title="agent.modelConfig.model">
              <span class="dot" :class="{online: agent.modelOnline}" aria-hidden="true"></span>
              {{ headModel }}
              <span class="sep">·</span>
              数据不出内网
            </p>
          </div>
        </div>
        <div class="head-actions">
          <el-button text @click="agent.exportTranscript()">导出对话</el-button>
          <el-button text :disabled="agent.streaming" @click="newChat">新建对话</el-button>
          <el-button text type="primary" @click="modelVisible = true">模型设置</el-button>
        </div>
      </header>

      <div ref="scroll" class="chat-scroll" role="log" aria-live="polite">
        <div class="thread">
          <AgentWelcome
            v-if="agent.isEmpty"
            :model-label="agent.modelLabel"
            :online="agent.modelOnline"
            :suggestions="startCards"
            :capabilities="capabilities"
            @pick="pick"
          />
          <AgentMessageItem
            v-for="message in agent.messages"
            :key="message.id"
            :message="message"
            :is-last="message.id === lastAssistantId || message.id === agent.messages.at(-1)?.id"
            :streaming="agent.streaming"
            @run="run"
            @retry="agent.regenerate"
            @copy="copy"
            @suggestion="pick"
          />
          <div v-if="agent.isEmpty" class="agent-footnote">
            <p>示例指令：<button type="button" @click="pick('布控')">布控</button><button type="button" @click="pick('新建布控')">新建布控</button><button type="button" @click="pick('查看布控预警结果')">查看布控预警结果</button></p>
          </div>
        </div>
      </div>

      <footer class="composer-area">
        <div class="composer-wrap">
          <AgentComposer
            ref="composer"
            v-model="agent.draft"
            :model-label="agent.modelLabel"
            :online="agent.modelOnline"
            :streaming="agent.streaming"
            :quick="agent.isEmpty ? quick : []"
            @send="send"
            @stop="agent.stop"
            @open-model="modelVisible = true"
          />
          <p class="legal">智能体负责识别意图与打开对应功能；新建、提交、下发等操作仍需在业务页面由你确认。答案由本地模型与内置规则引擎生成，请结合页面数据核对。</p>
        </div>
      </footer>
    </section>

    <AgentModelDialog
      v-model="modelVisible"
      :config="agent.modelConfig"
      :state="agent.modelState"
      :warning="agent.endpointWarning"
      @save="saveModel"
      @probe="agent.testModel"
    />
  </div>
</template>

<style scoped>
.agent-page{display:grid;grid-template-columns:248px minmax(0,1fr);gap:14px;height:calc(100vh - 250px);min-height:500px}
.chat-panel{display:flex;flex-direction:column;min-width:0;min-height:0;background:linear-gradient(180deg,#fbfdff,#f6faff 40%,#f4f8fd);border:1px solid #e2e8ef;border-radius:12px;box-shadow:0 2px 10px #23374d08;overflow:hidden}
.chat-head{flex:none;display:flex;align-items:center;gap:12px;padding:12px 18px;background:#fff;border-bottom:1px solid #e8eef5}
.agent-identity{display:flex;align-items:center;gap:10px;min-width:0;flex:1}
.avatar{width:38px;height:38px;border-radius:11px;display:grid;place-items:center;color:#fff;background:linear-gradient(135deg,#2f8fe0,#1769aa 60%,#0f4c7d);box-shadow:0 5px 14px #1769aa30}
.name h2{font-size:15px;margin:0 0 3px;color:#16324a}
.name{min-width:0}
.name p{margin:0;font-size:11.5px;color:#8b9bab;display:flex;align-items:center;gap:5px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.name .sep{color:#c3ced9}
.dot{width:6px;height:6px;border-radius:50%;background:#c3ccd6}
.dot.online{background:#20a565;box-shadow:0 0 0 3px #20a5651f}
.head-actions{margin-left:auto;display:flex;align-items:center;gap:2px;flex:none}
.head-actions :deep(.el-button+.el-button){margin-left:0}
.chat-scroll{flex:1;overflow-y:auto;min-height:0;padding:18px 22px 8px;scroll-behavior:smooth}
.thread{max-width:840px;margin:0 auto;width:100%}
.agent-footnote{margin:6px auto 18px;text-align:center;font-size:11.5px;color:#a3b2c1}
.agent-footnote p{margin:0}
.agent-footnote button{border:0;background:transparent;color:#6f8aa3;font-size:11.5px;text-decoration:underline;text-underline-offset:2px;padding:0 5px}
.agent-footnote button:hover{color:#1769aa}
.composer-area{flex:none;padding:6px 22px 12px;background:linear-gradient(180deg,#f4f8fd00,#f4f8fd 30%,#f4f8fd)}
.composer-wrap{max-width:840px;margin:0 auto}
.legal{margin:7px 2px 0;font-size:11px;color:#a3b2c1;line-height:1.55;text-align:center}
@media(max-width:1250px){.agent-page{grid-template-columns:212px minmax(0,1fr)}}
@media(max-width:1000px){.agent-page{grid-template-columns:1fr}.agent-page > :first-child{display:none}}
</style>

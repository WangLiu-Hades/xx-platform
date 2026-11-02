/**
 * 智能体会话状态。
 *
 * 一次问答分两步：
 * 1. 本地规则引擎（utils/agentNlu）先把问题映射到平台能力与可点击操作，保证离线可用；
 * 2. 本地大模型（api/llm）可用时，仅负责把识别结果组织成自然语言，流式渲染。
 *
 * 界面动作永远由本地动作目录和用户点击触发，模型不能直接跳转或执行操作。
 */
import {computed, reactive, ref} from 'vue';
import {defineStore} from 'pinia';
import {ElMessage} from 'element-plus';
import {usePlatform} from './platform';
import {apiMode} from '../api/client';
import {
  LocalModelError,
  isLocalEndpoint,
  loadModelConfig,
  normalizeBaseUrl,
  probeLocalModel,
  saveModelConfig,
  streamLocalChat,
} from '../api/llm';
import type {ChatMessage, LocalModelConfig} from '../api/llm';
import {agentIntents, agentSuggestions, buildAgentReply, recognizeAgentIntent} from '../utils/agentNlu';
import type {AgentAction, AgentRecognition, AgentStats} from '../utils/agentNlu';
import {download} from '../utils/files';

export type AgentStep = {label: string; state: 'done' | 'active' | 'pending'};
export type AgentMessage = {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  time: string;
  status: 'thinking' | 'streaming' | 'done' | 'error';
  intent?: string;
  intentTitle?: string;
  actions?: AgentAction[];
  steps?: AgentStep[];
  suggestions?: string[];
  source?: 'rule' | 'local-llm';
  note?: string;
  stopped?: boolean;
};
export type AgentConversation = {
  id: string;
  title: string;
  createdAt: string;
  updatedAt: string;
  messages: AgentMessage[];
};

type ModelState = {
  status: 'unknown' | 'checking' | 'online' | 'offline' | 'disabled';
  models: string[];
  latencyMs: number;
  error: string;
  checkedAt: string;
};

const STORAGE_KEY = 'vue-agent-conversations-v1';
const MAX_CONVERSATIONS = 30;
const MAX_MESSAGES = 120;

const now = () => new Date().toLocaleString('sv-SE', {timeZone: 'Asia/Shanghai'});
const uuid = () => crypto.randomUUID();

const newConversation = (): AgentConversation => ({id: uuid(), title: '新对话', createdAt: now(), updatedAt: now(), messages: []});

function loadConversations(): {conversations: AgentConversation[]; activeId: string} {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) throw new Error('empty');
    const parsed = JSON.parse(raw) as {version?: number; conversations?: AgentConversation[]; activeId?: string};
    const conversations = (parsed.conversations ?? [])
      .filter(item => item && typeof item.id === 'string' && Array.isArray(item.messages))
      .map(item => ({
        ...item,
        // 上次关闭页面时仍在流式输出的消息不能在刷新后永远停在"正在处理"
        messages: item.messages.map(message =>
          message.status === 'thinking' || message.status === 'streaming'
            ? {...message, status: 'done' as const, content: message.content?.trim() ? message.content : '（上次回答未完成，可点击"重新生成"）'}
            : message,
        ),
      }));
    if (!conversations.length) throw new Error('empty');
    const activeId = conversations.some(item => item.id === parsed.activeId) ? String(parsed.activeId) : conversations[0]!.id;
    return {conversations: conversations.slice(0, MAX_CONVERSATIONS), activeId};
  } catch {
    const conversation = newConversation();
    return {conversations: [conversation], activeId: conversation.id};
  }
}

export const useAgent = defineStore('agent', () => {
  const platform = usePlatform();
  const restored = loadConversations();
  const conversations = ref<AgentConversation[]>(restored.conversations);
  const activeId = ref(restored.activeId);
  const draft = ref('');
  const streaming = ref(false);
  const modelConfig = ref<LocalModelConfig>(loadModelConfig());
  const modelState = reactive<ModelState>({status: 'unknown', models: [], latencyMs: 0, error: '', checkedAt: ''});
  let controller: AbortController | null = null;
  let probeController: AbortController | null = null;
  let probeToken = 0;

  const activeConversation = computed(() => conversations.value.find(item => item.id === activeId.value) ?? conversations.value[0]);
  const messages = computed<AgentMessage[]>(() => activeConversation.value?.messages ?? []);
  const isEmpty = computed(() => messages.value.length === 0);
  const modelOnline = computed(() => modelConfig.value.enabled && modelState.status === 'online');
  const modelLabel = computed(() => {
    if (!modelConfig.value.enabled) return '规则引擎';
    if (modelState.status === 'online') return '本地模型 · ' + (modelConfig.value.model || '已连接');
    if (modelState.status === 'checking') return '正在检测本地模型…';
    if (modelState.status === 'offline') return '本地模型未连接';
    return '本地模型';
  });
  const endpointWarning = computed(() => (isLocalEndpoint(modelConfig.value.baseUrl) ? '' : '当前模型地址不是本机或内网地址，请确认符合数据不出内网的要求。'));

  function persist() {
    try {
      const trimmed = conversations.value.slice(0, MAX_CONVERSATIONS).map(item => ({
        ...item,
        messages: item.messages.slice(-MAX_MESSAGES).map(message => ({...message, steps: undefined})),
      }));
      localStorage.setItem(STORAGE_KEY, JSON.stringify({version: 1, activeId: activeId.value, conversations: trimmed}));
    } catch {
      /* 存储不可用时只保留内存会话 */
    }
  }

  function ensureConversation(): AgentConversation {
    let conversation = activeConversation.value;
    if (!conversation) {
      conversation = newConversation();
      conversations.value.unshift(conversation);
      activeId.value = conversation.id;
    }
    return conversation;
  }

  function createConversation() {
    const current = activeConversation.value;
    if (current && !current.messages.length) {
      activeId.value = current.id;
      draft.value = '';
      return current;
    }
    const conversation = newConversation();
    conversations.value.unshift(conversation);
    activeId.value = conversation.id;
    draft.value = '';
    persist();
    return conversation;
  }

  function selectConversation(id: string) {
    if (streaming.value) return;
    if (!conversations.value.some(item => item.id === id)) return;
    activeId.value = id;
    persist();
  }

  function deleteConversation(id: string) {
    if (streaming.value) return;
    conversations.value = conversations.value.filter(item => item.id !== id);
    if (!conversations.value.length) conversations.value.push(newConversation());
    if (!conversations.value.some(item => item.id === activeId.value)) activeId.value = conversations.value[0]!.id;
    persist();
  }

  function renameConversation(id: string, title: string) {
    const conversation = conversations.value.find(item => item.id === id);
    const value = title.trim();
    if (!conversation || !value) return;
    conversation.title = value.slice(0, 40);
    persist();
  }

  function clearActive() {
    const conversation = ensureConversation();
    conversation.messages = [];
    conversation.title = '新对话';
    conversation.updatedAt = now();
    persist();
  }

  function stats(): AgentStats {
    return {
      remote: apiMode === 'http',
      captures: apiMode === 'mock' ? platform.db.captures.length : 0,
      watches: apiMode === 'mock' ? platform.db.watches.length : 0,
      runningWatches: apiMode === 'mock' ? platform.db.watches.filter(item => item.status === '运行中').length : 0,
      alerts: apiMode === 'mock' ? platform.db.watches.reduce((total, item) => total + item.hits.length, 0) : 0,
      devices: apiMode === 'mock' ? platform.db.devices.length : 0,
      onlineDevices: apiMode === 'mock' ? platform.db.devices.filter(item => item.status === '在线').length : 0,
      sites: apiMode === 'mock' ? platform.db.sites.length : 0,
      datasets: apiMode === 'mock' ? platform.db.datasets.length : 0,
      people: apiMode === 'mock' ? platform.db.people.length : 0,
      reports: apiMode === 'mock' ? platform.db.reports.length : 0,
      tasks: apiMode === 'http' ? platform.remote.tasks.length : 0,
    };
  }

  async function ensureModel(force = false) {
    if (!modelConfig.value.enabled) {
      modelState.status = 'disabled';
      modelState.error = '';
      return;
    }
    if (!force && (modelState.status === 'online' || modelState.status === 'checking')) return;
    probeController?.abort();
    probeController = new AbortController();
    const token = ++probeToken;
    modelState.status = 'checking';
    modelState.error = '';
    try {
      const result = await probeLocalModel(modelConfig.value, probeController.signal);
      if (token !== probeToken) return;
      if (!result.ok) {
        modelState.status = 'offline';
        modelState.error = result.error ?? '本地模型服务未响应';
        modelState.latencyMs = result.latencyMs;
        modelState.checkedAt = new Date().toLocaleTimeString('zh-CN', {timeZone: 'Asia/Shanghai'});
        return;
      }
      modelState.status = 'online';
      modelState.models = result.models;
      modelState.latencyMs = result.latencyMs;
      modelState.checkedAt = new Date().toLocaleTimeString('zh-CN', {timeZone: 'Asia/Shanghai'});
      if (result.models.length && !result.models.includes(modelConfig.value.model)) {
        modelState.error = '服务端没有列出模型「' + modelConfig.value.model + '」，请确认已 ollama pull';
      }
    } catch (error) {
      if (token !== probeToken) return;
      modelState.status = 'offline';
      modelState.error = error instanceof Error ? error.message : String(error);
      modelState.checkedAt = new Date().toLocaleTimeString('zh-CN', {timeZone: 'Asia/Shanghai'});
    }
  }

  function updateModelConfig(next: LocalModelConfig) {
    modelConfig.value = {...next, baseUrl: normalizeBaseUrl(next.baseUrl)};
    saveModelConfig(modelConfig.value);
    modelState.status = 'unknown';
    modelState.models = [];
    modelState.error = '';
    void ensureModel(true);
  }

  /** 设置页"测试连接"：使用尚未保存的表单值探测，不写入本地配置。 */
  async function testModel(next: LocalModelConfig) {
    const candidate = {...next, baseUrl: normalizeBaseUrl(next.baseUrl)};
    probeController?.abort();
    probeController = new AbortController();
    const token = ++probeToken;
    modelState.status = 'checking';
    modelState.error = '';
    try {
      const result = await probeLocalModel(candidate, probeController.signal);
      if (token !== probeToken) return;
      modelState.latencyMs = result.latencyMs;
      modelState.checkedAt = new Date().toLocaleTimeString('zh-CN', {timeZone: 'Asia/Shanghai'});
      if (!result.ok) {
        modelState.status = 'offline';
        modelState.error = result.error ?? '本地模型服务未响应';
        return;
      }
      modelState.status = 'online';
      modelState.models = result.models;
      if (result.models.length && !result.models.includes(candidate.model)) {
        modelState.error = '服务端没有列出模型「' + candidate.model + '」，请确认已拉取该模型';
      }
    } catch (error) {
      if (token !== probeToken) return;
      modelState.status = 'offline';
      modelState.error = error instanceof Error ? error.message : String(error);
    }
  }

  /** 系统提示词只包含能力目录与聚合数量，不包含采集明细、人员信息。 */
  function systemPrompt(recognition: AgentRecognition, actionCount: number): string {
    const numbers = stats();
    const recognitionLine = '已识别能力：' + recognition.intent.title + '（' + recognition.intent.id + '）';
    const entityLine =
      '识别条件：' +
      ([
        recognition.entities.type && '类型=' + recognition.entities.type,
        recognition.entities.target && '目标=' + recognition.entities.target,
        recognition.entities.timeLabel && '时间=' + recognition.entities.timeLabel,
        recognition.entities.site && '场所=' + recognition.entities.site,
      ]
        .filter(Boolean)
        .join('，') || '无');
    const numbersLine = numbers.remote
      ? '平台概况：接口模式，数量以服务端为准，不要写具体数字。'
      : '平台概况：采集记录 ' +
        numbers.captures +
        ' 条，布控任务 ' +
        numbers.watches +
        ' 个（运行中 ' +
        numbers.runningWatches +
        ' 个），告警命中 ' +
        numbers.alerts +
        ' 条，设备 ' +
        numbers.devices +
        ' 台（在线 ' +
        numbers.onlineDevices +
        ' 台），场所 ' +
        numbers.sites +
        ' 个，数据集 ' +
        numbers.datasets +
        ' 个，人员 ' +
        numbers.people +
        ' 人，报表 ' +
        numbers.reports +
        ' 份。';
    return [
      '你是“多维数据分析智能体”，部署在本地大模型上，服务于多维数据分析平台（电围/车牌/MAC/人像采集检索、布控预警、数据研判、地图研判、档案、运维服务、系统设置、任务中心）。',
      '回答要求：',
      '1. 简体中文，2~5 句，最多 160 字，可用少量 Markdown（**加粗**、列表）。',
      '2. 界面会自动在回答下方展示可点击的操作卡片，因此只说明“能做什么、当前概况、建议先点哪个”，不要复述按钮清单，不要输出链接。',
      '3. 不得编造任务编号、告警明细、设备状态或用户数据，只能使用下面给出的概况数字；未给出的信息回答“进入页面后查看”。',
      '4. 不要声称已经替用户执行操作，所有跳转和新建都由用户点击卡片触发；涉及删除、下发命令等动作提醒在业务页面确认。',
      '5. 不要输出身份证号、手机号等敏感明文；用户主动给出目标标识时可以复述其输入。',
      recognitionLine,
      entityLine,
      numbersLine,
      '可执行入口数量：' + actionCount,
    ].join('\n');
  }

  function historyForModel(conversation: AgentConversation, current: string): ChatMessage[] {
    const history = conversation.messages.filter(message => message.status === 'done' && message.content.trim());
    const last = history.at(-1);
    if (last?.role === 'user' && last.content === current) history.pop();
    return history
      .slice(-6)
      .map(message => ({role: message.role, content: message.content.slice(0, 600)}));
  }

  function noteMessage(conversation: AgentConversation, content: string, note = '') {
    conversation.messages.push({
      id: uuid(),
      role: 'assistant',
      content,
      time: now(),
      status: 'done',
      source: 'rule',
      note,
      intent: 'action',
    });
    conversation.updatedAt = now();
    persist();
  }

  /** Mock 演示数据的日期是固定的，相对时间以数据最新时间为基准才能命中演示记录。 */
  function referenceNow(): number | undefined {
    if (apiMode !== 'mock' || !platform.db.captures.length) return undefined;
    const stamps = platform.db.captures.map(item => new Date(item.time.replace(' ', 'T') + '+08:00').getTime()).filter(Number.isFinite);
    return stamps.length ? Math.max(...stamps) : undefined;
  }

  async function answer(conversation: AgentConversation, text: string) {
    const recognition = recognizeAgentIntent(text, {sites: platform.db.sites.map(site => ({name: site.name, region: site.region})), now: referenceNow()});
    const reply = buildAgentReply(recognition, stats());
    const assistant: AgentMessage = {
      id: uuid(),
      role: 'assistant',
      content: '',
      time: now(),
      status: 'thinking',
      intent: recognition.intent.id,
      intentTitle: recognition.intent.title,
      actions: recognition.actions,
      suggestions: agentSuggestions(recognition),
      steps: [
        {label: recognition.steps[0] ?? '理解问题', state: 'done'},
        {label: recognition.steps[1] ?? '识别条件', state: 'done'},
        {label: recognition.steps[2] ?? '准备操作', state: 'done'},
      ],
      source: 'rule',
    };
    conversation.messages.push(assistant);
    conversation.updatedAt = now();
    persist();
    // 必须通过数组里的响应式代理更新后续流式内容，直接改原始对象不会触发气泡重渲染。
    const live = conversation.messages[conversation.messages.length - 1]!;

    const useModel = modelConfig.value.enabled && modelState.status === 'online';
    if (!useModel) {
      live.content = reply;
      live.status = 'done';
      live.note = modelConfig.value.enabled && modelState.status === 'offline' ? '本地模型未连接，本条由内置规则引擎应答，功能入口不受影响。' : '';
      if (modelConfig.value.enabled && modelState.status === 'unknown') void ensureModel();
      conversation.updatedAt = now();
      persist();
      return;
    }

    live.status = 'streaming';
    live.source = 'local-llm';
    live.steps = [
      {label: recognition.steps[0] ?? '理解问题', state: 'done'},
      {label: recognition.steps[1] ?? '识别条件', state: 'done'},
      {label: '本地模型（' + modelConfig.value.model + '）生成回答', state: 'active'},
    ];
    controller = new AbortController();
    try {
      const payload: ChatMessage[] = [
        {role: 'system', content: systemPrompt(recognition, recognition.actions.length)},
        ...historyForModel(conversation, text),
        {role: 'user', content: text},
      ];
      await streamLocalChat(
        modelConfig.value,
        payload,
        delta => {
          live.content += delta;
          live.status = 'streaming';
        },
        controller.signal,
      );
      live.status = 'done';
      live.steps = (live.steps ?? []).map(step => ({...step, state: 'done'}));
    } catch (error) {
      const stopped = error instanceof LocalModelError && error.code === 'aborted';
      if (!live.content.trim()) {
        live.content = reply;
        live.source = 'rule';
        live.note = stopped ? '已停止生成，以下为规则引擎应答。' : '本地模型调用失败，已改用规则引擎应答：' + (error instanceof Error ? error.message : String(error));
      } else {
        live.note = stopped ? '已停止生成，保留已输出的部分内容。' : '本地模型输出中断，保留已生成内容：' + (error instanceof Error ? error.message : String(error));
      }
      live.status = stopped ? 'done' : 'error';
      live.stopped = stopped;
      live.steps = (live.steps ?? []).map(step => (step.state === 'active' ? {...step, state: 'done'} : step));
    } finally {
      controller = null;
      streaming.value = false;
      conversation.updatedAt = now();
      persist();
    }
  }

  /** 发送一条用户消息；返回后助手消息已创建（模型可能仍在流式输出）。 */
  async function send(text = draft.value) {
    const value = text.trim();
    if (!value) return;
    if (streaming.value) {
      ElMessage.warning('上一条回答仍在生成，请稍候或先停止');
      return;
    }
    const conversation = ensureConversation();
    const user: AgentMessage = {id: uuid(), role: 'user', content: value, time: now(), status: 'done'};
    conversation.messages.push(user);
    if (conversation.title === '新对话') conversation.title = value.replace(/\s+/g, ' ').slice(0, 18);
    conversation.updatedAt = now();
    draft.value = '';
    streaming.value = true;
    persist();
    await answer(conversation, value);
    streaming.value = false;
  }

  function stop() {
    controller?.abort();
    controller = null;
    streaming.value = false;
  }

  async function regenerate(assistantId: string) {
    if (streaming.value) return;
    const conversation = ensureConversation();
    const index = conversation.messages.findIndex(message => message.id === assistantId);
    if (index < 0) return;
    const question = [...conversation.messages.slice(0, index)].reverse().find(message => message.role === 'user');
    if (!question) return;
    conversation.messages.splice(index, 1);
    streaming.value = true;
    await answer(conversation, question.content);
    streaming.value = false;
  }

  function suggestionsOf(message: AgentMessage): string[] {
    if (message.status !== 'done') return [];
    if (message.suggestions?.length) return message.suggestions;
    const intent = agentIntents.find(item => item.id === message.intent);
    return intent ? intent.followUps : [];
  }

  /** 执行操作卡片。返回 true 表示已跳转/已打开表单。 */
  function runAction(action: AgentAction): boolean {
    const conversation = ensureConversation();
    if (action.page && !platform.allowed(action.page)) {
      noteMessage(conversation, '当前角色没有「' + action.page + '」的访问权限。请使用有权限的账号，或联系管理员在角色管理中分配该菜单。', '权限校验在进入页面前执行');
      ElMessage.error('当前角色没有此菜单权限');
      return false;
    }
    if (action.kind === 'prompt') {
      draft.value = action.prompt ?? '';
      return true;
    }
    if (action.kind === 'settings') return true;
    if (action.page) platform.navigate(action.page, action.filters);
    switch (action.handler) {
      case 'watch.create':
        platform.formWatch();
        break;
      case 'library.create':
        platform.formLibrary();
        break;
      case 'analysis.companion':
        platform.formAnalysis('伴随任务');
        break;
      case 'analysis.collision':
        platform.formAnalysis('碰撞任务');
        break;
      case 'analysis.region':
        platform.formAnalysis('区域统计');
        break;
      case 'report.create':
        platform.formReport();
        break;
      case 'notification.create':
        platform.formNotification();
        break;
      case 'model.edit':
        platform.formModel(platform.db.models[0]?.id);
        break;
      default:
        break;
    }
    noteMessage(
      conversation,
      action.kind === 'create' ? '已打开「' + (action.page ?? '') + '」并弹出「' + action.label + '」表单，填写后请在弹窗内保存。' : '已为你打开「' + (action.page ?? '') + '」，可以在左侧菜单或页面内继续操作。',
      '页面跳转由界面执行，对话不代替业务提交',
    );
    return true;
  }

  function transcript(): {name: string; content: string} {
    const conversation = ensureConversation();
    const lines = ['# ' + conversation.title, '', '> 导出时间：' + now() + ' · 会话 ' + conversation.id, ''];
    for (const message of conversation.messages) {
      lines.push('## ' + (message.role === 'user' ? '用户' : '智能体') + ' · ' + message.time);
      lines.push('');
      lines.push(message.content);
      if (message.actions?.length) lines.push('', '可执行操作：' + message.actions.map(item => item.label).join(' / '));
      lines.push('');
    }
    return {name: '智能体会话-' + conversation.title.replace(/[\\/:*?"<>|]/g, '_') + '.md', content: lines.join('\n')};
  }

  function exportTranscript() {
    const file = transcript();
    download(new Blob([file.content], {type: 'text/markdown;charset=utf-8'}), file.name);
  }

  return {
    conversations,
    activeId,
    activeConversation,
    messages,
    isEmpty,
    draft,
    streaming,
    modelConfig,
    modelState,
    modelOnline,
    modelLabel,
    endpointWarning,
    createConversation,
    selectConversation,
    deleteConversation,
    renameConversation,
    clearActive,
    ensureModel,
    updateModelConfig,
    testModel,
    send,
    stop,
    regenerate,
    suggestionsOf,
    runAction,
    exportTranscript,
  };
});

import {beforeEach,describe,expect,it,vi} from 'vitest';
import {createPinia,setActivePinia} from 'pinia';

const env = vi.hoisted(() => {
  const memory = new Map<string, string>();
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: {
      getItem: (key: string) => memory.get(key) ?? null,
      setItem: (key: string, value: string) => memory.set(key, value),
      removeItem: (key: string) => memory.delete(key),
      clear: () => memory.clear(),
    },
  });
  Object.defineProperty(globalThis, 'navigator', {configurable: true, value: {onLine: true, clipboard: {writeText: vi.fn().mockResolvedValue(undefined)}}});
  return {memory, message: {success: vi.fn(), warning: vi.fn(), error: vi.fn(), info: vi.fn()}};
});
vi.mock('element-plus', () => ({ElMessage: env.message, ElMessageBox: {confirm: vi.fn().mockResolvedValue('confirm')}}));
vi.mock('../src/utils/files', () => ({exportRows: vi.fn().mockResolvedValue(undefined), download: vi.fn(), workbook: vi.fn().mockResolvedValue(new Blob(['workbook'])), parseImport: vi.fn()}));

import {usePlatform} from '../src/stores/platform';
import {useAgent} from '../src/stores/agent';
import {resetMock} from '../src/mock/repository';
import {normalizeQuery, recognizeAgentIntent} from '../src/utils/agentNlu';
import {renderAgentMarkdown} from '../src/utils/agentMarkdown';
import {extractSseData, isLocalEndpoint, normalizeBaseUrl} from '../src/api/llm';

let p: ReturnType<typeof usePlatform>;
let agent: ReturnType<typeof useAgent>;
const recognition = (text: string) => recognizeAgentIntent(text, {sites: [{name: '中心广场', region: '中心区'}, {name: '东区车站', region: '东区'}]});
const lastAssistant = () => [...agent.messages].reverse().find(message => message.role === 'assistant')!;

beforeEach(async () => {
  localStorage.clear();
  resetMock();
  setActivePinia(createPinia());
  p = usePlatform();
  agent = useAgent();
  vi.clearAllMocks();
  await p.login('demo.admin', 'demo123');
});

describe('智能体主界面：意图识别与操作目录', () => {
  it('输入“布控”返回查看、新建、预警结果三个入口', () => {
    const result = recognition('布控');
    expect(result.intent.id).toBe('watch');
    expect(result.actions.map(action => action.label)).toEqual(['查看布控任务', '新建布控', '查看布控预警结果']);
    expect(result.actions[0]).toMatchObject({kind: 'navigate', page: '综合布控'});
    expect(result.actions[1]).toMatchObject({kind: 'create', page: '综合布控', handler: 'watch.create'});
    expect(result.actions[2]).toMatchObject({kind: 'navigate', page: '告警结果'});
  });

  it('“新建布控”把新建入口排在第一位，“查看布控预警结果”把预警入口排在第一位', () => {
    expect(recognition('新建布控').actions[0]).toMatchObject({id: 'watch.create', kind: 'create'});
    expect(recognition('查看布控预警结果').actions[0]).toMatchObject({id: 'watch.alerts', page: '告警结果'});
  });

  it('检索类问题提取目标与时间范围并带入筛选条件', () => {
    const result = recognition('检索 460000000000001 最近24小时');
    expect(result.intent.id).toBe('search');
    expect(result.entities.target).toBe('460000000000001');
    expect(result.entities.timeLabel).toBe('最近 24 小时');
    expect(result.actions[0]).toMatchObject({page: '围栏检索'});
    expect(result.actions[0]!.filters).toMatchObject({target: '460000000000001'});
    expect(String(result.actions[0]!.filters!.start)).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/);
  });

  it('车牌、MAC 与人像按类型路由，并识别场所', () => {
    expect(recognition('查一下京A12345').actions[0]).toMatchObject({page: '车牌检索'});
    expect(recognition('MAC aa:bb:cc:dd:ee:ff 最近7天').actions[0]).toMatchObject({page: 'MAC检索', filters: {target: 'AA:BB:CC:DD:EE:FF'}});
    expect(recognition('人像比对').actions[0]).toMatchObject({page: '人像检索'});
    expect(recognition('中心广场最近24小时采集记录').entities.site).toBe('中心广场');
  });

  it('研判、地图、运维、报表、任务各自返回正确入口', () => {
    expect(recognition('新建伴随分析').actions[0]).toMatchObject({id: 'analysis.companion.new', handler: 'analysis.companion'});
    expect(recognition('地图研判').actions[0]).toMatchObject({page: '地图研判'});
    expect(recognition('设备在线率').actions.map(action => action.page)).toContain('运维首页');
    expect(recognition('运维报表').actions.map(action => action.label)).toEqual(['查看运维报表', '新建运维报表']);
    expect(recognition('任务中心').actions[0]).toMatchObject({page: '任务中心'});
  });

  it('无法识别时回到能力清单而不是编造页面', () => {
    const result = recognition('帮我随便看看');
    expect(result.intent.id).toBe('help');
    expect(result.actions.every(action => action.page && action.page !== '智能对话')).toBe(true);
  });

  it('归一化与 Markdown 转义：模型输出不会被当作 HTML 注入', () => {
    expect(normalizeQuery('  布控   ')).toBe('布控');
    const html = renderAgentMarkdown('<script>alert(1)</script>\n\n- **重点** `code`');
    expect(html).not.toContain('<script>');
    expect(html).toContain('&lt;script&gt;');
    expect(html).toContain('<strong>重点</strong>');
    expect(html).toContain('<ul><li>');
  });
});

describe('智能体会话与页面跳转', () => {
  it('登录后默认主界面为智能对话，欢迎态可发送并得到操作卡片', async () => {
    expect(p.active).toBe('智能对话');
    expect(agent.isEmpty).toBe(true);
    await agent.send('布控');
    expect(agent.messages).toHaveLength(2);
    const answer = lastAssistant();
    expect(answer.intent).toBe('watch');
    expect(answer.status).toBe('done');
    expect(answer.actions?.map(action => action.label)).toEqual(['查看布控任务', '新建布控', '查看布控预警结果']);
    expect(answer.content).toContain('布控预警');
    expect(answer.steps?.every(step => step.state === 'done')).toBe(true);
    expect(agent.streaming).toBe(false);
    expect(agent.activeConversation?.title).toBe('布控');
    const stored = JSON.parse(localStorage.getItem('vue-agent-conversations-v1') ?? '{}');
    expect(stored.conversations[0].messages).toHaveLength(2);
  });

  it('点击“查看布控预警结果”跳到告警结果页；点击“新建布控”跳转并打开表单', async () => {
    await agent.send('布控');
    const actions = lastAssistant().actions!;
    expect(agent.runAction(actions[2]!)).toBe(true);
    expect(p.active).toBe('告警结果');
    expect(lastAssistant().content).toContain('告警结果');

    p.navigate('智能对话');
    await agent.send('新建布控');
    expect(agent.runAction(lastAssistant().actions![0]!)).toBe(true);
    expect(p.active).toBe('综合布控');
    expect(p.modal.visible).toBe(true);
    expect(p.modal.title).toContain('布控');
    p.modal.visible = false;
  });

  it('检索入口携带目标条件进入查询页', async () => {
    await agent.send('检索 460000000000001 最近24小时');
    const action = lastAssistant().actions![0]!;
    expect(agent.runAction(action)).toBe(true);
    expect(p.active).toBe('围栏检索');
    expect(p.f.target).toBe('460000000000001');
  });

  it('无权限的菜单不会被跳转，并在对话中说明原因', async () => {
    await p.logout();
    await p.login('demo.test', 'demo123');
    agent.createConversation();
    await agent.send('角色管理');
    const action = lastAssistant().actions!.find(item => item.page === '角色管理')!;
    expect(agent.runAction(action)).toBe(false);
    expect(p.active).toBe('智能对话');
    expect(lastAssistant().content).toContain('没有「角色管理」的访问权限');
  });

  it('会话可以新建、切换与删除，重新生成复用原问题', async () => {
    await agent.send('布控');
    const first = agent.activeId;
    agent.createConversation();
    expect(agent.conversations).toHaveLength(2);
    expect(agent.isEmpty).toBe(true);
    agent.selectConversation(first);
    expect(agent.messages).toHaveLength(2);
    const answerId = lastAssistant().id;
    await agent.regenerate(answerId);
    expect(agent.messages.filter(message => message.role === 'user')).toHaveLength(1);
    expect(lastAssistant().content).toContain('布控预警');
    agent.deleteConversation(first);
    expect(agent.conversations.some(item => item.id === first)).toBe(false);
  });

  it('刷新时把中断的流式消息恢复为可重新生成的完成态', () => {
    localStorage.setItem(
      'vue-agent-conversations-v1',
      JSON.stringify({
        version: 1,
        activeId: 'c1',
        conversations: [{id: 'c1', title: '中断的对话', createdAt: '2026-11-02 10:00:00', updatedAt: '2026-11-02 10:00:00', messages: [{id: 'm1', role: 'assistant', content: '', time: '2026-11-02 10:00:00', status: 'streaming'}]}],
      }),
    );
    setActivePinia(createPinia());
    const restored = useAgent();
    expect(restored.messages).toHaveLength(1);
    expect(restored.messages[0]?.status).toBe('done');
    expect(restored.messages[0]?.content).toContain('上次回答未完成');
  });
});

describe('本地大模型接入工具', () => {
  it('服务地址补全 /v1 并识别本机与内网地址', () => {
    expect(normalizeBaseUrl('http://127.0.0.1:11434')).toBe('http://127.0.0.1:11434/v1');
    expect(normalizeBaseUrl('http://127.0.0.1:11434/v1/')).toBe('http://127.0.0.1:11434/v1');
    expect(isLocalEndpoint('http://127.0.0.1:11434/v1')).toBe(true);
    expect(isLocalEndpoint('http://192.168.1.20:8000/v1')).toBe(true);
    expect(isLocalEndpoint('http://10.8.0.2:8000/v1')).toBe(true);
    expect(isLocalEndpoint('https://api.example.com/v1')).toBe(false);
  });

  it('SSE 解析兼容多行 data、event 行与不完整尾部', () => {
    const first = extractSseData('data: {"choices":[{"delta":{"content":"你"}}]}\n\ndata: {"choices":[{"delta":{"content":"好"}}]}\n\n');
    expect(first.events).toHaveLength(2);
    expect(first.rest).toBe('');
    const second = extractSseData('event: message\ndata: [DONE]\n\ndata: {"choices":[{"delta":{"content":"半"');
    expect(second.events).toEqual(['[DONE]']);
    expect(second.rest).toContain('半');
  });
});

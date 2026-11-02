/**
 * 智能体本地意图识别（规则引擎）。
 *
 * 设计目标：
 * 1. 不依赖大模型即可完成"输入 → 识别平台能力 → 生成可点击操作"的闭环，
 *    本地模型未部署、离线或超时都不会影响功能跳转。
 * 2. 大模型只负责把识别结果组织成自然语言，界面动作始终由本地目录决定，
 *    避免模型虚构页面、按钮或执行结果。
 * 3. 只暴露"入口"和聚合数量，不把采集记录、人员信息等明细交给模型或写入对话。
 */
import type {DataType,Filter} from '../domain';

export type AgentActionKind = 'navigate' | 'create' | 'prompt' | 'settings';

/** 对话中可点击的操作卡片。页面名称必须与 domain.ts 的 groups 一致。 */
export type AgentAction = {
  id: string;
  label: string;
  description: string;
  kind: AgentActionKind;
  /** navigate / create 的目标菜单页 */
  page?: string;
  /** create 时由 stores/agent.ts 分发的动作编号 */
  handler?: string;
  /** navigate 时预填的筛选条件 */
  filters?: Filter;
  /** prompt 时填充到输入框的建议文本 */
  prompt?: string;
  /** 卡片左上角短标识，避免额外引入图标依赖 */
  icon: string;
  /** primary 用于"新建"这类主操作 */
  tone?: 'primary' | 'default';
};

export type AgentIntentId =
  | 'watch'
  | 'search'
  | 'analysis'
  | 'map'
  | 'dataset'
  | 'library'
  | 'profiles'
  | 'model'
  | 'ops'
  | 'report'
  | 'notification'
  | 'admin'
  | 'task'
  | 'home'
  | 'help';

export type AgentIntent = {
  id: AgentIntentId;
  title: string;
  group: string;
  summary: string;
  keywords: string[];
  followUps: string[];
};

export type AgentMode = 'view' | 'create' | 'alerts';

export type AgentEntities = {
  target?: string;
  type?: DataType;
  start?: string;
  end?: string;
  timeLabel?: string;
  site?: string;
  region?: string;
  mode: AgentMode;
};

export type AgentStats = {
  /** HTTP 模式下数量以服务端为准，对话里不写死本地数字 */
  remote: boolean;
  captures: number;
  watches: number;
  runningWatches: number;
  alerts: number;
  devices: number;
  onlineDevices: number;
  sites: number;
  datasets: number;
  people: number;
  reports: number;
  tasks: number;
};

export type AgentRecognition = {
  intent: AgentIntent;
  score: number;
  entities: AgentEntities;
  actions: AgentAction[];
  steps: string[];
};

export const agentIntents: AgentIntent[] = [
  {
    id: 'watch',
    title: '布控预警',
    group: '布控预警',
    summary: '查看布控任务、新建布控、查看布控预警结果',
    keywords: ['布控', '布控任务', '布控告警', '布控预警', '预警', '告警', '命中', '管控', '盯防', '重点人员布控', '临时布控'],
    followUps: ['查看布控任务', '新建布控', '查看布控预警结果'],
  },
  {
    id: 'search',
    title: '数据检索',
    group: '数据检索',
    summary: '按电围、车牌、MAC、人像检索采集记录',
    keywords: ['检索', '查询', '搜索', '采集记录', '采集数据', '电围', '围栏', 'imsi', 'imei', '车牌', '车辆', '号牌', 'mac地址', 'wifi', '探针', '人像', '人脸', '比对', '搜图', '相似度'],
    followUps: ['围栏检索', '车牌检索', 'MAC检索', '人像检索'],
  },
  {
    id: 'analysis',
    title: '数据研判',
    group: '数据研判',
    summary: '发起伴随、碰撞、区域统计等分析任务',
    keywords: ['伴随', '同行', '碰撞', '共同出现', '区域统计', '区域分析', '研判', '分析任务', '研判任务'],
    followUps: ['新建伴随分析', '新建碰撞分析', '新建区域统计', '查看伴随任务'],
  },
  {
    id: 'map',
    title: '综合分析',
    group: '综合分析',
    summary: '地图研判、轨迹回放、设备分布',
    keywords: ['地图', '地图研判', '轨迹', '位置追踪', '热力', '设备分布', '点位', '框选'],
    followUps: ['打开地图研判', '查看设备分布', '查询目标轨迹'],
  },
  {
    id: 'dataset',
    title: '数据运算',
    group: '数据研判',
    summary: '数据集管理与交集、并集、差集运算',
    keywords: ['数据运算', '数据集', '集合', '交集', '并集', '差集', '交并差', '运算'],
    followUps: ['进入数据运算', '怎样把检索结果加入数据集'],
  },
  {
    id: 'library',
    title: '人员库',
    group: '数据检索',
    summary: '重点人员库、人员档案与批量导入',
    keywords: ['人员库', '重点人员', '名单', '人员档案', '人员信息'],
    followUps: ['查看人员库', '新建人员库', '如何批量导入人员'],
  },
  {
    id: 'profiles',
    title: '数据模型',
    group: '数据模型',
    summary: '图码档案、车码档案与数据态势',
    keywords: ['档案', '图码', '车码', '画像', '数据态势', '常驻', '标签'],
    followUps: ['打开图码档案', '打开车码档案', '查看数据态势'],
  },
  {
    id: 'model',
    title: '常驻数据模型',
    group: '数据模型',
    summary: '配置常驻模型的天数、次数与标签',
    keywords: ['常驻模型', '模型参数', '标签模型', '阈值', '连续出现', '模型配置'],
    followUps: ['打开数据态势', '编辑常驻模型参数'],
  },
  {
    id: 'ops',
    title: '运维服务',
    group: '运维服务',
    summary: '设备状态、在线率、参数配置与场所管理',
    keywords: ['运维', '设备', '在线率', '离线', '故障', '参数配置', '采集间隔', '功率', '场所', '复位', '设备状态'],
    followUps: ['打开运维首页', '查看设备管理', '进入参数配置'],
  },
  {
    id: 'report',
    title: '运维报表',
    group: '运维服务',
    summary: '生成与下载设备、数据、系统报表',
    keywords: ['报表', '运维报表', '统计报表', '报表生成', '导出报表'],
    followUps: ['查看运维报表', '新建运维报表'],
  },
  {
    id: 'notification',
    title: '运维通知',
    group: '运维服务',
    summary: '短信、邮件通知任务与发送记录',
    keywords: ['通知', '短信', '邮件', '通知任务', '告警通知'],
    followUps: ['查看运维通知', '新建通知任务', '查看短信日志'],
  },
  {
    id: 'admin',
    title: '系统设置',
    group: '系统设置',
    summary: '用户、角色权限与操作/短信日志',
    keywords: ['用户', '账号', '角色', '权限', '菜单权限', '操作日志', '短信日志', '日志'],
    followUps: ['用户管理', '角色管理', '操作日志'],
  },
  {
    id: 'task',
    title: '任务中心',
    group: '共享入口',
    summary: '查看异步任务进度、取消重试与结果下载',
    keywords: ['任务中心', '任务进度', '导出任务', '异步任务', '下载结果', '任务状态'],
    followUps: ['打开任务中心', '怎样下载导出结果'],
  },
  {
    id: 'home',
    title: '系统首页',
    group: '系统首页',
    summary: '返回首页查看在线率、采集量与排行',
    keywords: ['系统首页', '工作台', '大屏', '指标', '首页'],
    followUps: ['返回系统首页'],
  },
  {
    id: 'help',
    title: '平台帮助',
    group: '智能助手',
    summary: '了解平台能做什么，以及如何用一句话发起操作',
    keywords: ['帮助', '能做什么', '功能', '怎么用', '使用说明', '你会什么', '你好', 'hi', 'hello', '智能体', '介绍一下'],
    followUps: ['布控', '数据检索', '伴随分析', '运维报表'],
  },
];

const intentById = (id: AgentIntentId): AgentIntent => agentIntents.find(intent => intent.id === id)!;

const stamp = (ms: number) => new Date(ms).toLocaleString('sv-SE', {timeZone: 'Asia/Shanghai'}).replace(' ', 'T').slice(0, 16);

const nav = (id: string, label: string, page: string, description: string, icon: string, filters?: Filter): AgentAction => ({id, label, description, kind: 'navigate', page, filters, icon});
const create = (id: string, label: string, page: string, handler: string, description: string, icon: string): AgentAction => ({id, label, description, kind: 'create', page, handler, icon, tone: 'primary'});
const prompt = (id: string, label: string, text: string, description: string, icon: string): AgentAction => ({id, label, description, kind: 'prompt', prompt: text, icon});

const SEARCH_TYPES: {type: DataType; page: string; icon: string; label: string; words: string[]}[] = [
  {type: '电围', page: '围栏检索', icon: '围', label: '围栏检索', words: ['电围', '围栏', 'imsi']},
  {type: '车牌', page: '车牌检索', icon: '车', label: '车牌检索', words: ['车牌', '车辆', '号牌']},
  {type: 'MAC', page: 'MAC检索', icon: 'M', label: 'MAC 检索', words: ['mac', 'wifi', '探针']},
  {type: '人像', page: '人像检索', icon: '像', label: '人像检索', words: ['人像', '人脸', '比对', '搜图']},
];

/** 把多行中文文本压缩为可匹配形式：小写、去空白、全角字符归一。 */
export function normalizeQuery(text: string): string {
  return text
    .replace(/[\uFF01-\uFF5E]/g, char => String.fromCharCode(char.charCodeAt(0) - 0xFEE0))
    .replace(/\s+/g, ' ')
    .toLowerCase()
    .trim();
}

function matchMode(text: string): AgentMode {
  if (/(新建|新增|创建|发起|添加|做一个|开一个|临时布控|申请)/.test(text)) return 'create';
  if (/(告警|预警|命中|结果|记录)/.test(text)) return 'alerts';
  return 'view';
}

function extractTarget(text: string): {target?: string; type?: DataType} {
  const mac = text.match(/(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}/i);
  if (mac) return {target: mac[0].toUpperCase(), type: 'MAC'};
  const plate = text.match(/[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领][a-z][a-z0-9]{4,6}/i);
  if (plate) return {target: plate[0].toUpperCase(), type: '车牌'};
  const digits = text.match(/\b\d{15}\b/);
  if (digits) return {target: digits[0], type: '电围'};
  return {};
}

function extractTime(text: string, referenceNow?: number): {start?: string; end?: string; timeLabel?: string} {
  const now = referenceNow ?? Date.now();
  const explicit = text.match(/最近\s*(\d+)\s*(小时|天|周)/);
  if (explicit) {
    const amount = Number(explicit[1]);
    const unit = explicit[2] === '小时' ? 3600_000 : explicit[2] === '天' ? 86_400_000 : 7 * 86_400_000;
    return {start: stamp(now - amount * unit), end: stamp(now), timeLabel: '最近 ' + amount + ' ' + explicit[2]};
  }
  if (/今天|今日/.test(text)) return {start: stamp(now).slice(0, 10) + 'T00:00', end: stamp(now), timeLabel: '今天'};
  if (/昨天/.test(text)) {
    const day = stamp(now - 86_400_000).slice(0, 10);
    return {start: day + 'T00:00', end: day + 'T23:59', timeLabel: '昨天'};
  }
  if (/本周|这周/.test(text)) return {start: stamp(now).slice(0, 10) + 'T00:00', end: stamp(now), timeLabel: '本周'};
  if (/近7天|最近一周|一周|7天/.test(text)) return {start: stamp(now - 7 * 86_400_000), end: stamp(now), timeLabel: '最近 7 天'};
  return {};
}

export type RecognitionContext = {
  sites: {name: string; region: string}[];
  /** Mock 演示数据是固定日期，相对时间以数据最新时间为基准，避免"最近24小时"查不到演示记录。 */
  now?: number;
};

export function recognizeAgentIntent(rawText: string, context: RecognitionContext = {sites: []}): AgentRecognition {
  const text = normalizeQuery(rawText);
  const entities: AgentEntities = {mode: matchMode(text), ...extractTarget(text), ...extractTime(text, context.now)};
  const spotted = context.sites.find(site => text.includes(normalizeQuery(site.name)) || text.includes(normalizeQuery(site.region)));
  if (spotted) {
    entities.site = spotted.name;
    entities.region = spotted.region;
  }

  let best = intentById('help');
  let bestScore = 0;
  for (const intent of agentIntents) {
    let score = 0;
    for (const keyword of intent.keywords) {
      if (text.includes(keyword)) score += 2 + keyword.length;
    }
    if (score > bestScore) {
      best = intent;
      bestScore = score;
    }
  }
  if (!bestScore) {
    // 只输入一个目标号码时，默认理解为采集记录检索
    if (entities.target) {
      best = intentById('search');
      bestScore = 3;
    }
  }
  if (best.id === 'search' && !entities.type && !entities.target) {
    // 未给具体目标时，用关键词判断想查哪一类采集记录
    const hinted = SEARCH_TYPES.find(item => item.words.some(word => text.includes(word)));
    if (hinted) entities.type = hinted.type;
  }
  const actions = buildActions(best, entities, text);
  const steps = [
    `理解问题：${bestScore ? '匹配到「' + best.title + '」' : '未匹配到明确能力，按帮助处理'}`,
    `识别条件：${[entities.type && entities.type + '类型', entities.target && '目标 ' + entities.target, entities.timeLabel].filter(Boolean).join(' · ') || '未提供额外条件'}`,
    `准备操作：${actions.length ? actions.length + ' 个入口' : '能力清单'}`,
  ];
  return {intent: best, score: bestScore, entities, actions, steps};
}

function buildActions(intent: AgentIntent, entities: AgentEntities, text: string): AgentAction[] {
  switch (intent.id) {
    case 'watch': {
      const actions = [
        nav('watch.list', '查看布控任务', '综合布控', '查看运行中、已结束的布控任务与命中详情', '列'),
        create('watch.create', '新建布控', '综合布控', 'watch.create', '按数据类型、目标标识和时间段发起布控', '新'),
        nav('watch.alerts', '查看布控预警结果', '告警结果', '按布控任务和告警时间筛选命中记录', '警'),
      ];
      if (entities.mode === 'create') return [actions[1]!, actions[0]!, actions[2]!];
      if (entities.mode === 'alerts') return [actions[2]!, actions[0]!, actions[1]!];
      return actions;
    }
    case 'search': {
      const matched = entities.type ? SEARCH_TYPES.find(item => item.type === entities.type) : undefined;
      const ordered = matched ? [matched, ...SEARCH_TYPES.filter(item => item !== matched)] : SEARCH_TYPES;
      const filters: Filter = {};
      if (entities.target) filters.target = entities.target;
      if (entities.start) filters.start = entities.start;
      if (entities.end) filters.end = entities.end;
      if (entities.site) filters.site = entities.site;
      return [
        ...ordered.map(item => nav('search.' + item.type, item.label, item.page, entities.target ? '带入目标 ' + entities.target + ' 与时间范围查询' : '按目标号码、车牌、MAC 或人像检索采集记录', item.icon, {...filters})),
        nav('search.library', '人员库', '人员库', '查看重点人员库与人员档案', '库'),
      ];
    }
    case 'analysis': {
      const definitions: {key: string; name: string; page: string; icon: string; words: RegExp}[] = [
        {key: 'companion', name: '伴随分析', page: '伴随任务', icon: '伴', words: /伴随|同行/},
        {key: 'collision', name: '碰撞分析', page: '碰撞任务', icon: '碰', words: /碰撞|共同出现/},
        {key: 'region', name: '区域统计', page: '区域统计', icon: '域', words: /区域统计|区域分析|区域/},
      ];
      const matched = definitions.find(item => item.words.test(text));
      const ordered = matched ? [matched, ...definitions.filter(item => item !== matched)] : definitions;
      const actions: AgentAction[] = [];
      for (const item of ordered) {
        actions.push(nav('analysis.' + item.key, '查看' + item.name, item.page, '查看任务列表、状态与分类结果', item.icon));
        actions.push(create('analysis.' + item.key + '.new', '新建' + item.name, item.page, 'analysis.' + item.key, '填写目标、时间范围和任务选区后提交', '新'));
      }
      if (entities.mode === 'create' && matched) {
        const createAction = actions.find(action => action.id === 'analysis.' + matched.key + '.new');
        return createAction ? [createAction, ...actions.filter(action => action !== createAction)] : actions;
      }
      return actions;
    }
    case 'map':
      return [
        nav('map.judge', '地图研判', '地图研判', '在地图上查看点位、轨迹、热力与区域摘要', '图'),
        nav('map.devices', '设备分布', '设备分布', '查看设备点位、聚合与选点详情', '布'),
        prompt('map.track', '查询目标轨迹', '查询目标 460000000000001 最近24小时的轨迹', '补充目标与时间后由我带你进入地图', '轨'),
      ];
    case 'dataset':
      return [
        nav('dataset.run', '进入数据运算', '数据运算', '选择数据集做交集、并集或差集运算', '算'),
        prompt('dataset.add', '添加数据集', '把当前检索结果加入数据集', '在检索页把结果保存为数据集', '集'),
      ];
    case 'library':
      return [
        nav('library.list', '查看人员库', '人员库', '查看人员库、人员档案与布控入口', '库'),
        create('library.create', '新建人员库', '人员库', 'library.create', '填写库名与分类后开始维护人员', '新'),
      ];
    case 'profiles':
      return [
        nav('profiles.code', '图码档案', '图码档案', '按目标查看关联档案、证据与画像', '档'),
        nav('profiles.plate', '车码档案', '车码档案', '按车牌查看车辆档案与轨迹', '车'),
        nav('profiles.trend', '数据态势', '数据态势', '查看常驻模型与数据标签结果', '势'),
      ];
    case 'model':
      return [
        nav('model.overview', '打开数据态势', '数据态势', '查看模型标签结果与常驻数据', '势'),
        create('model.edit', '编辑常驻模型', '数据态势', 'model.edit', '调整连续出现天数、最少次数与标签', '改'),
      ];
    case 'ops':
      return [
        nav('ops.home', '运维首页', '运维首页', '查看在线率、采集量与设备概览', '维'),
        nav('ops.devices', '设备管理', '设备管理', '查看设备状态、参数、复位与导入导出', '备'),
        nav('ops.params', '参数配置', '参数配置', '按区域与分组批量配置设备参数', '参'),
        nav('ops.map', '设备分布', '设备分布', '在地图上查看设备点位与聚合', '图'),
      ];
    case 'report':
      return [
        nav('report.list', '查看运维报表', '运维报表', '查看历史报表并下载单报表或多报表', '报'),
        create('report.create', '新建运维报表', '运维报表', 'report.create', '选择报表类型、周期、日期与区域后生成', '新'),
      ];
    case 'notification':
      return [
        nav('notification.list', '查看运维通知', '运维通知', '查看通知任务、执行状态与最近发送', '通'),
        create('notification.create', '新建通知任务', '运维通知', 'notification.create', '配置短信/邮件、发送时间与接收人', '新'),
        nav('notification.sms', '短信日志', '短信日志', '查看短信发送记录与状态', '信'),
      ];
    case 'admin':
      return [
        nav('admin.users', '用户管理', '用户管理', '查看账号、角色与启用状态', '用'),
        nav('admin.roles', '角色管理', '角色管理', '配置菜单权限与场所范围', '角'),
        nav('admin.logs', '操作日志', '操作日志', '按用户、动作与时间查询操作记录', '志'),
        nav('admin.sms', '短信日志', '短信日志', '查看短信发送记录', '信'),
      ];
    case 'task':
      return [nav('task.list', '任务中心', '任务中心', '查看导出、导入、分析等异步任务进度与结果', '任')];
    case 'home':
      return [nav('home.dashboard', '返回系统首页', '系统首页', '查看在线率、采集量、排行与应用入口', '首')];
    default:
      return [
        nav('help.watch', '布控预警', '综合布控', '查看布控任务、新建布控、查看预警结果', '控'),
        nav('help.search', '数据检索', '围栏检索', '按电围、车牌、MAC、人像检索采集记录', '检'),
        nav('help.analysis', '数据研判', '伴随任务', '发起伴随、碰撞、区域统计等分析任务', '研'),
        nav('help.ops', '运维服务', '运维首页', '设备状态、参数配置、报表与通知', '维'),
      ];
  }
}

const entityLine = (entities: AgentEntities) => {
  const parts = [
    entities.type ? '类型 **' + entities.type + '**' : '',
    entities.target ? '目标 **' + entities.target + '**' : '',
    entities.timeLabel ? '时间 **' + entities.timeLabel + '**' : '',
    entities.site ? '场所 **' + entities.site + '**' : '',
  ].filter(Boolean);
  return parts.length ? '已识别条件：' + parts.join('、') + '。\n\n' : '';
};

const modeLine = (entities: AgentEntities) => {
  if (entities.mode === 'create') return '已把「新建」入口放在最前面，点击后会打开对应表单。';
  if (entities.mode === 'alerts') return '已把预警结果入口放在最前面。';
  return '';
};

function statText(stats: AgentStats, lines: string[]): string {
  if (stats.remote) return '当前为接口模式，以下数量以服务端最新数据为准。';
  return lines.join('；') + '。';
}

/** 规则引擎应答模板：只描述能力与聚合概况，不虚构明细，也不替代用户执行操作。 */
export function buildAgentReply(recognition: AgentRecognition, stats: AgentStats): string {
  const {intent, entities} = recognition;
  const line = modeLine(entities);
  const tail = line ? '\n\n' + line : '';
  switch (intent.id) {
    case 'watch':
      return `已识别为「布控预警」。${statText(stats, [
        `当前有 **${stats.watches} 个布控任务**（运行中 ${stats.runningWatches} 个）`,
        `累计命中 **${stats.alerts} 条**`,
      ])}\n\n${entityLine(entities)}点击下方卡片可直接进入布控任务列表、新建布控表单或预警结果页，操作会在原业务界面完成，不会在这里直接执行。${tail}`;
    case 'search':
      return `已识别为「数据检索」。${statText(stats, [
        `平台已有 **${stats.captures} 条**采集记录`,
        `覆盖 **${stats.sites} 个**场所`,
        `人员库 **${stats.people} 人**`,
      ])}\n\n${entityLine(entities)}选择一种数据类型进入检索页；如果提供目标号码、车牌、MAC 或人像，我会把条件带到查询表单。${tail}`;
    case 'analysis':
      return `已识别为「数据研判」。${statText(stats, [
        `已保存 **${stats.datasets} 个**数据集`,
        `可发起伴随、碰撞、区域统计三类分析`,
      ])}\n\n${entityLine(entities)}先「查看」任务列表了解已有结果，或直接「新建」一个分析任务；具体参数在任务表单中确认。${tail}`;
    case 'map':
      return `已识别为「综合分析」。地图研判用于轨迹、热力与区域摘要，设备分布用于点位聚合与选点详情。${statText(stats, [`当前 **${stats.devices} 台**设备、**${stats.sites} 个**场所`])}\n\n${entityLine(entities)}进入地图后会按服务端/本地数据绘制，不会在对话中输出轨迹明细。${tail}`;
    case 'dataset':
      return `已识别为「数据运算」。数据集保存检索或分析结果，可在数据运算页组合成新的集合（交集、并集、差集）。${statText(stats, [`当前有 **${stats.datasets} 个**数据集`])}\n\n${entityLine(entities)}先进入数据运算查看已有数据集；新增数据集请在检索结果或分析结果上操作。${tail}`;
    case 'library':
      return `已识别为「人员库」。人员库用于维护重点人员与自建名单，人员可携带 IMSI、特征码、常驻地等信息并用于布控。${statText(stats, [`当前人员库共 **${stats.people} 人**`])}\n\n${entityLine(entities)}可以查看已有人员库，或新建一个库后批量导入人员。${tail}`;
    case 'profiles':
      return `已识别为「数据模型」。图码档案、车码档案按目标聚合关联记录与证据，数据态势展示常驻模型标签。${statText(stats, [`当前有 **${stats.captures} 条**采集记录可供关联`])}\n\n${entityLine(entities)}档案内容涉及受控信息，进入页面后按权限查看，对话中不展示明细。${tail}`;
    case 'model':
      return `已识别为「常驻数据模型」。模型按"连续出现天数 + 每日最少采集次数"给目标打标签，标签结果在数据态势中查看。${statText(stats, ['模型参数保存到数据态势页'])}\n\n${entityLine(entities)}可以查看数据态势，或直接编辑常驻模型参数。${tail}`;
    case 'ops':
      return `已识别为「运维服务」。${statText(stats, [
        `当前 **${stats.devices} 台**设备，在线 **${stats.onlineDevices} 台**`,
        `共 **${stats.sites} 个**场所`,
      ])}\n\n${entityLine(entities)}运维首页看总览，设备管理做状态/参数/导入导出，参数配置用于批量下发；设备命令在真实环境中需要后端执行。${tail}`;
    case 'report':
      return `已识别为「运维报表」。${statText(stats, [`已生成 **${stats.reports} 份**报表`])}\n\n${entityLine(entities)}可以查看历史报表并下载，或按报表类型、周期、日期和区域新建一份。报表生成在接口模式下是异步任务，完成后到任务中心下载。${tail}`;
    case 'notification':
      return `已识别为「运维通知」。通知任务支持短信/邮件，按设定时间向接收人发送；发送记录可在短信日志中核对。${statText(stats, ['通知执行状态以服务端记录为准'])}\n\n${entityLine(entities)}可以查看已有通知任务、新建任务，或直接查看短信日志。${tail}`;
    case 'admin':
      return `已识别为「系统设置」。用户管理维护账号与角色，角色管理控制菜单权限和场所范围，日志用于审计。${statText(stats, ['权限由后端逐请求校验'])}\n\n${entityLine(entities)}注意：页面隐藏按钮不等于授权，越权操作会被后端拒绝。${tail}`;
    case 'task':
      return `已识别为「任务中心」。导出、导入、分析、模型和报表等异步任务都在这里查看进度、取消重试并下载结果。${statText(stats, [`当前关联 **${stats.tasks} 个**任务`])}\n\n${entityLine(entities)}任务成功后才可下载文件；失败时任务详情会给出行级错误。${tail}`;
    case 'home':
      return '好的，已为你准备返回系统首页。首页包含设备在线率、采集量、场所排行与应用入口，也可以从左侧菜单继续进入其他功能。';
    default:
      return `我是多维数据分析智能体，可以把你的一句话转成平台里的具体操作。当前我可以处理下面几类事情：

- **布控预警**：查看布控任务、新建布控、查看预警结果
- **数据检索**：按电围（IMSI）、车牌、MAC、人像检索采集记录
- **数据研判**：发起伴随、碰撞、区域统计任务，管理数据集
- **综合分析**：地图研判、轨迹、设备分布
- **数据模型**：图码/车码档案、数据态势与常驻模型
- **运维服务**：设备状态、参数配置、报表与通知
- **系统设置**：用户、角色权限与操作日志

你可以直接说「布控」「检索 460000000000001 最近24小时」「新建伴随分析」这样的话；也可以点击下面的常用入口。需要我先做哪一件？`;
  }
}

export function agentSuggestions(recognition: AgentRecognition): string[] {
  const {intent, entities} = recognition;
  if (intent.id === 'help') return ['布控', '数据检索', '伴随分析', '运维报表', '任务中心'];
  const items = [...intent.followUps];
  if (entities.type && intent.id === 'search') items.unshift('查看' + entities.type + '检索');
  return [...new Set(items)].slice(0, 5);
}

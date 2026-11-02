#!/usr/bin/env node
/**
 * 本地契约桩（Contract Stub）——仅本地开发用，不参与构建，不进 dist。
 *
 * 作用：后端业务 API 尚未实现时，让前端能走**真实 HTTP 路径**（浏览器 → Vite 代理 →
 * 本桩），把 Cookie / CSRF / 请求头 / 状态码 / 响应结构校验全部提前跑通。
 *
 * 它不是"假数据演示"：响应结构取自 contracts/api/openapi.yaml 的强类型样例，
 * 由前端同一份 AJV 契约校验；任何一个字段不合契约，前端都会抛"接口响应不符合约定"。
 * 因此它可以当作给后端的**可执行接口规范**：实现这张表里的端点即可替换本桩。
 *
 * 用法：
 *   npm run stub                      # 监听 8080 /api/v1
 *   npm run stub -- --port 8090       # 换端口
 *   npm run stub -- --selfcheck       # 只做契约自检，校验全部样例响应后退出
 *   npm run stub -- --latency 300     # 每个响应加 300ms 延迟，观察加载态
 *
 * 场景开关（浏览器或 curl 访问即可，无需重启）：
 *   GET /_stub/scenario?s=ok           正常
 *   GET /_stub/scenario?s=forbidden    业务端点返回 403 PERMISSION_DENIED
 *   GET /_stub/scenario?s=conflict     返回 409 VERSION_CONFLICT
 *   GET /_stub/scenario?s=expired      返回 410 CONTEXT_EXPIRED
 *   GET /_stub/scenario?s=server       返回 500 INTERNAL_ERROR
 *   GET /_stub/scenario?s=timeout      延迟 25s（触发前端 20s 超时）
 *   GET /_stub/scenario?s=empty        列表/检索/轨迹返回空集
 *   GET /_stub/scenario?s=pending      导出任务永远 RUNNING
 *   GET /_stub/scenario?s=unauthorized 业务端点返回 401（会话失效）
 *   GET /_stub/state                   查看当前场景、会话与任务
 *
 * 边界：CSRF 轮换、Cookie、幂等键、202 任务状态机都按契约模拟，但不是真实安全实现；
 * 它不能替代真实后端联调，也不能作为 215 项业务验收证据。
 */
import {createServer} from 'node:http';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {dirname, join} from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const contract = JSON.parse(readFileSync(join(here, '..', 'src', 'api', 'contract.json'), 'utf8'));

// ---------- CLI ----------
const argv = process.argv.slice(2);
const arg = (name, fallback) => {
  const i = argv.indexOf(`--${name}`);
  return i >= 0 && argv[i + 1] ? argv[i + 1] : fallback;
};
const PORT = Number(arg('port', 8080));
const LATENCY = Number(arg('latency', 0));
const SELFCHECK = argv.includes('--selfcheck');

// ---------- 常量：演示数据 ----------
// 菜单必须与 src/domain.ts 的 29 个菜单标签一致，否则 httpMenus 过滤后侧边栏会缺项。
const MENU_LABELS = [
  '智能对话',
  '系统首页',
  '围栏检索', '车牌检索', 'MAC检索', '人像检索', '人员库',
  '地图研判',
  '伴随任务', '碰撞任务', '区域统计', '数据运算',
  '综合布控', '告警结果',
  '图码档案', '车码档案', '数据态势',
  '运维首页', '场所管理', '设备管理', '设备分布', '参数配置', '运维报表', '运维通知',
  '用户管理', '角色管理', '操作日志', '短信日志',
  '任务中心',
];

const USER_ID = '00000000-0000-4000-8000-000000000001';

const SITES = [
  {siteId: '10000000-0000-4000-8000-000000000001', name: '中心广场', regionCode: '110101', deviceCount: 3},
  {siteId: '10000000-0000-4000-8000-000000000002', name: '东区车站', regionCode: '110105', deviceCount: 2},
  {siteId: '10000000-0000-4000-8000-000000000003', name: '西区公园', regionCode: '110106', deviceCount: 2},
  {siteId: '10000000-0000-4000-8000-000000000004', name: '北区路口', regionCode: '110108', deviceCount: 1},
  {siteId: '10000000-0000-4000-8000-000000000005', name: '南区商场', regionCode: '110112', deviceCount: 1},
];

const DEVICES = [
  {deviceId: '20000000-0000-4000-8000-000000000001', name: '设备 A-01', site: SITES[0], lng: 116.397, lat: 39.909, status: 'ONLINE'},
  {deviceId: '20000000-0000-4000-8000-000000000002', name: '设备 A-02', site: SITES[1], lng: 116.461, lat: 39.921, status: 'ONLINE'},
  {deviceId: '20000000-0000-4000-8000-000000000003', name: '设备 A-03', site: SITES[2], lng: 116.318, lat: 39.938, status: 'OFFLINE'},
  {deviceId: '20000000-0000-4000-8000-000000000004', name: '设备 A-04', site: SITES[3], lng: 116.372, lat: 39.972, status: 'ONLINE'},
  {deviceId: '20000000-0000-4000-8000-000000000005', name: '设备 A-05', site: SITES[4], lng: 116.421, lat: 39.877, status: 'UNKNOWN'},
];

const PERMISSIONS = [
  'dashboard:read', 'capture:read', 'capture:export', 'context:use', 'map:read',
  'analysis:read', 'dataset:read', 'watch:read', 'notification:read', 'device:read',
  'report:read', 'user:read', 'role:read', 'audit:read', 'delivery:read',
  'task:read', 'task:manage', 'file:download', 'site:read', 'profile:read',
  'model:read', 'person-library:read',
];

const FEATURES = [
  'FACE_SEARCH', 'DECRYPTION', 'COMPANION_ANALYSIS', 'COLLISION_ANALYSIS', 'REGION_STATISTICS',
  'PROFILE_ASSOCIATION', 'RESIDENCE_MODEL', 'WATCH_ACTIVATION', 'NOTIFICATION_SEND',
  'DEVICE_COMMAND', 'REPORT_GENERATION', 'RAW_INGESTION',
];

// ---------- 工具 ----------
const now = () => new Date();
const iso = (d = now()) => new Date(d).toISOString();
const minutesAgo = (n) => new Date(Date.now() - n * 60_000);
const trace = (op) => `stub-${op}-${Date.now().toString(36)}`;
const deepClone = (v) => JSON.parse(JSON.stringify(v));

/** 从契约样例里取某个操作的成功响应信封（保证字段集合与契约一致）。 */
function exampleEnvelope(op) {
  const responses = contract.operations[op].responses;
  for (const status of Object.keys(responses)) {
    const media = responses[status].content?.['application/json'];
    const value = media?.examples?.success?.value;
    if (value) return {status: Number(status), body: deepClone(value)};
  }
  throw new Error(`契约中没有 ${op} 的 JSON 成功样例`);
}

/** 只替换样例里 data 部分，保持外层 code/message/traceId 结构。 */
function envelope(op, data, {status, traceId} = {}) {
  const {status: exampleStatus, body} = exampleEnvelope(op);
  return {status: status ?? exampleStatus, body: {...body, data, traceId: traceId || trace(op)}};
}

const fail = (status, code, message, details = null) => ({
  status,
  body: {code, message, traceId: trace(code), ...(details ? {details} : {})},
});

// ---------- 状态 ----------
const state = {
  scenario: 'ok',
  csrf: null,
  session: null,
  // 模拟"会话空闲过期"：业务端点返回 401，但续期接口可以恢复
  expired: false,
  loginAttempts: 0,
  tasks: new Map(),
  fileId: '30000000-0000-4000-8000-000000000001',
  requests: [],
};

const rotateCsrf = () => (state.csrf = `stub-csrf-${Math.random().toString(36).slice(2, 18)}`);

// ---------- 数据构造 ----------
function menus() {
  return MENU_LABELS.map((label, i) => ({
    menuId: `40000000-0000-4000-8000-${String(i + 1).padStart(12, '0')}`,
    code: `MENU_${String(i + 1).padStart(2, '0')}`,
    label,
    route: `/page/${i}`,
    order: i + 1,
  }));
}

function meData() {
  const t = iso();
  return {
    user: {userId: USER_ID, loginName: 'stub.admin', displayName: '桩管理员', status: 'ACTIVE'},
    roleIds: ['50000000-0000-4000-8000-000000000001'],
    permissionCodes: PERMISSIONS,
    menus: menus(),
    dataScope: {siteIds: [], allSites: true, dataTypes: ['FENCE', 'PLATE', 'MAC', 'FACE'], ownOnly: false, scopeVersion: 1},
    siteScope: {siteIds: [], allSites: true},
    session: {userId: USER_ID, idleExpiresAt: new Date(Date.now() + 30 * 60_000).toISOString(), absoluteExpiresAt: new Date(Date.now() + 8 * 3600_000).toISOString()},
  };
}

function configData() {
  const base = exampleEnvelope('getClientConfig').body.data;
  return {
    ...base,
    version: 'stub-1.0.0',
    features: FEATURES.map((featureCode) => ({featureCode, enabled: true, pendingReason: null})),
    featureAvailability: FEATURES.map((code) => ({code, enabled: true})),
    uploadPolicies: [{category: 'FACE_SEARCH', mimeTypes: ['image/jpeg', 'image/png'], extensions: ['jpg', 'jpeg', 'png'], maxBytes: 5_242_880, enabled: true}],
  };
}

function sitePage() {
  return {
    items: SITES.map((s, i) => ({...s, enabled: true, updatedAt: iso(minutesAgo(30 * (i + 1))), version: 1})),
    page: 1,
    pageSize: 100,
    total: SITES.length,
  };
}

const variantOf = (dataType) => ({FENCE: 'fence', PLATE: 'plate', MAC: 'mac', FACE: 'face'}[dataType] || 'fence');

function capture(dataType, i, fromMs, pageRows) {
  const device = DEVICES[i % DEVICES.length];
  const at = new Date(fromMs - i * 47_000);
  const variant = variantOf(dataType);
  const record = {
    captureId: `60000000-0000-4000-8000-${String(i + 1).padStart(12, '0')}`,
    dataType,
    targetId: `70000000-0000-4000-8000-${String((i % 9) + 1).padStart(12, '0')}`,
    targetType: dataType === 'FENCE' ? 'IMSI' : dataType,
    deviceId: device.deviceId,
    siteId: device.site.siteId,
    captureTime: at.toISOString(),
    ingestedAt: new Date(at.getTime() + 4_000).toISOString(),
    longitude: device.lng + (i % 5) * 0.0012,
    latitude: device.lat + (i % 3) * 0.0009,
    source: `STUB_${dataType}`,
    status: 'VALID',
    displayTarget: '',
    isMasked: true,
  };
  if (dataType === 'FENCE') {
    record.displayTarget = `4600****${String(1000 + (i % 900))}`;
    record.fence = {imsiMasked: record.displayTarget, imeiMasked: `8600****${String(2000 + (i % 700))}`};
  } else if (dataType === 'PLATE') {
    record.displayTarget = `京A${String.fromCharCode(65 + (i % 6))}${String(100 + (i % 899))}`;
    record.plate = {plateMasked: record.displayTarget};
  } else if (dataType === 'MAC') {
    record.displayTarget = `AC:8B:${String(10 + (i % 80))}:**:**`;
    record.mac = {macMasked: record.displayTarget};
  } else {
    record.displayTarget = `人像-${String(i + 1).padStart(3, '0')}`;
    record.face = {imageAssetId: `C0000000-0000-4000-8000-${String(i + 1).padStart(12, '0')}`, modelVersion: 'stub-model-1.0', qualityScore: 0.9};
  }
  return record;
}

function searchCaptures(body) {
  const dataType = body?.dataType || 'FENCE';
  const from = Date.parse(body?.timeRange?.from || new Date(Date.now() - 3600_000).toISOString());
  const to = Date.parse(body?.timeRange?.to || new Date().toISOString());
  const pageSize = Math.min(Number(body?.pageSize) || 20, 100);
  const page = Number(readCursor(body?.cursor) || 1);
  const total = 25;
  if (state.scenario === 'empty') return {items: [], nextCursor: null, pageSize};
  const startIndex = (page - 1) * pageSize;
  const count = Math.max(0, Math.min(pageSize, total - startIndex));
  const span = Math.max(60_000, Math.min(to - from, 7 * 24 * 3600_000) - 60_000);
  const items = [];
  for (let k = 0; k < count; k++) {
    const i = startIndex + k;
    items.push(capture(dataType, i, to - 60_000 - (span * i) / total, pageSize));
  }
  const hasMore = startIndex + count < total;
  return {items, nextCursor: hasMore ? writeCursor(page + 1) : null, pageSize};
}

const readCursor = (cursor) => {
  if (!cursor) return null;
  try {
    return Buffer.from(String(cursor), 'base64url').toString('utf8').split(':')[1] || null;
  } catch {
    return null;
  }
};
const writeCursor = (page) => Buffer.from(`page:${page}`, 'utf8').toString('base64url');

function mapFeatures() {
  const features = DEVICES.map((d) => ({
    type: 'Feature',
    id: d.deviceId,
    geometry: {type: 'Point', coordinates: [d.lng, d.lat]},
    properties: {
      featureType: 'DEVICE',
      deviceId: d.deviceId,
      deviceName: d.name,
      displayLabel: d.name,
      siteId: d.site.siteId,
      siteName: d.site.name,
      regionCode: d.site.regionCode,
      dataType: 'FENCE',
      status: d.status,
      count: 12 + Number(d.deviceId.slice(-1)) * 3,
    },
  }));
  // 混入一个 CAPTURE 要素，验证前端只取 featureType=DEVICE 的点位
  features.push({
    type: 'Feature',
    id: '80000000-0000-4000-8000-000000000001',
    geometry: {type: 'Point', coordinates: [116.404, 39.915]},
    properties: {featureType: 'CAPTURE', displayLabel: '采集点'},
  });
  return {type: 'FeatureCollection', features, aggregated: false, truncated: false, maxFeatures: 500, asOf: iso()};
}

function trajectory() {
  const base = DEVICES[0];
  if (state.scenario === 'empty') {
    return {items: [], nextCursor: null, pageSize: 100, sortBy: 'captureTime', sortOrder: 'ASC'};
  }
  const items = Array.from({length: 8}, (_, i) => ({
    captureId: `90000000-0000-4000-8000-${String(i + 1).padStart(12, '0')}`,
    captureTime: iso(minutesAgo(120 - i * 12)),
    location: {type: 'Point', coordinates: [base.lng + i * 0.0035, base.lat + i * 0.0022]},
    dataType: 'FENCE',
    target: {targetId: '70000000-0000-4000-8000-000000000001', targetType: 'IMSI'},
  }));
  return {items, nextCursor: null, pageSize: 100, sortBy: 'captureTime', sortOrder: 'ASC'};
}

function dashboard() {
  const base = exampleEnvelope('getDashboardSummary').body.data;
  const meta = (unit = '台', status = 'READY', refreshPolicy = 'POLL') => ({
    asOf: iso(minutesAgo(1)), status, unit, refreshPolicy,
  });
  const series = (codes, points) =>
    codes.map(([code, label], idx) => ({
      code, label,
      points: points.map((at, i) => ({at, value: 800 + idx * 260 + i * 120 + (i % 3) * 40})),
    }));
  const days = Array.from({length: 7}, (_, i) => iso(new Date(Date.now() - (6 - i) * 86_400_000)));
  const hours = Array.from({length: 12}, (_, i) => iso(minutesAgo((11 - i) * 120)));
  return {
    ...base,
    onlineDevices: {
      meta: meta(),
      total: DEVICES.length,
      online: DEVICES.filter((d) => d.status === 'ONLINE').length,
      onlineRate: 0.6,
      byDeviceType: [{deviceType: 'FENCE', total: DEVICES.length, online: DEVICES.filter((d) => d.status === 'ONLINE').length}],
      byPowerCategory: [
        {powerCategoryCode: 'LOW', label: '低功率', total: 4, online: 3, onlineRate: 0.75},
        {powerCategoryCode: 'MID', label: '中功率', total: 3, online: 2, onlineRate: 0.667},
        {powerCategoryCode: 'HIGH', label: '高功率', total: 3, online: 3, onlineRate: 1},
      ],
    },
    lastSevenDaysCapture: {meta: meta('条'), series: series([['FENCE', '电围'], ['PLATE', '车牌'], ['MAC', 'MAC']], days)},
    topCaptureSites: {meta: meta('条'), items: SITES.map((s, i) => ({key: s.siteId, label: s.name, value: 980 - i * 130}))},
    todayAndTotalCapture: {meta: meta('条'), todayCaptured: 12_480, allCaptured: 12_800_000, todayIngested: 11_902, allIngested: 12_640_310},
    deviceCaptureTotals: {meta: meta('条'), items: [
      {code: 'FENCE', label: '电围', count: 123_000},
      {code: 'PLATE', label: '车牌', count: 246_000},
      {code: 'MAC', label: 'MAC', count: 369_000},
    ]},
    alertCount: {meta: meta('次'), value: 7},
    deviceMap: {meta: meta('台'), deviceCount: DEVICES.length, siteCount: SITES.length, mapEndpoint: '/map/devices'},
    deduplicatedCapture: {meta: meta('条'), items: [{code: 'FENCE', label: '电围', count: 12_100}, {code: 'PLATE', label: '车牌', count: 24_300}]},
    originTopTen: {meta: meta('条'), items: Array.from({length: 10}, (_, i) => ({key: `ORIGIN_${i + 1}`, label: `归属地 ${i + 1}`, value: 1000 - i * 70}))},
    applicationEntries: [
      {code: 'SEARCH', label: '数据检索', route: '/page/1'},
      {code: 'ANALYSIS', label: '数据研判', route: '/page/7'},
      {code: 'MAP', label: '综合分析', route: '/page/6'},
      {code: 'WATCH', label: '布控预警', route: '/page/10'},
    ],
    fenceRadioStatistics: {meta: meta('条'), series: series([['2G', '2G'], ['4G', '4G'], ['5G', '5G']], days)},
    operationStatistics: {meta: meta('次'), series: series([['SEARCH', '数据查询'], ['ANALYSIS', '数据分析'], ['MAINTAIN', '运维操作']], days)},
    topSitesByDataType: ['FENCE', 'PLATE', 'MAC'].map((dataType) => ({
      dataType,
      ranking: {meta: meta('条'), items: SITES.map((s, i) => ({key: s.siteId, label: s.name, value: 800 - i * 120}))},
    })),
    deduplicatedSeries: ['FENCE', 'PLATE'].map((dataType) => ({
      dataType,
      originClass: 'ALL',
      metric: {meta: meta('条'), series: series([[`${dataType}_ALL`, '全部']], days)},
    })),
    deviceAlertCounts: {meta: meta('次'), items: DEVICES.map((d, i) => ({deviceId: d.deviceId, deviceName: d.name, count: (i % 4) + 1}))},
  };
}

function taskView(taskId) {
  const task = state.tasks.get(taskId);
  if (!task) return null;
  const elapsed = Date.now() - task.createdAtMs;
  const pending = state.scenario === 'pending';
  const cancelled = Boolean(task.cancelledAt) || state.scenario === 'cancelled';
  const progress = cancelled ? 45 : pending ? 40 : elapsed < 1500 ? 20 : elapsed < 3000 ? 65 : 100;
  const status = cancelled ? 'CANCELLED' : pending ? 'RUNNING' : progress === 100 ? 'SUCCESS' : 'RUNNING';
  const view = {
    ...deepClone(exampleEnvelope('getTask').body.data),
    taskId,
    taskType: 'EXPORT',
    status,
    progress,
    ownerUserId: USER_ID,
    createdAt: new Date(task.createdAtMs).toISOString(),
    startedAt: new Date(task.createdAtMs + 300).toISOString(),
    finishedAt: status === 'SUCCESS' || status === 'CANCELLED' ? new Date(task.createdAtMs + 3200).toISOString() : undefined,
    retryCount: task.retryCount || 0,
    name: `检索结果导出 ${taskId.slice(-4)}`,
    traceId: task.traceId,
    parameterSnapshot: {
      schemaVersion: '1.0',
      contextId: task.contextId || 'A0000000-0000-4000-8000-000000000000',
      submittedAt: new Date(task.createdAtMs).toISOString(),
      parameterHash: '0'.repeat(56) + String(state.tasks.size + 1).padStart(8, '0'),
    },
    resultReference: status === 'SUCCESS' ? {
      resultType: 'EXPORT',
      fileAssetId: state.fileId,
      expiresAt: new Date(task.createdAtMs + 7 * 86_400_000).toISOString(),
    } : undefined,
  };
  return view;
}

// ---------- 导出文件（最小可用 XLSX，可直接被 Excel/WPS/openpyxl 打开） ----------
async function buildXlsx() {
  const {default: JSZip} = await import('jszip');
  const zip = new JSZip();
  const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  const rows = [
    ['采集时间', '数据类型', '目标标识', '场所', '设备'],
    ...Array.from({length: 5}, (_, i) => {
      const c = capture('FENCE', i, Date.now() - 60_000, 20);
      return [c.captureTime, '电围', c.displayTarget, DEVICES[i % DEVICES.length].site.name, DEVICES[i % DEVICES.length].name];
    }),
  ];
  const cell = (value, ref) =>
    `<c r="${ref}" t="inlineStr"><is><t>${esc(value)}</t></is></c>`;
  const sheetRows = rows
    .map((row, r) => `<row r="${r + 1}">${row.map((v, c) => cell(v, `${String.fromCharCode(65 + c)}${r + 1}`)).join('')}</row>`)
    .join('');
  zip.file('[Content_Types].xml', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>`);
  zip.file('_rels/.rels', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>`);
  zip.file('xl/workbook.xml', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="检索结果" sheetId="1" r:id="rId1"/></sheets></workbook>`);
  zip.file('xl/_rels/workbook.xml.rels', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>`);
  zip.file('xl/worksheets/sheet1.xml', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>${sheetRows}</sheetData></worksheet>`);
  return zip.generateAsync({type: 'nodebuffer'});
}

// ---------- 场景 ----------
const scenarioError = () => {
  switch (state.scenario) {
    case 'forbidden': return fail(403, 'PERMISSION_DENIED', '当前账号无此操作权限（桩场景 forbidden）');
    case 'conflict': return fail(409, 'VERSION_CONFLICT', '数据已被他人修改，请刷新后重试（桩场景 conflict）');
    case 'expired': return fail(410, 'CONTEXT_EXPIRED', '查询上下文已过期，请重新查询（桩场景 expired）');
    case 'server': return fail(500, 'INTERNAL_ERROR', '服务内部错误（桩场景 server）');
    case 'unauthorized': return fail(401, 'AUTH_REQUIRED', '会话已失效，请重新登录（桩场景 unauthorized）');
    default: return null;
  }
};

// ---------- 路由 ----------
const routes = [];
const route = (method, path, handler) => routes.push({method, path, handler});

route('GET', '/_stub/scenario', ({query}) => {
  const next = query.get('s') || 'ok';
  const allowed = ['ok', 'forbidden', 'conflict', 'expired', 'server', 'timeout', 'empty', 'pending', 'cancelled', 'unauthorized'];
  if (!allowed.includes(next)) return fail(400, 'STUB_INVALID_SCENARIO', `可选值：${allowed.join('|')}`);
  state.scenario = next;
  return {status: 200, body: {scenario: next}};
});

route('GET', '/_stub/state', () => ({
  status: 200,
  body: {
    scenario: state.scenario,
    session: state.session ? 'active' : 'none',
    csrfRotations: state.csrf ? 1 : 0,
    tasks: [...state.tasks.keys()],
    recentRequests: state.requests.slice(-15),
  },
}));

route('GET', '/_stub/reset', () => {
  state.scenario = 'ok';
  state.session = null;
  state.csrf = null;
  state.expired = false;
  state.tasks.clear();
  state.requests.length = 0;
  return {status: 200, body: {reset: true}};
});

// 让当前会话"过期"：业务端点 401，续期接口可恢复。用于验证 401 静默续期。
route('GET', '/_stub/expire', () => {
  if (!state.session) return fail(400, 'STUB_NO_SESSION', '当前没有登录会话，无法置为过期');
  state.expired = true;
  return {status: 200, body: {expired: true}};
});

route('GET', '/auth/csrf', () => {
  rotateCsrf();
  return {
    ...envelope('getCsrfToken', {token: state.csrf, headerName: 'X-CSRF-Token'}),
    cookies: [`CSRF-TOKEN=${state.csrf}; Path=/; SameSite=Lax`],
  };
});

route('POST', '/auth/login', ({body, headers}) => {
  state.loginAttempts++;
  if (headers['x-csrf-token'] !== state.csrf) {
    return fail(403, 'CSRF_INVALID', 'CSRF 校验失败：请先 GET /auth/csrf 并回传 X-CSRF-Token');
  }
  if (!headers['idempotency-key']) {
    return fail(400, 'IDEMPOTENCY_KEY_REQUIRED', '写请求缺少 Idempotency-Key');
  }
  if (!body?.loginName || !body?.password) {
    return fail(400, 'VALIDATION_FAILED', 'loginName 与 password 必填');
  }
  // 演示账号：stub.admin / stub123；其余账号也接受，便于联调
  if (body.password === 'wrong') {
    return fail(401, 'AUTH_INVALID_CREDENTIALS', '账号或密码错误');
  }
  state.session = `stub-sid-${Math.random().toString(36).slice(2, 12)}`;
  // 契约要求：登录成功后轮换 CSRF，前端会再次 GET /auth/csrf
  rotateCsrf();
  const t = iso();
  return {
    ...envelope('login', {
      userId: USER_ID,
      idleExpiresAt: new Date(Date.now() + 30 * 60_000).toISOString(),
      absoluteExpiresAt: new Date(Date.now() + 8 * 3600_000).toISOString(),
    }, {traceId: `stub-login-${t}`}),
    cookies: [`SID=${state.session}; Path=/; HttpOnly; SameSite=Lax`, `CSRF-TOKEN=${state.csrf}; Path=/; SameSite=Lax`],
  };
});

route('POST', '/auth/session/renew', () => {
  if (!state.session) return fail(401, 'AUTH_REQUIRED', '会话不存在');
  // 续期成功即恢复会话（模拟真实后端的滑动过期）
  state.expired = false;
  rotateCsrf();
  return {
    ...envelope('renewSession', {
      userId: USER_ID,
      idleExpiresAt: new Date(Date.now() + 30 * 60_000).toISOString(),
      absoluteExpiresAt: new Date(Date.now() + 8 * 3600_000).toISOString(),
    }),
    cookies: [`CSRF-TOKEN=${state.csrf}; Path=/; SameSite=Lax`],
  };
});

route('POST', '/auth/logout', () => {
  state.session = null;
  state.csrf = null;
  return {...envelope('logout', {accepted: true}), cookies: ['SID=; Path=/; Max-Age=0']};
});

route('GET', '/me', () => envelope('getMe', meData()));
route('GET', '/client-config', () => envelope('getClientConfig', configData()));
route('GET', '/menus', () => envelope('listMenuCatalog', {items: menus(), version: 'stub-menu-1'}));
route('GET', '/dashboard/summary', () => envelope('getDashboardSummary', dashboard()));
route('GET', '/sites', () => envelope('listSites', sitePage()));

// 布控与告警只读入口：供智能体"查看布控任务 / 查看布控预警结果"在 HTTP 模式下联调
route('GET', '/watch-rules', () => envelope('listWatchRules', deepClone(exampleEnvelope('listWatchRules').body.data)));
route('GET', '/alerts', () => envelope('listAlerts', deepClone(exampleEnvelope('listAlerts').body.data)));

route('GET', '/devices', ({query}) => {
  const ids = query.getAll('ids').flatMap((v) => String(v).split(',')).filter(Boolean);
  const list = DEVICES.filter((d) => !ids.length || ids.includes(d.deviceId));
  return envelope('listDevices', {
    items: list.map((d, i) => ({
      deviceId: d.deviceId,
      serialNumber: `SN-${d.deviceId.slice(-4)}`,
      name: d.name,
      siteId: d.site.siteId,
      siteName: d.site.name,
      regionCode: d.site.regionCode,
      category: 'FENCE',
      status: d.status,
      lastSeenAt: iso(minutesAgo(2 + i)),
      updatedAt: iso(minutesAgo(30 * (i + 1))),
      version: 1,
      groupIds: [],
      resetCount: i % 2,
    })),
    page: Number(query.get('page') || 1),
    pageSize: Number(query.get('pageSize') || 20),
    total: list.length,
  });
});
route('POST', '/captures/search', ({body}) => envelope('searchCaptures', searchCaptures(body)));

route('GET', '/captures/{captureId}', ({params}) => {
  const index = Math.max(0, Number(String(params.captureId).slice(-4)) - 1 || 0);
  const record = capture('FENCE', index, Date.now() - 60_000, 20);
  record.captureId = params.captureId;
  return envelope('getCapture', {record, relatedProfileIds: [], canDownloadImage: false, availableActions: ['capture:read']});
});

route('GET', '/map/devices', () => envelope('getDeviceMapFeatures', mapFeatures()));

route('POST', '/targets/resolve', ({body}) => {
  const items = (body?.items || []).map((item, index) => ({
    index,
    status: 'FOUND',
    target: {targetId: `70000000-0000-4000-8000-${String(index + 1).padStart(12, '0')}`, targetType: item.targetType || 'PLATE'},
    displayTarget: String(item.value || '').replace(/[0-9]/g, '*').replace(/\*(.{2})$/, '**$1'),
  }));
  return envelope('resolveTargets', {items});
});

route('POST', '/contexts', ({body}) => {
  const t = iso();
  const data = {
    ...deepClone(exampleEnvelope('createContext').body.data),
    contextId: `A0000000-0000-4000-8000-${String(Date.now()).slice(-12)}`,
    ownerUserId: USER_ID,
    sourceModule: body?.sourceModule || 'M05',
    targets: body?.targets || [],
    filter: body?.filter || {filterType: 'CAPTURE_SEARCH', filterVersion: 1, captureFilter: {dataType: 'FENCE', timeRange: {from: t, to: t}}},
    createdAt: t,
    expiresAt: new Date(Date.now() + 30 * 60_000).toISOString(),
    permissionVersion: 1,
    targetCount: (body?.targets || []).length,
    targetsTruncated: false,
    spatialRange: body?.spatialRange,
  };
  return envelope('createContext', data, {status: 201});
});

route('POST', '/trajectories/query', () => envelope('queryTrajectory', trajectory()));

route('POST', '/exports', ({body}) => {
  const taskId = `B0000000-0000-4000-8000-${String(state.tasks.size + 1).padStart(12, '0')}`;
  state.tasks.set(taskId, {createdAtMs: Date.now(), contextId: body?.contextId || null, traceId: trace('export')});
  return envelope('createExport', {taskId, status: 'PENDING', createdAt: iso(), resourceType: 'EXPORT'}, {status: 202});
});

route('GET', '/tasks/{taskId}', ({params}) => {
  const view = taskView(params.taskId);
  if (!view) return fail(404, 'DATA_NOT_FOUND', `任务 ${params.taskId} 不存在（先 POST /exports 创建）`);
  return envelope('getTask', view);
});

route('GET', '/tasks', () => {
  const items = [...state.tasks.keys()].map((id) => taskView(id)).filter(Boolean);
  return envelope('listTasks', {items, page: 1, pageSize: 20, total: items.length});
});

route('POST', '/tasks/{taskId}/cancel', ({params}) => {
  const task = state.tasks.get(params.taskId);
  if (!task) return fail(404, 'DATA_NOT_FOUND', `任务 ${params.taskId} 不存在`);
  task.cancelledAt = Date.now();
  return envelope('cancelTask', taskView(params.taskId), {status: 202});
});

route('POST', '/tasks/{taskId}/retry', ({params}) => {
  const task = state.tasks.get(params.taskId);
  if (!task) return fail(404, 'DATA_NOT_FOUND', `任务 ${params.taskId} 不存在`);
  task.cancelledAt = null;
  task.createdAtMs = Date.now();
  task.retryCount = (task.retryCount || 0) + 1;
  return envelope('retryTask', taskView(params.taskId), {status: 202});
});

route('GET', '/files/{fileAssetId}', ({params}) => envelope('getFileAsset', {
  fileAssetId: params.fileAssetId,
  category: 'EXPORT',
  fileName: '检索结果导出.xlsx',
  mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  byteSize: 6144,
  status: 'ACTIVE',
  createdAt: iso(),
  expiresAt: new Date(Date.now() + 7 * 86_400_000).toISOString(),
}));

route('GET', '/files/{fileAssetId}/download', async () => ({raw: await buildXlsx(), contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', filename: '检索结果导出.xlsx'}));

// ---------- 服务器 ----------
const match = (pattern, pathname) => {
  const pp = pattern.split('/');
  const ap = pathname.split('/');
  if (pp.length !== ap.length) return null;
  const params = {};
  for (let i = 0; i < pp.length; i++) {
    if (pp[i].startsWith('{')) params[pp[i].slice(1, -1)] = decodeURIComponent(ap[i]);
    else if (pp[i] !== ap[i]) return null;
  }
  return params;
};

const readBody = (req) =>
  new Promise((resolve) => {
    const chunks = [];
    req.on('data', (c) => chunks.push(c));
    req.on('end', () => {
      const text = Buffer.concat(chunks).toString('utf8');
      if (!text) return resolve(undefined);
      try {
        resolve(JSON.parse(text));
      } catch {
        resolve({__invalidJson: text});
      }
    });
  });

const server = createServer(async (req, res) => {
  const url = new URL(req.url, `http://127.0.0.1:${PORT}`);
  const pathname = url.pathname.replace(/^\/api\/v1/, '') || '/';
  const method = req.method || 'GET';
  const headers = Object.fromEntries(Object.entries(req.headers).map(([k, v]) => [k.toLowerCase(), Array.isArray(v) ? v[0] : v]));
  const cookies = Object.fromEntries(
    (headers.cookie || '').split(';').filter(Boolean).map((c) => {
      const [k, ...rest] = c.trim().split('=');
      return [k, rest.join('=')];
    }),
  );

  if (LATENCY) await new Promise((r) => setTimeout(r, LATENCY));

  // 会话校验：除公开端点外都要求 SID
  const PUBLIC = new Set(['/auth/csrf', '/auth/login', '/_stub/scenario', '/_stub/state', '/_stub/reset', '/_stub/expire']);
  if (!PUBLIC.has(pathname) && !pathname.startsWith('/auth/session') && !state.session && state.scenario !== 'unauthorized') {
    return send(res, fail(401, 'AUTH_REQUIRED', '未登录或会话已失效'));
  }
  // 会话空闲过期：业务端点一律 401，只有续期接口能恢复（验证前端的静默续期与重放）
  if (state.expired && !PUBLIC.has(pathname) && pathname !== '/auth/session/renew') {
    return send(res, fail(401, 'AUTH_SESSION_EXPIRED', '会话空闲超时（桩场景 expire）'));
  }
  if (state.scenario === 'unauthorized' && !PUBLIC.has(pathname)) {
    return send(res, fail(401, 'AUTH_REQUIRED', '会话已失效，请重新登录（桩场景 unauthorized）'));
  }
  if (state.scenario === 'timeout' && !PUBLIC.has(pathname)) {
    await new Promise((r) => setTimeout(r, 25_000));
    return send(res, fail(504, 'UPSTREAM_TIMEOUT', '桩场景 timeout'));
  }
  if (state.scenario !== 'ok' && state.scenario !== 'empty' && state.scenario !== 'pending' && state.scenario !== 'cancelled' && !PUBLIC.has(pathname)) {
    const scenario = scenarioError();
    if (scenario) return send(res, scenario);
  }

  for (const r of routes) {
    if (r.method !== method) continue;
    const params = match(r.path, pathname);
    if (!params) continue;
    const body = ['POST', 'PUT', 'PATCH'].includes(method) ? await readBody(req) : undefined;
    if (body?.__invalidJson) return send(res, fail(400, 'INVALID_JSON', '请求体不是合法 JSON'));
    // 写请求校验幂等键与 CSRF（登出除外）
    if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(method) && pathname !== '/auth/login' && pathname !== '/auth/logout') {
      if (headers['x-csrf-token'] !== state.csrf) {
        return send(res, fail(403, 'CSRF_INVALID', 'CSRF 令牌缺失或已过期，请重新 GET /auth/csrf'));
      }
    }
    state.requests.push(`${method} ${pathname}${url.search}`);
    try {
      const out = await r.handler({body, headers, cookies, params, query: url.searchParams});
      return send(res, out);
    } catch (e) {
      console.error('[stub] handler error', pathname, e);
      return send(res, fail(500, 'STUB_HANDLER_ERROR', e instanceof Error ? e.message : String(e)));
    }
  }
  return send(res, fail(404, 'NOT_FOUND', `桩未实现 ${method} ${pathname}`));
});

function send(res, out) {
  const headers = {'X-Trace-Id': out.body?.traceId || trace('stub'), 'Cache-Control': 'no-store'};
  if (out.cookies) headers['Set-Cookie'] = out.cookies;
  if (out.raw) {
    res.writeHead(out.status || 200, {
      ...headers,
      'Content-Type': out.contentType || 'application/octet-stream',
      'Content-Length': out.raw.length,
      'Content-Disposition': `attachment; filename*=UTF-8''${encodeURIComponent(out.filename || 'download.bin')}`,
    });
    return res.end(out.raw);
  }
  if (out.body === null || out.status === 204) {
    res.writeHead(out.status || 204, headers);
    return res.end();
  }
  const payload = Buffer.from(JSON.stringify(out.body), 'utf8');
  res.writeHead(out.status || 200, {...headers, 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': payload.length});
  return res.end(payload);
}

// ---------- 自检 ----------
async function selfcheck() {
  const {default: Ajv} = await import('ajv');
  const {default: addFormats} = await import('ajv-formats');
  const validation = JSON.parse(readFileSync(join(here, '..', 'src', 'api', 'validation-contract.json'), 'utf8'));

  // 与 src/api/validation.ts 的 convert() 保持一致：丢弃非 schema 关键字、把 $ref 改成本地 id、nullable 转 anyOf
  function convert(value) {
    if (Array.isArray(value)) return value.map(convert);
    if (!value || typeof value !== 'object') return value;
    const result = {};
    for (const [key, entry] of Object.entries(value)) {
      if (['nullable', 'discriminator', 'example', 'examples', 'description', 'deprecated', 'readOnly', 'writeOnly'].includes(key) || key.startsWith('x-')) continue;
      if (['properties', 'definitions', '$defs'].includes(key) && entry && typeof entry === 'object') {
        result[key] = Object.fromEntries(Object.entries(entry).map(([name, schema]) => [name, convert(schema)]));
      } else {
        result[key] = key === '$ref' && typeof entry === 'string' && entry.startsWith('#/') ? `https://contract.invalid/openapi${entry}` : convert(entry);
      }
    }
    return 'nullable' in value && value.nullable === true ? {anyOf: [result, {type: 'null'}]} : result;
  }

  const ajv = new Ajv({strict: false, allErrors: true, validateFormats: true});
  addFormats(ajv);
  ajv.addSchema({$id: 'https://contract.invalid/openapi', components: {schemas: Object.fromEntries(Object.entries(validation.components.schemas).map(([n, s]) => [n, convert(s)]))}});

  const cases = [
    ['getCsrfToken', 200, envelope('getCsrfToken', {token: 'stub-csrf-0123456789abcdef', headerName: 'X-CSRF-Token'}).body],
    ['login', 200, envelope('login', {userId: USER_ID, idleExpiresAt: iso(), absoluteExpiresAt: iso()}).body],
    ['renewSession', 200, envelope('renewSession', {userId: USER_ID, idleExpiresAt: iso(), absoluteExpiresAt: iso()}).body],
    ['getMe', 200, envelope('getMe', meData()).body],
    ['getClientConfig', 200, envelope('getClientConfig', configData()).body],
    ['listMenuCatalog', 200, envelope('listMenuCatalog', {items: menus(), version: 'stub-menu-1'}).body],
    ['getDashboardSummary', 200, envelope('getDashboardSummary', dashboard()).body],
    ['listSites', 200, envelope('listSites', sitePage()).body],
    ['listWatchRules', 200, envelope('listWatchRules', deepClone(exampleEnvelope('listWatchRules').body.data)).body],
    ['listAlerts', 200, envelope('listAlerts', deepClone(exampleEnvelope('listAlerts').body.data)).body],
    ['listDevices', 200, envelope('listDevices', {
      items: DEVICES.map((d) => ({deviceId: d.deviceId, serialNumber: `SN-${d.deviceId.slice(-4)}`, name: d.name, siteId: d.site.siteId, siteName: d.site.name, regionCode: d.site.regionCode, category: 'FENCE', status: d.status, lastSeenAt: iso(), updatedAt: iso(), version: 1, groupIds: [], resetCount: 0})),
      page: 1, pageSize: 20, total: DEVICES.length,
    }).body],
    ['searchCaptures', 200, envelope('searchCaptures', searchCaptures({dataType: 'FENCE', timeRange: {from: iso(minutesAgo(120)), to: iso()}, pageSize: 5})).body],
    ['searchCaptures', 200, envelope('searchCaptures', searchCaptures({dataType: 'PLATE', timeRange: {from: iso(minutesAgo(120)), to: iso()}, pageSize: 5})).body],
    ['searchCaptures', 200, envelope('searchCaptures', searchCaptures({dataType: 'MAC', timeRange: {from: iso(minutesAgo(120)), to: iso()}, pageSize: 5})).body],
    ['searchCaptures', 200, envelope('searchCaptures', searchCaptures({dataType: 'FACE', timeRange: {from: iso(minutesAgo(120)), to: iso()}, pageSize: 5})).body],
    ['getDeviceMapFeatures', 200, envelope('getDeviceMapFeatures', mapFeatures()).body],
    ['getCapture', 200, envelope('getCapture', {record: capture('FENCE', 0, Date.now() - 60_000, 20), relatedProfileIds: [], canDownloadImage: false, availableActions: ['capture:read']}).body],
    ['getFileAsset', 200, envelope('getFileAsset', {fileAssetId: state.fileId, category: 'EXPORT', fileName: '检索结果导出.xlsx', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', byteSize: 6144, status: 'ACTIVE', createdAt: iso(), expiresAt: iso()}).body],
    ['resolveTargets', 200, envelope('resolveTargets', {items: [{index: 0, status: 'FOUND', target: {targetId: USER_ID, targetType: 'PLATE'}, displayTarget: '京A***1'}]}).body],
    ['createContext', 201, envelope('createContext', {...exampleEnvelope('createContext').body.data, targets: [], targetCount: 0}, {status: 201}).body],
    ['queryTrajectory', 200, envelope('queryTrajectory', trajectory()).body],
    ['createExport', 202, envelope('createExport', {taskId: USER_ID, status: 'PENDING', createdAt: iso()}, {status: 202}).body],
    ['listTasks', 200, envelope('listTasks', {items: [], page: 1, pageSize: 20, total: 0}).body],
  ];
  // getTask 需要先造一个任务
  const selfcheckTaskId = 'D0000000-0000-4000-8000-000000000001';
  state.tasks.set(selfcheckTaskId, {createdAtMs: Date.now() - 5000, contextId: null, traceId: 'trace'});
  cases.push(['getTask', 200, envelope('getTask', taskView(selfcheckTaskId)).body]);

  let failures = 0;
  let index = 0;
  for (const [op, status, body] of cases) {
    const schema = validation.operations[op]?.responses?.[String(status)]?.content?.['application/json']?.schema;
    if (!schema) {
      console.log(`? ${op} [${status}] 契约中没有 JSON schema，跳过`);
      continue;
    }
    const validate = ajv.compile({$id: `https://contract.invalid/validators/${op}/${status}/${index++}`, ...convert(schema)});
    if (validate(body)) {
      console.log(`✓ ${op} [${status}]`);
    } else {
      failures++;
      console.log(`✗ ${op} [${status}] ${ajv.errorsText(validate.errors, {separator: '\n    '})}`);
    }
  }
  state.tasks.delete(selfcheckTaskId);
  console.log(`\n自检：${cases.length - failures}/${cases.length} 通过`);
  if (failures) process.exitCode = 1;
}

if (SELFCHECK) {
  await selfcheck();
} else {
  server.listen(PORT, '127.0.0.1', () => {
    console.log(`契约桩已启动：http://127.0.0.1:${PORT}/api/v1`);
    console.log(`场景开关：http://127.0.0.1:${PORT}/_stub/scenario?s=ok|forbidden|conflict|expired|server|timeout|empty|pending|unauthorized`);
    console.log('演示账号：stub.admin / stub123（密码填 wrong 可验证登录失败分支）');
  });
}

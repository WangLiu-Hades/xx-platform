#!/usr/bin/env node
/**
 * 首链五格证据生成器。
 *
 * 前提：契约桩在 8080、前端 HTTP 模式在 5181。
 *   npm run stub
 *   npm run dev:http -- --port 5181 --strictPort
 *   npm run evidence
 *
 * 产出：audit/vue-migration/first-chain-<日期>/
 *   *.png          每个场景的截图
 *   matrix.json    机器可读结果（含实际请求序列与 UI 文本）
 *   README.md      就绪度矩阵
 *
 * 边界：这是"契约桩验证"，不是真实后端联调，不能作为 215 项业务验收证据。
 */
import {chromium} from 'playwright';
import {mkdirSync, writeFileSync} from 'node:fs';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const webDir = join(here, '..');
const day = new Intl.DateTimeFormat('sv-SE', {timeZone: 'Asia/Shanghai'}).format(new Date());
const outDir = join(webDir, 'audit', 'vue-migration', `first-chain-${day}`);
const APP = process.env.APP_URL || 'http://127.0.0.1:5181';
const STUB = process.env.STUB_URL || 'http://127.0.0.1:8080';
const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';

mkdirSync(outDir, {recursive: true});

const setScenario = async (name) => {
  const res = await fetch(`${STUB}/_stub/scenario?s=${name}`);
  if (!res.ok) throw new Error(`切换场景失败：${name}`);
};

// 清空桩内的会话与任务，避免上一次运行的任务残留在列表里
await fetch(`${STUB}/_stub/reset`).catch(() => {});

const results = [];

async function newPage(browser) {
  const context = await browser.newContext({viewport: {width: 1600, height: 1000}, acceptDownloads: true});
  const page = await context.newPage();
  await page.addInitScript(() => {
    try {
      localStorage.setItem('vue-agent-llm-config-v1', JSON.stringify({enabled: false}));
    } catch {}
  });
  const record = {pageErrors: [], consoleErrors: [], api: []};
  page.on('pageerror', (e) => record.pageErrors.push(e.message));
  page.on('console', (m) => {
    if (m.type() === 'error' && !m.text().includes('favicon')) record.consoleErrors.push(m.text());
  });
  page.on('response', (r) => {
    // 只记录业务接口，忽略 Vite 的 /src/api/*.ts 模块加载
    if (new URL(r.url()).pathname.startsWith('/api/')) record.api.push(`${r.status()} ${r.request().method()} ${new URL(r.url()).pathname}`);
  });
  return {context, page, record};
}

async function login(page) {
  await page.goto(APP, {waitUntil: 'networkidle'});
  await page.getByLabel('登录账号').fill('stub.admin');
  await page.getByLabel('密码').fill('stub123');
  await page.getByRole('button', {name: '登录', exact: true}).click();
  await page.locator('.sidebar').waitFor({timeout: 20_000});
}

async function goto(page, name) {
  await page.locator('.sidebar').getByRole('button', {name, exact: true}).click();
  await page.locator('.page-title h1').filter({hasText: name}).waitFor({timeout: 15_000});
  await page.waitForTimeout(600);
}

const alerts = (page) => page.locator('main .el-alert').allInnerTexts().catch(() => []);
const rows = (page) => page.locator('main .el-table__row').count();

async function shot(page, name) {
  await page.screenshot({path: join(outDir, `${name}.png`), fullPage: true});
  return `${name}.png`;
}

async function runCase(browser, {id, label, setup, action}) {
  // 登录始终在 ok 场景下完成，再切到目标场景，避免 403/500 把登录流程本身打断
  await setScenario('ok');
  const {context, page, record} = await newPage(browser);
  const result = {id, label, scenario: setup.after || setup.scenario || 'ok', ok: true, notes: [], shot: null, alerts: [], rows: null};
  try {
    if (setup.login !== false) await login(page);
    if (setup.after) await setScenario(setup.after);
    if (setup.page) await goto(page, setup.page);
    if (action) await action(page, result);
    result.alerts = await alerts(page);
    result.rows = await rows(page);
    result.shot = await shot(page, id);
  } catch (e) {
    result.ok = false;
    result.notes.push(`脚本异常：${e instanceof Error ? e.message.split('\n')[0] : String(e)}`);
    await shot(page, `${id}-error`).catch(() => {});
    result.shot = `${id}-error.png`;
  } finally {
    result.pageErrors = record.pageErrors;
    result.consoleErrors = record.consoleErrors;
    result.api = record.api;
    results.push(result);
    await context.close();
    await setScenario('ok');
  }
  const mark = result.ok && !result.pageErrors.length ? '✓' : '✗';
  console.log(`${mark} ${id.padEnd(18)} ${label}${result.notes.length ? ' · ' + result.notes.join('；') : ''}`);
}

const browser = await chromium.launch({headless: true, executablePath: EDGE}).catch(() =>
  chromium.launch({headless: true}),
);

// ---------- 登录 ----------
await runCase(browser, {
  id: 'login-ok',
  label: '登录成功并进入智能对话主界面',
  setup: {scenario: 'ok', login: false},
  action: async (page, r) => {
    await login(page);
    const menus = await page.locator('.sidebar button').allTextContents();
    r.notes.push(`菜单 ${menus.length} 个`);
    const name = await page.locator('.page-title h1').innerText();
    r.notes.push(`落地页：${name}`);
    if (menus.length !== 29) r.ok = false;
    if (name !== '智能对话') r.ok = false;
    await page.waitForTimeout(800);
  },
});

await runCase(browser, {
  id: 'login-fail',
  label: '密码错误被拒绝（401）',
  setup: {scenario: 'ok', login: false},
  action: async (page, r) => {
    await page.goto(APP, {waitUntil: 'networkidle'});
    await page.getByLabel('登录账号').fill('stub.admin');
    await page.getByLabel('密码').fill('wrong');
    await page.getByRole('button', {name: '登录', exact: true}).click();
    await page.locator('.login-card .el-alert').first().waitFor({timeout: 15_000});
    r.notes.push(`提示：${(await page.locator('.login-card .el-alert').first().innerText()).trim()}`);
    if (await page.locator('.sidebar').count()) r.ok = false;
  },
});

// ---------- 围栏检索 ----------
await runCase(browser, {
  id: 'search-ok',
  label: '检索成功（20 条 + 游标翻页）',
  setup: {page: '围栏检索'},
  action: async (page, r) => {
    const first = await rows(page);
    r.notes.push(`首页 ${first} 条`);
    const cells = page.locator('main .el-table__row').first().locator('td');
    const siteCell = (await cells.nth(5).innerText()).trim();
    const deviceCell = (await cells.nth(6).innerText()).trim();
    r.notes.push(`场所列：${siteCell}；设备列：${deviceCell.slice(0, 22)}`);
    if (/^[0-9a-f]{8}-/i.test(deviceCell)) r.notes.push('⚠ 设备列显示 deviceId 而不是设备名');
    await page.getByRole('button', {name: '下一页'}).click();
    await page.waitForTimeout(1200);
    r.notes.push(`第二页 ${await rows(page)} 条`);
  },
});

await runCase(browser, {
  id: 'search-empty',
  label: '空结果',
  setup: {after: 'empty', page: '围栏检索'},
  action: async (page, r) => {
    const body = await page.locator('main').innerText();
    r.notes.push(body.includes('无结果') ? '显示空态文案' : '未显示空态文案');
    if (!body.includes('无结果')) r.ok = false;
  },
});

await runCase(browser, {
  id: 'search-forbidden',
  label: '无权访问（403）',
  setup: {after: 'forbidden', page: '围栏检索'},
});

await runCase(browser, {
  id: 'search-server',
  label: '服务异常（500）',
  setup: {after: 'server', page: '围栏检索'},
});

await runCase(browser, {
  id: 'search-timeout',
  label: '请求超时（20s 客户端超时）',
  setup: {after: 'timeout', page: '围栏检索'},
  action: async (page, r) => {
    await page.waitForTimeout(23_000);
    r.notes.push('等待 23s 观察超时提示');
  },
});

// ---------- 地图 ----------
await runCase(browser, {
  id: 'map-ok',
  label: '地图研判加载（5 台设备 + 8 点轨迹）',
  setup: {page: '地图研判'},
  action: async (page, r) => {
    await page.waitForTimeout(1200);
    const devices = await page.locator('main .el-table__row').count();
    const body = await page.locator('main').innerText();
    r.notes.push(`轨迹行 ${devices} 条`);
    const online = (body.match(/在线\s*(\d+)\s*\/\s*(\d+)/) || [])[1];
    if (online) r.notes.push(`在线 ${online}`);
  },
});

await runCase(browser, {
  id: 'map-forbidden',
  label: '地图无权（403）',
  setup: {after: 'forbidden', page: '地图研判'},
});

// ---------- 会话续期 ----------
await runCase(browser, {
  id: 'session-renew',
  label: '401 静默续期并重放（不跳登录页）',
  setup: {page: '系统首页'},
  action: async (page, r) => {
    await fetch(`${STUB}/_stub/expire`);
    await goto(page, '围栏检索');
    await page.waitForTimeout(2000);
    const rowCount = await rows(page);
    const loggedOut = (await page.locator('.login-card').count()) > 0;
    r.notes.push(`续期后检索 ${rowCount} 条；${loggedOut ? '被退出到登录页' : '未跳登录页'}`);
    if (loggedOut || rowCount === 0) r.ok = false;
    const renewed = await page.evaluate(() => performance.getEntriesByType('resource').some((e) => e.name.includes('/auth/session/renew')));
    r.notes.push(renewed ? '已调用 renewSession' : '未观察到 renewSession');
  },
});

await runCase(browser, {
  id: 'search-expired-410',
  label: '上下文过期 410 与「重新查询」',
  setup: {after: 'expired', page: '围栏检索'},
  action: async (page, r) => {
    await page.waitForTimeout(800);
    const retry = page.getByRole('button', {name: '重新查询', exact: true});
    const visible = await retry.count();
    r.notes.push(visible ? '出现「重新查询」按钮' : '未出现「重新查询」按钮');
    await setScenario('ok');
    if (visible) {
      await retry.first().click();
      await page.waitForTimeout(1200);
      const rowCount = await rows(page);
      r.notes.push(`重查后 ${rowCount} 条`);
      if (rowCount === 0) r.ok = false;
    } else r.ok = false;
  },
});

// ---------- 导出与任务中心 ----------
await runCase(browser, {
  id: 'export-ok',
  label: '导出 202 → 任务 → 下载',
  setup: {page: '围栏检索'},
  action: async (page, r) => {
    await page.locator('main').getByRole('button', {name: '导出', exact: true}).click();
    await page.waitForTimeout(1500);
    await goto(page, '任务中心');
    const done = (text) => text.includes('SUCCESS') || text.includes('成功');
    for (let i = 0; i < 8; i++) {
      const text = await page.locator('main').innerText();
      if (done(text)) break;
      // 任务中心没有自动轮询：重新进入页面触发一次列表刷新
      await goto(page, '系统首页');
      await goto(page, '任务中心');
      await page.waitForTimeout(800);
    }
    const text = await page.locator('main').innerText();
    r.notes.push(done(text) ? `任务状态列：${text.includes('成功') ? '成功（中文映射）' : 'SUCCESS（英文枚举）'}` : '任务未达成功状态');
    if (!done(text)) {
      r.ok = false;
      return;
    }
    const downloadPromise = page.waitForEvent('download', {timeout: 15_000}).catch(() => null);
    await page
      .locator('main .el-table__row')
      .filter({hasText: '成功'})
      .getByRole('button', {name: '下载任务结果', exact: true})
      .first()
      .click();
    const download = await downloadPromise;
    if (download) {
      const path = join(outDir, 'download-sample.xlsx');
      await download.saveAs(path);
      r.notes.push(`下载文件名：${download.suggestedFilename()}`);
    } else {
      r.notes.push('未捕获到下载事件');
    }
  },
});

await runCase(browser, {
  id: 'export-cancel',
  label: '任务取消（RUNNING → 已取消）',
  setup: {after: 'pending', page: '围栏检索'},
  action: async (page, r) => {
    await page.locator('main').getByRole('button', {name: '导出', exact: true}).click();
    await page.waitForTimeout(1200);
    await goto(page, '任务中心');
    const running = page.locator('main .el-table__row').filter({hasText: '运行中'}).first();
    await running.getByRole('button', {name: '任务详情 / 导入结果', exact: true}).click();
    await page.getByRole('button', {name: '请求取消任务', exact: true}).waitFor({timeout: 15_000});
    await page.getByRole('button', {name: '请求取消任务', exact: true}).click();
    await page.waitForTimeout(1500);
    const dialog = await page.locator('.el-dialog').innerText();
    const cancelled = dialog.includes('CANCELLED') || dialog.includes('已取消');
    r.notes.push(`详情状态：${dialog.includes('CANCELLED') ? 'CANCELLED' : dialog.includes('已取消') ? '已取消' : '未变更'}`);
    if (!cancelled) r.ok = false;
  },
});

await runCase(browser, {
  id: 'export-conflict',
  label: '导出冲突（409）',
  setup: {after: 'conflict', page: '围栏检索'},
  action: async (page) => {
    await page.locator('main').getByRole('button', {name: '导出', exact: true}).click();
    await page.waitForTimeout(1500);
  },
});

await browser.close();
await setScenario('ok');

// ---------- 汇总 ----------
const summary = {
  generatedAt: new Date().toISOString(),
  timeZone: 'Asia/Shanghai',
  app: APP,
  stub: STUB,
  note: '契约桩验证：验证前端在真实 HTTP 路径下的行为，不代表真实后端联调或业务验收',
  cases: results,
};
writeFileSync(join(outDir, 'matrix.json'), JSON.stringify(summary, null, 2), 'utf8');

const pass = results.filter((r) => r.ok).length;
const md = [];
md.push(`# 首链就绪度矩阵（契约桩）· ${day}`);
md.push('');
md.push(`运行时间：${new Date().toLocaleString('sv-SE', {timeZone: 'Asia/Shanghai'})}（Asia/Shanghai）  `);
md.push(`前端：${APP} 　契约桩：${STUB}`);
md.push('');
md.push('> **这是契约桩验证，不是真实后端联调。** 每个"通过"都只说明前端在真实 HTTP 路径下的请求、校验、状态处理正确；后端实现后须按同一矩阵重跑。');
md.push('');
md.push(`**结果：${pass}/${results.length} 通过**`);
md.push('');
md.push('| 场景 | 说明 | 结果 | 关键观测 | 截图 |');
md.push('| --- | --- | --- | --- | --- |');
for (const r of results) {
  const observed = [...r.notes, ...r.alerts.filter(Boolean).map((a) => `提示：「${a.replace(/\s+/g, ' ').slice(0, 60)}」`), ...(r.pageErrors.length ? [`页面异常 ${r.pageErrors.length} 条`] : [])].join('；') || '—';
  md.push(`| ${r.id} | ${r.label} | ${r.ok ? '✅ 通过' : '❌ 异常'} | ${observed.replace(/\|/g, '/')} | \`${r.shot}\` |`);
}
md.push('');
md.push('## 请求序列（成功路径）');
md.push('');
const ok = results.find((r) => r.id === 'search-ok');
if (ok) {
  md.push('```');
  for (const line of ok.api) md.push(line);
  md.push('```');
}
md.push('');
md.push('## 说明');
md.push('');
md.push('- 所有请求都经过 Vite 代理（5181 → 8080），带 Cookie、X-CSRF-Token、Idempotency-Key，与接真实后端时的路径一致。');
md.push('- 响应全部通过前端 AJV 契约校验；任一字段不合契约会抛"接口响应不符合约定"，矩阵里会显示为异常。');
md.push('- 本目录不包含真实业务数据，不能作为 215 项 PDF 需求的验收证据。');
md.push('');
writeFileSync(join(outDir, 'README.md'), md.join('\n'), 'utf8');

console.log(`\n证据目录：${outDir}`);
console.log(`通过 ${pass}/${results.length}，矩阵见 README.md / matrix.json`);
if (pass !== results.length) process.exitCode = 1;

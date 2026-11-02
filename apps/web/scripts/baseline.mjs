#!/usr/bin/env node
/**
 * 前端基线一键存档。
 *
 * 目的：每周会前留一份"带日期的当前状态"，避免拿 10/09 的历史报告当本周证据。
 * 产出：audit/vue-migration/baseline-YYYY-MM-DD/
 *   summary.json      机器可读汇总（版本、各步结果、耗时）
 *   typecheck.log     类型检查输出
 *   unit-tests.log    单测输出
 *   build.log         Mock 构建输出
 *   build-http.log    HTTP 模式构建输出（产物在 dist-http/，不覆盖 dist/）
 *   smoke.json        当前全部菜单（29 页）浏览器遍历结果（页面错误 / 控制台错误 / 内容长度）
 *   dashboard.png     系统首页截图（智能对话主界面另见 agent-chat.png）
 *   README.md         本次跑了什么、怎么复现
 *
 * 用法：npm run baseline
 * 说明：结果只能证明"原型可运行"，不能作为 215 项业务验收证据。
 */
import {spawnSync, spawn} from 'node:child_process';
import {existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync, statSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const webDir = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repoRoot = resolve(webDir, '..', '..');
const stamp = new Date();
const day = new Intl.DateTimeFormat('sv-SE', {timeZone: 'Asia/Shanghai'}).format(stamp);
const outDir = join(webDir, 'audit', 'vue-migration', `baseline-${day}`);
const PREVIEW_PORT = 5199;
const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';

mkdirSync(outDir, {recursive: true});

const isWin = process.platform === 'win32';
const npx = isWin ? 'npx.cmd' : 'npx';
const results = [];
const summary = {
  generatedAt: stamp.toISOString(),
  timeZone: 'Asia/Shanghai',
  workdayLabel: day,
  note: '本地基线：证明原型可运行，不是 215 项业务验收证据',
  env: {
    node: process.version,
    npm: run('npm --version', {quiet: true}).stdout.trim(),
    platform: `${process.platform} ${process.arch}`,
  },
  hashes: {},
  steps: results,
};

function run(command, {cwd = webDir, quiet = false, timeout = 20 * 60 * 1000} = {}) {
  const started = Date.now();
  const r = spawnSync(command, {
    cwd,
    shell: true,
    encoding: 'utf8',
    maxBuffer: 64 * 1024 * 1024,
    timeout,
    env: process.env,
  });
  const out = `${r.stdout || ''}${r.stderr || ''}`;
  const step = {
    command,
    exitCode: r.status ?? (r.error ? 1 : 0),
    durationMs: Date.now() - started,
    error: r.error ? String(r.error.message || r.error) : null,
  };
  if (!quiet) {
    results.push(step);
    process.stdout.write(`\n=== ${command}  (exit ${step.exitCode}, ${(step.durationMs / 1000).toFixed(1)}s)\n`);
  }
  return {...step, stdout: out};
}

function hashFile(rel) {
  const p = join(repoRoot, rel);
  if (!existsSync(p)) return null;
  const buf = readFileSync(p);
  return {path: rel, bytes: buf.length, sha256: createHash('sha256').update(buf).digest('hex')};
}

function writeLog(name, content) {
  writeFileSync(join(outDir, name), content, 'utf8');
}

function bail(message) {
  console.error(`\n[baseline] ${message}`);
  writeSummary();
  process.exitCode = 1;
}

function writeSummary() {
  writeFileSync(join(outDir, 'summary.json'), JSON.stringify(summary, null, 2), 'utf8');
}

// ---------- 1. 版本与契约指纹 ----------
summary.hashes.packageLock = hashFile('apps/web/package-lock.json');
summary.hashes.openapi = hashFile('contracts/api/openapi.yaml');
console.log(`[baseline] 输出目录：${outDir}`);
console.log(`[baseline] node ${summary.env.node} / npm ${summary.env.npm}`);

// ---------- 2. 类型检查 ----------
const typecheck = run('npx vue-tsc --noEmit');
writeLog('typecheck.log', typecheck.stdout);
if (typecheck.exitCode !== 0) bail('类型检查未通过，后续步骤继续执行以便留证');

// ---------- 3. 单元/契约测试 ----------
const tests = run('npx vitest run --pool=threads --maxWorkers=1');
writeLog('unit-tests.log', tests.stdout);
const testLine = (tests.stdout.match(/Tests\s+(\d+) passed/) || [])[1];
const testFiles = (tests.stdout.match(/Test Files\s+(\d+) passed/) || [])[1];
summary.unitTests = {passed: testLine ? Number(testLine) : null, files: testFiles ? Number(testFiles) : null};

// ---------- 4. 两种模式各构建一次 ----------
const buildMock = run('npx vite build');
writeLog('build.log', buildMock.stdout);

const buildHttp = run('npx vite build --mode http --outDir dist-http --emptyOutDir');
writeLog('build-http.log', buildHttp.stdout);
summary.builds = {
  mock: {exitCode: buildMock.exitCode, distMB: dirSizeMB(join(webDir, 'dist'))},
  http: {exitCode: buildHttp.exitCode, distMB: dirSizeMB(join(webDir, 'dist-http'))},
};

// ---------- 5. 起 preview，跑全部菜单页冒烟 ----------
const server = spawn(`npx vite preview --port ${PREVIEW_PORT} --strictPort`, {
  cwd: webDir,
  shell: true,
  stdio: 'ignore',
  windowsHide: true,
});

const smoke = await runSmoke();
if (server && server.pid) {
  if (isWin) spawnSync(`taskkill /pid ${server.pid} /t /f`, {shell: true, stdio: 'ignore'});
  else try { process.kill(-server.pid); } catch { server.kill(); }
}

writeSummary();
writeReadme();
console.log(`\n[baseline] 完成 → ${outDir}`);
console.log(`[baseline] 类型检查 ${typecheck.exitCode === 0 ? 'OK' : 'FAIL'} · 单测 ${summary.unitTests.passed ?? '?'} · Mock 构建 ${buildMock.exitCode === 0 ? 'OK' : 'FAIL'} · HTTP 构建 ${buildHttp.exitCode === 0 ? 'OK' : 'FAIL'} · 页面 ${smoke.menuCount} 个 / 错误 ${smoke.errors.length + smoke.consoleErrors.length}`);

if (typecheck.exitCode !== 0 || tests.exitCode !== 0 || buildMock.exitCode !== 0 || buildHttp.exitCode !== 0 || smoke.errors.length || smoke.consoleErrors.length) {
  process.exitCode = 1;
}

// ---------- 工具函数 ----------
function dirSizeMB(dir) {
  if (!existsSync(dir)) return 0;
  let total = 0;
  for (const entry of walk(dir)) total += statSync(entry).size;
  return Number((total / 1024 / 1024).toFixed(2));
}

function* walk(dir) {
  for (const name of readdirSync(dir, {withFileTypes: true})) {
    const p = join(dir, name.name);
    if (name.isDirectory()) yield* walk(p);
    else yield p;
  }
}

async function runSmoke() {
  const result = {previewUrl: `http://127.0.0.1:${PREVIEW_PORT}`, menuCount: 0, pages: [], errors: [], consoleErrors: []};
  const ready = await waitFor(`http://127.0.0.1:${PREVIEW_PORT}`, 60_000);
  if (!ready) {
    result.errors.push('preview 服务启动超时');
    writeLog('smoke.json', JSON.stringify(result, null, 2));
    return result;
  }

  const {chromium} = await import('playwright');
  let browser;
  try {
    browser = await chromium.launch({headless: true});
  } catch {
    browser = await chromium.launch({headless: true, executablePath: EDGE});
  }
  const page = await browser.newPage({viewport: {width: 1600, height: 1000}});
  // 基线冒烟不依赖可选的本地大模型，避免未启动模型时产生网络错误噪音
  await page.addInitScript(() => {
    try {
      localStorage.setItem('vue-agent-llm-config-v1', JSON.stringify({enabled: false}));
    } catch {}
  });
  page.on('pageerror', (e) => result.errors.push(e.message));
  page.on('console', (m) => {
    if (m.type() === 'error') result.consoleErrors.push(m.text());
  });

  try {
    await page.goto(result.previewUrl, {waitUntil: 'networkidle'});
    await page.getByRole('button', {name: '登录', exact: true}).click();
    await page.locator('.sidebar').waitFor({timeout: 15_000});

    const names = await page.locator('.sidebar button').allTextContents();
    result.menuCount = names.length;

    for (const name of names) {
      const before = result.errors.length;
      await page.locator('.sidebar').getByRole('button', {name, exact: true}).click();
      await page.locator('.page-title h1').filter({hasText: name}).waitFor({timeout: 15_000});
      await page.waitForTimeout(250);
      const text = await page.locator('main').innerText();
      const rows = await page.locator('main .el-table__row').count();
      const buttons = await page.locator('main button').count();
      const fields = await page.locator('main input, main .el-select').count();
      result.pages.push({
        page: name,
        visible: text.trim().length > 0,
        chars: text.trim().length,
        tableRows: rows,
        buttons,
        fields,
        pageErrors: result.errors.length - before,
      });
    }

    await page.locator('.sidebar').getByRole('button', {name: '系统首页', exact: true}).click();
    await page.waitForTimeout(800);
    await page.screenshot({path: join(outDir, 'dashboard.png'), fullPage: true});
  } catch (e) {
    result.errors.push(`冒烟脚本异常：${e instanceof Error ? e.message : String(e)}`);
  } finally {
    await browser.close();
  }

  writeLog('smoke.json', JSON.stringify({...result, sparsePages: result.pages.filter((p) => p.chars < 120).map((p) => p.page)}, null, 2));
  return result;
}

async function waitFor(url, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const res = await fetch(url, {signal: AbortSignal.timeout(2000)});
      if (res.ok) return true;
    } catch {
      /* 还没起来 */
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  return false;
}

function writeReadme() {
  const localTime = new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Shanghai',
    dateStyle: 'short',
    timeStyle: 'medium',
  }).format(new Date(summary.generatedAt));
  const lines = [
    `# 前端基线 ${day}`,
    '',
    `生成时间：${localTime}（Asia/Shanghai）`,
    `Node ${summary.env.node} / npm ${summary.env.npm}`,
    '',
    '| 项目 | 结果 |',
    '| --- | --- |',
    `| 类型检查 | ${typecheck.exitCode === 0 ? '通过' : `失败（exit ${typecheck.exitCode}）`} |`,
    `| 单元/契约测试 | ${summary.unitTests.passed ?? '?'} 项通过（${summary.unitTests.files ?? '?'} 个文件） |`,
    `| Mock 构建 | ${buildMock.exitCode === 0 ? `通过，dist ${summary.builds.mock.distMB} MB` : '失败'} |`,
    `| HTTP 构建 | ${buildHttp.exitCode === 0 ? `通过，dist-http ${summary.builds.http.distMB} MB` : '失败'} |`,
    `| 页面冒烟 | ${smoke.menuCount} 个菜单，页面错误 ${smoke.errors.length}，控制台错误 ${smoke.consoleErrors.length} |`,
    '',
    '## 契约指纹',
    '',
    `- package-lock.json：\`${summary.hashes.packageLock?.sha256 ?? '缺失'}\``,
    `- openapi.yaml：\`${summary.hashes.openapi?.sha256 ?? '缺失'}\``,
    '',
    '## 复现',
    '',
    '```powershell',
    'cd apps/web',
    'npm run baseline',
    '```',
    '',
    '## 边界',
    '',
    '本目录只能证明"原型可运行、可构建、全部菜单页无脚本错误"，**不能**作为 215 项 PDF 需求的业务验收证据，也不代表真实后端已联调。',
    '',
  ];
  writeFileSync(join(outDir, 'README.md'), lines.join('\n'), 'utf8');
}

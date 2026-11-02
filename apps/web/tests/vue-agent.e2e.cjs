/**
 * 智能体主界面浏览器回归：验证豆包式对话、布控三入口、跳转/新建以及页面无脚本错误。
 *
 * 前置：npm run dev -- --port 5180 --strictPort
 * 运行：node tests/vue-agent.e2e.cjs
 * 产物：audit/smart-agent-<日期>/ 下的截图与 results.json
 */
const {chromium} = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

const origin = process.env.PROTOTYPE_URL || 'http://127.0.0.1:5180';
const output = path.resolve('audit/smart-agent-' + new Date().toLocaleDateString('sv-SE', {timeZone: 'Asia/Shanghai'}));
fs.mkdirSync(output, {recursive: true});

(async () => {
  const browser = await chromium.launch({headless: true, executablePath: process.env.AUDIT_BROWSER || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'});
  const context = await browser.newContext({viewport: {width: 1600, height: 1000}});
  const page = await context.newPage();
  const pageErrors = [];
  const consoleErrors = [];
  const checks = [];
  page.on('pageerror', error => pageErrors.push(error.message));
  page.on('console', message => {
    if (message.type() === 'error') consoleErrors.push(message.text());
  });
  const run = async (name, fn) => {
    try {
      await fn();
      checks.push({name, passed: true});
      process.stdout.write('PASS ' + name + '\n');
    } catch (error) {
      checks.push({name, passed: false, error: String(error?.stack || error)});
      process.stdout.write('FAIL ' + name + ': ' + error.message + '\n');
    }
  };

  try {
    await page.goto(origin);
    await page.getByRole('button', {name: '登录', exact: true}).click();
    await page.locator('.agent-page').waitFor();

    await run('登录后默认进入智能对话主界面', async () => {
      assert.equal(await page.locator('.page-title h1').innerText(), '智能对话');
      await page.locator('.chat-head h2').waitFor();
      assert.equal(await page.locator('.sidebar button').count(), 29);
      await page.screenshot({path: path.join(output, 'agent-welcome.png')});
    });

    await run('对话区高度不撑出页面、欢迎页元素齐全', async () => {
      const box = await page.locator('.chat-panel').boundingBox();
      assert.ok(box && box.height > 420, '对话面板高度 ' + JSON.stringify(box));
      const dims = await page.evaluate(() => ({scroll: document.documentElement.scrollHeight, inner: innerHeight}));
      assert.ok(dims.scroll <= dims.inner + 4, '页面出现纵向溢出 ' + JSON.stringify(dims));
      assert.equal(await page.locator('.suggest-card').count(), 4);
      assert.ok((await page.locator('.welcome .lead').innerText()).includes('布控'));
    });

    await run('输入“布控”返回查看、新建、预警结果三个可点击入口', async () => {
      await page.locator('.composer').getByRole('button', {name: '布控', exact: true}).click();
      await page.locator('.action[data-action-id="watch.create"]').waitFor();
      const labels = await page.locator('.action b').allTextContents();
      assert.deepEqual(labels.slice(0, 3), ['查看布控任务', '新建布控', '查看布控预警结果']);
      assert.equal(await page.locator('.action-grid').count(), 1);
      await page.waitForFunction(() => {
        const node = document.querySelector('.assistant-body .markdown');
        return !!node && (node.textContent || '').trim().length > 20;
      });
      const bubble = await page.locator('.assistant-body .markdown').last().innerText();
      assert.ok(bubble.includes('布控'), bubble);
      await page.locator('.send.stop').waitFor({state: 'detached'});
      await page.waitForTimeout(250);
      await page.screenshot({path: path.join(output, 'agent-watch-actions.png')});
    });

    await run('点击“新建布控”跳到布控页并打开新建表单', async () => {
      await page.locator('.action[data-action-id="watch.create"]').click();
      const dialog = page.locator('.el-dialog').filter({hasText: '新增布控'});
      await dialog.waitFor();
      assert.equal(await page.locator('.page-title h1').innerText(), '综合布控');
      await page.waitForTimeout(200);
      await page.screenshot({path: path.join(output, 'agent-watch-create.png')});
      await dialog.getByRole('button', {name: '取消'}).click();
      await page.locator('.el-dialog').waitFor({state: 'hidden'});
    });

    await run('点击“查看布控预警结果”跳到告警结果页', async () => {
      await page.locator('.sidebar').getByRole('button', {name: '智能对话', exact: true}).click();
      await page.locator('.agent-page').waitFor();
      await page.locator('.action[data-action-id="watch.alerts"]').click();
      await page.locator('.page-title h1').filter({hasText: '告警结果'}).waitFor();
      assert.equal(await page.locator('.page-title h1').innerText(), '告警结果');
      await page.waitForTimeout(250);
      await page.screenshot({path: path.join(output, 'agent-alerts.png')});
    });

    await run('追问检索条件：目标与时间带入围栏检索', async () => {
      await page.locator('.sidebar').getByRole('button', {name: '智能对话', exact: true}).click();
      await page.locator('.agent-page').waitFor();
      await page.locator('.send.stop').waitFor({state: 'detached'});
      await page.locator('.composer textarea').fill('检索 460000000000001 最近24小时');
      await page.locator('.composer').getByRole('button', {name: '发送'}).click();
      await page.locator('.action[data-action-id="search.电围"]').waitFor();
      await page.locator('.action[data-action-id="search.电围"]').click();
      await page.locator('.page-title h1').filter({hasText: '围栏检索'}).waitFor();
      const target = await page.locator('.query-form .el-form-item').filter({hasText: 'IMSI'}).locator('input').first().inputValue();
      assert.equal(target, '460000000000001');
      await page.screenshot({path: path.join(output, 'agent-search-handoff.png')});
    });

    await run('会话列表可新建、切换并保留历史', async () => {
      await page.locator('.sidebar').getByRole('button', {name: '智能对话', exact: true}).click();
      await page.locator('.agent-page').waitFor();
      assert.ok((await page.locator('.conversation').count()) >= 1);
      await page.locator('.chat-head').getByRole('button', {name: '新建对话'}).click();
      assert.ok((await page.locator('.welcome').count()) === 1);
      await page.locator('.conversation').filter({hasText: '布控'}).first().click();
      assert.ok((await page.locator('.msg.user').count()) >= 2);
    });

    await run('模型设置可打开并显示本地部署说明', async () => {
      await page.locator('.chat-head').getByRole('button', {name: '模型设置'}).click();
      const dialog = page.locator('.el-dialog').filter({hasText: '本地大模型设置'});
      await dialog.waitFor();
      assert.ok((await dialog.innerText()).includes('ollama pull qwen2.5:7b-instruct'));
      await page.waitForTimeout(200);
      await page.screenshot({path: path.join(output, 'agent-model-settings.png')});
      await dialog.getByRole('button', {name: '取消'}).click();
    });

    assert.deepEqual(pageErrors, []);
    assert.deepEqual(consoleErrors, []);
  } finally {
    const result = {
      time: new Date().toISOString(),
      origin,
      checks,
      passed: checks.filter(item => item.passed).length,
      failed: checks.filter(item => !item.passed).length,
      pageErrors,
      consoleErrors,
    };
    fs.writeFileSync(path.join(output, 'results.json'), JSON.stringify(result, null, 2));
    await browser.close();
    console.log(JSON.stringify({passed: result.passed, failed: result.failed, pageErrors, consoleErrors}));
    if (result.failed || pageErrors.length || consoleErrors.length) process.exitCode = 1;
  }
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
});

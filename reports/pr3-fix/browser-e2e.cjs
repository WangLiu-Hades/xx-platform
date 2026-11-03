/*
 * M21 浏览器证据：用真实 Edge（headless）在真实页面上下文里跑认证链路。
 *
 * 为什么不是"点界面"：`apps/web` 目前是带 mock 数据的原型（见 tests/vue.e2e.cjs），
 * 没有接到后端 API；本脚本验证的是"浏览器网络栈 + 同源 Cookie/CSRF + 幂等头"这条链路，
 * 即 `vite.config.ts` 里 `/api` 同源代理所依赖的那一层（同源请求不带 Origin、不触发预检）。
 *
 * 用法（在 apps/web 目录下，playwright 依赖在那里）：
 *   node ..\..\reports\pr3-fix\browser-e2e.cjs
 * 环境变量：
 *   BROWSER_URL   默认 http://127.0.0.1:8081/api/v1
 *   BROWSER_EDGE  默认 C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe
 *   LOGIN_NAME    默认 demo.admin
 *   LOGIN_PASS    默认 Dev-Only-Passw0rd-2026
 *   NEW_PASS      默认 Browser-Changed-Passw0rd-2026
 */
const { chromium } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');

const base = process.env.BROWSER_URL || 'http://127.0.0.1:8081/api/v1';
const edge = process.env.BROWSER_EDGE || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const loginName = process.env.LOGIN_NAME || 'demo.admin';
const initialPassword = process.env.LOGIN_PASS || 'Dev-Only-Passw0rd-2026';
const newPassword = process.env.NEW_PASS || 'Browser-Changed-Passw0rd-2026';
const outFile = path.resolve(__dirname, 'browser-e2e-results.json');

const steps = [];
function record(name, ok, detail) {
  steps.push({ name, passed: ok, detail });
  process.stdout.write(`${ok ? 'PASS' : 'FAIL'} ${name}${detail ? ' :: ' + JSON.stringify(detail) : ''}\n`);
}

(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: edge });
  const context = await browser.newContext({ acceptDownloads: true });
  const page = await context.newPage();
  const consoleErrors = [];
  page.on('console', m => { if (m.type() === 'error') consoleErrors.push(m.text()); });
  page.on('pageerror', e => consoleErrors.push('pageerror: ' + e.message));
  try {
    // 先落在一个同源页面，后面的 fetch 才是"页面上下文里的同源请求"
    const health = await page.goto(base + '/health', { waitUntil: 'domcontentloaded' });
    record('same-origin page loaded', health && health.status() === 200, { status: health && health.status(), url: page.url() });

    const result = await page.evaluate(async ({ base, loginName, initialPassword, newPassword }) => {
      const log = [];
      const call = async (name, url, init) => {
        const response = await fetch(url, Object.assign({ credentials: 'include' }, init));
        const text = await response.text();
        log.push({
          name,
          status: response.status,
          requestOrigin: new URL(url, location.href).origin,
          pageOrigin: location.origin,
          csrfEcho: response.headers.get('X-CSRF-Token'),
          cacheControl: response.headers.get('Cache-Control'),
          body: text.slice(0, 400)
        });
        return { response, text, log: log[log.length - 1] };
      };

      // 1) CSRF：同源 GET，浏览器自动带/存 CSRFID Cookie
      const csrf = await call('csrf', base + '/auth/csrf', { method: 'GET' });
      const csrfJson = JSON.parse(csrf.text || '{}');
      const token = csrf.log.csrfEcho || (csrfJson.data && (csrfJson.data.token || csrfJson.data.csrfToken)) || '';

      // 2) 登录：带 X-CSRF-Token（同源请求不触发预检，浏览器直接发出）
      const login = await call('login', base + '/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': token },
        body: JSON.stringify({ loginName, password: initialPassword })
      });

      // 3) 当前用户
      const me = await call('me', base + '/me', { method: 'GET' });
      const meJson = JSON.parse(me.text || '{}');
      const loginNameActual = meJson.data && (meJson.data.loginName || meJson.data.user && meJson.data.user.loginName);

      // 4) 改密（初始密码门禁下唯一放行的写操作；带 Idempotency-Key）
      const key = 'browser-e2e-key-' + Date.now();
      const changed = await call('change-password', base + '/me/password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': token, 'Idempotency-Key': key },
        body: JSON.stringify({ currentPassword: initialPassword, newPassword })
      });

      // 5) 门禁解除后业务读 + 显式带 X-Approval-Id（模拟审批后执行的敏感调用头）
      const tasks = await call('tasks-after-unlock', base + '/tasks', { method: 'GET' });
      const approvalHeader = await call('tasks-with-approval-header', base + '/tasks', {
        method: 'GET',
        headers: { 'X-Approval-Id': '00000000-0000-0000-0000-000000000000' }
      });

      return { log, loginNameActual, key };
    }, { base, loginName, initialPassword, newPassword });

    const by = name => result.log.find(x => x.name === name);
    record('csrf issued (same origin, no preflight)',
      by('csrf').status === 200 && by('csrf').requestOrigin === by('csrf').pageOrigin,
      { status: by('csrf').status, origin: by('csrf').requestOrigin });
    record('login with CSRF header', by('login').status === 200, { status: by('login').status, body: by('login').body });
    record('session cookie works for /me', by('me').status === 200 && !!result.loginNameActual,
      { status: by('me').status, loginName: result.loginNameActual });
    record('password change with Idempotency-Key', by('change-password').status === 200,
      { status: by('change-password').status, body: by('change-password').body });
    record('business read after unlock', by('tasks-after-unlock').status === 200,
      { status: by('tasks-after-unlock').status });
    record('X-Approval-Id header passes same-origin proxy path',
      by('tasks-with-approval-header').status === 200,
      { status: by('tasks-with-approval-header').status });
    record('credential responses are not cacheable',
      (by('csrf').cacheControl || '').includes('no-store'),
      { cacheControl: by('csrf').cacheControl, loginCacheControl: by('login').cacheControl });
    record('no console errors', consoleErrors.length === 0, consoleErrors.slice(0, 5));

    const report = {
      timestamp: new Date().toISOString(),
      base,
      browser: await browser.version(),
      note: 'apps/web 原型未接后端；本脚本验证浏览器同源链路（Cookie/CSRF/幂等头/缓存头），对应 vite /api 代理所依赖的网络语义。',
      steps,
      consoleErrors
    };
    fs.writeFileSync(outFile, JSON.stringify(report, null, 2));
    const failed = steps.filter(s => !s.passed).length;
    console.log(JSON.stringify({ passed: steps.length - failed, failed, outFile }));
    if (failed) process.exitCode = 1;
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });

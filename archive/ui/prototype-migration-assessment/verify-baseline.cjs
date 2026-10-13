const fs = require('fs');
const path = require('path');
const http = require('http');
const assert = require('assert/strict');
const vm = require('vm');
const bundled = 'C:/Users/C-Linglong/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/';
const { chromium } = require(bundled + 'playwright');
const { babelParse } = require(bundled + 'playwright/lib/transform/babelBundle.js');
const root = path.resolve(__dirname, '../prototype');
const source = fs.readFileSync(path.join(root, 'app.js'), 'utf8');
const ast = babelParse(source, 'app.js', false);
const setFunction = ast.program.body.find(n => n.type === 'FunctionDeclaration' && n.id.name === 'setEvaluate');
const evaluate = vm.runInNewContext(source.slice(setFunction.start, setFunction.end) + ';setEvaluate');
assert.deepEqual(Array.from(evaluate('A∩B', [[1, 2], [2, 3]])), [2]);
assert.deepEqual(Array.from(evaluate('(A∪B)-C', [[1, 2], [2, 3], [2]])), [1, 3]);
assert.throws(() => evaluate('A+alert(1)', [[1]]));
const findings = [], baselineErrors = [];
let browser, server;
(async () => {
  server = http.createServer((req, res) => {
    let decoded; try { decoded = decodeURIComponent(new URL(req.url, 'http://localhost').pathname); } catch { res.writeHead(400); return res.end(); }
    const p = path.resolve(root, '.' + (decoded === '/' ? '/index.html' : decoded));
    if (!p.startsWith(root + path.sep)) { res.writeHead(403); return res.end(); }
    try { res.setHeader('Content-Type', p.endsWith('.js') ? 'text/javascript' : p.endsWith('.css') ? 'text/css' : p.endsWith('.html') ? 'text/html' : 'application/octet-stream'); res.end(fs.readFileSync(p)); } catch { res.writeHead(404); res.end(); }
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const url = 'http://127.0.0.1:' + server.address().port;
  browser = await chromium.launch({ headless: true, executablePath: process.env.AUDIT_BROWSER || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe' });
  const context = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  const page = await context.newPage(); page.setDefaultTimeout(7000);
  let recordBaseline = true;
  page.on('pageerror', e => { if (recordBaseline) baselineErrors.push(e.message); });
  page.on('console', m => { if (recordBaseline && m.type() === 'error') baselineErrors.push(m.text()); });
  const login = () => page.getByRole('button', { name: '登录并查看原型' }).click();
  const action = (name, arg) => page.locator('[data-action="' + name + '"]' + (arg === undefined ? '' : '[data-args="' + arg + '"]')).first().click();
  const data = () => page.evaluate(() => JSON.parse(localStorage.getItem('prototype-pdf-audit-v1')));
  await page.goto(url); await login();
  const pages = await page.locator('#side button').allTextContents(); assert.equal(pages.length, 28);
  for (const name of pages) { await page.locator('#side button').filter({ hasText: new RegExp('^' + name + '$') }).click(); assert.equal(await page.locator('#view h1').innerText(), name); }
  assert.equal(baselineErrors.length, 0);
  recordBaseline = false;
  await page.goto(url); await page.evaluate(() => localStorage.clear()); await page.reload(); await login();
  await page.locator('#side button').filter({ hasText: /^人员库$/ }).click();
  await action('import-libraries');
  const fixture = [{ name: '迁移评估安全样本', category: '自建库', personName: '虚构评估人员', target: '460000000009991', sex: '<img src="data:," onerror="window.__migrationAssessmentMarker=1">' }];
  await page.locator('[name="importFile"]').setInputFiles({ name: 'assessment.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify(fixture)) });
  await page.waitForFunction(() => document.getElementById('import-preview')?.textContent.includes('1'));
  await page.locator('#modal-form button[type="submit"]').click();
  const id = (await data()).libraries.find(l => l.name === fixture[0].name).id;
  await action('library-detail', id);
  await page.waitForFunction(() => window.__migrationAssessmentMarker === 1);
  findings.push({ id: 'R-XSS', observation: 'CONFIRMED', source: 'app.js:85 libraryPage raw p.sex + app.js:149 importLibraries row.sex', detail: 'A benign imported sex value created an HTML img element and its error handler set an in-memory marker. Isolated synthetic browser data only; no network transmission or user storage modification.', productionImpact: 'Stored XSS if this raw rendering/import pattern is carried into production.' });
  await page.evaluate(() => { localStorage.clear(); }); await page.reload(); await login();
  await page.evaluate(() => { const d = JSON.parse(localStorage.getItem('prototype-pdf-audit-v1')); d.roles = d.roles.filter(r => r.id !== 'R1'); localStorage.setItem('prototype-pdf-audit-v1', JSON.stringify(d)); });
  await page.reload(); await page.locator('#account').fill('demo.analyst'); await login();
  const roleFallbackMenus = await page.locator('#side button').count(); assert.equal(roleFallbackMenus, 28);
  findings.push({ id: 'R-AUTH', observation: 'CONFIRMED', source: 'app.js:23 role fallback to db.roles[0]', detail: 'Analyst referencing a missing role still obtained all 28 menu entries through fallback. This is a client-side Mock behavior, not a demonstrated server authorization bypass.', productionImpact: 'Unknown/deleted roles must fail closed; server must enforce permissions independently.' });
  const corruptionErrors = []; page.on('pageerror', e => corruptionErrors.push(e.message));
  await page.evaluate(() => localStorage.setItem('prototype-pdf-audit-v1', '{}')); await page.reload(); await login();
  await page.waitForFunction(() => document.getElementById('shell')?.style.display !== 'block');
  assert.ok(corruptionErrors.some(e => e.includes('find')));
  findings.push({ id: 'R-STORAGE', observation: 'CONFIRMED', source: 'app.js:19 only JSON parse guard; app.js:29 db.users.find', detail: 'Syntactically valid but structurally invalid local storage {} broke login with a page error. Persisted-state schema/version recovery is required.', productionImpact: 'Unavailable UI after incompatible or corrupted persisted state; avoid sensitive production state in localStorage.' });
  const output = { timestamp: new Date().toISOString(), browser: browser.version(), scope: '28-page smoke baseline + 3 targeted migration risk reproductions + 3 pure set parser assertions; this is not a full security audit or rerun of prior 69 cases.', pageCount: pages.length, pages, baselineErrors, pureSetParserAssertions: 3, findings, corruptionErrors };
  fs.writeFileSync(path.join(__dirname, 'baseline-verification.json'), JSON.stringify(output, null, 2));
  console.log(JSON.stringify(output, null, 2));
})().catch(e => { console.error(e); process.exitCode = 1; }).finally(async () => { if (browser) await browser.close(); if (server) await new Promise(resolve => server.close(resolve)); });

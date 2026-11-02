#!/usr/bin/env node
/**
 * 本地大模型桩：实现 OpenAI 兼容的 /v1/models 与 /v1/chat/completions（SSE 流式）。
 *
 * 用途：未安装 Ollama / 内网模型时，仍能演示智能体的流式对话链路；
 * 前端把它当成普通本地模型，不区分真实模型与桩。
 *
 * 运行：npm run llm
 * 端口：默认 11434（Ollama 默认端口），被占用时自动尝试 11435、11436。
 * 自检：node scripts/llm-stub.mjs --selfcheck
 */
import http from 'node:http';

const argv = process.argv.slice(2);
const arg = (name, fallback) => {
  const i = argv.indexOf(`--${name}`);
  return i >= 0 && argv[i + 1] ? argv[i + 1] : fallback;
};
const SELF_CHECK = argv.includes('--selfcheck');
const MODEL = arg('model', process.env.LLM_STUB_MODEL || 'qwen2.5:7b-instruct');
const BASE_PORT = Number(arg('port', process.env.LLM_STUB_PORT || (SELF_CHECK ? 0 : 11434)));
const LATENCY = Number(arg('latency', 18));

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, content-type, accept',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Max-Age': '86400',
};

const json = (res, status, body) => {
  const payload = JSON.stringify(body);
  res.writeHead(status, {...CORS, 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': Buffer.byteLength(payload)});
  res.end(payload);
};

/** 桩的"回答"只做语气承接，不虚构业务数据；数字只从系统提示词给出的聚合概况中取。 */
function composeAnswer(userText, systemText = '') {
  const text = String(userText || '');
  const pick = pattern => (String(systemText).match(pattern) || [])[1];
  const watches = pick(/布控任务 (\d+) 个/);
  const running = pick(/运行中 (\d+) 个/);
  const alerts = pick(/告警命中 (\d+) 条/);
  const captures = pick(/采集记录 (\d+) 条/);
  const sites = pick(/场所 (\d+) 个/);
  if (/布控|告警|预警/.test(text)) {
    const summary = watches ? `平台当前有 **${watches} 个布控任务**（运行中 ${running ?? '0'} 个），累计命中 **${alerts ?? '0'} 条**告警。` : '布控入口已就绪。';
    return `已识别到布控预警相关需求。${summary}你可以直接查看任务列表、新建布控，或查看预警结果；补充目标标识和时间范围后，打开新建布控时会自动带入条件。`;
  }
  if (/检索|查询|车牌|mac|人像|围栏|imsi/i.test(text)) {
    const summary = captures ? `平台现有 **${captures} 条**采集记录，覆盖 **${sites ?? '0'} 个**场所。` : '检索入口已就绪。';
    return `已识别到数据检索需求。${summary}选择下方任一数据类型即可进入检索页；把目标号码、车牌或 MAC 告诉我，我会把条件一并带入查询表单。`;
  }
  if (/研判|伴随|碰撞|区域统计/.test(text)) {
    return '已识别到数据研判需求。可以先查看已有任务，或新建伴随、碰撞、区域统计任务；目标、时间范围和选区需要在业务表单中确认后提交。';
  }
  if (/报表|运维|设备|通知/.test(text)) {
    return '已识别到运维相关需求。运维首页看设备在线率与采集量，报表、通知和参数配置各有独立入口；设备命令与报表生成由后端异步执行，完成后在任务中心查看结果。';
  }
  return '我是运行在本机的多维数据分析智能体，可以把你的一句话转成平台操作入口：布控预警、数据检索、分析研判、地图、档案、运维报表与任务中心。告诉我具体要做什么，或直接点击下方入口开始。';
}

function streamCompletion(res, content) {
  res.writeHead(200, {...CORS, 'Content-Type': 'text/event-stream; charset=utf-8', 'Cache-Control': 'no-cache, no-transform', Connection: 'keep-alive'});
  const id = 'chatcmpl-stub-' + Date.now();
  const created = Math.floor(Date.now() / 1000);
  const send = payload => res.write('data: ' + JSON.stringify(payload) + '\n\n');
  const chunk = (delta, finish = null) => send({id, object: 'chat.completion.chunk', created, model: MODEL, choices: [{index: 0, delta, finish_reason: finish}]});
  chunk({role: 'assistant', content: ''});
  const chars = Array.from(content);
  let index = 0;
  const timer = setInterval(() => {
    if (res.destroyed) {
      clearInterval(timer);
      return;
    }
    index += 3;
    chunk({content: chars.slice(Math.max(0, index - 3), index).join('')});
    if (index >= chars.length) {
      clearInterval(timer);
      chunk({}, 'stop');
      res.write('data: [DONE]\n\n');
      res.end();
    }
  }, LATENCY);
  res.on('close', () => clearInterval(timer));
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url || '/', 'http://127.0.0.1');
  if (req.method === 'OPTIONS') {
    res.writeHead(204, CORS);
    res.end();
    return;
  }
  if (req.method === 'GET' && (url.pathname === '/v1/models' || url.pathname === '/models')) {
    json(res, 200, {object: 'list', data: [{id: MODEL, object: 'model', created: 0, owned_by: 'local-stub'}]});
    return;
  }
  if (req.method === 'GET' && (url.pathname === '/health' || url.pathname === '/')) {
    json(res, 200, {ok: true, service: 'llm-stub', model: MODEL, mode: 'OpenAI compatible'});
    return;
  }
  if (req.method === 'POST' && (url.pathname === '/v1/chat/completions' || url.pathname === '/chat/completions')) {
    let body = '';
    req.on('data', piece => {
      body += piece;
      if (body.length > 2_000_000) req.destroy();
    });
    req.on('end', () => {
      let payload = {};
      try {
        payload = JSON.parse(body || '{}');
      } catch {
        json(res, 400, {error: {message: '请求体不是合法 JSON', type: 'invalid_request_error'}});
        return;
      }
      const messages = Array.isArray(payload.messages) ? payload.messages : [];
      const user = [...messages].reverse().find(message => message.role === 'user');
      const system = messages.find(message => message.role === 'system');
      const answer = composeAnswer(user?.content, system?.content);
      if (!SELF_CHECK) console.log(`[llm-stub] ${req.method} ${url.pathname} model=${payload.model || MODEL} stream=${payload.stream !== false} chars=${answer.length}`);
      if (payload.stream === false) {
        json(res, 200, {id: 'chatcmpl-stub', object: 'chat.completion', created: Math.floor(Date.now() / 1000), model: payload.model || MODEL, choices: [{index: 0, message: {role: 'assistant', content: answer}, finish_reason: 'stop'}], usage: {prompt_tokens: 0, completion_tokens: answer.length, total_tokens: answer.length}});
        return;
      }
      streamCompletion(res, answer);
    });
    return;
  }
  json(res, 404, {error: {message: '未知接口：' + url.pathname, type: 'invalid_request_error'}});
});

function listen(port, attempt = 0) {
  server.once('error', error => {
    if (error.code === 'EADDRINUSE' && !SELF_CHECK && attempt < 4) {
      listen(port + 1, attempt + 1);
      return;
    }
    console.error('[llm-stub] 启动失败：', error.message);
    process.exitCode = 1;
  });
  server.listen(port, '127.0.0.1', () => {
    const address = server.address();
    console.log(`[llm-stub] OpenAI 兼容本地模型桩已启动：http://127.0.0.1:${address.port}/v1`);
    console.log(`[llm-stub] 模型：${MODEL}（前端"模型设置"中填入上面的地址即可）`);
    console.log('[llm-stub] Ctrl+C 停止；真实模型请运行 ollama serve 并拉取模型');
  });
}

if (SELF_CHECK) {
  const checks = [];
  server.listen(0, '127.0.0.1', async () => {
    const port = server.address().port;
    const origin = `http://127.0.0.1:${port}`;
    try {
      const models = await (await fetch(origin + '/v1/models')).json();
      checks.push(['models 返回模型列表', models.data?.[0]?.id === MODEL]);
      const response = await fetch(origin + '/v1/chat/completions', {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({model: MODEL, stream: true, messages: [{role: 'user', content: '布控'}]})});
      const text = await response.text();
      checks.push(['SSE 输出含 delta', text.includes('"delta"')]);
      checks.push(['SSE 以 [DONE] 结束', text.trimEnd().endsWith('data: [DONE]')]);
      checks.push(['回答覆盖布控语义', text.includes('布控')]);
      const plain = await (await fetch(origin + '/v1/chat/completions', {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({model: MODEL, stream: false, messages: [{role: 'user', content: '报表'}]})})).json();
      checks.push(['非流式返回 message.content', typeof plain.choices?.[0]?.message?.content === 'string' && plain.choices[0].message.content.length > 10]);
      checks.push(['CORS 允许跨端口', (await fetch(origin + '/v1/models', {headers: {Origin: 'http://127.0.0.1:5180'}})).headers.get('access-control-allow-origin') === '*']);
    } catch (error) {
      checks.push(['自检执行', false, error.message]);
    }
    for (const [name, ok, detail] of checks) console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${detail ? ' · ' + detail : ''}`);
    const failed = checks.filter(([, ok]) => !ok).length;
    console.log(`[llm-stub] 自检 ${checks.length - failed}/${checks.length} 通过`);
    server.close(() => {
      if (failed) process.exitCode = 1;
    });
  });
} else {
  listen(BASE_PORT);
}

/**
 * 本地大模型客户端（OpenAI 兼容协议）。
 *
 * 默认指向本机 Ollama：`http://127.0.0.1:11434/v1`，也可指向内网的 vLLM、
 * LM Studio、Xinference 等兼容服务。默认只接受本机/内网地址，避免误把
 * 平台数据发到公网；确需改动时由配置页显式提示风险。
 */
export type ChatRole = 'system' | 'user' | 'assistant';
export type ChatMessage = {role: ChatRole; content: string};

export type LocalModelConfig = {
  enabled: boolean;
  /** OpenAI 兼容根地址，例如 http://127.0.0.1:11434/v1 */
  baseUrl: string;
  model: string;
  apiKey: string;
  temperature: number;
  timeoutMs: number;
};

export type ModelProbe = {
  ok: boolean;
  models: string[];
  latencyMs: number;
  error?: string;
};

const STORAGE_KEY = 'vue-agent-llm-config-v1';

const envText = (value: unknown, fallback: string) => (typeof value === 'string' && value.trim() ? value.trim() : fallback);

export const defaultModelConfig = (): LocalModelConfig => ({
  enabled: String(import.meta.env.VITE_LLM_ENABLED ?? 'true') !== 'false',
  baseUrl: envText(import.meta.env.VITE_LLM_BASE_URL, 'http://127.0.0.1:11434/v1'),
  model: envText(import.meta.env.VITE_LLM_MODEL, 'qwen2.5:7b-instruct'),
  apiKey: envText(import.meta.env.VITE_LLM_API_KEY, ''),
  temperature: 0.3,
  timeoutMs: 60_000,
});

/** 兼容用户只填到端口的写法：补 /v1，去掉末尾斜杠。 */
export function normalizeBaseUrl(raw: string): string {
  const trimmed = raw.trim().replace(/\/+$/, '');
  if (!trimmed) return '';
  if (/\/v\d+$/i.test(trimmed) || /\/api\/v\d+$/i.test(trimmed)) return trimmed;
  return trimmed + '/v1';
}

export function loadModelConfig(): LocalModelConfig {
  const fallback = defaultModelConfig();
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return fallback;
    const saved = JSON.parse(raw) as Partial<LocalModelConfig>;
    return {
      enabled: typeof saved.enabled === 'boolean' ? saved.enabled : fallback.enabled,
      baseUrl: envText(saved.baseUrl, fallback.baseUrl),
      model: envText(saved.model, fallback.model),
      apiKey: typeof saved.apiKey === 'string' ? saved.apiKey : fallback.apiKey,
      temperature: typeof saved.temperature === 'number' && saved.temperature >= 0 && saved.temperature <= 2 ? saved.temperature : fallback.temperature,
      timeoutMs: typeof saved.timeoutMs === 'number' && saved.timeoutMs >= 3_000 && saved.timeoutMs <= 300_000 ? saved.timeoutMs : fallback.timeoutMs,
    };
  } catch {
    return fallback;
  }
}

export function saveModelConfig(config: LocalModelConfig): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(config));
  } catch {
    /* 隐私模式或配额不足时仍允许本次会话使用内存配置 */
  }
}

export const modelConfigStorageKey = STORAGE_KEY;

/** 是否本机或内网地址；公网地址在设置页给出明确警告。 */
export function isLocalEndpoint(raw: string): boolean {
  try {
    const host = new URL(raw).hostname.toLowerCase();
    if (host === 'localhost' || host === '::1' || host.endsWith('.local')) return true;
    if (/^127\./.test(host) || /^10\./.test(host) || /^192\.168\./.test(host)) return true;
    const private172 = host.match(/^172\.(\d{1,3})\./);
    if (private172 && Number(private172[1]) >= 16 && Number(private172[1]) <= 31) return true;
    if (!host.includes('.') && !/^\d+$/.test(host)) return true; // 内网主机名
    return false;
  } catch {
    return false;
  }
}

export class LocalModelError extends Error {
  constructor(message: string, public code: 'unreachable' | 'http' | 'timeout' | 'aborted' | 'empty', public detail = '') {
    super(message);
    this.name = 'LocalModelError';
  }
}

const authHeaders = (config: LocalModelConfig): Record<string, string> => (config.apiKey ? {Authorization: 'Bearer ' + config.apiKey} : {});

function linkSignals(external: AbortSignal | undefined, timeoutMs: number): {signal: AbortSignal; cleanup: () => void; timedOut: () => boolean} {
  const controller = new AbortController();
  let timedOut = false;
  const timer = setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, timeoutMs);
  const onAbort = () => controller.abort();
  if (external) {
    if (external.aborted) controller.abort();
    else external.addEventListener('abort', onAbort, {once: true});
  }
  return {
    signal: controller.signal,
    cleanup: () => {
      clearTimeout(timer);
      external?.removeEventListener('abort', onAbort);
    },
    timedOut: () => timedOut,
  };
}

/** 读取本地模型列表并测量一次连接耗时。 */
export async function probeLocalModel(config: LocalModelConfig, signal?: AbortSignal): Promise<ModelProbe> {
  const baseUrl = normalizeBaseUrl(config.baseUrl);
  if (!baseUrl) return {ok: false, models: [], latencyMs: 0, error: '请填写本地模型服务地址'};
  const link = linkSignals(signal, Math.min(config.timeoutMs, 15_000));
  const started = performance.now();
  try {
    const response = await fetch(baseUrl + '/models', {headers: {Accept: 'application/json', ...authHeaders(config)}, signal: link.signal});
    if (!response.ok) throw new LocalModelError('模型服务返回 ' + response.status, 'http', (await response.text()).slice(0, 200));
    const body = (await response.json()) as {data?: {id?: unknown}[]; models?: {name?: unknown}[]};
    const ids = [
      ...(body.data ?? []).map(item => (typeof item.id === 'string' ? item.id : '')),
      ...(body.models ?? []).map(item => (typeof item.name === 'string' ? item.name : '')),
    ].filter(Boolean);
    return {ok: true, models: [...new Set(ids)], latencyMs: Math.round(performance.now() - started)};
  } catch (error) {
    if (signal?.aborted) throw new LocalModelError('已取消连接', 'aborted');
    if (error instanceof LocalModelError) throw error;
    if (link.timedOut()) return {ok: false, models: [], latencyMs: Math.round(performance.now() - started), error: '连接超时，请确认模型服务已启动'};
    return {ok: false, models: [], latencyMs: Math.round(performance.now() - started), error: '无法连接本地模型服务：' + (error instanceof Error ? error.message : String(error))};
  } finally {
    link.cleanup();
  }
}

/** 从 SSE 缓冲中拆出已完整的 data 行，返回剩余不完整内容。 */
export function extractSseData(buffer: string): {events: string[]; rest: string} {
  const normalized = buffer.replace(/\r\n/g, '\n');
  const parts = normalized.split('\n\n');
  const rest = parts.pop() ?? '';
  const events: string[] = [];
  for (const block of parts) {
    const lines = block.split('\n').filter(line => line.startsWith('data:'));
    if (!lines.length) continue;
    events.push(lines.map(line => line.slice(5).trim()).join('\n'));
  }
  return {events, rest};
}

type CompletionBody = {choices?: {message?: {content?: unknown}; delta?: {content?: unknown}}[]; error?: {message?: unknown}};

/** 发起流式对话；返回完整文本，调用方通过 onDelta 逐段渲染。 */
export async function streamLocalChat(
  config: LocalModelConfig,
  messages: ChatMessage[],
  onDelta: (text: string) => void,
  signal?: AbortSignal,
): Promise<{content: string; model: string}> {
  const baseUrl = normalizeBaseUrl(config.baseUrl);
  if (!baseUrl) throw new LocalModelError('请先在模型设置中填写服务地址', 'unreachable');
  const link = linkSignals(signal, config.timeoutMs);
  let content = '';
  try {
    const response = await fetch(baseUrl + '/chat/completions', {
      method: 'POST',
      headers: {'Content-Type': 'application/json', Accept: 'text/event-stream, application/json', ...authHeaders(config)},
      body: JSON.stringify({model: config.model, messages, stream: true, temperature: config.temperature}),
      signal: link.signal,
    });
    if (!response.ok) {
      const detail = (await response.text()).slice(0, 300);
      throw new LocalModelError('本地模型返回 ' + response.status + (detail ? '：' + detail : ''), 'http', detail);
    }
    const type = response.headers.get('content-type') ?? '';
    if (!type.includes('text/event-stream') || !response.body) {
      const body = (await response.json()) as CompletionBody;
      const text = String(body.choices?.[0]?.message?.content ?? '');
      if (!text) throw new LocalModelError('本地模型返回了空内容', 'empty');
      content = text;
      onDelta(text);
      return {content, model: config.model};
    }
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    for (;;) {
      const {value, done} = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, {stream: true});
      const {events, rest} = extractSseData(buffer);
      buffer = rest;
      for (const event of events) {
        if (event === '[DONE]') continue;
        let payload: CompletionBody;
        try {
          payload = JSON.parse(event) as CompletionBody;
        } catch {
          continue;
        }
        if (payload.error?.message) throw new LocalModelError('本地模型错误：' + String(payload.error.message), 'http');
        const choice = payload.choices?.[0];
        const delta = choice?.delta?.content ?? choice?.message?.content;
        if (typeof delta === 'string' && delta) {
          content += delta;
          onDelta(delta);
        }
      }
    }
    if (!content.trim()) throw new LocalModelError('本地模型没有返回内容，请检查模型名称是否正确', 'empty');
    return {content, model: config.model};
  } catch (error) {
    if (signal?.aborted) throw new LocalModelError('已停止生成', 'aborted');
    if (link.timedOut()) throw new LocalModelError('本地模型响应超时（' + Math.round(config.timeoutMs / 1000) + ' 秒）', 'timeout');
    if (error instanceof LocalModelError) throw error;
    throw new LocalModelError('连接本地模型失败：' + (error instanceof Error ? error.message : String(error)), 'unreachable');
  } finally {
    link.cleanup();
  }
}

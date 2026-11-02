<script setup lang="ts">
import {computed, reactive, ref, watch} from 'vue';
import type {LocalModelConfig} from '../../api/llm';
type ModelState = {status: string; models: string[]; latencyMs: number; error: string; checkedAt: string};
const props = defineProps<{modelValue: boolean; config: LocalModelConfig; state: ModelState; warning: string}>();
const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void;
  (event: 'save', config: LocalModelConfig): void;
  (event: 'probe', config: LocalModelConfig): void;
}>();
const form = reactive<LocalModelConfig & {timeoutSeconds: number}>({...props.config, timeoutSeconds: 60});
const saving = ref(false);

watch(
  () => props.modelValue,
  visible => {
    if (!visible) return;
    Object.assign(form, props.config, {timeoutSeconds: Math.round(props.config.timeoutMs / 1000)});
  },
);
const models = computed(() => [...new Set([props.config.model, ...props.state.models].filter(Boolean))]);
const statusText = computed(() => {
  if (props.state.status === 'checking') return '正在测试连接…';
  if (props.state.status === 'online') return '连接正常 · ' + props.state.latencyMs + ' ms · ' + props.state.checkedAt;
  if (props.state.status === 'offline') return '未连接 · ' + props.state.checkedAt;
  return '尚未检测';
});

function payload(): LocalModelConfig {
  return {...form, timeoutMs: Math.max(10, Number(form.timeoutSeconds) || 60) * 1000, baseUrl: form.baseUrl.trim(), model: form.model.trim()};
}
function save() {
  saving.value = true;
  try {
    emit('save', payload());
    emit('update:modelValue', false);
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <el-dialog :model-value="modelValue" title="本地大模型设置" width="680px" @update:model-value="$emit('update:modelValue', $event)">
    <el-form label-width="104px" label-position="left">
      <el-form-item label="启用本地模型">
        <el-switch v-model="form.enabled" />
        <span class="field-hint">关闭后仍可使用内置规则引擎完成识别与跳转</span>
      </el-form-item>
      <el-form-item label="服务地址">
        <el-input v-model="form.baseUrl" placeholder="http://127.0.0.1:11434/v1" />
        <p class="field-hint">OpenAI 兼容地址。Ollama 默认 <code>http://127.0.0.1:11434/v1</code>，只填端口会自动补 <code>/v1</code>。</p>
      </el-form-item>
      <el-form-item label="模型名称">
        <el-select v-model="form.model" filterable allow-create default-first-option placeholder="例如 qwen2.5:7b-instruct" class="model-select">
          <el-option v-for="item in models" :key="item" :label="item" :value="item" />
        </el-select>
        <el-button class="probe" :loading="state.status === 'checking'" @click="$emit('probe', payload())">测试并刷新</el-button>
      </el-form-item>
      <el-form-item v-if="form.enabled" label="连接状态">
        <div class="status" :class="state.status">
          <span class="dot" aria-hidden="true"></span>
          <span>{{ statusText }}</span>
          <span v-if="state.error" class="error">{{ state.error }}</span>
        </div>
      </el-form-item>
      <el-form-item label="API Key">
        <el-input v-model="form.apiKey" type="password" show-password placeholder="本地 Ollama 通常留空" />
      </el-form-item>
      <el-form-item label="温度">
        <el-slider v-model="form.temperature" :min="0" :max="1.5" :step="0.1" show-input :show-input-controls="false" />
      </el-form-item>
      <el-form-item label="超时（秒）">
        <el-input-number v-model="form.timeoutSeconds" :min="10" :max="300" :step="10" controls-position="right" />
      </el-form-item>
    </el-form>

    <el-alert v-if="warning" :title="warning" type="warning" :closable="false" show-icon class="warn" />
    <div class="deploy-hint">
      <b>本地部署提示</b>
      <p>推荐 Ollama：<code>ollama pull qwen2.5:7b-instruct</code> 后运行 <code>ollama serve</code>；浏览器页面跨端口访问时设置 <code>OLLAMA_ORIGINS=http://127.0.0.1:5180</code>。</p>
      <p>未安装模型时，可在项目目录运行 <code>npm run llm</code> 启动本地模型桩（同样是 OpenAI 兼容流式接口），用于演示流式回复。详细步骤见 <code>docs/smart-agent/本地大模型部署与接入.md</code>。</p>
      <p class="privacy">提示词只包含能力目录和聚合数量，不发送采集明细、人员信息或身份证/手机号等敏感字段。</p>
    </div>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button :loading="state.status === 'checking'" @click="$emit('probe', payload())">测试连接</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存并使用</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.field-hint{display:block;flex-basis:100%;width:100%;margin-top:5px;font-size:11.5px;color:#93a3b3;line-height:1.6}
.model-select{flex:1;min-width:0}
.probe{margin-left:8px}
.status{display:flex;align-items:center;gap:7px;font-size:12.5px;color:#5d7183;flex-wrap:wrap}
.status .dot{width:7px;height:7px;border-radius:50%;background:#c3ccd6}
.status.online .dot{background:#20a565}
.status.offline .dot{background:#c24545}
.status.checking .dot{background:#d9a23a}
.status .error{color:#b42332;font-size:11.5px}
.warn{margin:4px 0 10px}
.deploy-hint{background:#f7fafd;border:1px solid #e4ecf4;border-radius:8px;padding:10px 14px;font-size:12px;color:#5d7183;line-height:1.8}
.deploy-hint b{color:#33536f;font-size:12.5px}
.deploy-hint p{margin:4px 0}
.deploy-hint code,.field-hint code{background:#eef3f9;border:1px solid #e1eaf3;border-radius:4px;padding:0 4px;font-family:Consolas,Monaco,monospace;font-size:11.5px}
.privacy{color:#9a6a12}
</style>

<script setup lang="ts">
import {nextTick, onMounted, ref, watch} from 'vue';
const props = defineProps<{modelValue: string; modelLabel: string; online: boolean; streaming: boolean; quick: string[]}>();
const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void;
  (event: 'send', value: string): void;
  (event: 'stop'): void;
  (event: 'openModel'): void;
}>();
const input = ref<HTMLTextAreaElement>();
const composing = ref(false);

function resize() {
  const element = input.value;
  if (!element) return;
  element.style.height = 'auto';
  element.style.height = Math.min(element.scrollHeight, 132) + 'px';
}
function onInput(event: Event) {
  emit('update:modelValue', (event.target as HTMLTextAreaElement).value);
  resize();
}
function submit() {
  const value = props.modelValue.trim();
  if (!value) return;
  emit('send', value);
  nextTick(resize);
}
function onKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter' || event.shiftKey) return;
  if (composing.value || event.isComposing) return;
  event.preventDefault();
  if (!props.streaming) submit();
}
watch(() => props.modelValue, () => nextTick(resize));
onMounted(resize);
defineExpose({focus: () => input.value?.focus()});
</script>

<template>
  <div class="composer">
    <div v-if="quick.length" class="quick-row">
      <button v-for="item in quick" :key="item" type="button" class="quick" :disabled="streaming" @click="$emit('send', item)">{{ item }}</button>
    </div>
    <div class="box" :class="{busy: streaming}">
      <textarea
        ref="input"
        :value="modelValue"
        rows="1"
        maxlength="800"
        placeholder="输入你的问题，例如：布控、新建布控、检索 460000000000001 最近24小时…"
        aria-label="输入消息"
        @input="onInput"
        @keydown="onKeydown"
        @compositionstart="composing = true"
        @compositionend="composing = false"
      ></textarea>
      <div class="toolbar">
        <button type="button" class="model-pill" :class="{online}" :title="'本地模型设置'" @click="$emit('openModel')">
          <span class="dot" aria-hidden="true"></span>
          <span class="label">{{ modelLabel }}</span>
          <span class="gear" aria-hidden="true">⚙</span>
        </button>
        <span class="spacer"></span>
        <span class="hint">Enter 发送 · Shift + Enter 换行 · {{ modelValue.length }}/800</span>
        <button v-if="streaming" type="button" class="send stop" aria-label="停止生成" @click="$emit('stop')">
          <span class="square" aria-hidden="true"></span>
        </button>
        <button v-else type="button" class="send" :disabled="!modelValue.trim()" aria-label="发送" @click="submit">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" aria-hidden="true">
            <path d="M4 11.5 19.5 4l-4.2 15.5-4.4-5.6L4 11.5Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/>
            <path d="M10.9 13.9 19.5 4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.composer{width:100%}
.quick-row{display:flex;gap:7px;flex-wrap:wrap;margin-bottom:8px}
.quick{border:1px solid #e0e9f2;background:#fff;color:#4d6a85;border-radius:999px;padding:4px 11px;font-size:12px;transition:.16s}
.quick:hover:not(:disabled){border-color:#9dc6e8;color:#1769aa;background:#f4faff}
.quick:disabled{opacity:.55;cursor:not-allowed}
.box{border:1px solid #d8e3ee;background:#fff;border-radius:16px;box-shadow:0 6px 20px #18324912;padding:12px 14px 8px;transition:border-color .18s,box-shadow .18s}
.box:focus-within{border-color:#8dbcdf;box-shadow:0 8px 26px #1769aa1f}
.box.busy{border-color:#c3d5e6;background:#fbfdff}
textarea{display:block;width:100%;border:0;outline:none;resize:none;background:transparent;font:inherit;font-size:13.5px;line-height:1.7;color:#20374d;min-height:26px;max-height:132px;padding:0}
textarea::placeholder{color:#a3b2c1}
.toolbar{display:flex;align-items:center;gap:8px;margin-top:8px}
.spacer{flex:1}
.model-pill{display:inline-flex;align-items:center;gap:6px;border:1px solid #e2eaf2;background:#f8fbfe;color:#5a7288;border-radius:999px;padding:3px 9px;font-size:11.5px;max-width:46%}
.model-pill:hover{border-color:#b9d3e8;color:#1769aa}
.model-pill .dot{width:6px;height:6px;border-radius:50%;background:#c3ccd6;flex:none}
.model-pill.online .dot{background:#20a565;box-shadow:0 0 0 3px #20a5651f}
.model-pill .label{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.model-pill .gear{font-size:11px;color:#a8b7c6}
.hint{font-size:11px;color:#a0afbe;white-space:nowrap}
.send{flex:none;width:34px;height:34px;border-radius:50%;border:0;background:#1769aa;color:#fff;display:grid;place-items:center;transition:.16s}
.send:hover:not(:disabled){background:#125a93}
.send:disabled{background:#d7e1ea;color:#fff;cursor:not-allowed}
.send.stop{background:#e8f0f8;color:#3f6482}
.send.stop:hover{background:#dbe8f4}
.square{width:9px;height:9px;background:#3f6482;border-radius:2px}
@media(max-width:900px){.hint{display:none}}
</style>

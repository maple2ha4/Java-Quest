<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import * as monaco from 'monaco-editor/esm/vs/editor/editor.api'
import 'monaco-editor/esm/vs/basic-languages/java/java.contribution'
import EditorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker'
const props = defineProps<{ modelValue: string; filename: string; readonly?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const host = ref<HTMLElement>()
let editor: monaco.editor.IStandaloneCodeEditor | undefined, observer: MutationObserver | undefined
self.MonacoEnvironment = { getWorker: () => new EditorWorker() }
function theme() {
  return document.documentElement.dataset.theme === 'dark' ? 'vs-dark' : 'vs'
}
onMounted(() => {
  editor = monaco.editor.create(host.value!, {
    value: props.modelValue,
    language: 'java',
    theme: theme(),
    fontSize: 14,
    fontFamily: '"SFMono-Regular",Consolas,"Liberation Mono",monospace',
    lineHeight: 24,
    minimap: { enabled: false },
    automaticLayout: true,
    scrollBeyondLastLine: false,
    editContext: false,
    padding: { top: 20 },
    tabSize: 4,
    readOnly: props.readonly,
    wordWrap: 'on',
    renderLineHighlight: 'none',
    overviewRulerLanes: 0,
    fontLigatures: true,
  })
  editor.onDidChangeModelContent(() => emit('update:modelValue', editor!.getValue()))
  observer = new MutationObserver(() => monaco.editor.setTheme(theme()))
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })
})
watch(
  () => props.modelValue,
  (v) => {
    if (editor && editor.getValue() !== v) editor.setValue(v)
  },
)
watch(
  () => props.readonly,
  (v) => editor?.updateOptions({ readOnly: v }),
)
onBeforeUnmount(() => {
  observer?.disconnect()
  const model = editor?.getModel()
  editor?.dispose()
  model?.dispose()
})
</script>
<template><div ref="host" class="monaco-host" :aria-label="filename + ' 代码编辑器'" /></template>

<script setup>
import { computed, ref } from 'vue'
import { uploadActivityImage } from '@/api/attachment'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  max: { type: Number, default: 6 },
  label: { type: String, default: '活动图片' },
  hint: { type: String, default: '' },
  captionable: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'uploading-change'])

const fileInput = ref(null)
const uploading = ref(false)
const error = ref('')

const mockMode = import.meta.env.VITE_USE_MOCK === 'true'
const canAdd = computed(() => !mockMode && props.modelValue.length < props.max)
const accept = 'image/jpeg,image/png'

function chooseFiles() {
  if (!canAdd.value || uploading.value) return
  fileInput.value?.click()
}

function validateFile(file) {
  if (!['image/jpeg', 'image/png'].includes(file.type)) {
    return '仅支持 JPG、PNG 图片'
  }
  if (file.size > 5 * 1024 * 1024) {
    return '单张图片不能超过 5MB'
  }
  return ''
}

async function onFilesSelected(event) {
  const files = Array.from(event.target.files || [])
  event.target.value = ''
  if (!files.length) return

  error.value = ''
  const remaining = props.max - props.modelValue.length
  if (files.length > remaining) {
    error.value = `最多还能添加 ${remaining} 张图片`
  }

  uploading.value = true
  emit('uploading-change', true)
  const added = []
  try {
    for (const file of files.slice(0, remaining)) {
      const validationError = validateFile(file)
      if (validationError) {
        error.value = validationError
        continue
      }
      added.push(await uploadActivityImage(file))
    }
  } catch (err) {
    error.value = err.message || '图片上传失败'
  } finally {
    if (added.length) {
      emit('update:modelValue', [...props.modelValue, ...added])
    }
    uploading.value = false
    emit('uploading-change', false)
  }
}

function removeAt(index) {
  emit(
    'update:modelValue',
    props.modelValue.filter((_, current) => current !== index),
  )
}

function updateCaption(index, caption) {
  emit(
    'update:modelValue',
    props.modelValue.map((item, current) => (current === index ? { ...item, caption } : item)),
  )
}
</script>

<template>
  <div class="ink-image-uploader" :aria-busy="uploading">
    <div class="ink-image-uploader-head">
      <span class="ink-image-uploader-label">{{ label }}</span>
      <span class="ink-image-uploader-count num">{{ modelValue.length }} / {{ max }}</span>
    </div>

    <div class="ink-image-grid">
      <figure v-for="(item, index) in modelValue" :key="item.fileUrl" class="ink-image-card">
        <img
          :src="item.fileUrl"
          :alt="item.caption || item.fileName || `${label} ${index + 1}`"
          loading="lazy"
        />
        <div class="ink-image-card-body">
          <input
            v-if="captionable"
            class="ink-input ink-image-caption"
            type="text"
            maxlength="100"
            :value="item.caption || ''"
            placeholder="填写图片说明"
            :aria-label="`第 ${index + 1} 张图片说明`"
            @input="updateCaption(index, $event.target.value)"
          />
          <div class="ink-image-card-actions">
            <span class="ink-image-file-name">{{ item.fileName || '活动图片' }}</span>
            <button type="button" class="ink-image-remove" @click="removeAt(index)">移除</button>
          </div>
        </div>
      </figure>

      <button
        v-if="canAdd"
        type="button"
        class="ink-image-add"
        :disabled="uploading"
        @click="chooseFiles"
      >
        <span aria-hidden="true">＋</span>
        <b>{{ uploading ? '上传中…' : '添加图片' }}</b>
      </button>
    </div>

    <input
      ref="fileInput"
      class="sr-only"
      type="file"
      :accept="accept"
      :multiple="max > 1"
      tabindex="-1"
      :aria-label="`选择${label}`"
      @change="onFilesSelected"
    />

    <span class="sr-only" aria-live="polite">{{ uploading ? '图片上传中' : '' }}</span>
    <p v-if="hint" class="ink-image-hint">{{ hint }}</p>
    <p v-if="mockMode" class="ink-image-error">当前为模拟数据模式，图片上传不可用。</p>
    <p v-if="error" class="ink-image-error" role="alert">{{ error }}</p>
  </div>
</template>

<style scoped>
.ink-image-uploader {
  padding-top: 4px;
}

.ink-image-uploader-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--c-line);
}

.ink-image-uploader-label {
  font-family: var(--font-display);
  font-size: 14px;
  letter-spacing: 0.08em;
  color: var(--c-ink-2);
}

.ink-image-uploader-count {
  font-size: 12px;
  color: var(--c-ink-3);
}

.ink-image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 16px;
  margin-top: 16px;
}

.ink-image-card {
  min-width: 0;
  border: 1px solid var(--c-line);
  background: #fff;
}

.ink-image-card img {
  width: 100%;
  aspect-ratio: 3 / 2;
  object-fit: cover;
  background: var(--c-line-2);
}

.ink-image-card-body {
  padding: 10px;
}

.ink-image-caption {
  min-height: 36px;
  padding: 6px 9px;
  font-size: 13px;
}

.ink-image-card-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 8px;
}

.ink-image-file-name {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--c-ink-3);
}

.ink-image-remove {
  flex: none;
  font-size: 12px;
  color: var(--c-bad);
  border-bottom: 1px solid var(--c-bad);
}

.ink-image-add {
  min-height: 0;
  aspect-ratio: 3 / 2;
  display: grid;
  place-content: center;
  gap: 8px;
  border: 1px dashed var(--c-line);
  color: var(--c-ink-3);
  background: transparent;
  transition:
    color 0.15s ease-out,
    border-color 0.15s ease-out,
    background-color 0.15s ease-out;
}

.ink-image-add:hover {
  color: var(--c-ink);
  border-color: var(--c-ink);
  background: #fff;
}

.ink-image-add:disabled {
  cursor: wait;
  opacity: 0.6;
}

.ink-image-add span {
  font-size: 28px;
  line-height: 1;
}

.ink-image-add b {
  font-family: var(--font-display);
  font-size: 14px;
  font-weight: 400;
  letter-spacing: 0.08em;
}

.ink-image-hint,
.ink-image-error {
  margin-top: 10px;
  font-size: 12px;
  line-height: 1.7;
}

.ink-image-hint {
  color: var(--c-ink-3);
}

.ink-image-error {
  color: var(--c-bad);
}
</style>

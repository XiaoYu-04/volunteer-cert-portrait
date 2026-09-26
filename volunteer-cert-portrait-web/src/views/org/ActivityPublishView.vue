<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createActivity, listCategories } from '@/api/volunteer'
import { getOrg } from '@/api/org'
import { useUserStore } from '@/stores/user'
import { useToast } from '@/composables/useToast'

import InkField from '@/components/common/InkField.vue'
import InkButton from '@/components/common/InkButton.vue'
import InkImageUploader from '@/components/common/InkImageUploader.vue'

const router = useRouter()
const toast = useToast()
const user = useUserStore()

const orgId = computed(() => user.info?.orgId || 1)

const categories = ref([])
const orgName = ref('')
const submitting = ref(false)
const coverImages = ref([])
const descriptionImages = ref([])
const uploadingImages = ref(0)

const form = reactive({
  title: '',
  categoryId: '',
  date: '',
  time: '',
  place: '',
  capacity: '',
  hours: '',
  deadline: '',
  contact: '',
  description: '',
})

const errors = reactive({})

/** 必填项与提示文案，键名即 form 的字段名 */
const REQUIRED = [
  ['title', '请填写活动名称'],
  ['categoryId', '请选择活动分类'],
  ['date', '请选择活动日期'],
  ['time', '请选择开始时间'],
  ['place', '请填写活动地点'],
  ['capacity', '请填写招募名额'],
  ['hours', '请填写单人服务时长'],
  ['deadline', '请选择报名截止日期'],
  ['contact', '请填写联系人'],
  ['description', '请填写活动简介'],
]

onMounted(async () => {
  try {
    const [list, org] = await Promise.all([listCategories(), getOrg(orgId.value)])
    categories.value = list || []
    orgName.value = org?.name || ''
  } catch (err) {
    toast.error(err.message)
  }
})

const selectedCategory = computed(
  () => categories.value.find((c) => c.id === Number(form.categoryId)) || null,
)

function clearErrors() {
  Object.keys(errors).forEach((key) => {
    errors[key] = ''
  })
}

function validate() {
  clearErrors()
  REQUIRED.forEach(([key, message]) => {
    if (!String(form[key]).trim()) errors[key] = message
  })

  if (form.capacity && (!Number.isInteger(Number(form.capacity)) || Number(form.capacity) <= 0)) {
    errors.capacity = '名额需为正整数'
  }
  if (form.hours && (!(Number(form.hours) > 0) || Number(form.hours) > 24)) {
    errors.hours = '服务时长需在 0 ~ 24 小时之间'
  }
  // 报名截止晚于活动当天会让报名窗口失去意义
  if (form.deadline && form.date && form.deadline > form.date) {
    errors.deadline = '报名截止日期不能晚于活动日期'
  }

  return !Object.values(errors).some(Boolean)
}

async function onSubmit() {
  if (submitting.value) return
  if (uploadingImages.value > 0) {
    toast.warn('图片上传完成后再提交')
    return
  }
  if (!validate()) {
    toast.warn('请先补全标有 * 的必填项')
    return
  }

  submitting.value = true
  try {
    await createActivity({
      title: form.title.trim(),
      categoryId: Number(form.categoryId),
      type: selectedCategory.value?.name || '',
      date: form.date,
      time: form.time,
      place: form.place.trim(),
      capacity: Number(form.capacity),
      hours: Number(form.hours),
      deadline: form.deadline,
      contact: form.contact.trim(),
      description: form.description.trim(),
      cover: coverImages.value[0]?.fileUrl || '',
      images: descriptionImages.value.map(
        ({ fileUrl, fileName, fileSize, contentType, caption }) => ({
          fileUrl,
          fileName,
          fileSize,
          contentType,
          caption: caption?.trim() || '',
        }),
      ),
      orgId: orgId.value,
      org: orgName.value,
    })
    toast.success('活动已创建为草稿，可在活动管理中发布')
    router.push('/org/activities')
  } catch (err) {
    toast.error(err.message)
  } finally {
    submitting.value = false
  }
}

function onReset() {
  if (uploadingImages.value > 0) return
  Object.assign(form, {
    title: '',
    categoryId: '',
    date: '',
    time: '',
    place: '',
    capacity: '',
    hours: '',
    deadline: '',
    contact: '',
    description: '',
  })
  coverImages.value = []
  descriptionImages.value = []
  clearErrors()
}

function onUploadingChange(active) {
  uploadingImages.value = Math.max(0, uploadingImages.value + (active ? 1 : -1))
}
</script>

<template>
  <h1 class="console-title">发布活动</h1>
  <p class="console-sub">填写活动信息，提交即存为草稿，发布入口在活动管理页。</p>

  <section class="panel">
    <div class="panel-head">
      <h2 class="panel-title">活动信息</h2>
      <span class="panel-extra">
        发布组织：{{ orgName || '本组织' }}
        <span class="form-legend" aria-hidden="true">标 * 的为必填项</span>
      </span>
    </div>

    <form class="ink-form-grid" @submit.prevent="onSubmit">
      <InkField label="活动名称" required :error="errors.title" class="span-2">
        <input
          v-model.trim="form.title"
          class="ink-input"
          type="text"
          placeholder="如 社区敬老院陪伴服务"
        />
      </InkField>

      <InkField label="活动分类" required :error="errors.categoryId">
        <select v-model="form.categoryId" class="ink-select">
          <option value="">请选择分类</option>
          <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
      </InkField>

      <InkField label="活动地点" required :error="errors.place">
        <input
          v-model.trim="form.place"
          class="ink-input"
          type="text"
          placeholder="如 幸福里社区敬老院"
        />
      </InkField>

      <InkField label="活动日期" required :error="errors.date">
        <input v-model="form.date" class="ink-input" type="date" />
      </InkField>

      <InkField label="开始时间" required :error="errors.time">
        <input v-model="form.time" class="ink-input" type="time" />
      </InkField>

      <InkField
        label="报名截止日期"
        required
        :error="errors.deadline"
        hint="截止后不再接受新的报名"
      >
        <input v-model="form.deadline" class="ink-input" type="date" />
      </InkField>

      <InkField label="招募名额" required :error="errors.capacity" hint="达到名额后自动停止报名">
        <input
          v-model="form.capacity"
          class="ink-input"
          type="number"
          min="1"
          placeholder="如 60"
        />
      </InkField>

      <InkField label="单人服务时长" required :error="errors.hours" hint="单位：小时">
        <input
          v-model="form.hours"
          class="ink-input"
          type="number"
          min="1"
          max="24"
          step="0.5"
          placeholder="如 4"
        />
      </InkField>

      <InkField label="联系人" required :error="errors.contact" hint="格式：姓名 + 联系方式">
        <input
          v-model.trim="form.contact"
          class="ink-input"
          type="text"
          placeholder="如 李同学 138****2201"
        />
      </InkField>

      <InkField label="活动简介" required :error="errors.description" class="span-2">
        <textarea
          v-model.trim="form.description"
          class="ink-textarea"
          placeholder="如 集合时间、着装要求、需自带的工具"
        ></textarea>
      </InkField>

      <div class="span-2 image-field">
        <InkImageUploader
          v-model="coverImages"
          :max="1"
          label="活动封面"
          hint="建议使用 3:2 横图，支持 JPG、PNG，单张不超过 5MB。"
          @uploading-change="onUploadingChange"
        />
      </div>

      <div class="span-2 image-field">
        <InkImageUploader
          v-model="descriptionImages"
          :max="6"
          label="图文说明"
          hint="可添加最多 6 张图片，并为每张填写说明。"
          captionable
          @uploading-change="onUploadingChange"
        />
      </div>

      <div class="ink-form-actions span-2">
        <InkButton
          native-type="submit"
          variant="primary"
          :disabled="submitting || uploadingImages > 0"
        >
          {{ submitting ? '提交中…' : uploadingImages > 0 ? '图片上传中…' : '提交活动' }}
        </InkButton>
        <InkButton :disabled="submitting || uploadingImages > 0" @click="onReset">重置</InkButton>
        <InkButton variant="ghost" to="/org/activities">返回活动管理</InkButton>
      </div>
    </form>
  </section>
</template>

<style scoped>
.ink-form-grid {
  max-width: 920px;
}

/* 字段节奏：标签与控件之间留出 12px，比全局的 8px 略松，标签成组更清楚 */
.ink-form-grid :deep(.ink-field-label) {
  margin-bottom: var(--sp-3);
}

/* 提示与错误紧贴控件，8px 一档，不与下一个字段的 22px 抢间距 */
.ink-form-grid :deep(.ink-field-hint),
.ink-form-grid :deep(.ink-field-error) {
  margin-top: var(--sp-2);
}

/* 十个字段全为必填，星号需要一句说明；与 .panel-note 同一档小字。
   align-self 让 12px 的小字与左侧 16px 的「发布组织」共用一条基线 */
.form-legend {
  align-self: baseline;
  font-size: 12px;
  letter-spacing: 0.06em;
  color: var(--c-ink-3);
}

.form-legend b {
  font-weight: 400;
  color: var(--c-a2);
}

.ink-form-actions {
  padding-top: var(--sp-2);
}

.image-field {
  padding: 18px 0;
  border-top: 1px solid var(--c-line-2);
}
</style>

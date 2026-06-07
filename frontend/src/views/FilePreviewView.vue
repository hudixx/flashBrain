<template>
  <div class="file-preview-page">
    <div class="preview-header">
      <div>
        <h2>{{ title }}</h2>
        <p>{{ subtitle }}</p>
      </div>
      <el-button type="primary" plain @click="openOriginal">打开原文件</el-button>
    </div>

    <el-card class="preview-card">
      <div v-if="loading" class="preview-loading">正在加载预览...</div>
      <el-alert v-else-if="error" :title="error" type="error" show-icon />
      <el-alert
        v-else-if="requiresKkFileView && !kkFileViewBaseUrl"
        title="未配置 kkFileView 预览服务地址，请设置 VITE_KKFILEVIEW_BASE_URL"
        type="warning"
        show-icon
      />
      <iframe
        v-else
        class="preview-frame"
        :src="frameUrl"
        title="文件预览"
      ></iframe>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import apiClient from '../api/client'

const route = useRoute()
const loading = ref(true)
const error = ref('')
const preview = ref<any>(null)

const normalizeBaseUrl = (value: string) => {
  const trimmed = (value || '').trim().replace(/\/$/, '')
  return trimmed.replace(/^https?:\/\/https?:\/\//i, (matched) => matched.substring(0, matched.indexOf('://') + 3))
}

const kkFileViewBaseUrl = normalizeBaseUrl(import.meta.env.VITE_KKFILEVIEW_BASE_URL || '')
const backendPublicBaseUrl = normalizeBaseUrl(import.meta.env.VITE_BACKEND_PUBLIC_BASE_URL || window.location.origin)

const title = computed(() => preview.value?.originalFilename || '文件预览')
const subtitle = computed(() => {
  if (!preview.value?.fileType) return '在浏览器页面中预览上传文件'
  if (requiresKkFileView.value) return `${preview.value.fileType} 文件由 kkFileView 内嵌预览`
  return `${preview.value.fileType} 文件预览`
})
const requiresKkFileView = computed(() => {
  const fileType = preview.value?.fileType
  return fileType === 'DOC' || fileType === 'DOCX' || fileType === 'OFD'
})
const originalFileUrl = computed(() => {
  const url = preview.value?.url || ''
  if (!url) return ''
  if (/^https?:\/\//i.test(url)) return url
  return `${backendPublicBaseUrl}${url.startsWith('/') ? url : `/${url}`}`
})
const frameUrl = computed(() => {
  if (!originalFileUrl.value) return ''
  if (!requiresKkFileView.value) return originalFileUrl.value
  if (!kkFileViewBaseUrl) return ''
  return `${kkFileViewBaseUrl}/onlinePreview?url=${encodeURIComponent(encodeBase64(originalFileUrl.value))}`
})

const encodeBase64 = (value: string) => {
  return window.btoa(unescape(encodeURIComponent(value)))
}

const loadPreview = async () => {
  const snippetId = route.params.snippetId
  const fileId = route.params.fileId
  loading.value = true
  error.value = ''
  try {
    const res = await apiClient.get(`/snippets/${snippetId}/files/${fileId}/preview`)
    preview.value = res.data
  } catch (err: any) {
    error.value = err?.response?.data?.message || '文件预览加载失败'
  } finally {
    loading.value = false
  }
}

const openOriginal = () => {
  if (!originalFileUrl.value) return
  window.open(originalFileUrl.value, '_blank', 'noopener,noreferrer')
}

onMounted(loadPreview)
</script>

<style scoped>
.file-preview-page {
  min-height: 100vh;
  padding: 24px;
  box-sizing: border-box;
  background: #f5f7fa;
}
.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}
.preview-header h2 {
  margin: 0 0 6px;
  font-size: 20px;
  word-break: break-all;
}
.preview-header p {
  margin: 0;
  color: #909399;
  font-size: 13px;
}
.preview-card {
  min-height: calc(100vh - 120px);
}
.preview-loading {
  color: #909399;
  padding: 24px;
  text-align: center;
}
.preview-frame {
  width: 100%;
  height: calc(100vh - 170px);
  border: none;
  background: white;
}
</style>

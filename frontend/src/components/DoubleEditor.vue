<template>
  <div v-if="snippet" class="double-editor">
    <div class="editor-header">
      <div class="primary-actions">
        <template v-if="isRecycleMode">
          <el-button size="small" type="primary" @click="handleRestore">恢复片段</el-button>
          <el-button size="small" type="danger" plain @click="handlePermanentDelete">永久删除</el-button>
        </template>
        <template v-else>
          <el-button size="small" type="primary" @click="saveDetail" :disabled="!snippet">保存</el-button>
          <el-upload
            action="#"
            :auto-upload="false"
            :show-file-list="false"
            accept="image/*,.txt,.docx,.doc,.pdf,.ofd"
            :disabled="uploading"
            @change="handleFileChange"
          >
            <el-button size="small" type="primary" plain :loading="uploading">上传文件</el-button>
          </el-upload>
          <el-button size="small" @click="openFileDialog">查看文件</el-button>
        </template>
      </div>
      <div class="header-tools">
        <el-button size="small" text bg @click="emit('toggle-fullscreen')">
          {{ fullscreen ? '退出全屏' : '全屏编辑' }}
        </el-button>
        <template v-if="!isRecycleMode">
          <el-tooltip content="置顶" placement="top">
            <el-icon :class="{ active: snippet.isPinned }" @click="handlePin"><PriceTag /></el-icon>
          </el-tooltip>
          <el-tooltip content="标记掌握" placement="top">
            <el-icon :class="{ active: snippet.isMastered }" @click="handleMastered"><CircleCheck /></el-icon>
          </el-tooltip>
          <el-tooltip content="删除" placement="top">
            <el-icon @click="handleDelete"><Delete /></el-icon>
          </el-tooltip>
        </template>
      </div>
    </div>

    <div v-if="isRecycleMode" class="recycle-tip">
      该知识片段位于回收站，只能恢复或永久删除。
    </div>

    <div class="title-row">
      <el-input
        v-model="title"
        type="textarea"
        :rows="1"
        resize="vertical"
        placeholder="请输入片段标题"
        :disabled="isRecycleMode"
      />
    </div>

    <!-- OCR 原文区 -->
    <div class="section">
      <div class="section-header collapsible-header" @click="ocrCollapsed = !ocrCollapsed">
        <span>OCR 原文 (系统清洗后)</span>
        <el-button size="small" text>{{ ocrCollapsed ? '展开' : '收起' }}</el-button>
      </div>
      <el-input
        v-if="!ocrCollapsed"
        v-model="ocrText"
        type="textarea"
        :rows="4"
        placeholder="OCR 识别结果将在此显示..."
        :disabled="!snippet || isRecycleMode"
      />
    </div>

    <!-- 个人笔记区 -->
    <div class="section note-section">
      <div class="section-header">
        <span>我的个人内化笔记 (MARKDOWN)</span>
      </div>
      <el-tabs type="border-card" class="editor-tabs">
        <el-tab-pane label="编辑">
          <el-input
            v-model="noteContent"
            class="note-input"
            type="textarea"
            :rows="1"
            placeholder="输入您的思考与总结..."
            :disabled="!snippet || isRecycleMode"
          />
        </el-tab-pane>
        <el-tab-pane label="预览">
          <div class="markdown-body" v-html="renderedMarkdown"></div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="fileDialogVisible" title="上传文件" width="760px">
      <el-empty v-if="snippetFiles.length === 0" description="暂无上传文件" />
      <el-table v-else :data="snippetFiles" size="small" class="file-table">
        <el-table-column label="文件名" min-width="260">
          <template #default="{ row }">
            <span class="file-name" :title="row.originalFilename">{{ row.originalFilename }}</span>
          </template>
        </el-table-column>
        <el-table-column label="上传时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="viewFile(row)">查看</el-button>
            <el-button size="small" text type="primary" @click="downloadFile(row)">下载</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
  <div v-else class="empty-state">
    <el-empty description="请选择一个知识片段进行编辑" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { PriceTag, CircleCheck, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import MarkdownIt from 'markdown-it'
import apiClient from '../api/client'

const props = defineProps<{
  snippet?: any
  fullscreen?: boolean
  recycleMode?: boolean
}>()

const emit = defineEmits(['updated', 'toggle-fullscreen'])

const md = new MarkdownIt({
  breaks: true
})
const title = ref('')
const ocrText = ref('')
const noteContent = ref('')
const fileDialogVisible = ref(false)
const snippetFiles = ref<any[]>([])
const ocrCollapsed = ref(true)
const uploading = ref(false)
const IMAGE_EXTENSIONS = ['jpg', 'jpeg', 'png', 'bmp', 'webp']

const isRecycleMode = computed(() => Boolean(props.recycleMode || props.snippet?.isDeleted))

// 监听 prop 变化并同步到本地 ref
watch(() => props.snippet, (newVal) => {
  if (newVal) {
    title.value = newVal.title || ''
    ocrText.value = newVal.ocrText || ''
    noteContent.value = newVal.noteContent || ''
    snippetFiles.value = []
    ocrCollapsed.value = !Boolean(newVal.ocrText)
  } else {
    title.value = ''
    ocrText.value = ''
    noteContent.value = ''
    snippetFiles.value = []
    ocrCollapsed.value = true
  }
}, { immediate: true })

const renderedMarkdown = computed(() => {
  return md.render(noteContent.value || '*暂无内容*')
})

const isImageFile = (rawFile: File) => {
  const extension = rawFile.name.split('.').pop()?.toLowerCase() || ''
  return rawFile.type.startsWith('image/') || IMAGE_EXTENSIONS.includes(extension)
}

const getErrorMessage = (err: any, fallback: string) => {
  return err?.response?.data?.message || err?.message || fallback
}

const confirmReplaceOcrIfNeeded = async (rawFile: File) => {
  if (isImageFile(rawFile)) {
    return true
  }
  const originalOcrText = props.snippet?.ocrText || ''
  const currentOcrText = ocrText.value || ''
  if (!originalOcrText && !currentOcrText) {
    return true
  }
  try {
    await ElMessageBox.confirm(
      '上传文档读取出的内容将替换当前 OCR 原文，是否继续？',
      '替换 OCR 原文',
      {
        type: 'warning',
        confirmButtonText: '替换',
        cancelButtonText: '取消'
      }
    )
    return true
  } catch (err) {
    return false
  }
}

const handleFileChange = async (file: any) => {
  if (isRecycleMode.value) return
  if (!props.snippet?.id) {
    ElMessage.warning('请先选择知识片段')
    return
  }
  const rawFile = file.raw as File | undefined
  if (!rawFile) {
    ElMessage.warning('未读取到上传文件')
    return
  }
  if (!(await confirmReplaceOcrIfNeeded(rawFile))) {
    return
  }

  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', rawFile)
    if (props.snippet.ocrTextVersion !== undefined && props.snippet.ocrTextVersion !== null) {
      formData.append('ocrTextVersion', String(props.snippet.ocrTextVersion))
    }
    const res = await apiClient.post(`/ocr/upload?snippetId=${props.snippet.id}`, formData)
    const result = res.data
    if (result.status === 'TEXT_EXTRACTED') {
      ocrText.value = result.text || ''
      ocrCollapsed.value = false
      ElMessage.success(result.message || '文件内容已读取到 OCR 原文区')
      emit('updated')
      return
    }
    ElMessage.success(result.message || '图片上传成功，OCR 正在后台识别，稍后刷新或切换片段查看结果')
    if (fileDialogVisible.value) {
      await loadSnippetFiles()
    }
  } catch (err: any) {
    ElMessage.error(getErrorMessage(err, '文件上传失败，请检查后端服务状态'))
  } finally {
    uploading.value = false
  }
}

const loadSnippetFiles = async () => {
  if (!props.snippet?.id) return
  try {
    const res = await apiClient.get(`/snippets/${props.snippet.id}/files`)
    snippetFiles.value = res.data
  } catch (err) {
    ElMessage.error('文件列表加载失败')
  }
}

const openFileDialog = async () => {
  fileDialogVisible.value = true
  await loadSnippetFiles()
}

const formatDateTime = (value?: string) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}

const shouldUsePreviewPage = (file: any) => {
  const filename = (file?.originalFilename || '').toLowerCase()
  return filename.endsWith('.doc') || filename.endsWith('.docx') || filename.endsWith('.ofd')
}

const viewFile = (file: any) => {
  if (!file?.url) return
  if (shouldUsePreviewPage(file) && props.snippet?.id && file.id) {
    window.open(`/preview/snippets/${props.snippet.id}/files/${file.id}`, '_blank', 'noopener,noreferrer')
    return
  }
  window.open(file.url, '_blank', 'noopener,noreferrer')
}

const downloadFile = (file: any) => {
  if (!file?.url) return
  const link = document.createElement('a')
  link.href = file.url
  link.download = file.originalFilename || file.storedFilename || 'upload-file'
  link.rel = 'noopener noreferrer'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

const saveDetail = async () => {
  if (isRecycleMode.value || !props.snippet?.id) return
  try {
    await apiClient.put(`/snippets/${props.snippet.id}`, {
      title: title.value,
      ocrText: ocrText.value,
      noteContent: noteContent.value
    })
    ElMessage.success('保存成功')
    emit('updated')
  } catch (err) {
    ElMessage.error('保存失败')
  }
}

const handlePin = async () => {
  if (isRecycleMode.value || !props.snippet?.id) return
  try {
    await apiClient.post(`/snippets/${props.snippet.id}/toggle-pin`)
    ElMessage.success('置顶状态已更新')
    emit('updated')
  } catch (err) {
    ElMessage.error('操作失败')
  }
}

const handleMastered = async () => {
  if (isRecycleMode.value || !props.snippet?.id) return
  try {
    await apiClient.post(`/snippets/${props.snippet.id}/toggle-mastered`)
    ElMessage.success(props.snippet.isMastered ? '已设为活跃' : '已标记为掌握')
    emit('updated')
  } catch (err) {
    ElMessage.error('操作失败')
  }
}

const handleDelete = () => {
  if (isRecycleMode.value || !props.snippet?.id) return
  ElMessageBox.confirm('确定要将这个知识片段移入回收站吗？', '提示', {
    type: 'warning',
    confirmButtonText: '移入回收站',
    cancelButtonText: '取消'
  }).then(async () => {
    try {
      await apiClient.delete(`/snippets/${props.snippet.id}`)
      ElMessage.success('已移入回收站')
      emit('updated')
    } catch (err) {
      ElMessage.error('删除失败')
    }
  })
}

const handleRestore = async () => {
  if (!props.snippet?.id) return
  try {
    await apiClient.post(`/snippets/${props.snippet.id}/restore`)
    ElMessage.success('恢复成功')
    emit('updated')
  } catch (err) {
    ElMessage.error('恢复失败')
  }
}

const handlePermanentDelete = () => {
  if (!props.snippet?.id) return
  ElMessageBox.confirm('永久删除后不可恢复，确定继续吗？', '永久删除片段', {
    type: 'error',
    confirmButtonText: '永久删除',
    cancelButtonText: '取消'
  }).then(async () => {
    try {
      await apiClient.delete(`/snippets/${props.snippet.id}/permanent`)
      ElMessage.success('已永久删除')
      emit('updated')
    } catch (err) {
      ElMessage.error('永久删除失败')
    }
  })
}
</script>

<style scoped>
.double-editor {
  display: flex;
  flex-direction: column;
  gap: 15px;
  height: 100%;
  min-height: 0;
}
.editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 5px;
}
.editor-header h3 {
  margin: 0;
  font-size: 18px;
}
.recycle-tip {
  padding: 8px 12px;
  border-radius: 6px;
  color: #b88230;
  background: #fdf6ec;
  font-size: 13px;
}
.title-row {
  display: flex;
  align-items: center;
}
.primary-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.header-tools {
  display: flex;
  align-items: center;
  gap: 15px;
  color: #909399;
}
.header-tools .el-icon {
  cursor: pointer;
  transition: color 0.3s;
}
.header-tools .el-icon.active {
  color: #409eff;
}
.header-tools .el-icon:hover {
  color: #333;
}
.file-table {
  width: 100%;
}
.file-name {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}
.section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.note-section {
  flex: 1;
  min-height: 0;
}
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: #606266;
  font-weight: bold;
}
.collapsible-header {
  cursor: pointer;
  user-select: none;
}
.editor-tabs {
  flex: 1;
  min-height: 0;
  border-radius: 4px;
}
:deep(.editor-tabs .el-tabs__content) {
  height: calc(100% - 30px);
  padding: 10px;
  overflow: hidden;
}
:deep(.editor-tabs .el-tab-pane) {
  height: 100%;
}
:deep(.note-input) {
  height: 100%;
}
:deep(.note-input .el-textarea__inner) {
  height: 100%;
  min-height: 180px;
  resize: vertical;
}
:deep(.editor-tabs .el-tabs__item) {
  font-size: 12px;
  height: 30px;
  line-height: 30px;
}
.markdown-body {
  height: 100%;
  overflow: auto;
  padding: 10px;
  background: white;
  font-size: 14px;
  line-height: 1.6;
}
:deep(.markdown-body h1) { font-size: 1.5em; }
:deep(.markdown-body h2) { font-size: 1.3em; }
:deep(.markdown-body code) { background: #f0f0f0; padding: 2px 4px; border-radius: 3px; }
</style>

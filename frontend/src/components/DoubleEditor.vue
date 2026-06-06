<template>
  <div v-if="snippet" class="double-editor">
    <div class="editor-header">
      <div class="primary-actions">
        <el-button size="small" type="primary" @click="saveDetail" :disabled="!snippet">保存</el-button>
        <el-upload
          action="#"
          :auto-upload="false"
          :show-file-list="false"
          @change="handleImageChange"
        >
          <el-button size="small" type="primary" plain>上传</el-button>
        </el-upload>
        <el-button size="small" @click="openImageDialog">查看图片</el-button>
      </div>
      <div class="header-tools">
        <el-button size="small" text bg @click="emit('toggle-fullscreen')">
          {{ fullscreen ? '退出全屏' : '全屏编辑' }}
        </el-button>
        <el-tooltip content="置顶" placement="top">
          <el-icon :class="{ active: snippet.isPinned }" @click="handlePin"><PriceTag /></el-icon>
        </el-tooltip>
        <el-tooltip content="标记掌握" placement="top">
          <el-icon :class="{ active: snippet.isMastered }" @click="handleMastered"><CircleCheck /></el-icon>
        </el-tooltip>
        <el-tooltip content="删除" placement="top">
          <el-icon @click="handleDelete"><Delete /></el-icon>
        </el-tooltip>
      </div>
    </div>

    <div class="title-row">
      <el-input
        v-model="title"
        type="textarea"
        :rows="1"
        resize="vertical"
        placeholder="请输入片段标题"
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
        :disabled="!snippet"
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
            :disabled="!snippet"
          />
        </el-tab-pane>
        <el-tab-pane label="预览">
          <div class="markdown-body" v-html="renderedMarkdown"></div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="imageDialogVisible" title="OCR 图片" width="720px">
      <el-empty v-if="snippetImages.length === 0" description="暂无上传图片" />
      <div v-else class="image-grid">
        <el-image
          v-for="image in snippetImages"
          :key="image.id"
          class="image-thumb"
          :src="image.url"
          :preview-src-list="imagePreviewUrls"
          :initial-index="imagePreviewUrls.indexOf(image.url)"
          fit="cover"
          preview-teleported
        />
      </div>
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
import axios from 'axios'

const props = defineProps<{
  snippet?: any
  fullscreen?: boolean
}>()

const emit = defineEmits(['updated', 'toggle-fullscreen'])

const md = new MarkdownIt()
const title = ref('')
const ocrText = ref('')
const noteContent = ref('')
const imageDialogVisible = ref(false)
const snippetImages = ref<any[]>([])
const ocrCollapsed = ref(true)

// 监听 prop 变化并同步到本地 ref
watch(() => props.snippet, (newVal) => {
  if (newVal) {
    title.value = newVal.title || ''
    ocrText.value = newVal.ocrText || ''
    noteContent.value = newVal.noteContent || ''
    snippetImages.value = []
    ocrCollapsed.value = !Boolean(newVal.ocrText)
  } else {
    title.value = ''
    ocrText.value = ''
    noteContent.value = ''
    snippetImages.value = []
    ocrCollapsed.value = true
  }
}, { immediate: true })

const renderedMarkdown = computed(() => {
  return md.render(noteContent.value || '*暂无内容*')
})

const imagePreviewUrls = computed(() => snippetImages.value.map(image => image.url))

const handleImageChange = async (file: any) => {
  if (!props.snippet?.id) {
    ElMessage.warning('请先选择知识片段')
    return
  }

  // 上传图片后立即返回，OCR 在后端后台识别并自动保存到当前片段
  try {
    const formData = new FormData()
    formData.append('file', file.raw)
    await axios.post(`/api/ocr/upload?snippetId=${props.snippet.id}`, formData)
    ElMessage.success('图片上传成功，OCR 正在后台识别，稍后刷新或切换片段查看结果')
    if (imageDialogVisible.value) {
      await loadSnippetImages()
    }
  } catch (err) {
    ElMessage.error('图片上传失败，请检查后端服务状态')
  }
}

const loadSnippetImages = async () => {
  if (!props.snippet?.id) return
  try {
    const res = await axios.get(`/api/snippets/${props.snippet.id}/images`)
    snippetImages.value = res.data
  } catch (err) {
    ElMessage.error('图片加载失败')
  }
}

const openImageDialog = async () => {
  imageDialogVisible.value = true
  await loadSnippetImages()
}

const saveDetail = async () => {
  if (!props.snippet?.id) return
  try {
    await axios.put(`/api/snippets/${props.snippet.id}`, {
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
  if (!props.snippet?.id) return
  try {
    await axios.post(`/api/snippets/${props.snippet.id}/toggle-pin`)
    ElMessage.success('置顶状态已更新')
    emit('updated')
  } catch (err) {
    ElMessage.error('操作失败')
  }
}

const handleMastered = async () => {
  if (!props.snippet?.id) return
  try {
    await axios.post(`/api/snippets/${props.snippet.id}/toggle-mastered`)
    ElMessage.success(props.snippet.isMastered ? '已设为活跃' : '已标记为掌握')
    emit('updated')
  } catch (err) {
    ElMessage.error('操作失败')
  }
}

const handleDelete = () => {
  if (!props.snippet?.id) return
  ElMessageBox.confirm('确定要删除这个知识片段吗？', '提示', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  }).then(async () => {
    try {
      await axios.delete(`/api/snippets/${props.snippet.id}`)
      ElMessage.success('删除成功')
      emit('updated')
    } catch (err) {
      ElMessage.error('删除失败')
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
.image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 12px;
}
.image-thumb {
  width: 120px;
  height: 120px;
  border-radius: 6px;
  cursor: pointer;
  background: #f0f2f5;
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

<template>
  <div v-if="snippet" class="double-editor">
    <div class="editor-header">
      <h3>编辑知识片段</h3>
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

    <!-- 图片展示区 -->
    <div class="image-preview">
      <img v-if="imageUrl" :src="imageUrl" />
      <el-upload
        v-else
        drag
        action="#"
        :auto-upload="false"
        @change="handleImageChange"
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">拖拽或点击上传图片</div>
      </el-upload>
    </div>

    <!-- OCR 原文区 -->
    <div class="section">
      <div class="section-header">
        <span>OCR 原文 (系统清洗后)</span>
        <el-button size="small" type="primary" plain @click="saveOcr" :disabled="!snippet">保存原文修改</el-button>
      </div>
      <el-input
        v-model="ocrText"
        type="textarea"
        :rows="4"
        placeholder="OCR 识别结果将在此显示..."
        :disabled="!snippet"
      />
    </div>

    <!-- 个人笔记区 -->
    <div class="section">
      <div class="section-header">
        <span>我的个人内化笔记 (MARKDOWN)</span>
        <el-button size="small" type="primary" @click="saveNote" :disabled="!snippet">保存个人笔记</el-button>
      </div>
      <el-tabs type="border-card" class="editor-tabs">
        <el-tab-pane label="编辑">
          <el-input
            v-model="noteContent"
            type="textarea"
            :rows="10"
            placeholder="输入您的思考与总结..."
            :disabled="!snippet"
          />
        </el-tab-pane>
        <el-tab-pane label="预览">
          <div class="markdown-body" v-html="renderedMarkdown"></div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
  <div v-else class="empty-state">
    <el-empty description="请选择一个知识片段进行编辑" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { PriceTag, CircleCheck, Delete, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import MarkdownIt from 'markdown-it'
import axios from 'axios'

const props = defineProps<{
  snippet?: any
  fullscreen?: boolean
}>()

const emit = defineEmits(['updated', 'toggle-fullscreen'])

const md = new MarkdownIt()
const imageUrl = ref('')
const ocrText = ref('')
const noteContent = ref('')

// 监听 prop 变化并同步到本地 ref
watch(() => props.snippet, (newVal) => {
  if (newVal) {
    ocrText.value = newVal.ocrText || ''
    noteContent.value = newVal.noteContent || ''
    imageUrl.value = newVal.imagePath || ''
  } else {
    ocrText.value = ''
    noteContent.value = ''
    imageUrl.value = ''
  }
}, { immediate: true })

const renderedMarkdown = computed(() => {
  return md.render(noteContent.value || '*暂无内容*')
})

const handleImageChange = async (file: any) => {
  imageUrl.value = URL.createObjectURL(file.raw)
  ElMessage.success('图片上传成功，正在识别...')
  
  // 调用后端中转 OCR 服务
  try {
    const formData = new FormData()
    formData.append('file', file.raw)
    const res = await axios.post('/api/ocr/upload', formData)
    ocrText.value = res.data
    ElMessage.success('OCR 识别完成')
  } catch (err) {
    ElMessage.error('OCR 服务调用失败，请检查后端服务及 OCR Server 状态')
  }
}

const saveOcr = async () => {
  if (!props.snippet?.id) return
  try {
    await axios.put(`/api/snippets/${props.snippet.id}/ocr`, ocrText.value, {
      headers: { 'Content-Type': 'text/plain' }
    })
    ElMessage.success('原文存档成功')
    emit('updated')
  } catch (err) {
    ElMessage.error('保存失败')
  }
}

const saveNote = async () => {
  if (!props.snippet?.id) return
  try {
    await axios.put(`/api/snippets/${props.snippet.id}/note`, noteContent.value, {
      headers: { 'Content-Type': 'text/plain' }
    })
    ElMessage.success('笔记存档成功')
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
.image-preview img {
  width: 100%;
  border-radius: 8px;
  max-height: 180px;
  object-fit: contain;
  background: #f0f2f5;
}
.section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: #606266;
  font-weight: bold;
}
.editor-tabs {
  border-radius: 4px;
}
.markdown-body {
  min-height: 200px;
  padding: 10px;
  background: white;
  font-size: 14px;
  line-height: 1.6;
}
:deep(.markdown-body h1) { font-size: 1.5em; }
:deep(.markdown-body h2) { font-size: 1.3em; }
:deep(.markdown-body code) { background: #f0f0f0; padding: 2px 4px; border-radius: 3px; }
</style>

<template>
  <el-container class="main-layout">
    <!-- 左侧导航栏 -->
    <el-aside width="280px" class="aside">
      <div class="logo-header">
        <div class="logo">
          <el-icon><Monitor /></el-icon>
          <span>FlashBrain</span>
        </div>
        <el-button class="add-subject-btn" circle size="small" @click="addSubject">
          <el-icon><Plus /></el-icon>
        </el-button>
      </div>
      <el-menu :default-active="activeSubjectId ? '1-' + activeSubjectId : '1'" class="menu" @select="handleMenuSelect">
        <el-sub-menu index="1">
          <template #title>
            <el-icon><Folder /></el-icon>
            <span>我的学科体系</span>
          </template>
          <el-menu-item 
            v-for="sub in subjects" 
            :key="sub.id" 
            :index="'1-' + sub.id"
          >
            # {{ sub.name }}
          </el-menu-item>
          <el-menu-item v-if="subjects.length === 0" index="0" disabled>
            暂无科目，请点击 + 添加
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
      <div class="storage-info">
        存储: 1.2GB / 10GB
      </div>
    </el-aside>

    <!-- 中间内容流 -->
    <el-main class="content-stream">
      <div class="header-actions">
        <el-input placeholder="搜索关键词 (穿透归档)..." class="search-input">
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <div class="header-right">
          <span>已掌握内容</span>
          <el-switch v-model="showMastered" />
          <el-button type="primary" class="add-btn" :disabled="!activeSubjectId" @click="addSnippet">
            <el-icon><Plus /></el-icon> 记一下
          </el-button>
        </div>
      </div>

      <div class="snippet-list">
        <el-card 
          v-for="s in filteredSnippetList" 
          :key="s.id" 
          :class="['snippet-card', { active: currentSnippet?.id === s.id, mastered: s.isMastered }]" 
          shadow="hover"
          @click="currentSnippet = s"
        >
          <div class="snippet-content">
            <img :src="s.imagePath || 'https://via.placeholder.com/60'" class="snippet-img" />
            <div class="snippet-info">
              <h3>{{ s.title }} <el-tag v-if="s.isPinned" size="small">置顶</el-tag></h3>
              <p class="ocr-preview">OCR原文: {{ s.ocrText?.substring(0, 50) }}...</p>
              <span class="meta">#{{ activeSubjectName }}</span>
            </div>
            <el-icon v-if="s.isPinned" class="pin-icon"><MapLocation /></el-icon>
          </div>
        </el-card>
        <div v-if="filteredSnippetList.length === 0" class="empty-state">
           请选择一个科目，并点击“记一下”开始记录
        </div>
      </div>
    </el-main>

    <!-- 右侧编辑器 -->
    <el-aside width="450px" class="editor-aside">
      <slot name="editor" :snippet="currentSnippet" :fetch-snippets="fetchSnippets"></slot>
    </el-aside>
  </el-container>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { Monitor, Folder, Search, Plus, MapLocation } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import axios from 'axios'

const showMastered = ref(true)
const subjects = ref<any[]>([])
const activeSubjectId = ref<number | null>(null)
const snippetList = ref<any[]>([])
const currentSnippet = ref<any>(null)

const filteredSnippetList = computed(() => {
  if (showMastered.value) {
    return snippetList.value
  }
  return snippetList.value.filter(s => !s.isMastered)
})

const activeSubjectName = computed(() => {
  return subjects.value.find(s => s.id === activeSubjectId.value)?.name || ''
})

const fetchSubjects = async () => {
  try {
    const res = await axios.get('http://localhost:8080/api/subjects')
    subjects.value = res.data
  } catch (err) {
    console.error('加载科目失败', err)
  }
}

const fetchSnippets = async () => {
  if (!activeSubjectId.value) return
  try {
    const res = await axios.get(`http://localhost:8080/api/snippets/subject/${activeSubjectId.value}`)
    snippetList.value = res.data
  } catch (err) {
    console.error('加载碎片失败', err)
  }
}

const handleMenuSelect = (index: string) => {
  const id = parseInt(index.replace('1-', ''))
  if (!isNaN(id)) {
    activeSubjectId.value = id
    fetchSnippets()
  }
}

const addSubject = () => {
  ElMessageBox.prompt('请输入新科目的名称', '添加分类', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPattern: /.+/,
    inputErrorMessage: '名称不能为空',
  }).then(async ({ value }) => {
    try {
      await axios.post('http://localhost:8080/api/subjects', {
        name: value,
        parentId: null
      })
      ElMessage.success(`成功创建科目: ${value}`)
      fetchSubjects()
    } catch (err) {
      ElMessage.error('创建失败')
    }
  })
}

const addSnippet = () => {
  if (!activeSubjectId.value) return
  ElMessageBox.prompt('请输入片段标题', '记一下', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPattern: /.+/,
    inputErrorMessage: '标题不能为空',
  }).then(async ({ value }) => {
    try {
      const res = await axios.post('http://localhost:8080/api/snippets', {
        title: value,
        subjectId: activeSubjectId.value,
        ocrText: '',
        noteContent: ''
      })
      ElMessage.success(`成功创建片段: ${value}`)
      fetchSnippets()
      currentSnippet.value = res.data
    } catch (err) {
      ElMessage.error('创建失败')
    }
  })
}

onMounted(() => {
  fetchSubjects()
})
</script>

<style scoped>
.main-layout {
  height: 100vh;
  background-color: #f5f7fa;
}
.aside {
  background-color: #1a1c1e;
  color: white;
  display: flex;
  flex-direction: column;
}
.logo-header {
  padding: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.logo {
  font-size: 20px;
  font-weight: bold;
  display: flex;
  align-items: center;
  gap: 10px;
}
.add-subject-btn {
  background: rgba(255, 255, 255, 0.1);
  border: none;
  color: white;
}
.add-subject-btn:hover {
  background: rgba(255, 255, 255, 0.2);
}
.menu {
  border-right: none;
  background-color: transparent;
  flex: 1;
}
:deep(.el-sub-menu__title) {
  color: #909399;
}
:deep(.el-menu-item) {
  color: #cfd3dc;
}
:deep(.el-menu-item.is-active) {
  background-color: #409eff;
  color: white;
}
.storage-info {
  padding: 20px;
  font-size: 12px;
  color: #606266;
  border-top: 1px solid #333;
}
.content-stream {
  padding: 20px;
  overflow-y: auto;
}
.header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.search-input {
  width: 400px;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 15px;
  font-size: 14px;
}
.snippet-card {
  margin-bottom: 15px;
  cursor: pointer;
  border-radius: 8px;
}
.snippet-card.active {
  border-left: 4px solid #409eff;
}
.snippet-card.mastered {
  opacity: 0.6;
  filter: grayscale(1);
}
.snippet-card.mastered h3,
.snippet-card.mastered p {
  text-decoration: line-through;
  color: #909399;
}
.snippet-content {
  display: flex;
  gap: 15px;
  position: relative;
}
.snippet-img {
  width: 60px;
  height: 60px;
  border-radius: 4px;
  object-fit: cover;
}
.snippet-info h3 {
  margin: 0 0 5px 0;
  font-size: 16px;
}
.ocr-preview {
  margin: 0;
  font-size: 13px;
  color: #606266;
}
.meta {
  font-size: 12px;
  color: #909399;
}
.pin-icon {
  position: absolute;
  top: 0;
  right: 0;
  color: #409eff;
}
.editor-aside {
  background-color: white;
  border-left: 1px solid #e4e7ed;
  padding: 20px;
}
</style>

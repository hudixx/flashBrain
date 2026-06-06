<template>
  <el-container :class="['main-layout', { 'editor-fullscreen': isEditorFullscreen }]">
    <!-- 左侧导航栏 -->
    <el-aside
      v-if="!isEditorFullscreen"
      :width="`${currentAsideWidth}px`"
      :class="['aside', { collapsed: isSubjectCollapsed }]"
    >
      <template v-if="!isSubjectCollapsed">
        <div class="logo-header">
          <div class="logo">
            <el-icon><Monitor /></el-icon>
            <span>FlashBrain</span>
          </div>
          <div class="subject-tools">
            <el-button class="collapse-subject-btn" circle size="small" title="收缩科目栏" @click="toggleSubjectCollapsed">
              <el-icon><Fold /></el-icon>
            </el-button>
            <el-button class="add-subject-btn" circle size="small" @click="addSubject">
              <el-icon><Plus /></el-icon>
            </el-button>
          </div>
        </div>
        <div class="user-bar">
          <span class="user-name">{{ authStore.user?.displayName || authStore.user?.username || '当前用户' }}</span>
          <el-button class="logout-btn" size="small" text @click="logout">
            <el-icon><SwitchButton /></el-icon>
            退出
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
      </template>

      <template v-else>
        <div class="collapsed-rail">
          <el-tooltip content="FlashBrain" placement="right">
            <div class="collapsed-logo">
              <el-icon><Monitor /></el-icon>
            </div>
          </el-tooltip>
          <el-tooltip content="展开科目栏" placement="right">
            <el-button class="rail-button" circle size="small" @click="toggleSubjectCollapsed">
              <el-icon><Expand /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip content="添加科目" placement="right">
            <el-button class="rail-button" circle size="small" @click="addSubject">
              <el-icon><Plus /></el-icon>
            </el-button>
          </el-tooltip>
        </div>
      </template>
    </el-aside>

    <!-- 中间内容流 -->
    <el-main
      v-if="!isEditorFullscreen"
      class="content-stream"
      :style="{ width: `${snippetPanelWidth}px` }"
    >
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
            <div class="snippet-info">
              <h3 class="snippet-title">
                <span class="title-text">{{ s.title }}</span>
                <el-tag v-if="s.isPinned" size="small">置顶</el-tag>
              </h3>
            </div>
            <el-tooltip content="全屏查看" placement="top">
              <el-button
                class="snippet-fullscreen-btn"
                circle
                size="small"
                @click="openSnippetFullscreen(s, $event)"
              >
                <el-icon><FullScreen /></el-icon>
              </el-button>
            </el-tooltip>
          </div>
        </el-card>
        <div v-if="filteredSnippetList.length === 0" class="empty-state">
           请选择一个科目，并点击“记一下”开始记录
        </div>
      </div>
    </el-main>

    <div
      v-if="!isEditorFullscreen"
      class="resize-handle"
      role="separator"
      aria-orientation="vertical"
      title="拖动调整详情区宽度"
      @mousedown="startResize"
    ></div>

    <!-- 右侧编辑器 -->
    <el-aside class="editor-aside">
      <slot
        name="editor"
        :snippet="currentSnippet"
        :fetch-snippets="fetchSnippets"
        :fullscreen="isEditorFullscreen"
        :toggle-fullscreen="toggleEditorFullscreen"
      ></slot>
    </el-aside>
  </el-container>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { SwitchButton, Monitor, Folder, Search, Plus, Fold, Expand, FullScreen } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import apiClient from '../api/client'
import { useAuthStore } from '../stores/auth'

const authStore = useAuthStore()
const router = useRouter()
const showMastered = ref(true)
const subjects = ref<any[]>([])
const activeSubjectId = ref<number | null>(null)
const snippetList = ref<any[]>([])
const currentSnippet = ref<any>(null)

const SNIPPET_PANEL_WIDTH_KEY = 'flashbrain.snippetPanelWidth'
const SUBJECT_COLLAPSED_KEY = 'flashbrain.subjectCollapsed'
const DEFAULT_SNIPPET_PANEL_WIDTH = 360
const MIN_SNIPPET_PANEL_WIDTH = 260
const MIN_EDITOR_WIDTH = 480
const ASIDE_WIDTH = 280
const COLLAPSED_ASIDE_WIDTH = 56
const RESIZE_HANDLE_WIDTH = 8

const isEditorFullscreen = ref(false)
const isSubjectCollapsed = ref(false)
const snippetPanelWidth = ref(DEFAULT_SNIPPET_PANEL_WIDTH)
const isResizing = ref(false)

const filteredSnippetList = computed(() => {
  if (showMastered.value) {
    return snippetList.value
  }
  return snippetList.value.filter(s => !s.isMastered)
})

const currentAsideWidth = computed(() => {
  return isSubjectCollapsed.value ? COLLAPSED_ASIDE_WIDTH : ASIDE_WIDTH
})

const loadSubjectCollapsed = () => {
  const savedValue = window.localStorage.getItem(SUBJECT_COLLAPSED_KEY)
  isSubjectCollapsed.value = savedValue === 'true'
}

const saveSubjectCollapsed = () => {
  window.localStorage.setItem(SUBJECT_COLLAPSED_KEY, String(isSubjectCollapsed.value))
}

const toggleSubjectCollapsed = () => {
  isSubjectCollapsed.value = !isSubjectCollapsed.value
  saveSubjectCollapsed()
}

const getMaxSnippetPanelWidth = () => {
  if (typeof window === 'undefined') return DEFAULT_SNIPPET_PANEL_WIDTH
  return Math.max(
    MIN_SNIPPET_PANEL_WIDTH,
    window.innerWidth - currentAsideWidth.value - RESIZE_HANDLE_WIDTH - MIN_EDITOR_WIDTH
  )
}

const normalizeSnippetPanelWidth = (value: number) => {
  if (!Number.isFinite(value)) return DEFAULT_SNIPPET_PANEL_WIDTH
  const maxWidth = getMaxSnippetPanelWidth()
  return Math.min(Math.max(value, MIN_SNIPPET_PANEL_WIDTH), maxWidth)
}

const loadSnippetPanelWidth = () => {
  const savedValue = window.localStorage.getItem(SNIPPET_PANEL_WIDTH_KEY)
  const parsedValue = savedValue ? Number(savedValue) : DEFAULT_SNIPPET_PANEL_WIDTH
  snippetPanelWidth.value = normalizeSnippetPanelWidth(parsedValue)
}

const saveSnippetPanelWidth = () => {
  window.localStorage.setItem(SNIPPET_PANEL_WIDTH_KEY, String(snippetPanelWidth.value))
}

const fetchSubjects = async () => {
  try {
    const res = await apiClient.get('/subjects')
    subjects.value = res.data
  } catch (err) {
    console.error('加载科目失败', err)
  }
}

const fetchSnippets = async () => {
  if (!activeSubjectId.value) return
  try {
    const res = await apiClient.get(`/snippets/subject/${activeSubjectId.value}`)
    snippetList.value = res.data
    if (currentSnippet.value && !snippetList.value.some(s => s.id === currentSnippet.value.id)) {
      currentSnippet.value = null
      isEditorFullscreen.value = false
    }
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
      await apiClient.post('/subjects', {
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
      const res = await apiClient.post('/snippets', {
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

const logout = () => {
  authStore.logout()
  router.push('/login')
}

const stopResize = () => {
  if (!isResizing.value) return
  isResizing.value = false
  document.body.classList.remove('is-resizing-layout')
  saveSnippetPanelWidth()
}

const handleResize = (event: MouseEvent) => {
  if (!isResizing.value) return
  snippetPanelWidth.value = normalizeSnippetPanelWidth(event.clientX - currentAsideWidth.value)
}

const startResize = () => {
  isResizing.value = true
  document.body.classList.add('is-resizing-layout')
}

const toggleEditorFullscreen = () => {
  isEditorFullscreen.value = !isEditorFullscreen.value
}

const openSnippetFullscreen = (snippet: any, event?: MouseEvent) => {
  event?.stopPropagation()
  currentSnippet.value = snippet
  isEditorFullscreen.value = true
}

const handleWindowResize = () => {
  snippetPanelWidth.value = normalizeSnippetPanelWidth(snippetPanelWidth.value)
}

onMounted(() => {
  fetchSubjects()
  loadSubjectCollapsed()
  loadSnippetPanelWidth()
  window.addEventListener('mousemove', handleResize)
  window.addEventListener('mouseup', stopResize)
  window.addEventListener('resize', handleWindowResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('mousemove', handleResize)
  window.removeEventListener('mouseup', stopResize)
  window.removeEventListener('resize', handleWindowResize)
  document.body.classList.remove('is-resizing-layout')
})
</script>

<style scoped>
.main-layout {
  height: 100vh;
  background-color: #f5f7fa;
  overflow: hidden;
}
.main-layout.editor-fullscreen {
  display: block;
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
.user-bar {
  margin: 0 20px 14px;
  padding: 10px 12px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  background: rgba(255, 255, 255, 0.08);
}
.user-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #cfd3dc;
  font-size: 13px;
}
.logout-btn {
  color: #cfd3dc;
}
.logout-btn:hover {
  color: white;
}
.subject-tools {
  display: flex;
  align-items: center;
  gap: 8px;
}
.add-subject-btn,
.collapse-subject-btn,
.rail-button {
  background: rgba(255, 255, 255, 0.1);
  border: none;
  color: white;
}
.add-subject-btn:hover,
.collapse-subject-btn:hover,
.rail-button:hover {
  background: rgba(255, 255, 255, 0.2);
  color: white;
}
.aside.collapsed {
  align-items: center;
}
.collapsed-rail {
  width: 100%;
  padding: 18px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}
.collapsed-logo {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  background: rgba(255, 255, 255, 0.1);
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
  flex: none;
  padding: 20px;
  overflow-y: auto;
  min-width: 260px;
  max-width: calc(100vw - 280px - 8px - 480px);
}
.header-actions {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 12px;
  margin-bottom: 20px;
}
.search-input {
  width: 100%;
}
.header-right {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 14px;
}
.snippet-card {
  margin-bottom: 8px;
  cursor: pointer;
  border-radius: 8px;
}
.snippet-card :deep(.el-card__body) {
  padding: 10px 12px;
}
.snippet-card.active {
  border-left: 4px solid #409eff;
}
.snippet-card.mastered {
  opacity: 0.6;
  filter: grayscale(1);
}
.snippet-card.mastered h3 {
  text-decoration: line-through;
  color: #909399;
}
.snippet-content {
  display: flex;
  align-items: center;
  gap: 8px;
  position: relative;
  min-height: 28px;
  padding-right: 34px;
}
.snippet-info {
  min-width: 0;
  flex: 1;
}
.snippet-title {
  margin: 0;
  font-size: 15px;
  line-height: 22px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.title-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.snippet-fullscreen-btn {
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 24px;
  height: 24px;
  min-height: 24px;
  padding: 0;
  color: #409eff;
}
.editor-aside {
  flex: 1;
  width: auto;
  min-width: 480px;
  background-color: white;
  border-left: 1px solid #e4e7ed;
  padding: 20px;
  overflow-y: auto;
}
.editor-fullscreen .editor-aside {
  width: 100vw;
  min-width: 0;
  height: 100vh;
  border-left: none;
  box-sizing: border-box;
}
.resize-handle {
  flex: 0 0 8px;
  cursor: col-resize;
  background-color: #f0f2f5;
  border-left: 1px solid #e4e7ed;
  border-right: 1px solid #e4e7ed;
  transition: background-color 0.2s;
}
.resize-handle:hover {
  background-color: #d9ecff;
}
:global(body.is-resizing-layout) {
  cursor: col-resize;
  user-select: none;
}
</style>

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
          <div
            class="logo"
            role="button"
            tabindex="0"
            title="收缩科目栏"
            @click="toggleSubjectCollapsed"
            @keydown.enter.prevent="toggleSubjectCollapsed"
            @keydown.space.prevent="toggleSubjectCollapsed"
          >
            <el-icon><Monitor /></el-icon>
            <span>FlashBrain</span>
          </div>
          <div class="subject-tools">
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
        <el-menu :default-active="menuActiveIndex" class="menu" @select="handleMenuSelect" @open="handleMenuOpen">
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
              <div class="subject-menu-item">
                <span class="subject-name"># {{ sub.name }}</span>
                <span class="subject-actions">
                  <el-button size="small" text title="编辑科目" @click.stop="editSubject(sub)">
                    <el-icon><Edit /></el-icon>
                  </el-button>
                  <el-button size="small" text title="删除科目" @click.stop="deleteSubject(sub)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </span>
              </div>
            </el-menu-item>
            <el-menu-item v-if="subjects.length === 0" index="0" disabled>
              暂无科目，请点击 + 添加
            </el-menu-item>
          </el-sub-menu>
          <el-sub-menu index="recycle">
            <template #title>
              <el-icon><Delete /></el-icon>
              <span>回收站</span>
            </template>
            <el-menu-item
              v-for="sub in recycleSubjects"
              :key="'recycle-' + sub.id"
              :index="'recycle-' + sub.id"
            >
              <div class="subject-menu-item recycle-subject-menu-item">
                <span class="subject-name">· {{ sub.name }}</span>
                <span class="subject-actions">
                  <el-button v-if="sub.isDeleted" size="small" text title="恢复科目" @click.stop="restoreSubject(sub)">
                    恢复
                  </el-button>
                  <el-button v-if="sub.isDeleted" size="small" text title="永久删除科目" @click.stop="permanentDeleteSubject(sub)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </span>
              </div>
            </el-menu-item>
            <el-menu-item v-if="recycleSubjects.length === 0" index="recycle-empty" disabled>
              回收站为空
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
            <div
              class="collapsed-logo"
              role="button"
              tabindex="0"
              title="展开科目栏"
              @click="toggleSubjectCollapsed"
              @keydown.enter.prevent="toggleSubjectCollapsed"
              @keydown.space.prevent="toggleSubjectCollapsed"
            >
              <el-icon><Monitor /></el-icon>
            </div>
          </el-tooltip>
          <el-tooltip content="添加科目" placement="right">
            <el-button class="rail-button" circle size="small" @click="addSubject">
              <el-icon><Plus /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip content="回收站" placement="right">
            <el-button class="rail-button" circle size="small" @click="openRecycleBin">
              <el-icon><Delete /></el-icon>
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
        <el-input :placeholder="headerPlaceholder" class="search-input">
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <div class="header-right">
          <template v-if="viewMode === 'normal'">
            <el-button
              v-if="selectedSnippetIds.length > 0"
              type="danger"
              plain
              size="small"
              @click="batchDeleteSelectedSnippets"
            >
              删除已选 {{ selectedSnippetIds.length }} 项
            </el-button>
            <span>已掌握内容</span>
            <el-switch v-model="showMastered" />
            <el-button type="primary" class="add-btn" :disabled="!activeSubjectId" @click="addSnippet">
              <el-icon><Plus /></el-icon> 记一下
            </el-button>
          </template>
          <template v-else-if="viewMode === 'recycleSubjects'">
            <span class="mode-title">回收站</span>
            <el-button size="small" @click="refreshCurrentView">刷新</el-button>
          </template>
          <template v-else>
            <el-button size="small" @click="backToRecycleSubjects">返回回收站</el-button>
            <span class="mode-title">{{ activeRecycleSubject?.name || '已删除片段' }}</span>
          </template>
        </div>
      </div>

      <div v-if="viewMode === 'normal'" class="snippet-list">
        <el-card
          v-for="s in filteredSnippetList"
          :key="s.id"
          :class="['snippet-card', { active: currentSnippet?.id === s.id, mastered: s.isMastered }]"
          shadow="hover"
          @click="selectSnippet(s)"
        >
          <div class="snippet-content">
            <el-checkbox
              class="snippet-checkbox"
              :model-value="selectedSnippetIds.includes(s.id)"
              size="small"
              @click.stop
              @change="checked => toggleSnippetSelection(s.id, Boolean(checked))"
            />
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

      <div v-else-if="viewMode === 'recycleSubjects'" class="snippet-list">
        <div class="empty-state">
          <template v-if="recycleSubjects.length === 0">
            回收站为空
          </template>
          <template v-else>
            请在左侧“回收站”下选择一个科目查看已删除片段
          </template>
        </div>
      </div>

      <div v-else class="snippet-list">
        <el-card
          v-for="s in recycleSnippetList"
          :key="s.id"
          :class="['snippet-card', 'recycle-card', { active: currentSnippet?.id === s.id }]"
          shadow="hover"
          @click="currentSnippet = s"
        >
          <div class="recycle-card-content">
            <div class="recycle-info">
              <h3 class="snippet-title">
                <span class="title-text">{{ s.title }}</span>
                <el-tag size="small" type="danger">已删除</el-tag>
              </h3>
              <div class="recycle-meta">删除时间：{{ formatDateTime(s.deletedAt) }}</div>
            </div>
            <div class="recycle-actions" @click.stop>
              <el-button size="small" type="primary" text @click="restoreSnippet(s)">恢复</el-button>
              <el-button size="small" type="danger" text @click="permanentDeleteSnippet(s)">永久删除</el-button>
            </div>
          </div>
        </el-card>
        <div v-if="recycleSnippetList.length === 0" class="empty-state">
          该科目下暂无已删除片段
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
        :fetch-snippets="refreshCurrentView"
        :fullscreen="isEditorFullscreen"
        :toggle-fullscreen="toggleEditorFullscreen"
        :recycle-mode="viewMode === 'recycleSnippets'"
      ></slot>
    </el-aside>
  </el-container>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { SwitchButton, Monitor, Folder, Search, Plus, Expand, FullScreen, Edit, Delete } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import apiClient from '../api/client'
import { useAuthStore } from '../stores/auth'

type ViewMode = 'normal' | 'recycleSubjects' | 'recycleSnippets'

const authStore = useAuthStore()
const router = useRouter()
const showMastered = ref(true)
const subjects = ref<any[]>([])
const recycleSubjects = ref<any[]>([])
const activeSubjectId = ref<number | null>(null)
const activeRecycleSubject = ref<any | null>(null)
const snippetList = ref<any[]>([])
const recycleSnippetList = ref<any[]>([])
const currentSnippet = ref<any>(null)
const selectedSnippetIds = ref<number[]>([])
const viewMode = ref<ViewMode>('normal')

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

const menuActiveIndex = computed(() => {
  if (viewMode.value === 'recycleSnippets' && activeRecycleSubject.value?.id) {
    return `recycle-${activeRecycleSubject.value.id}`
  }
  if (viewMode.value === 'recycleSubjects') return 'recycle'
  return activeSubjectId.value ? `1-${activeSubjectId.value}` : '1'
})

const headerPlaceholder = computed(() => {
  if (viewMode.value === 'normal') return '搜索关键词 (穿透归档)...'
  if (viewMode.value === 'recycleSubjects') return '回收站科目列表'
  return '回收站片段列表'
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

const clearSnippetSelection = () => {
  selectedSnippetIds.value = []
}

const fetchSubjects = async () => {
  try {
    const res = await apiClient.get('/subjects')
    subjects.value = res.data
  } catch (err) {
    console.error('加载科目失败', err)
  }
}

const fetchRecycleSubjects = async () => {
  try {
    const res = await apiClient.get('/subjects/recycle')
    recycleSubjects.value = res.data
  } catch (err) {
    ElMessage.error('加载回收站失败')
  }
}

const fetchSnippets = async () => {
  if (!activeSubjectId.value) return
  try {
    const res = await apiClient.get(`/snippets/subject/${activeSubjectId.value}`)
    snippetList.value = res.data
    clearSnippetSelection()
    if (currentSnippet.value) {
      const latestSnippet = snippetList.value.find(s => s.id === currentSnippet.value.id)
      if (latestSnippet) {
        currentSnippet.value = latestSnippet
      } else {
        currentSnippet.value = null
        isEditorFullscreen.value = false
      }
    }
  } catch (err) {
    console.error('加载碎片失败', err)
  }
}

const fetchRecycleSnippets = async () => {
  if (!activeRecycleSubject.value?.id) return
  try {
    const res = await apiClient.get(`/snippets/recycle/subject/${activeRecycleSubject.value.id}`)
    recycleSnippetList.value = res.data
    if (currentSnippet.value) {
      const latestSnippet = recycleSnippetList.value.find(s => s.id === currentSnippet.value.id)
      if (latestSnippet) {
        currentSnippet.value = latestSnippet
      } else {
        currentSnippet.value = null
        isEditorFullscreen.value = false
      }
    }
  } catch (err) {
    ElMessage.error('加载回收站片段失败')
  }
}

const refreshCurrentView = async () => {
  await fetchSubjects()
  if (viewMode.value === 'normal') {
    await fetchSnippets()
    return
  }
  await fetchRecycleSubjects()
  if (viewMode.value === 'recycleSnippets') {
    await fetchRecycleSnippets()
  }
}

const openRecycleBin = async () => {
  viewMode.value = 'recycleSubjects'
  activeSubjectId.value = null
  activeRecycleSubject.value = null
  currentSnippet.value = null
  snippetList.value = []
  recycleSnippetList.value = []
  clearSnippetSelection()
  isEditorFullscreen.value = false
  await fetchRecycleSubjects()
}

const openRecycleSubject = async (subject: any) => {
  activeRecycleSubject.value = subject
  viewMode.value = 'recycleSnippets'
  currentSnippet.value = null
  recycleSnippetList.value = []
  await fetchRecycleSnippets()
}

const backToRecycleSubjects = async () => {
  viewMode.value = 'recycleSubjects'
  activeRecycleSubject.value = null
  currentSnippet.value = null
  recycleSnippetList.value = []
  await fetchRecycleSubjects()
}

const handleMenuOpen = (index: string) => {
  if (index === 'recycle') {
    openRecycleBin()
  }
}

const handleMenuSelect = (index: string) => {
  if (index === 'recycle') {
    openRecycleBin()
    return
  }
  if (index.startsWith('recycle-')) {
    const id = parseInt(index.replace('recycle-', ''))
    const subject = recycleSubjects.value.find(item => item.id === id)
    if (subject) {
      openRecycleSubject(subject)
    }
    return
  }
  const id = parseInt(index.replace('1-', ''))
  if (!isNaN(id)) {
    viewMode.value = 'normal'
    activeSubjectId.value = id
    activeRecycleSubject.value = null
    currentSnippet.value = null
    recycleSnippetList.value = []
    clearSnippetSelection()
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
      await fetchSubjects()
    } catch (err) {
      ElMessage.error('创建失败')
    }
  })
}

const editSubject = (subject: any) => {
  ElMessageBox.prompt('请输入新的科目名称', '编辑科目', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputValue: subject.name,
    inputPattern: /.+/,
    inputErrorMessage: '名称不能为空',
  }).then(async ({ value }) => {
    try {
      await apiClient.put(`/subjects/${subject.id}`, { name: value })
      ElMessage.success('科目名称已更新')
      await fetchSubjects()
    } catch (err) {
      ElMessage.error('更新失败')
    }
  })
}

const deleteSubject = (subject: any) => {
  ElMessageBox.confirm(`确定要将科目“${subject.name}”及其下知识片段移入回收站吗？`, '删除科目', {
    type: 'warning',
    confirmButtonText: '移入回收站',
    cancelButtonText: '取消'
  }).then(async () => {
    try {
      await apiClient.delete(`/subjects/${subject.id}`)
      ElMessage.success('科目已移入回收站')
      if (activeSubjectId.value === subject.id) {
        activeSubjectId.value = null
        snippetList.value = []
        currentSnippet.value = null
        clearSnippetSelection()
        isEditorFullscreen.value = false
      }
      await fetchSubjects()
    } catch (err) {
      ElMessage.error('删除失败')
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
      await fetchSnippets()
      currentSnippet.value = res.data
    } catch (err) {
      ElMessage.error('创建失败')
    }
  })
}

const toggleSnippetSelection = (id: number, checked: boolean) => {
  if (checked) {
    if (!selectedSnippetIds.value.includes(id)) {
      selectedSnippetIds.value = [...selectedSnippetIds.value, id]
    }
    return
  }
  selectedSnippetIds.value = selectedSnippetIds.value.filter(selectedId => selectedId !== id)
}

const batchDeleteSelectedSnippets = () => {
  if (selectedSnippetIds.value.length === 0) return
  ElMessageBox.confirm(`确定要将选中的 ${selectedSnippetIds.value.length} 个知识片段移入回收站吗？`, '批量删除', {
    type: 'warning',
    confirmButtonText: '移入回收站',
    cancelButtonText: '取消'
  }).then(async () => {
    try {
      const ids = [...selectedSnippetIds.value]
      await apiClient.post('/snippets/batch-delete', ids)
      if (currentSnippet.value && ids.includes(currentSnippet.value.id)) {
        currentSnippet.value = null
        isEditorFullscreen.value = false
      }
      clearSnippetSelection()
      ElMessage.success('已移入回收站')
      await fetchSnippets()
    } catch (err) {
      ElMessage.error('批量删除失败')
    }
  })
}

const restoreSubject = async (subject: any) => {
  try {
    await apiClient.post(`/subjects/${subject.id}/restore`)
    ElMessage.success('科目已恢复')
    await refreshCurrentView()
  } catch (err) {
    ElMessage.error('恢复失败')
  }
}

const permanentDeleteSubject = (subject: any) => {
  ElMessageBox.confirm(`永久删除科目“${subject.name}”及其下全部片段和文件？此操作不可恢复。`, '永久删除科目', {
    type: 'error',
    confirmButtonText: '永久删除',
    cancelButtonText: '取消'
  }).then(async () => {
    try {
      await apiClient.delete(`/subjects/${subject.id}/permanent`)
      ElMessage.success('已永久删除')
      if (activeRecycleSubject.value?.id === subject.id) {
        activeRecycleSubject.value = null
        currentSnippet.value = null
        viewMode.value = 'recycleSubjects'
      }
      await refreshCurrentView()
    } catch (err) {
      ElMessage.error('永久删除失败')
    }
  })
}

const restoreSnippet = async (snippet: any) => {
  try {
    await apiClient.post(`/snippets/${snippet.id}/restore`)
    ElMessage.success('片段已恢复')
    if (currentSnippet.value?.id === snippet.id) {
      currentSnippet.value = null
      isEditorFullscreen.value = false
    }
    await refreshCurrentView()
  } catch (err) {
    ElMessage.error('恢复失败')
  }
}

const permanentDeleteSnippet = (snippet: any) => {
  ElMessageBox.confirm(`永久删除知识片段“${snippet.title || '未命名'}”？此操作不可恢复。`, '永久删除片段', {
    type: 'error',
    confirmButtonText: '永久删除',
    cancelButtonText: '取消'
  }).then(async () => {
    try {
      await apiClient.delete(`/snippets/${snippet.id}/permanent`)
      ElMessage.success('已永久删除')
      if (currentSnippet.value?.id === snippet.id) {
        currentSnippet.value = null
        isEditorFullscreen.value = false
      }
      await refreshCurrentView()
    } catch (err) {
      ElMessage.error('永久删除失败')
    }
  })
}

const formatDateTime = (value?: string) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
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

const selectSnippet = async (snippet: any) => {
  try {
    const res = await apiClient.get(`/snippets/${snippet.id}`)
    currentSnippet.value = res.data
  } catch (err) {
    console.error('获取片段详情失败', err)
    ElMessage.warning('获取最新详情失败，使用本地缓存')
    currentSnippet.value = snippet
  }
}

const openSnippetFullscreen = async (snippet: any, event?: MouseEvent) => {
  event?.stopPropagation()
  await selectSnippet(snippet)
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
  cursor: pointer;
  border-radius: 6px;
  padding: 4px 6px;
  margin: -4px -6px;
  transition: background-color 0.2s;
}
.logo:hover,
.logo:focus-visible {
  background: rgba(255, 255, 255, 0.1);
  outline: none;
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
  cursor: pointer;
  transition: background-color 0.2s;
}
.collapsed-logo:hover,
.collapsed-logo:focus-visible {
  background: rgba(255, 255, 255, 0.2);
  outline: none;
}
.menu {
  border-right: none;
  background-color: transparent;
  flex: 1;
}
:deep(.menu.el-menu),
:deep(.menu .el-menu),
:deep(.el-sub-menu .el-menu) {
  background-color: #1a1c1e;
}
:deep(.el-sub-menu__title) {
  color: #cfd3dc;
  background-color: #1a1c1e;
}
:deep(.el-sub-menu__title:hover) {
  color: white;
  background-color: #24272a;
}
:deep(.el-menu-item) {
  color: #cfd3dc;
  background-color: #1f2225;
}
:deep(.el-menu-item:hover) {
  color: white;
  background-color: #2a2e33;
}
:deep(.el-menu-item.is-active) {
  background-color: #409eff;
  color: white;
}
.subject-menu-item {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.subject-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.subject-actions {
  display: none;
  align-items: center;
}
:deep(.el-menu-item:hover) .subject-actions,
:deep(.el-menu-item.is-active) .subject-actions {
  display: inline-flex;
}
.recycle-subject-menu-item .subject-actions {
  gap: 4px;
}
.subject-actions .el-button {
  color: inherit;
  padding: 2px;
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
.mode-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
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
.snippet-checkbox {
  flex: 0 0 auto;
}
.snippet-info,
.recycle-info {
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
.recycle-card-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.recycle-meta {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
.recycle-actions {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 4px;
}
.empty-state {
  color: #909399;
  font-size: 14px;
  text-align: center;
  padding: 40px 12px;
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

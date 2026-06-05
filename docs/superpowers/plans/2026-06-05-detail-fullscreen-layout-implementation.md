# Detail Fullscreen Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make FlashBrain's detail editor easier to read and edit by adding a resizable snippet list panel and a true fullscreen editor mode.

**Architecture:** `MainLayout.vue` owns page-level layout state: snippet panel width, drag handling, localStorage persistence, and fullscreen mode. `DoubleEditor.vue` stays focused on snippet editing and only exposes a `toggle-fullscreen` event plus a `fullscreen` prop. `App.vue` bridges scoped-slot props from `MainLayout` into `DoubleEditor`.

**Tech Stack:** Vue 3 `<script setup lang="ts">`, Element Plus, existing scoped CSS, browser `localStorage`, existing `npm run build` verification.

---

## Source Spec

- `docs/superpowers/specs/2026-06-05-detail-fullscreen-layout-design.md`

## Scope Check

The spec covers one cohesive frontend feature: improving the detail editor layout. It does not require backend, OCR, database, or API changes. This is suitable for one implementation plan.

## File Structure

Modify these files only:

- `frontend/src/App.vue`
  - Responsibility: pass `MainLayout` scoped-slot layout props/events into `DoubleEditor`.
- `frontend/src/layout/MainLayout.vue`
  - Responsibility: own shell layout, snippet list width, drag-to-resize behavior, fullscreen editor state, and persistence.
- `frontend/src/components/DoubleEditor.vue`
  - Responsibility: display the detail editor and emit a layout toggle request without knowing how the page layout is implemented.

No new files are required.

## Testing Strategy

The current frontend has no configured unit-test or lint scripts. Do not add a test framework for this focused UI change. Verification uses:

1. `npm run build` for Vue/TypeScript compile correctness.
2. Manual browser checks for drag resizing, localStorage persistence, fullscreen entry/exit, and existing editor actions.

Because the UI behavior is layout-heavy and the project currently lacks test infrastructure, each implementation task includes a build or manual checkpoint rather than introducing new test dependencies.

---

## Task 1: Wire fullscreen state through the editor slot

**Files:**

- Modify: `frontend/src/App.vue:1-5`

### Steps

- [ ] **Step 1: Replace `App.vue` template with scoped fullscreen props/events**

Replace the whole file with:

```vue
<template>
  <MainLayout>
    <template #editor="{ snippet, fetchSnippets, fullscreen, toggleFullscreen }">
      <DoubleEditor
        :snippet="snippet"
        :fullscreen="fullscreen"
        @toggle-fullscreen="toggleFullscreen"
        @updated="fetchSnippets"
      />
    </template>
  </MainLayout>
</template>

<script setup lang="ts">
import MainLayout from './layout/MainLayout.vue'
import DoubleEditor from './components/DoubleEditor.vue'
</script>

<style>
body {
  margin: 0;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
}
</style>
```

- [ ] **Step 2: Run build checkpoint**

Run:

```bash
cd frontend
npm run build
```

Expected at this point: the build may fail because `MainLayout` and `DoubleEditor` do not yet expose the new slot props and events. Continue to Task 2 and Task 3 before treating this as a blocker.

- [ ] **Step 3: Checkpoint instead of commit**

Run:

```bash
git diff -- frontend/src/App.vue
```

Expected: diff only shows slot prop/event wiring in `App.vue`.

Do not commit unless the user explicitly asks for a commit.

---

## Task 2: Add fullscreen control to `DoubleEditor.vue`

**Files:**

- Modify: `frontend/src/components/DoubleEditor.vue:1-15`
- Modify: `frontend/src/components/DoubleEditor.vue:74-86`
- Modify: `frontend/src/components/DoubleEditor.vue:207-221`

### Steps

- [ ] **Step 1: Update the editor header controls**

In `frontend/src/components/DoubleEditor.vue`, replace the current header block:

```vue
    <div class="editor-header">
      <h3>编辑知识片段</h3>
      <div class="header-tools">
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
```

with:

```vue
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
```

This keeps the button inside `v-if="snippet"`, so the empty state does not expose a misleading fullscreen action.

- [ ] **Step 2: Update props and emits**

Replace:

```ts
const props = defineProps<{
  snippet?: any
}>()

const emit = defineEmits(['updated'])
```

with:

```ts
const props = defineProps<{
  snippet?: any
  fullscreen?: boolean
}>()

const emit = defineEmits(['updated', 'toggle-fullscreen'])
```

- [ ] **Step 3: Keep template access to `fullscreen` simple**

No extra computed is required. In Vue `<script setup>`, the optional prop `fullscreen` is available to the template and will be falsy when not passed.

- [ ] **Step 4: Adjust header tool alignment for the new button**

Replace the `.header-tools` CSS block:

```css
.header-tools {
  display: flex;
  gap: 15px;
  color: #909399;
}
```

with:

```css
.header-tools {
  display: flex;
  align-items: center;
  gap: 15px;
  color: #909399;
}
```

- [ ] **Step 5: Run build checkpoint**

Run:

```bash
cd frontend
npm run build
```

Expected at this point: the build may still fail until `MainLayout.vue` exposes `fullscreen` and `toggleFullscreen` slot props. Continue to Task 3 before treating this as a blocker.

- [ ] **Step 6: Checkpoint instead of commit**

Run:

```bash
git diff -- frontend/src/components/DoubleEditor.vue
```

Expected: diff only adds the fullscreen button, `fullscreen` prop, `toggle-fullscreen` emit, and header alignment.

Do not commit unless the user explicitly asks for a commit.

---

## Task 3: Implement resizable panels and true fullscreen in `MainLayout.vue`

**Files:**

- Modify: `frontend/src/layout/MainLayout.vue:1-81`
- Modify: `frontend/src/layout/MainLayout.vue:84-180`
- Modify: `frontend/src/layout/MainLayout.vue:183-307`

### Steps

- [ ] **Step 1: Replace the template with conditional layout and resize handle**

Replace the entire `<template>` block in `frontend/src/layout/MainLayout.vue` with:

```vue
<template>
  <el-container :class="['main-layout', { 'editor-fullscreen': isEditorFullscreen }]">
    <!-- 左侧导航栏 -->
    <el-aside v-if="!isEditorFullscreen" width="280px" class="aside">
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
```

- [ ] **Step 2: Replace the Vue import**

Replace:

```ts
import { ref, onMounted, computed } from 'vue'
```

with:

```ts
import { ref, onMounted, computed, onBeforeUnmount } from 'vue'
```

- [ ] **Step 3: Add layout constants and state after `currentSnippet`**

Immediately after:

```ts
const currentSnippet = ref<any>(null)
```

add:

```ts
const SNIPPET_PANEL_WIDTH_KEY = 'flashbrain.snippetPanelWidth'
const DEFAULT_SNIPPET_PANEL_WIDTH = 360
const MIN_SNIPPET_PANEL_WIDTH = 260
const MIN_EDITOR_WIDTH = 480
const ASIDE_WIDTH = 280
const RESIZE_HANDLE_WIDTH = 8

const isEditorFullscreen = ref(false)
const snippetPanelWidth = ref(DEFAULT_SNIPPET_PANEL_WIDTH)
const isResizing = ref(false)
```

- [ ] **Step 4: Add width validation helpers after `activeSubjectName`**

Immediately after the `activeSubjectName` computed block, add:

```ts
const getMaxSnippetPanelWidth = () => {
  if (typeof window === 'undefined') return DEFAULT_SNIPPET_PANEL_WIDTH
  return Math.max(
    MIN_SNIPPET_PANEL_WIDTH,
    window.innerWidth - ASIDE_WIDTH - RESIZE_HANDLE_WIDTH - MIN_EDITOR_WIDTH
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
```

- [ ] **Step 5: Add resize and fullscreen handlers after `addSnippet`**

Immediately after the `addSnippet` function, add:

```ts
const stopResize = () => {
  if (!isResizing.value) return
  isResizing.value = false
  document.body.classList.remove('is-resizing-layout')
  saveSnippetPanelWidth()
}

const handleResize = (event: MouseEvent) => {
  if (!isResizing.value) return
  snippetPanelWidth.value = normalizeSnippetPanelWidth(event.clientX - ASIDE_WIDTH)
}

const startResize = () => {
  isResizing.value = true
  document.body.classList.add('is-resizing-layout')
}

const toggleEditorFullscreen = () => {
  isEditorFullscreen.value = !isEditorFullscreen.value
}

const handleWindowResize = () => {
  snippetPanelWidth.value = normalizeSnippetPanelWidth(snippetPanelWidth.value)
}
```

- [ ] **Step 6: Replace the `onMounted` block and add cleanup**

Replace:

```ts
onMounted(() => {
  fetchSubjects()
})
```

with:

```ts
onMounted(() => {
  fetchSubjects()
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
```

- [ ] **Step 7: Replace layout CSS for `.main-layout`, `.content-stream`, and `.editor-aside`**

Replace:

```css
.main-layout {
  height: 100vh;
  background-color: #f5f7fa;
}
```

with:

```css
.main-layout {
  height: 100vh;
  background-color: #f5f7fa;
  overflow: hidden;
}
.main-layout.editor-fullscreen {
  display: block;
}
```

Replace:

```css
.content-stream {
  padding: 20px;
  overflow-y: auto;
}
```

with:

```css
.content-stream {
  flex: none;
  padding: 20px;
  overflow-y: auto;
  min-width: 260px;
  max-width: calc(100vw - 280px - 8px - 480px);
}
```

Replace:

```css
.editor-aside {
  background-color: white;
  border-left: 1px solid #e4e7ed;
  padding: 20px;
}
```

with:

```css
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
```

- [ ] **Step 8: Make header controls fit the resizable panel**

Replace:

```css
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
```

with:

```css
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
```

This avoids overflow when the user drags the snippet panel down to its minimum width.

- [ ] **Step 9: Run build verification**

Run:

```bash
cd frontend
npm run build
```

Expected: PASS. If it fails, fix TypeScript or Vue template errors before continuing.

- [ ] **Step 10: Checkpoint instead of commit**

Run:

```bash
git diff -- frontend/src/layout/MainLayout.vue frontend/src/App.vue frontend/src/components/DoubleEditor.vue
```

Expected:

- `MainLayout.vue` owns resize/fullscreen state and slot props.
- `App.vue` passes slot props/events to `DoubleEditor`.
- `DoubleEditor.vue` emits `toggle-fullscreen` and shows the correct button text.

Do not commit unless the user explicitly asks for a commit.

---

## Task 4: Manual browser verification

**Files:**

- Verify: `frontend/src/layout/MainLayout.vue`
- Verify: `frontend/src/components/DoubleEditor.vue`
- Verify: `frontend/src/App.vue`

### Steps

- [ ] **Step 1: Start the frontend dev server**

Run:

```bash
cd frontend
npm run dev
```

Expected: Vite starts and prints a local URL such as `http://localhost:5173/`.

- [ ] **Step 2: Open the app in the browser**

Navigate to the Vite URL.

Expected default state:

- Left subject panel is visible.
- Middle snippet list panel is visible.
- Resize handle appears between the snippet list and the editor.
- Right editor panel is visible.

- [ ] **Step 3: Verify drag resize**

Drag the resize handle left and right.

Expected:

- Dragging left makes the snippet list narrower and the editor wider.
- Dragging right makes the snippet list wider and the editor narrower.
- Snippet list does not shrink below roughly `260px`.
- Editor does not shrink below roughly `480px`.

- [ ] **Step 4: Verify persisted width**

After dragging the divider, reload the page.

Expected:

- The snippet list width is restored from `localStorage`.
- The app still starts in the normal three-column layout, not fullscreen.

- [ ] **Step 5: Verify fullscreen editor mode**

Select or create a snippet, then click `全屏编辑` in the editor header.

Expected:

- Left subject panel disappears.
- Middle snippet list disappears.
- Resize handle disappears.
- Detail editor fills the app window.
- Button text changes to `退出全屏`.

- [ ] **Step 6: Verify exit fullscreen**

Click `退出全屏`.

Expected:

- Left subject panel returns.
- Middle snippet list returns at the previously saved width.
- Resize handle returns.
- Button text changes back to `全屏编辑`.

- [ ] **Step 7: Verify empty state**

Reach a state where no snippet is selected, such as initial load before selecting a snippet.

Expected:

- Empty state says `请选择一个知识片段进行编辑`.
- No `全屏编辑` button is visible.

- [ ] **Step 8: Verify existing editor actions still work**

With a selected snippet, verify in normal and fullscreen modes as applicable:

- Save OCR text.
- Save personal note.
- Toggle pinned state.
- Toggle mastered state.
- Delete snippet and confirm the UI does not leave the user stuck in a misleading fullscreen state.

Expected:

- Existing success/error messages still appear.
- Existing API calls still run.
- After delete, the UI remains usable. If fullscreen empty state feels confusing, update `handleDelete` follow-up in a later refinement to exit fullscreen from the parent after deletion.

---

## Task 5: Final verification and cleanup

**Files:**

- Verify: all modified frontend files

### Steps

- [ ] **Step 1: Run production build one final time**

Run:

```bash
cd frontend
npm run build
```

Expected: PASS.

- [ ] **Step 2: Inspect final diff**

Run:

```bash
git diff -- frontend/src/App.vue frontend/src/layout/MainLayout.vue frontend/src/components/DoubleEditor.vue docs/superpowers/specs/2026-06-05-detail-fullscreen-layout-design.md docs/superpowers/plans/2026-06-05-detail-fullscreen-layout-implementation.md
```

Expected:

- No unrelated backend, OCR, IDE, generated build, or dependency changes are included.
- The spec and plan documents are present.
- Frontend changes are limited to the layout/fullscreen feature.

- [ ] **Step 3: Check repository status**

Run:

```bash
git status --short
```

Expected:

- Modified files include the intended frontend files.
- New or modified documentation includes this plan and the approved spec.
- Existing unrelated user changes may still appear; do not overwrite or revert them.

- [ ] **Step 4: Report verification evidence**

In the final implementation report, include:

- The exact `npm run build` result.
- Which manual browser checks passed.
- Any checks skipped and why.
- Any unrelated pre-existing working tree changes that were left untouched.

Do not claim completion unless the build and relevant manual checks have actually been run.

---

## Self-Review

### Spec coverage

- Default three-column layout remains visible: Task 3 and Task 4.
- Resizable snippet list with min widths: Task 3 Steps 3-8 and Task 4 Step 3.
- Width persistence in `localStorage`: Task 3 Steps 3-6 and Task 4 Step 4.
- True fullscreen editor hiding both left and middle panels: Task 3 Step 1 and Task 4 Step 5.
- `DoubleEditor` button text and event boundary: Task 2 and Task 4 Steps 5-6.
- Empty state without misleading fullscreen entry: Task 2 Step 1 and Task 4 Step 7.
- Existing editor actions remain unchanged: Task 2 avoids action logic changes; Task 4 Step 8 verifies behavior.
- Backend/OCR/API unchanged: File structure and final diff checks enforce this.

### Placeholder scan

No `TBD`, `TODO`, `implement later`, or unspecified “add appropriate handling” instructions are present. Each code-changing step includes exact replacement code.

### Type consistency

The same names are used throughout:

- Prop: `fullscreen`
- Emit: `toggle-fullscreen`
- Parent handler: `toggleEditorFullscreen`
- State: `isEditorFullscreen`
- Width state: `snippetPanelWidth`
- Storage key: `flashbrain.snippetPanelWidth`

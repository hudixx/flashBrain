# Subject Collapse and Card Fullscreen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a collapsible left subject rail and a per-snippet card fullscreen entry that opens that snippet directly in the existing true fullscreen detail editor.

**Architecture:** Keep `MainLayout.vue` as the single owner of layout state. Extend the existing `isEditorFullscreen` and snippet panel width logic with `isSubjectCollapsed`, a computed current aside width, and one `openSnippetFullscreen(snippet, event)` method. Reuse the existing `DoubleEditor` fullscreen state and event path; do not create another detail display mode.

**Tech Stack:** Vue 3 `<script setup lang="ts">`, Element Plus icons/buttons/tooltips, existing scoped CSS, browser `localStorage`, existing `npm run build` verification.

---

## Source Spec

- `docs/superpowers/specs/2026-06-05-subject-collapse-card-fullscreen-design.md`

## Scope Check

The spec covers one cohesive frontend layout enhancement. It only changes the page shell and snippet cards in `MainLayout.vue`; it does not require backend, OCR, API, database, routing, or `DoubleEditor.vue` changes.

## File Structure

Modify this file only:

- `frontend/src/layout/MainLayout.vue`
  - Responsibility: page-level layout state, subject rail expanded/collapsed rendering, snippet list width calculation, and card-level fullscreen entry.

No new source files are required.

## Testing Strategy

The frontend project currently has no unit test script. The user previously approved using build and manual/browser verification instead of adding a test framework for these layout changes. Verification for this plan uses:

1. `npm run build` for Vue and TypeScript correctness.
2. Manual browser checks for left rail collapse/expand, persistence, card button behavior, true fullscreen entry, and exit fullscreen behavior.

Do not add new test dependencies for this focused UI layout change.

---

## Task 1: Add subject rail collapse state and render modes

**Files:**

- Modify: `frontend/src/layout/MainLayout.vue`

### Steps

- [ ] **Step 1: Update the Element Plus icon import**

In `frontend/src/layout/MainLayout.vue`, replace:

```ts
import { Monitor, Folder, Search, Plus, MapLocation } from '@element-plus/icons-vue'
```

with:

```ts
import { Monitor, Folder, Search, Plus, MapLocation, Fold, Expand, FullScreen } from '@element-plus/icons-vue'
```

- [ ] **Step 2: Add subject collapse constants and state**

Immediately after the existing layout constants:

```ts
const SNIPPET_PANEL_WIDTH_KEY = 'flashbrain.snippetPanelWidth'
const DEFAULT_SNIPPET_PANEL_WIDTH = 360
const MIN_SNIPPET_PANEL_WIDTH = 260
const MIN_EDITOR_WIDTH = 480
const ASIDE_WIDTH = 280
const RESIZE_HANDLE_WIDTH = 8
```

replace that block with:

```ts
const SNIPPET_PANEL_WIDTH_KEY = 'flashbrain.snippetPanelWidth'
const SUBJECT_COLLAPSED_KEY = 'flashbrain.subjectCollapsed'
const DEFAULT_SNIPPET_PANEL_WIDTH = 360
const MIN_SNIPPET_PANEL_WIDTH = 260
const MIN_EDITOR_WIDTH = 480
const ASIDE_WIDTH = 280
const COLLAPSED_ASIDE_WIDTH = 56
const RESIZE_HANDLE_WIDTH = 8
```

Immediately after:

```ts
const isEditorFullscreen = ref(false)
```

add:

```ts
const isSubjectCollapsed = ref(false)
```

- [ ] **Step 3: Add current aside width and persistence helpers**

Immediately after the `activeSubjectName` computed block, add:

```ts
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
```

- [ ] **Step 4: Update width calculations to use current aside width**

Replace `getMaxSnippetPanelWidth` with:

```ts
const getMaxSnippetPanelWidth = () => {
  if (typeof window === 'undefined') return DEFAULT_SNIPPET_PANEL_WIDTH
  return Math.max(
    MIN_SNIPPET_PANEL_WIDTH,
    window.innerWidth - currentAsideWidth.value - RESIZE_HANDLE_WIDTH - MIN_EDITOR_WIDTH
  )
}
```

Replace `handleResize` with:

```ts
const handleResize = (event: MouseEvent) => {
  if (!isResizing.value) return
  snippetPanelWidth.value = normalizeSnippetPanelWidth(event.clientX - currentAsideWidth.value)
}
```

- [ ] **Step 5: Load collapsed state on mount**

Replace the current `onMounted` block:

```ts
onMounted(() => {
  fetchSubjects()
  loadSnippetPanelWidth()
  window.addEventListener('mousemove', handleResize)
  window.addEventListener('mouseup', stopResize)
  window.addEventListener('resize', handleWindowResize)
})
```

with:

```ts
onMounted(() => {
  fetchSubjects()
  loadSubjectCollapsed()
  loadSnippetPanelWidth()
  window.addEventListener('mousemove', handleResize)
  window.addEventListener('mouseup', stopResize)
  window.addEventListener('resize', handleWindowResize)
})
```

- [ ] **Step 6: Replace the left aside template**

Replace the current left aside block from:

```vue
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
```

with:

```vue
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
```

- [ ] **Step 7: Add CSS for the subject tools and collapsed rail**

Immediately after the existing `.logo` CSS block, add:

```css
.subject-tools {
  display: flex;
  align-items: center;
  gap: 8px;
}
```

Replace the existing `.add-subject-btn` and `.add-subject-btn:hover` CSS blocks:

```css
.add-subject-btn {
  background: rgba(255, 255, 255, 0.1);
  border: none;
  color: white;
}
.add-subject-btn:hover {
  background: rgba(255, 255, 255, 0.2);
}
```

with:

```css
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
```

- [ ] **Step 8: Run build checkpoint**

Run:

```bash
npm run build
```

Expected: PASS. Existing Vite warnings about large chunks or Rollup pure annotations are acceptable if the command exits successfully.

- [ ] **Step 9: Check diff for this task**

Run:

```bash
git diff -- frontend/src/layout/MainLayout.vue
```

Expected: diff shows subject collapsed state, full rail/collapsed rail template, and associated CSS. Do not commit unless the user explicitly asks for a commit.

---

## Task 2: Add per-card fullscreen entry

**Files:**

- Modify: `frontend/src/layout/MainLayout.vue`

### Steps

- [ ] **Step 1: Add card fullscreen button in each snippet card**

Inside each snippet card, after the pinned icon block:

```vue
            <el-icon v-if="s.isPinned" class="pin-icon"><MapLocation /></el-icon>
```

add:

```vue
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
```

The resulting card content block should look like:

```vue
          <div class="snippet-content">
            <img :src="s.imagePath || 'https://via.placeholder.com/60'" class="snippet-img" />
            <div class="snippet-info">
              <h3>{{ s.title }} <el-tag v-if="s.isPinned" size="small">置顶</el-tag></h3>
              <p class="ocr-preview">OCR原文: {{ s.ocrText?.substring(0, 50) }}...</p>
              <span class="meta">#{{ activeSubjectName }}</span>
            </div>
            <el-icon v-if="s.isPinned" class="pin-icon"><MapLocation /></el-icon>
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
```

- [ ] **Step 2: Add `openSnippetFullscreen` method**

Immediately after `toggleEditorFullscreen`, add:

```ts
const openSnippetFullscreen = (snippet: any, event?: MouseEvent) => {
  event?.stopPropagation()
  currentSnippet.value = snippet
  isEditorFullscreen.value = true
}
```

- [ ] **Step 3: Add card button CSS**

Replace the existing `.snippet-content` CSS block:

```css
.snippet-content {
  display: flex;
  gap: 15px;
  position: relative;
}
```

with:

```css
.snippet-content {
  display: flex;
  gap: 15px;
  position: relative;
  padding-right: 34px;
  padding-bottom: 26px;
}
```

Immediately after the existing `.pin-icon` CSS block:

```css
.pin-icon {
  position: absolute;
  top: 0;
  right: 0;
  color: #409eff;
}
```

add:

```css
.snippet-fullscreen-btn {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 26px;
  height: 26px;
  min-height: 26px;
  padding: 0;
  color: #409eff;
}
```

- [ ] **Step 4: Run build checkpoint**

Run:

```bash
npm run build
```

Expected: PASS. Existing Vite warnings about large chunks or Rollup pure annotations are acceptable if the command exits successfully.

- [ ] **Step 5: Check diff for this task**

Run:

```bash
git diff -- frontend/src/layout/MainLayout.vue
```

Expected: diff shows `FullScreen` icon usage, `openSnippetFullscreen`, and `.snippet-fullscreen-btn` styles. Do not commit unless the user explicitly asks for a commit.

---

## Task 3: Manual verification and final cleanup

**Files:**

- Verify: `frontend/src/layout/MainLayout.vue`

### Steps

- [ ] **Step 1: Run final production build**

Run:

```bash
npm run build
```

Expected: PASS.

- [ ] **Step 2: Start the frontend dev server**

Run:

```bash
npm run dev -- --host 127.0.0.1
```

Expected: Vite prints a local URL such as `http://127.0.0.1:5173/`.

- [ ] **Step 3: Verify left rail collapse and expand in the browser**

Open the app URL and perform these actions:

1. Confirm the full left subject rail appears at about `280px` wide.
2. Click the subject rail collapse button.
3. Confirm the rail becomes a narrow icon rail at about `56px` wide.
4. Click the expand button in the narrow rail.
5. Confirm the full subject rail returns.

Expected: the middle snippet list and right editor remain visible during these non-fullscreen layout changes.

- [ ] **Step 4: Verify subject collapse persistence**

In the browser:

1. Collapse the subject rail.
2. Reload the page.
3. Confirm the narrow icon rail is still shown.
4. Expand the rail.
5. Reload the page.
6. Confirm the full rail is shown.

Expected: `localStorage` key `flashbrain.subjectCollapsed` stores the preference.

- [ ] **Step 5: Verify card-level fullscreen entry**

With at least one subject and snippet visible:

1. Click a snippet card body, not the fullscreen icon.
2. Confirm that the snippet becomes selected and the app remains in normal layout.
3. Click the same card's lower-right `全屏查看` button.
4. Confirm the selected snippet opens in true fullscreen detail mode.
5. Confirm the left subject rail, middle snippet list, and resize handle are hidden.
6. Click `退出全屏` in the detail editor.
7. Confirm the app restores the prior layout and subject rail collapsed/expanded state.

Expected: clicking the card button sets that card's snippet as the current detail and enters fullscreen. Clicking the card body alone does not enter fullscreen.

- [ ] **Step 6: Probe adjacent behavior**

In the browser:

1. Collapse the subject rail.
2. Click a different snippet card's `全屏查看` button.
3. Exit fullscreen.
4. Confirm the subject rail is still collapsed.
5. Expand the subject rail.
6. Click another snippet card's `全屏查看` button.
7. Exit fullscreen.
8. Confirm the subject rail is expanded.

Expected: fullscreen entry and exit do not overwrite the subject rail preference.

- [ ] **Step 7: Clean build output**

If `frontend/dist/` appears as an untracked directory after build, remove it unless the user explicitly wants build artifacts kept:

```bash
rm -rf frontend/dist
```

- [ ] **Step 8: Inspect final diff and status**

Run:

```bash
git diff -- frontend/src/layout/MainLayout.vue docs/superpowers/specs/2026-06-05-subject-collapse-card-fullscreen-design.md docs/superpowers/plans/2026-06-05-subject-collapse-card-fullscreen-implementation.md
git status --short
```

Expected:

- Feature source diff is limited to `frontend/src/layout/MainLayout.vue`.
- New documentation files include the approved spec and this plan.
- Existing unrelated working tree changes may still be present; do not revert them.

- [ ] **Step 9: Report verification evidence**

Final report must include:

- The exact `npm run build` result.
- Browser observations for collapse, expand, persistence, card fullscreen, and exit fullscreen.
- Any browser verification that could not be performed and why.
- Any unrelated pre-existing working tree changes left untouched.

Do not claim browser behavior is verified unless it was observed in the running app.

---

## Self-Review

### Spec coverage

- Full and collapsed subject rail states: Task 1 Steps 2-7 and Task 3 Steps 3-4.
- `localStorage` persistence for subject collapse: Task 1 Steps 2-5 and Task 3 Step 4.
- Current left width used in snippet panel calculations: Task 1 Step 4.
- Per-card fullscreen button: Task 2 Steps 1 and 3.
- Button stops card click propagation, selects that snippet, and enters true fullscreen: Task 2 Step 2 and Task 3 Step 5.
- Existing true fullscreen behavior is reused: Task 2 Step 2 uses `isEditorFullscreen`; no alternate detail mode is introduced.
- Fullscreen exit preserves subject rail preference: Task 3 Step 6.
- No backend/OCR/API changes: File Structure and Task 3 Step 8 enforce source scope.

### Placeholder scan

The plan contains concrete file paths, exact code snippets, exact commands, and expected outcomes. It does not contain incomplete implementation instructions.

### Type consistency

The same names are used throughout:

- State: `isSubjectCollapsed`
- Storage key: `flashbrain.subjectCollapsed`
- Computed width: `currentAsideWidth`
- Rail widths: `ASIDE_WIDTH`, `COLLAPSED_ASIDE_WIDTH`
- Toggle method: `toggleSubjectCollapsed`
- Card fullscreen method: `openSnippetFullscreen`
- Card button class: `snippet-fullscreen-btn`

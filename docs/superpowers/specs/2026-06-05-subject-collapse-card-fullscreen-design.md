# 科目栏收缩与卡片级全屏入口设计

日期：2026-06-05

## 背景

FlashBrain 已在详情页布局中引入三栏可调宽和详情真正全屏编辑能力。用户进一步希望左侧科目栏不必一直占据完整宽度，并希望在片段列表中直接从某一条片段进入详情全屏，而不依赖右侧详情栏顶部的“全屏编辑”按钮。

本设计在现有前端布局状态基础上扩展两个入口：左侧科目栏可收缩为窄图标栏；每条片段卡片右下角提供“全屏查看”按钮，点击后打开该条片段的全屏详情。

## 目标

- 左侧科目栏支持在完整栏和窄图标栏之间切换。
- 收缩后的左侧栏仍提供清晰的恢复入口，避免用户迷失。
- 每条片段卡片右下角提供全屏查看入口。
- 点击卡片主体仍保持现有“选中片段，在右侧显示详情”的行为。
- 点击卡片全屏按钮时，直接进入该条片段的真正全屏详情。
- 复用现有 `isEditorFullscreen` 全屏逻辑，不新增第二套详情展示模式。
- 不改后端、OCR 服务、数据库结构或 REST API。

## 非目标

- 不重新设计科目树层级或片段卡片信息结构。
- 不增加独立详情路由。
- 不引入新的布局管理库。
- 不改变 `DoubleEditor` 内部的 OCR、笔记、置顶、掌握、删除业务逻辑。

## 推荐方案

采用“左侧窄图标栏 + 每卡片全屏入口”。

### 左侧科目栏状态

左侧科目栏有两种非全屏状态：

```text
展开状态：左侧科目栏 280px | 中间片段列表 | 右侧详情
收缩状态：左侧图标栏 56px | 中间片段列表 | 右侧详情
```

行为要求：

- 默认可以使用完整科目栏；若 `localStorage` 中保存了用户偏好，则按偏好恢复。
- 完整科目栏顶部提供收缩按钮。
- 收缩后显示 56px 左右的窄图标栏。
- 窄图标栏保留 FlashBrain 图标和展开按钮。
- 可保留新增科目图标按钮，但不显示文字和科目列表。
- 点击展开按钮后恢复完整科目栏。
- 收缩/展开状态保存到 `localStorage`。
- 进入详情全屏时，左侧栏无论展开还是收缩都隐藏；退出全屏后恢复进入全屏前的科目栏状态。

### 卡片级全屏入口

每条片段卡片右下角增加一个小图标按钮。

布局示意：

```text
片段标题                     [置顶]
OCR原文: ...
#科目名                                  [全屏图标]
```

行为要求：

- 卡片主体点击行为保持不变：只设置 `currentSnippet = s`。
- 卡片右下角全屏按钮点击行为：
  1. 阻止事件冒泡，避免触发卡片主体点击的额外副作用。
  2. 将 `currentSnippet` 设置为该条片段。
  3. 将 `isEditorFullscreen` 设置为 `true`。
- 全屏按钮 tooltip 文案为 `全屏查看`。
- 进入全屏后行为与现有 `DoubleEditor` 顶部“全屏编辑”按钮一致：隐藏左侧栏、中间列表和拖拽条，只显示当前片段详情。
- 全屏详情中点击 `退出全屏` 后恢复三栏布局，并保留左侧栏收缩/展开偏好。

## 组件设计

### `MainLayout.vue`

`MainLayout.vue` 继续作为页面级布局状态的唯一拥有者。

新增职责：

- 维护 `isSubjectCollapsed` 状态。
- 初始化时从 `localStorage` 读取科目栏收缩偏好。
- 切换科目栏收缩/展开并写入 `localStorage`。
- 根据 `isSubjectCollapsed` 渲染完整科目栏或窄图标栏。
- 提供 `openSnippetFullscreen(snippet)` 方法，用于卡片按钮直接打开该条详情全屏。
- 调整列表宽度计算：左侧宽度应根据 `isSubjectCollapsed` 在完整宽度和窄栏宽度之间切换。

建议常量：

```ts
const SUBJECT_COLLAPSED_KEY = 'flashbrain.subjectCollapsed'
const ASIDE_WIDTH = 280
const COLLAPSED_ASIDE_WIDTH = 56
```

建议方法：

```ts
const currentAsideWidth = computed(() => {
  return isSubjectCollapsed.value ? COLLAPSED_ASIDE_WIDTH : ASIDE_WIDTH
})

const toggleSubjectCollapsed = () => {
  isSubjectCollapsed.value = !isSubjectCollapsed.value
  window.localStorage.setItem(SUBJECT_COLLAPSED_KEY, String(isSubjectCollapsed.value))
}

const openSnippetFullscreen = (snippet: any, event?: MouseEvent) => {
  event?.stopPropagation()
  currentSnippet.value = snippet
  isEditorFullscreen.value = true
}
```

### `DoubleEditor.vue`

`DoubleEditor.vue` 不需要新增职责。

它继续：

- 接收 `fullscreen` prop。
- 发出 `toggle-fullscreen` 事件。
- 显示 `全屏编辑` / `退出全屏` 按钮。
- 管理详情编辑内部操作。

## 状态与数据流

```text
用户点击完整科目栏收缩按钮
  -> MainLayout.toggleSubjectCollapsed()
  -> isSubjectCollapsed = true
  -> 左侧栏从 280px 变为 56px
  -> localStorage 保存 true

用户点击窄图标栏展开按钮
  -> MainLayout.toggleSubjectCollapsed()
  -> isSubjectCollapsed = false
  -> 左侧栏恢复 280px
  -> localStorage 保存 false

用户点击片段卡片主体
  -> currentSnippet = s
  -> 保持普通三栏/收缩栏布局

用户点击片段卡片右下角全屏按钮
  -> event.stopPropagation()
  -> currentSnippet = s
  -> isEditorFullscreen = true
  -> 左侧栏、中间列表、拖拽条隐藏
  -> 当前片段详情占满窗口
```

## 样式策略

- 窄图标栏宽度建议为 `56px`。
- 窄图标栏使用深色背景，与完整左侧栏保持一致。
- 收缩/展开按钮使用 Element Plus 图标按钮，建议图标为 `Fold` / `Expand` 或同类图标。
- 卡片右下角全屏按钮使用小型圆形或文字按钮，视觉上轻量，不抢夺标题和 OCR 预览注意力。
- 片段卡片需要 `position: relative`，全屏按钮可绝对定位在右下角。
- 卡片内容右侧和底部应留出少量空间，避免按钮遮挡文字。

## 边界情况

- 未选择科目或片段列表为空：不显示卡片全屏按钮，因为没有卡片。
- 点击全屏按钮时片段立即成为当前片段，即使此前选中的是另一条。
- 全屏状态下不显示左侧栏收缩按钮和卡片全屏按钮，因为中间列表已隐藏。
- `localStorage` 中保存的科目栏状态异常时，回退为展开状态。
- 浏览器宽度变化时，片段列表最大宽度计算应使用当前左侧栏宽度，避免收缩后仍按 280px 计算导致空间浪费。

## 验证标准

完成后应验证：

1. 默认进入页面时完整左侧科目栏可显示。
2. 点击收缩按钮后，左侧栏变为约 56px 的窄图标栏。
3. 点击窄图标栏展开按钮后，完整科目栏恢复。
4. 刷新页面后，左侧栏收缩/展开状态按上次偏好恢复。
5. 每条片段卡片右下角显示 `全屏查看` 入口。
6. 点击卡片主体仍只选中该片段，不进入全屏。
7. 点击某条卡片右下角全屏按钮后，该条片段进入真正全屏详情。
8. 全屏详情中点击 `退出全屏` 后恢复三栏布局，并保留左侧栏状态。
9. 原有 OCR 保存、笔记保存、置顶、掌握、删除逻辑不受影响。
10. `npm run build` 通过。

## 实施范围

预计只需要修改：

- `frontend/src/layout/MainLayout.vue`

通常不需要修改：

- `frontend/src/components/DoubleEditor.vue`
- `frontend/src/App.vue`
- 后端 Java 代码
- OCR 服务代码
- REST API

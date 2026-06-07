<template>
  <MainLayout>
    <template #editor="{ snippet, fetchSnippets, fullscreen, toggleFullscreen, recycleMode }">
      <DoubleEditor
        :snippet="snippet"
        :fullscreen="fullscreen"
        :recycle-mode="recycleMode"
        @toggle-fullscreen="toggleFullscreen"
        @updated="fetchSnippets"
      />
    </template>
  </MainLayout>
</template>

<script setup lang="ts">
import MainLayout from '../layout/MainLayout.vue'
import DoubleEditor from '../components/DoubleEditor.vue'
</script>

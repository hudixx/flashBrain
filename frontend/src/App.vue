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

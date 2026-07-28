<script setup>
// 앱 최상위 레이아웃
import { RouterView } from 'vue-router'
import Toast from './components/Toast.vue'
</script>

<template>
  <RouterView />
  <Toast />
</template>

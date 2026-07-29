<script setup>
// 데이터 로딩 중에도 자리를 차지하는 shimmer placeholder - 레이아웃 밀림 방지용
defineProps({
  width: { type: String, default: '100%' },
  height: { type: String, default: '14px' },
  radius: { type: String, default: '6px' },
})
</script>

<template>
  <span class="skeleton" :style="{ width, height, borderRadius: radius }"></span>
</template>

<style scoped>
.skeleton {
  display: inline-block;
  background: linear-gradient(90deg, #eceaf7 25%, #f5f3fb 37%, #eceaf7 63%);
  background-size: 400% 100%;
  animation: skeleton-shimmer 1.4s ease infinite;
}

@keyframes skeleton-shimmer {
  0% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0 50%;
  }
}
</style>

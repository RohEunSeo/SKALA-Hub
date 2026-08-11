<script setup>
// 링크 모음 탭 최초 로딩 중 자리채움용 - 실제 LinkGalleryCard와 같은 레이아웃(썸네일+본문)으로
// 만들어서 로딩이 끝났을 때 카드로 자연스럽게 이어지게 함(게시글용 스켈레톤과 모양이 달라서 별도로 둠)
import SkeletonBlock from './SkeletonBlock.vue'
</script>

<template>
  <div class="link-card-skeleton" aria-hidden="true">
    <div class="link-card-skeleton-thumb"></div>
    <div class="link-card-skeleton-body">
      <SkeletonBlock width="40%" height="12px" />
      <SkeletonBlock width="90%" height="14px" />
      <SkeletonBlock width="65%" height="14px" />
      <SkeletonBlock width="50%" height="12px" radius="4px" />
    </div>
  </div>
</template>

<style scoped>
.link-card-skeleton {
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border: 1px solid rgba(26, 26, 46, 0.08);
  border-radius: 12px;
  overflow: hidden;
}

.link-card-skeleton-thumb {
  height: 150px;
  background: linear-gradient(90deg, #eceaf7 25%, #f5f3fb 37%, #eceaf7 63%);
  background-size: 400% 100%;
  animation: link-skeleton-shimmer 1.4s ease infinite;
  border-bottom: 1px solid rgba(26, 26, 46, 0.08);
}

.link-card-skeleton-body {
  padding: 12px 14px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.link-card-skeleton-body > :last-child {
  margin-top: 8px;
}

@keyframes link-skeleton-shimmer {
  0% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0 50%;
  }
}
</style>

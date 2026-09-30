<script setup>
// 작은 폴더 아이콘 (색 지정) - 내 폴더 목록/챗봇 폴더 선택에서 공용
defineProps({ color: { type: String, default: '#b7a6e6' }, size: { type: Number, default: 20 } })
</script>

<template>
  <span class="fi" :style="{ '--c': color, '--s': `${size}px` }" aria-hidden="true">
    <i class="fi-tab"></i>
    <i class="fi-body"></i>
  </span>
</template>

<style scoped>
.fi {
  position: relative;
  display: inline-block;
  flex: none;
  width: var(--s);
  height: calc(var(--s) * 0.8);
  vertical-align: middle;
}

.fi i {
  position: absolute;
  display: block;
}

.fi-tab {
  left: 0;
  top: 0;
  width: 46%;
  height: 30%;
  border-radius: 2px 2px 0 0;
  background: var(--c);
}

.fi-body {
  left: 0;
  right: 0;
  top: 16%;
  bottom: 0;
  border-radius: 3px;
  background: linear-gradient(rgba(255, 255, 255, 0.35), rgba(255, 255, 255, 0)), var(--c);
}
</style>

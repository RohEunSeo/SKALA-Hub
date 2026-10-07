<script setup>
// 작은 원형 아바타 (이름 첫 글자 + 유저 색)
defineProps({ user: { type: Object, required: true }, size: { type: Number, default: 20 } })
</script>

<template>
  <span class="ud" :style="{ background: user.color, width: `${size}px`, height: `${size}px`, fontSize: `${size * 0.5}px` }" :title="user.name">{{ user.name[0] }}</span>
</template>

<style scoped>
.ud { display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; color: #fff; font-weight: 700; flex: none; vertical-align: middle; }
</style>

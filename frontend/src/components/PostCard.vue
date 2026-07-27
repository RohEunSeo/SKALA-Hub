<script setup>
// 게시글 한 건을 카드 형태로 보여주는 컴포넌트
defineProps({
  post: { type: Object, required: true },
})
</script>

<template>
  <article class="post-card">
    <h3>{{ post.ai_title }}</h3>
  </article>
</template>

<script setup>
// 게시글 한 건을 카드 형태로 보여주는 컴포넌트
defineProps({
  post: { type: Object, required: true },
})
</script>

<template>
  <article class="post-card">
    <p>{{ post.content }}</p>
  </article>
</template>

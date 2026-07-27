// 게시글 목록 및 필터 상태 관리
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const usePostsStore = defineStore('posts', () => {
  const posts = ref([])
  const selectedCategory = ref(null)

  // 게시글 목록 갱신
  function setPosts(newPosts) {
    posts.value = newPosts
  }

  // 카테고리 필터 변경
  function setCategory(category) {
    selectedCategory.value = category
  }

  return { posts, selectedCategory, setPosts, setCategory }
})

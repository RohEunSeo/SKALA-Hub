// 북마크한 게시글 목록 관리
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useBookmarksStore = defineStore('bookmarks', () => {
  const bookmarkedPostIds = ref([])

  // 북마크 목록 갱신
  function setBookmarks(postIds) {
    bookmarkedPostIds.value = postIds
  }

  return { bookmarkedPostIds, setBookmarks }
})

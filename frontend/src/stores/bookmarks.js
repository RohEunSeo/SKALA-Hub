// 저장한(북마크) 게시글 목록 관리
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fetchBookmarks, saveBookmark, removeBookmark } from '../api/bookmarks'

export const useBookmarksStore = defineStore('bookmarks', () => {
  const bookmarkedPostIds = ref([])

  // 북마크 목록 갱신
  function setBookmarks(postIds) {
    bookmarkedPostIds.value = postIds
  }

  // 로그인 유저의 저장 목록을 서버에서 불러오기
  async function loadBookmarks() {
    const { data } = await fetchBookmarks()
    bookmarkedPostIds.value = data
  }

  // 저장/저장취소 토글 (낙관적 갱신 후 API 반영)
  async function toggle(postId) {
    const isBookmarked = bookmarkedPostIds.value.includes(postId)
    if (isBookmarked) {
      bookmarkedPostIds.value = bookmarkedPostIds.value.filter((id) => id !== postId)
      await removeBookmark(postId)
    } else {
      bookmarkedPostIds.value = [...bookmarkedPostIds.value, postId]
      await saveBookmark(postId)
    }
  }

  return { bookmarkedPostIds, setBookmarks, loadBookmarks, toggle }
})

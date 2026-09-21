// 저장한(북마크) 게시글 목록 관리
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fetchBookmarks, saveBookmark, removeBookmark } from '../api/bookmarks'
import { useMyPageStore } from './mypage'

export const useBookmarksStore = defineStore('bookmarks', () => {
  const bookmarkedPostIds = ref([])

  // 북마크 목록 갱신
  function setBookmarks(postIds) {
    bookmarkedPostIds.value = postIds
  }

  // 로그인 유저의 저장 목록을 서버에서 불러오기
  async function loadBookmarks() {
    try {
      const { data } = await fetchBookmarks()
      bookmarkedPostIds.value = data ?? []
    } catch {
      // 저장 목록은 부가 정보라 실패해도 빈 목록으로 조용히 처리 (피드 자체는 계속 보여야 함)
      bookmarkedPostIds.value = []
    }
  }

  // 저장/저장취소 토글 (낙관적 갱신 후 API 반영, 실패하면 원래 상태로 롤백)
  async function toggle(postId) {
    const isBookmarked = bookmarkedPostIds.value.includes(postId)
    const previous = bookmarkedPostIds.value
    try {
      if (isBookmarked) {
        bookmarkedPostIds.value = bookmarkedPostIds.value.filter((id) => id !== postId)
        await removeBookmark(postId)
      } else {
        bookmarkedPostIds.value = [...bookmarkedPostIds.value, postId]
        await saveBookmark(postId)
      }
      // 사이드바 저장한 글 개수를 바로 맞추고, 마이페이지 저장한 글 목록 캐시는 다음 방문 때 다시 불러오게 함
      const myPageStore = useMyPageStore()
      myPageStore.adjustSavedCount(isBookmarked ? -1 : 1)
      myPageStore.invalidateCache()
    } catch (error) {
      bookmarkedPostIds.value = previous
      throw error
    }
  }

  return { bookmarkedPostIds, setBookmarks, loadBookmarks, toggle }
})

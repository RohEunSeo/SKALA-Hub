// SKALA 커리큘럼 탭 상태 - 선택된 단계/하위카테고리, 단계별 게시글 캐시, 다이어그램 카운트
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { CURRICULUM_STAGES } from '../constants/curriculum'
import { fetchCurriculumPosts, fetchCurriculumCounts } from '../api/curriculum'

export const useCurriculumStore = defineStore('curriculum', () => {
  const selectedStage = ref(CURRICULUM_STAGES[0].value)
  const selectedSubCategory = ref(null)
  const counts = ref({})
  const postsByKey = ref({}) // `${stage}:${subCategory ?? ''}` -> post list
  const loading = ref(false)
  const error = ref('')

  function keyFor(stage, subCategory) {
    return `${stage}:${subCategory ?? ''}`
  }

  async function loadCounts() {
    try {
      const { data } = await fetchCurriculumCounts()
      counts.value = data ?? {}
    } catch {
      // 카운트는 다이어그램 배지용 부가 정보라 실패해도 목록 조회는 계속 진행
      counts.value = {}
    }
  }

  async function selectStage(stage, subCategory = null) {
    selectedStage.value = stage
    selectedSubCategory.value = subCategory
    await loadPosts(stage, subCategory)
  }

  async function loadPosts(stage, subCategory) {
    loading.value = true
    error.value = ''
    try {
      const { data } = await fetchCurriculumPosts(stage, subCategory)
      postsByKey.value = { ...postsByKey.value, [keyFor(stage, subCategory)]: data ?? [] }
    } catch {
      error.value = '게시글을 불러오지 못했습니다.'
    } finally {
      loading.value = false
    }
  }

  // 관리자가 추가/카테고리 변경/제외한 뒤 - 현재 보고 있는 목록과 카운트를 함께 새로고침
  async function refresh() {
    await Promise.all([loadCounts(), loadPosts(selectedStage.value, selectedSubCategory.value)])
  }

  function currentPosts() {
    return postsByKey.value[keyFor(selectedStage.value, selectedSubCategory.value)] ?? []
  }

  return {
    selectedStage,
    selectedSubCategory,
    counts,
    loading,
    error,
    loadCounts,
    selectStage,
    loadPosts,
    refresh,
    currentPosts,
  }
})

// SKALA 커리큘럼 탭 공개 조회 API
import http from './http'

// 단계(+선택적 하위 카테고리)별 게시글 목록
export function fetchCurriculumPosts(stage, subCategory) {
  return http.get('/api/curriculum/posts', { params: { stage, subCategory: subCategory || undefined } })
}

// 다이어그램 카드에 표시할 단계별 게시글 수
export function fetchCurriculumCounts() {
  return http.get('/api/curriculum/counts')
}

// 하위 카테고리 필터 pill에 표시할 단계별 하위 카테고리 게시글 수
export function fetchCurriculumSubCategoryCounts() {
  return http.get('/api/curriculum/counts/subcategory')
}

// 공지 작성 시 "이동 경로" 프리셋 - 관리자가 클릭하면 입력란에 채워지고, 그 상태에서 직접 수정도 가능
export const ANNOUNCEMENT_LINK_PRESETS = [
  { label: '없음', path: '' },
  { label: '홈', path: '/' },
  { label: '피드', path: '/feed' },
  { label: '피드 - 링크 모음 탭', path: '/feed?tab=links' },
  { label: '피드 - 링크 모음 탭(기타>맛집)', path: '/feed?tab=links&category=기타&tag=맛집' },
  { label: '마이페이지', path: '/mypage' },
]

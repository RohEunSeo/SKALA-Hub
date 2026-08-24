// 카테고리/태그 정의 (Sidebar, CategoryFilter, Home 폴더 카드 공용) - CLAUDE.md 카테고리 체계
export const CATEGORIES = [
  { value: '개발 툴·환경', label: '개발 툴·환경', shortLabel: '개발 툴·환경', icon: '🛠️', color: '#5B8DEF' },
  {
    value: '학습자료',
    label: '학습 자료',
    shortLabel: '학습자료',
    icon: '📚',
    color: '#2BB3A3',
    tags: [
      { value: '영상', label: '영상' },
      { value: '블로그·글', label: '블로그·글' },
      { value: '깃허브', label: '깃허브·코드' },
    ],
  },
  { value: '자격증·취업', label: '자격증·취업', shortLabel: '자격증·취업', icon: '🏆', color: '#E8A33D' },
  {
    value: '교육생 서비스',
    label: '교육생 서비스',
    shortLabel: '교육생 서비스',
    icon: '🌐',
    color: '#8B6FD6',
    // 사이드바에 노출되는 필터용 하위카테고리
    tags: [
      { value: '캠퍼스 생활 편의', label: '캠퍼스 생활 편의' },
      { value: 'SKCT / SQLD', label: 'SKCT / SQLD' },
      { value: '학습 및 스터디매칭', label: '학습 및 스터디매칭' },
      { value: '개발 생산성 및 툴', label: '개발 생산성 및 툴' },
      { value: '기타', label: '기타' },
    ],
    // 관리자 화면 전용 체크박스 - 사이드바에는 노출하지 않음
    adminOnlyTags: [
      { value: '사이트', label: '[사이트]' },
      { value: '앱·툴', label: '[앱·툴]' },
      { value: 'Extension', label: '[Extension]' },
    ],
  },
  { value: '교수님', label: '교수님 게시글', shortLabel: '교수님', icon: '🧑‍🏫', color: '#E0607D' },
  {
    value: '기타',
    label: '기타',
    shortLabel: '기타',
    icon: '💬',
    color: '#B0B4BF',
    tags: [
      { value: '인사이트·경험 공유', label: '인사이트·경험 공유' },
      { value: '오류 해결', label: '오류 해결' },
      { value: '분실물', label: '분실물' },
      { value: '맛집', label: '맛집' },
      { value: '그 외', label: '그 외' },
    ],
  },
]

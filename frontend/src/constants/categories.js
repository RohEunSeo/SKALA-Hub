// 카테고리/태그 정의 (Sidebar, CategoryFilter, Home 폴더 카드 공용) - CLAUDE.md 카테고리 체계
export const CATEGORIES = [
  { value: '개발도구', label: '개발 툴.환경', shortLabel: '개발도구', icon: '🛠️', color: '#5B8DEF' },
  {
    value: '학습자료',
    label: '학습 자료',
    shortLabel: '학습자료',
    icon: '📚',
    color: '#2BB3A3',
    tags: [
      { value: '영상', label: '영상' },
      { value: '아티클', label: '아티클·블로그' },
      { value: '깃허브', label: '깃허브·코드' },
    ],
  },
  { value: '취업자격증', label: '취업.자격증', shortLabel: '취업자격증', icon: '🏆', color: '#E8A33D' },
  { value: '교육생서비스', label: '교육생 서비스', shortLabel: '교육생서비스', icon: '🌐', color: '#34AEBF' },
  { value: '교수님', label: '교수님 게시글', shortLabel: '교수님', icon: '🧑‍🏫', color: '#E0607D' },
  { value: '기타', label: '기타', shortLabel: '기타', icon: '💬', color: '#8890A3' },
]

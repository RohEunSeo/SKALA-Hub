// SKALA 커리큘럼 4단계 정의 (SKALA 교육 커리큘럼 ①~④, curriculum_posts.stage/sub_category 값과 1:1 대응)
import skLogo from '../assets/sk_logo.png'

export const CURRICULUM_STAGES = [
  {
    value: 'fullstack',
    label: 'Full-stack Engineering',
    shortLabel: 'Full-stack',
    subtitle: 'AI 서비스를 위한 SW 기초',
    icon: '💻',
    color: '#8C6FD6',
    subCategories: [
      { value: 'git', label: 'Git' },
      { value: 'frontend', label: '프론트엔드' },
      { value: 'backend', label: '백엔드' },
      { value: 'db', label: 'DB' },
    ],
  },
  {
    value: 'data_aiops',
    label: '데이터 분석 및 AIOps',
    shortLabel: '데이터·AIOps',
    subtitle: 'AI의 서비스화',
    icon: '📊',
    color: '#6FCF97',
    subCategories: [
      { value: 'python', label: 'Python' },
      { value: 'analysis', label: '데이터분석·통계' },
      { value: 'ml_dl', label: '머신러닝·딥러닝' },
      { value: 'aiops', label: '모델서빙·AIOps' },
    ],
  },
  {
    value: 'cloud_native',
    label: 'Cloud Native AI',
    shortLabel: 'Cloud Native',
    subtitle: 'AI 서비스 배포 및 운영',
    icon: '☁️',
    color: '#3FA7E1',
    subCategories: [
      { value: 'agile_msa', label: 'Agile·MSA' },
      { value: 'container', label: '컨테이너' },
      { value: 'kubernetes', label: '쿠버네티스' },
      { value: 'devops', label: 'DevOps' },
    ],
  },
  {
    value: 'genai',
    label: '생성형 AI 서비스 개발',
    shortLabel: '생성형AI',
    subtitle: 'AI 서비스의 고도화',
    icon: '🤖',
    color: '#E0607D',
    subCategories: [
      { value: 'llm', label: 'LLM 모델 이해·활용' },
      { value: 'vectordb_rag', label: 'Vector DB·RAG' },
      { value: 'agent', label: 'AI Agent 설계·구축' },
      { value: 'sllm', label: 'sLLM·Fine Tuning' },
      { value: 'mcp', label: 'MCP 연계' },
    ],
  },
  {
    value: 'ax',
    label: 'AX',
    shortLabel: 'AX',
    subtitle: 'AI 전환·산업 활용 사례',
    icon: '✨',
    iconImage: skLogo,
    color: '#E8823D',
    subCategories: [],
  },
]

export function findStage(value) {
  return CURRICULUM_STAGES.find((s) => s.value === value)
}

export function findSubCategory(stageValue, subValue) {
  return findStage(stageValue)?.subCategories.find((s) => s.value === subValue)
}

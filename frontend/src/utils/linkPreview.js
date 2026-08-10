// 링크 모음 카드 - 서비스명(service_name)별 썸네일 이모지/배경색 매핑
const THEMES = [
  { match: 'youtube', emoji: '▶️', bg: '#ffe8e8' },
  { match: 'github', emoji: '💻', bg: '#eef0f3' },
  { match: 'substack', emoji: '📝', bg: '#eafaf1' },
  { match: 'medium', emoji: '📝', bg: '#eafaf1' },
  { match: 'velog', emoji: '📝', bg: '#eafaf1' },
  { match: 'arxiv', emoji: '📑', bg: '#fff4e0' },
]
const DEFAULT_THEME = { emoji: '🔗', bg: '#f1eefc' }

export function getLinkTheme(serviceName) {
  const target = serviceName?.toLowerCase() ?? ''
  return THEMES.find((theme) => target.includes(theme.match)) ?? DEFAULT_THEME
}

// 카드에 표시할 출처 텍스트 - service_name이 없으면 링크 도메인으로 대체
export function getLinkSource(attachment) {
  if (attachment.serviceName) return attachment.serviceName
  const url = attachment.titleLink || attachment.fromUrl
  if (!url) return ''
  try {
    return new URL(url).hostname.replace(/^www\./, '')
  } catch {
    return ''
  }
}

// 카테고리 배지를 썸네일 위에 얹을 때 너무 쨍하지 않도록 반투명 배경으로 변환 (#RRGGBB -> rgba(...))
export function hexToRgba(hex, alpha) {
  const clean = hex.replace('#', '')
  const r = parseInt(clean.substring(0, 2), 16)
  const g = parseInt(clean.substring(2, 4), 16)
  const b = parseInt(clean.substring(4, 6), 16)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

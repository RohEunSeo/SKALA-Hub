// 한국어 상대시간 포맷 (게시글 작성시각, 마지막 동기화 시각 공용)
export function formatRelativeTime(dateInput) {
  if (!dateInput) return ''
  const date = new Date(dateInput)
  const diffMs = Date.now() - date.getTime()
  const diffMin = Math.floor(diffMs / 60000)

  if (diffMin < 1) return '방금 전'
  if (diffMin < 60) return `${diffMin}분 전`

  const diffHour = Math.floor(diffMin / 60)
  if (diffHour < 24) return `${diffHour}시간 전`

  const diffDay = Math.floor(diffHour / 24)
  if (diffDay === 1) return '어제'
  if (diffDay < 7) return `${diffDay}일 전`

  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`
}

// 게시글 작성 시각을 "M월 D일 HH:mm" 절대 시각으로 표시 (상대시간과 함께 노출용)
export function formatDateTime(dateInput) {
  if (!dateInput) return ''
  const date = new Date(dateInput)
  const month = date.getMonth() + 1
  const day = date.getDate()
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${month}월 ${day}일 ${hour}:${minute}`
}

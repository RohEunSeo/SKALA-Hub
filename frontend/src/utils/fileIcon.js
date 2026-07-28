// 파일 확장자 → 아이콘 이모지
const ICON_MAP = {
  pdf: '📄',
  doc: '📝',
  docx: '📝',
  hwp: '📝',
  ppt: '📽️',
  pptx: '📽️',
  xls: '📊',
  xlsx: '📊',
  csv: '📊',
  zip: '🗜️',
  '7z': '🗜️',
  rar: '🗜️',
}

export function getFileIcon(filetype) {
  if (!filetype) return '📎'
  return ICON_MAP[filetype.toLowerCase()] || '📎'
}

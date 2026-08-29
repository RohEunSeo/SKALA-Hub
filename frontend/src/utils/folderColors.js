// 맥 Finder 폴더 카드 색상 가공 - 카테고리 원색(cat.color)을 그대로 쓰면 너무 쨍해서
// 흰색/검은색을 섞어 연한 파스텔 톤으로 변환. 홈 화면 "카테고리별 아카이브"와 피드 탭
// "SKALA 커리큘럼" 폴더 카드가 동일한 로직을 공유한다.
function hexToRgb(hex) {
  const value = hex.replace('#', '')
  return [0, 2, 4].map((i) => parseInt(value.slice(i, i + 2), 16))
}

function mixWith(hex, target, amount, alpha = 1) {
  const [r, g, b] = hexToRgb(hex)
  const mix = (c) => Math.round(c + (target - c) * amount)
  return alpha === 1 ? `rgb(${mix(r)}, ${mix(g)}, ${mix(b)})` : `rgba(${mix(r)}, ${mix(g)}, ${mix(b)}, ${alpha})`
}

// 본체는 원색에 흰색을 섞어 연하게, 탭은 본체보다 더 밝게 - 탭/본체 톤 차이로 입체감
export function folderBodyColor(color) {
  return mixWith(color, 255, 0.4)
}

export function folderTabColor(color) {
  return mixWith(color, 255, 0.68)
}

export function folderTextColor(color) {
  return mixWith(color, 0, 0.42)
}

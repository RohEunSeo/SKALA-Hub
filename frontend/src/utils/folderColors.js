// 맥 Finder 폴더 카드 색상 가공 - 카테고리 원색(cat.color)을 그대로 쓰면 너무 쨍해서
// 흰색/검은색을 섞어 연한 파스텔 톤으로 변환. FolderCard(홈 "카테고리별 아카이브" +
// 피드 "SKALA 커리큘럼")가 공유한다.
function hexToRgb(hex) {
  const value = hex.replace('#', '')
  return [0, 2, 4].map((i) => parseInt(value.slice(i, i + 2), 16))
}

function mixWith(hex, target, amount, alpha = 1) {
  const [r, g, b] = hexToRgb(hex)
  const mix = (c) => Math.round(c + (target - c) * amount)
  return alpha === 1 ? `rgb(${mix(r)}, ${mix(g)}, ${mix(b)})` : `rgba(${mix(r)}, ${mix(g)}, ${mix(b)}, ${alpha})`
}

// 예전 폴더 카드 본체 색(원색 + 흰색 40%)을 기준으로 앞판은 위쪽만 살짝 밝은 세로 그라데이션,
// 뒷판은 앞판 아래쪽과 같은 톤 - 너무 연하면 칙칙해 보여서 흰색 비율을 낮게 유지
export function folderFrontTopColor(color) {
  return mixWith(color, 255, 0.46)
}

export function folderBodyColor(color) {
  return mixWith(color, 255, 0.38)
}

export function folderBackColor(color) {
  return mixWith(color, 255, 0.38)
}

export function folderTextColor(color) {
  return mixWith(color, 0, 0.42)
}

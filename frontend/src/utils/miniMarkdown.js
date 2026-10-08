// README용 아주 작은 마크다운 → HTML (제목/굵게/목록/인용/링크만). 먼저 HTML을 이스케이프해서 XSS 방지
const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
const inline = (s) =>
  esc(s)
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/`(.+?)`/g, '<code>$1</code>')
    .replace(/\[(.+?)\]\((https?:\/\/[^\s)]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>')

/**
 * 줄 구조는 건드리지 않고 **굵게** 와 `코드` 만 처리한다.
 * 챗봇 답변용 - 거기는 white-space: pre-wrap 으로 줄바꿈과 '·'를 이미 살리고 있어서,
 * miniMarkdown 처럼 <p>/<ul> 로 감싸면 간격이 다 틀어진다.
 * 이스케이프를 먼저 하므로 글 본문에 HTML이 섞여 있어도 글자로만 보인다.
 */
export function inlineMarkdown(src = '') {
  return esc(src)
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/`(.+?)`/g, '<code>$1</code>')
}

export function miniMarkdown(src = '') {
  const out = []
  let list = false
  for (const line of src.split('\n')) {
    const li = line.match(/^[-*] (.*)/)
    if (list && !li) { out.push('</ul>'); list = false }
    if (li) {
      if (!list) { out.push('<ul>'); list = true }
      out.push(`<li>${inline(li[1])}</li>`)
    } else if (/^### /.test(line)) out.push(`<h3>${inline(line.slice(4))}</h3>`)
    else if (/^## /.test(line)) out.push(`<h2>${inline(line.slice(3))}</h2>`)
    else if (/^# /.test(line)) out.push(`<h1>${inline(line.slice(2))}</h1>`)
    else if (/^> /.test(line)) out.push(`<blockquote>${inline(line.slice(2))}</blockquote>`)
    else if (line.trim()) out.push(`<p>${inline(line)}</p>`)
  }
  if (list) out.push('</ul>')
  return out.join('')
}

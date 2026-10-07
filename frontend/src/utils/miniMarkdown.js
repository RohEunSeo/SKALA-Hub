// README용 아주 작은 마크다운 → HTML (제목/굵게/목록/인용/링크만). 먼저 HTML을 이스케이프해서 XSS 방지
const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
const inline = (s) =>
  esc(s)
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/`(.+?)`/g, '<code>$1</code>')
    .replace(/\[(.+?)\]\((https?:\/\/[^\s)]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>')

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

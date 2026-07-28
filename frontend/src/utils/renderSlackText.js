// 슬랙 mrkdwn 원문을 HTML로 변환 (굵게/기울임/취소선/인용문/코드/링크/멘션/이모지)
import { emojify } from 'node-emoji'
import emojiNameMap from 'emoji-name-map'

// 슬랙 shortcode 이름이 node-emoji/emoji-name-map과 다른 대표적인 경우 (예: thumbsup ↔ +1)
const SLACK_NAME_REDIRECTS = {
  thumbsup: '+1',
  thumbsdown: '-1',
  poop: 'hankey',
  shit: 'hankey',
  facepunch: 'punch',
  the_horns: 'sign_of_the_horns',
}

function lookupEmoji(shortcode) {
  const viaNodeEmoji = emojify(shortcode)
  if (viaNodeEmoji !== shortcode) {
    return viaNodeEmoji
  }
  return emojiNameMap.get(shortcode) ?? null
}

// node-emoji → emoji-name-map(더 최신 유니코드 포함) → 이름 리다이렉트 순서로 조회
function emojifyWithFallback(shortcode) {
  const direct = lookupEmoji(shortcode)
  if (direct) {
    return direct
  }
  const name = shortcode.slice(1, -1).toLowerCase()
  const redirect = SLACK_NAME_REDIRECTS[name]
  if (redirect) {
    const viaRedirect = lookupEmoji(`:${redirect}:`)
    if (viaRedirect) {
      return viaRedirect
    }
  }
  return shortcode
}

function escapeHtml(str) {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function placeholder(type, index) {
  return `@@${type}${index}@@`
}

export function renderSlackText(raw) {
  if (!raw) return ''

  const codeBlocks = []
  const inlineCodes = []
  const links = []
  const emojis = []
  const mentions = []

  let text = raw

  // 1. 코드블록(```...```) - 원문 상태에서 먼저 추출 (내부 텍스트만 escape)
  text = text.replace(/```([\s\S]*?)```/g, (_, code) => {
    codeBlocks.push(escapeHtml(code.replace(/^\n/, '').replace(/\n$/, '')))
    return placeholder('CODEBLOCK', codeBlocks.length - 1)
  })

  // 2. 인라인 코드(`...`)
  text = text.replace(/`([^`\n]+)`/g, (_, code) => {
    inlineCodes.push(escapeHtml(code))
    return placeholder('INLINECODE', inlineCodes.length - 1)
  })

  // 3. 링크 <url|label> 또는 <url>
  text = text.replace(/<(https?:\/\/[^|>\s]+)\|([^>]+)>/g, (_, url, label) => {
    links.push({ url, label })
    return placeholder('LINK', links.length - 1)
  })
  text = text.replace(/<(https?:\/\/[^|>\s]+)>/g, (_, url) => {
    links.push({ url, label: url })
    return placeholder('LINK', links.length - 1)
  })

  // 3-1. 사용자 멘션 <@ID|이름>, 채널 멘션 <#ID|이름>, 특수 멘션 <!here>/<!channel>/<!everyone>
  text = text.replace(/<@[A-Z0-9]+\|([^>]+)>/g, (_, name) => {
    mentions.push(`@${name}`)
    return placeholder('MENTION', mentions.length - 1)
  })
  text = text.replace(/<#[A-Z0-9]+\|([^>]+)>/g, (_, name) => {
    mentions.push(`#${name}`)
    return placeholder('MENTION', mentions.length - 1)
  })
  text = text.replace(/<!(here|channel|everyone)>/g, (_, kind) => {
    mentions.push(`@${kind}`)
    return placeholder('MENTION', mentions.length - 1)
  })

  // 4. 나머지 텍스트 HTML escape (플레이스홀더는 영문/숫자/@만 포함되어 영향 없음)
  text = escapeHtml(text)

  // 5. 인용문(>) - 연속된 줄을 묶어서 blockquote로
  const lines = text.split('\n')
  const outLines = []
  let quoteBuffer = []
  const flushQuote = () => {
    if (quoteBuffer.length > 0) {
      outLines.push(`<blockquote class="slack-quote">${quoteBuffer.join('<br>')}</blockquote>`)
      quoteBuffer = []
    }
  }
  for (const line of lines) {
    const match = line.match(/^&gt;\s?(.*)$/)
    if (match) {
      quoteBuffer.push(match[1])
    } else {
      flushQuote()
      outLines.push(line)
    }
  }
  flushQuote()
  text = outLines.join('\n')

  // 6. 이모지 shortcode(:name:)를 먼저 플레이스홀더로 분리 - 커스텀/미등록 shortcode의 밑줄이
  // 기울임(_) 규칙과 충돌해 깨지는 것을 방지 (node-emoji가 모르는 shortcode는 원문 그대로 유지됨)
  text = text.replace(/:[a-z0-9_+-]+:/gi, (shortcode) => {
    emojis.push(emojifyWithFallback(shortcode))
    return placeholder('EMOJI', emojis.length - 1)
  })

  // 7. 굵게/기울임/취소선
  text = text.replace(/\*([^*\n]+)\*/g, '<strong>$1</strong>')
  text = text.replace(/_([^_\n]+)_/g, '<em>$1</em>')
  text = text.replace(/~([^~\n]+)~/g, '<del>$1</del>')

  // 8. 남은 줄바꿈 처리 (코드블록 내부 줄바꿈은 플레이스홀더 복원 전이라 영향 없음)
  text = text.replace(/\n/g, '<br>')

  // 9. 플레이스홀더 복원
  text = text.replace(
    /@@CODEBLOCK(\d+)@@/g,
    (_, i) => `<pre class="slack-codeblock"><code>${codeBlocks[i]}</code></pre>`,
  )
  text = text.replace(
    /@@INLINECODE(\d+)@@/g,
    (_, i) => `<code class="slack-inline-code">${inlineCodes[i]}</code>`,
  )
  text = text.replace(/@@LINK(\d+)@@/g, (_, i) => {
    const { url, label } = links[i]
    return `<a href="${escapeHtml(url)}" target="_blank" rel="noopener noreferrer" class="slack-link">${escapeHtml(label)}</a>`
  })
  text = text.replace(/@@EMOJI(\d+)@@/g, (_, i) => emojis[i])
  text = text.replace(/@@MENTION(\d+)@@/g, (_, i) => `<span class="slack-mention">${escapeHtml(mentions[i])}</span>`)

  return text
}

// 목록 미리보기용 - 마크다운 기호/링크 문법을 제거한 순수 텍스트로 변환 (잘라내도 태그가 깨지지 않음)
export function stripSlackMarkdown(raw) {
  if (!raw) return ''

  let text = raw
  text = text.replace(/```([\s\S]*?)```/g, (_, code) => code.trim())
  text = text.replace(/`([^`\n]+)`/g, '$1')
  text = text.replace(/<(https?:\/\/[^|>\s]+)\|([^>]+)>/g, '$2')
  text = text.replace(/<(https?:\/\/[^|>\s]+)>/g, '$1')
  text = text.replace(/<@[A-Z0-9]+\|([^>]+)>/g, '@$1')
  text = text.replace(/<#[A-Z0-9]+\|([^>]+)>/g, '#$1')
  text = text.replace(/<!(here|channel|everyone)>/g, '@$1')
  text = text.replace(/^>\s?/gm, '')
  text = text.replace(/:[a-z0-9_+-]+:/gi, (shortcode) => emojifyWithFallback(shortcode))
  text = text.replace(/\*([^*\n]+)\*/g, '$1')
  text = text.replace(/_([^_\n]+)_/g, '$1')
  text = text.replace(/~([^~\n]+)~/g, '$1')
  text = text.replace(/\n+/g, ' ')

  return text.trim()
}

function escapeRegExp(str) {
  return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

// 렌더링된 HTML의 순수 텍스트 노드에서만 검색어를 <mark>로 감싼다 (태그/속성은 절대 건드리지 않음)
export function highlightInHtml(html, keyword) {
  if (!keyword || !keyword.trim()) return html

  const container = document.createElement('div')
  container.innerHTML = html

  const regex = new RegExp(`(${escapeRegExp(keyword.trim())})`, 'gi')
  const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT)
  const targets = []
  let node = walker.nextNode()
  while (node) {
    if (regex.test(node.textContent)) {
      targets.push(node)
    }
    regex.lastIndex = 0
    node = walker.nextNode()
  }

  for (const textNode of targets) {
    const span = document.createElement('span')
    span.innerHTML = textNode.textContent.replace(regex, '<mark class="slack-highlight">$1</mark>')
    textNode.replaceWith(...span.childNodes)
  }

  return container.innerHTML
}

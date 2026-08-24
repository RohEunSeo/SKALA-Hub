<script setup>
// 링크 모음 탭 - URL 하나(=같은 링크를 올린 게시글을 전부 묶은 그룹) 당 카드 1개
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { usePostsStore } from '../stores/posts'
import { updateLinkAsAdmin } from '../api/admin'
import { getLinkTheme, getLinkSource, hexToRgba } from '../utils/linkPreview'
import { formatRelativeTime } from '../utils/relativeTime'
import { CATEGORIES } from '../constants/categories'

const props = defineProps({
  group: { type: Object, required: true },
})

const router = useRouter()
const authStore = useAuthStore()
const postsStore = usePostsStore()

// 외부 사이트 og:image가 CORP 정책 등으로 브라우저에서 차단되어 아예 로드 실패하는 경우가 있음 -
// 깨진 이미지 대신 썸네일 없을 때와 동일하게 이모지 플레이스홀더로 자연스럽게 전환
const imageFailed = ref(false)

const theme = getLinkTheme(props.group.serviceName)
const linkUrl = props.group.titleLink || props.group.url
// getLinkSource는 attachment 형태(serviceName/titleLink/fromUrl)를 기대해서 그룹 필드를 맞춰서 넘김
const source = getLinkSource({
  serviceName: props.group.serviceName,
  titleLink: props.group.titleLink,
  fromUrl: props.group.url,
})
// 대표 게시글(반응수 가장 높은 게시글)의 카테고리를 배지로 표시
const categoryInfo = computed(() => CATEGORIES.find((cat) => cat.value === props.group.category))
// 카테고리 하위 유형(학습자료: 영상/블로그·글/깃허브, 기타: 인사이트·경험 공유/오류 해결/분실물/맛집/그 외
// 등) 배지 - 백엔드가 이미 확정해서 내려준 group.typeTag 하나만 보고 표시(게시글 tags 배열을 직접 안
// 봄 - 게시글에 태그가 여러 개 달려있으면 이 링크 하나의 실제 유형과 어긋날 수 있어서 백엔드에서 하나로
// 정리해서 내려줌). 하위 태그가 없는 카테고리는 항상 null
const SUBTAG_EMOJI = {
  영상: '🎬',
  '블로그·글': '📝',
  깃허브: '💻',
  '인사이트·경험 공유': '💡',
  '오류 해결': '🛠️',
  분실물: '🔎',
  맛집: '🍽️',
  '그 외': '🏷️',
}
// 하위 태그(tags)가 정의된 카테고리에서만 관리자가 유형 배지를 직접 지정할 수 있게 함(자동 추정이
// 틀렸을 때 보정용) - 학습자료 전용이 아니라 categories.js에 tags가 있는 카테고리 전체에 적용됨
const hasTypeTags = computed(() => (categoryInfo.value?.tags?.length ?? 0) > 0)
const subTagInfo = computed(() => {
  if (!hasTypeTags.value || !props.group.typeTag) return null
  const tag = categoryInfo.value?.tags?.find((t) => t.value === props.group.typeTag)
  return tag ? { label: tag.label, emoji: SUBTAG_EMOJI[tag.value] ?? '🏷️' } : null
})
const TYPE_TAG_OPTIONS = computed(() => [
  { value: '', label: '자동 감지' },
  ...(categoryInfo.value?.tags ?? []),
])
const creatorsLabel = computed(() => (props.group.creators ?? []).map((name) => `${name}님`).join(', '))
// 만든 사람 표시는 "교육생 서비스" 카테고리에서만 의미가 있음(누가 만들었는지가 중요한 카테고리) - 나머지
// 카테고리(학습자료/GitHub 링크 등)는 만든 사람 개념이 없어서 굳이 표시하지 않음
const showCreators = computed(() => props.group.category === '교육생 서비스')
// 만든 사람을 출처(GitHub/YouTube 등) 옆에 붙여서 상단 한 줄로 처리 - 하단은 제목/설명이 안 잘리게 비워둠
const sourceLabel = computed(() =>
  showCreators.value && creatorsLabel.value ? `${source} (${creatorsLabel.value})` : source,
)

// 관리자만 카드 위에서 바로 제목/만든사람 수정, 숨김 가능 - 별도 /admin 페이지 이동 없이
const isAdmin = computed(() => authStore.effectiveIsAdmin)
// 정렬 드롭다운의 "숨김" 옵션으로 보고 있는 카드인지 - 이때는 🗑️(숨김) 대신 ♻️(복원) 액션을 보여줌
const isHiddenView = computed(() => postsStore.showHiddenLinks)

const editing = ref(false)
const editTitle = ref('')
const editSource = ref('')
const editCreators = ref('')
const editTypeTag = ref('')
const editEmoji = ref('')
const expanded = ref(false)

function goToDetail(postId) {
  if (!postId) return
  router.push({ name: 'post-detail', params: { id: postId }, query: { from: 'links' } })
}

function toggleExpand() {
  expanded.value = !expanded.value
}

// 편집/목록 펼침 중일 때는 카드 어디를 눌러도 외부 링크로 이동하지 않게 막음
function handleCardClick(event) {
  if (editing.value || expanded.value) {
    event.preventDefault()
  }
}

function startEdit() {
  editTitle.value = props.group.title || ''
  editSource.value = source
  editCreators.value = (props.group.creators ?? []).join(', ')
  editTypeTag.value = props.group.typeTag || ''
  editEmoji.value = props.group.emoji || ''
  editing.value = true
}

function cancelEdit() {
  editing.value = false
}

async function saveEdit() {
  await updateLinkAsAdmin({
    url: props.group.url,
    title: editTitle.value,
    source: editSource.value,
    creators: editCreators.value,
    typeTag: editTypeTag.value,
    emoji: editEmoji.value,
  })
  editing.value = false
  postsStore.invalidateLinkGroupsCache()
  if (isHiddenView.value) {
    postsStore.fetchHiddenLinkGroups()
  } else {
    postsStore.fetchLinkGroups(true)
  }
  // 유형/분류 필터 옆 숫자(예: "영상 (30)")도 방금 수정 내용을 바로 반영하도록 갱신
  postsStore.refreshCategoryCounts()
}

// url 기준 전역 숨김 - 같은 링크가 다른 게시글에도 있으면 전부 함께 숨겨짐
async function hideLink() {
  if (!confirm('이 링크를 링크 모음에서 숨길까요? (같은 링크가 다른 게시글에도 있으면 전부 숨겨집니다)')) {
    return
  }
  await updateLinkAsAdmin({ url: props.group.url, hidden: true })
  postsStore.invalidateLinkGroupsCache()
  postsStore.fetchLinkGroups(true)
  postsStore.refreshCategoryCounts()
}

// 숨김 뷰에서 복원 - 다시 모든 사용자에게 보이게 됨
async function restoreLink() {
  await updateLinkAsAdmin({ url: props.group.url, hidden: false })
  postsStore.invalidateLinkGroupsCache()
  postsStore.fetchHiddenLinkGroups()
  postsStore.refreshCategoryCounts()
}
</script>

<template>
  <a class="link-card" :href="linkUrl" target="_blank" rel="noopener noreferrer" @click="handleCardClick">
    <div class="link-card-thumb" :style="{ background: theme.bg }">
      <img v-if="group.imageUrl && !imageFailed" :src="group.imageUrl" alt="" @error="imageFailed = true" />
      <span v-else class="link-card-emoji">{{ group.emoji || theme.emoji }}</span>
      <div class="link-card-badges">
        <span
          v-if="categoryInfo"
          class="link-card-category-badge"
          :style="{ background: hexToRgba(categoryInfo.color, 0.72) }"
        >
          {{ categoryInfo.icon }} {{ categoryInfo.shortLabel }}
        </span>
        <span v-if="subTagInfo" class="link-card-subtag-badge">
          {{ subTagInfo.emoji }} {{ subTagInfo.label }}
        </span>
      </div>
      <div v-if="isAdmin" class="link-card-admin-actions">
        <span class="link-card-admin-btn" title="수정" @click.stop.prevent="startEdit">✏️</span>
        <span v-if="isHiddenView" class="link-card-admin-btn" title="복원" @click.stop.prevent="restoreLink">
          ♻️
        </span>
        <span v-else class="link-card-admin-btn" title="숨김" @click.stop.prevent="hideLink">🗑️</span>
      </div>
    </div>
    <div class="link-card-body">
      <template v-if="editing">
        <input
          v-model="editSource"
          class="link-card-edit-input link-card-edit-source"
          placeholder="표시할 출처(도메인) 텍스트"
          @click.stop.prevent
          @keydown.stop
        />
        <input
          v-model="editTitle"
          class="link-card-edit-input"
          placeholder="표시할 제목"
          @click.stop.prevent
          @keydown.stop
        />
        <input
          v-model="editCreators"
          class="link-card-edit-input"
          placeholder="만든 사람 (쉼표로 구분, 예: 김현수, 이영희)"
          @click.stop.prevent
          @keydown.stop
        />
        <select
          v-if="hasTypeTags"
          v-model="editTypeTag"
          class="link-card-edit-input link-card-edit-select"
          @click.stop.prevent
        >
          <option v-for="opt in TYPE_TAG_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
        </select>
        <input
          v-model="editEmoji"
          class="link-card-edit-input"
          placeholder="썸네일 이모지 (Mac: control+cmd+space)"
          maxlength="8"
          @click.stop.prevent
          @keydown.stop
        />
        <div class="link-card-edit-actions">
          <span class="link-card-edit-save" @click.stop.prevent="saveEdit">저장</span>
          <span class="link-card-edit-cancel" @click.stop.prevent="cancelEdit">취소</span>
        </div>
      </template>
      <template v-else>
        <div class="link-card-source">{{ sourceLabel }}</div>
        <div class="link-card-title">{{ group.title || linkUrl }}</div>
        <!-- 내용이 없어도 항상 같은 높이를 차지해서 카드마다 아래 통계/버튼 위치가 어긋나지 않게 함.
             길면 CSS line-clamp가 "..."으로 자연스럽게 잘라줌 -->
        <div class="link-card-desc">{{ group.text }}</div>
        <div class="link-card-footer">
          <span class="link-card-stats">
            <span>👍 {{ group.reactionCount ?? 0 }}</span>
            <span>💬 {{ group.replyCount ?? 0 }}</span>
          </span>
          <span
            v-if="group.posts.length <= 1"
            class="link-card-detail"
            @click.stop.prevent="goToDetail(group.posts[0]?.id)"
          >
            게시글 보러가기
          </span>
          <span v-else class="link-card-detail" @click.stop.prevent="toggleExpand">
            게시글 {{ group.posts.length }}개 보러가기 {{ expanded ? '▲' : '▼' }}
          </span>
        </div>
        <div v-if="expanded" class="link-card-post-list" @click.stop.prevent>
          <div
            v-for="p in group.posts"
            :key="p.id"
            class="link-card-post-item"
            @click.stop.prevent="goToDetail(p.id)"
          >
            <span class="link-card-post-author">{{ p.userName }}</span>
            <span class="link-card-post-date">{{ formatRelativeTime(p.createdAt) }}</span>
            <span class="link-card-post-stats">👍{{ p.reactionCount ?? 0 }} 💬{{ p.replyCount ?? 0 }}</span>
          </div>
        </div>
      </template>
    </div>
  </a>
</template>

<style scoped>
.link-card {
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border: 1px solid rgba(26, 26, 46, 0.08);
  border-radius: 12px;
  overflow: hidden;
  text-decoration: none;
  cursor: pointer;
  transition: box-shadow 0.15s ease, transform 0.15s ease;
}

.link-card:hover {
  box-shadow: 0 4px 16px rgba(26, 26, 46, 0.1);
  transform: translateY(-2px);
}

/* 고정 px 높이 대신 비율로 지정 - 모바일은 2열이라 카드 폭이 데스크톱 3열보다 훨씬 좁은데, 높이가
   고정값이면 폭만 좁아져서 정사각형에 가까워짐(웹의 가로로 긴 직사각형 느낌과 달라짐). aspect-ratio를
   쓰면 카드 폭이 얼마든 항상 같은 비율(가로로 긴 직사각형)을 유지함 */
.link-card-thumb {
  position: relative;
  aspect-ratio: 2 / 1;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-bottom: 1px solid rgba(26, 26, 46, 0.08);
}

.link-card-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.link-card-emoji {
  font-size: 32px;
}

/* 카테고리 배지 + 유형 배지(학습자료만)를 한 줄에 나란히 배치 */
.link-card-badges {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.link-card-category-badge {
  color: #ffffff;
  font-size: 11px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 999px;
  white-space: nowrap;
  box-shadow: 0 1px 4px rgba(26, 26, 46, 0.2);
}

.link-card-subtag-badge {
  background: rgba(255, 255, 255, 0.92);
  color: #1a1a2e;
  font-size: 11px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 999px;
  white-space: nowrap;
  box-shadow: 0 1px 4px rgba(26, 26, 46, 0.2);
}

.link-card-admin-actions {
  position: absolute;
  top: 8px;
  left: 8px;
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.link-card:hover .link-card-admin-actions {
  opacity: 1;
}

.link-card-admin-btn {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 50%;
  font-size: 12px;
  cursor: pointer;
  box-shadow: 0 1px 4px rgba(26, 26, 46, 0.2);
}

.link-card-admin-btn:hover {
  background: #ffffff;
}

.link-card-body {
  padding: 12px 14px 14px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

/* 카드마다 아래 통계/버튼 위치가 어긋나지 않도록, source/title/desc 세 블록 모두 내용 유무·길이와
   무관하게 항상 같은 높이(line-height * 줄 수)를 차지하게 고정함. 넘치는 글자는 line-clamp가
   "..."으로 자연스럽게 잘라줌 - 만든 사람은 여기(source) 옆에 괄호로 붙음 */
.link-card-source {
  font-size: 12px;
  line-height: 1.35;
  height: 2.7em;
  color: #636e72;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: keep-all;
}

.link-card-title {
  font-size: 14px;
  line-height: 1.3;
  height: 2.6em;
  font-weight: 600;
  color: #1a1a2e;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.link-card-desc {
  font-size: 12px;
  line-height: 1.35;
  height: 2.7em;
  font-weight: 400;
  color: #9199a1;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 카드가 좁아지면(모바일 2열 등) 통계+버튼이 한 줄에 다 안 들어갈 수 있음 - flex-wrap이 없으면
   "게시글 N개 보러가기" 텍스트가 카드 밖으로 밀려서 .link-card의 overflow:hidden에 잘려버림.
   wrap을 주면 안 잘리고 아래 줄로 자연스럽게 내려감 */
.link-card-footer {
  margin-top: 6px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 4px 8px;
  flex-shrink: 0;
}

.link-card-stats {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: #636e72;
  white-space: nowrap;
}

.link-card-detail {
  font-size: 12px;
  color: #1264a3;
  font-weight: 600;
  white-space: nowrap;
}

.link-card-detail:hover {
  text-decoration: underline;
}

.link-card-post-list {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  border-top: 1px solid rgba(26, 26, 46, 0.08);
  padding-top: 8px;
}

.link-card-post-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 5px 4px;
  border-radius: 6px;
  cursor: pointer;
}

.link-card-post-item:hover {
  background: #f8f8fb;
}

.link-card-post-author {
  font-size: 12px;
  font-weight: 600;
  color: #1a1a2e;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.link-card-post-date {
  font-size: 11px;
  color: #9199a1;
  white-space: nowrap;
}

.link-card-post-stats {
  font-size: 11px;
  color: #636e72;
  white-space: nowrap;
}

.link-card-edit-input {
  width: 100%;
  font-size: 14px;
  font-weight: 600;
  color: #1a1a2e;
  border: 1px solid rgba(26, 26, 46, 0.15);
  border-radius: 8px;
  padding: 6px 8px;
  font-family: inherit;
}

.link-card-edit-input + .link-card-edit-input {
  margin-top: 6px;
}

.link-card-edit-source {
  font-size: 12px;
  font-weight: 400;
  color: #636e72;
}

.link-card-edit-select {
  font-size: 12px;
  font-weight: 600;
  color: #4a3f8f;
  cursor: pointer;
}

.link-card-edit-actions {
  margin-top: 8px;
  display: flex;
  gap: 8px;
}

.link-card-edit-save,
.link-card-edit-cancel {
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  padding: 4px 10px;
  border-radius: 6px;
}

.link-card-edit-save {
  background: #4a3f8f;
  color: #ffffff;
}

.link-card-edit-cancel {
  background: #f1eefc;
  color: #4a3f8f;
}
</style>

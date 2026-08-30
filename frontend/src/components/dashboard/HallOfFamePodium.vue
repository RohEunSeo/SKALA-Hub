<script setup>
// 대시보드 - 카테고리별 명예의 전당 Top3 포디움 (3등→2등→1등 순차 등장, v3.html PAGE 7 DASHBOARD 재현)
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { CATEGORIES } from '../../constants/categories'
import { stripSlackMarkdown } from '../../utils/renderSlackText'
import { useIsMobile } from '../../composables/useIsMobile'

const props = defineProps({
  hallOfFame: { type: Object, required: true }, // { [categoryValue]: [{ postId, userName, isInstructor, content, aiTitle, reactionCount, createdAt }] }
})

const router = useRouter()
const isMobile = useIsMobile()

const RANK_HEIGHTS = ['170px', '120px', '86px']
const RANK_TEXT_COLORS = ['#7a5b00', '#4a4e56', '#7a3512']
const RANK_THEMES = [
  { bg: 'linear-gradient(120deg,#F5D061,#FFF3B0,#E8B93F,#FFF3B0,#F5D061)', glow: '#E8B93F' },
  { bg: 'linear-gradient(120deg,#D8D8DE,#F5F5F7,#B8BCC4,#F5F5F7,#D8D8DE)', glow: '#B8BCC4' },
  { bg: 'linear-gradient(120deg,#C77B3D,#E8B589,#B5651D,#E8B589,#C77B3D)', glow: '#C77B3D' },
]

const activeIndex = ref(0)
const activeCategory = computed(() => CATEGORIES[activeIndex.value])
const entries = computed(() => props.hallOfFame[activeCategory.value.value] ?? [])

const podiumOrder = computed(() => {
  const n = entries.value.length
  if (n >= 3) return [1, 0, 2]
  if (n === 2) return [1, 0]
  if (n === 1) return [0]
  return []
})

// 3등(threshold=1) → 2등(threshold=2) → 1등(threshold=n) 순으로 순차 등장
function revealThreshold(rankIdx) {
  return entries.value.length - rankIdx
}

const cardStep = ref(0)
let revealTimers = []

function playReveal() {
  revealTimers.forEach(clearTimeout)
  revealTimers = []
  cardStep.value = 0
  ;[1, 2, 3].forEach((step, idx) => {
    revealTimers.push(setTimeout(() => (cardStep.value = step), 300 + idx * 550))
  })
}

function isRevealed(rankIdx) {
  return cardStep.value >= revealThreshold(rankIdx)
}

// 카드가 어느 방향으로 슬라이드해서 들어올지 - 다음 카테고리면 오른쪽에서, 이전이면 왼쪽에서 들어오게
const slideDirection = ref(1)

function selectCategory(idx) {
  if (idx === activeIndex.value) return
  slideDirection.value = idx > activeIndex.value ? 1 : -1
  activeIndex.value = idx
}

function prevCategory() {
  slideDirection.value = -1
  activeIndex.value = (activeIndex.value - 1 + CATEGORIES.length) % CATEGORIES.length
}

function nextCategory() {
  slideDirection.value = 1
  activeIndex.value = (activeIndex.value + 1) % CATEGORIES.length
}

// 카드 영역을 옆으로 드래그(마우스/터치 공통 - pointer events)해도 다음/이전 카테고리로 넘어가게
const dragStartX = ref(null)
const SWIPE_THRESHOLD_PX = 40

function onPointerDown(e) {
  dragStartX.value = e.clientX
}

function onPointerUp(e) {
  if (dragStartX.value == null) return
  const delta = e.clientX - dragStartX.value
  dragStartX.value = null
  if (Math.abs(delta) < SWIPE_THRESHOLD_PX) return
  if (delta < 0) nextCategory()
  else prevCategory()
}

// 트랙패드 좌우 두 손가락 스와이프는 클릭드래그가 아니라 가로 방향 wheel(deltaX) 이벤트로 들어온다.
// 손가락을 뗀 뒤에도 관성(모멘텀) 스크롤로 같은 제스처의 wheel 이벤트가 500ms 넘게 이어지는 경우가
// 있어서, "트리거 시점부터 고정 500ms" 쿨다운으로는 그 사이 관성 이벤트가 다시 임계값을 넘겨
// 한 번의 스와이프가 두 칸으로 넘어가 버렸다. 대신 "이 제스처(연속 wheel 이벤트)당 한 번만" 방식으로:
// wheel 이벤트가 들어올 때마다 무음 타이머를 다시 걸어두고, 실제로 이벤트가 멈춰서(=제스처 종료)
// 그 타이머가 끝나야만 다음 제스처를 다시 받아들인다
let wheelGestureActive = false
let wheelIdleTimer = null

function onWheel(e) {
  if (Math.abs(e.deltaX) <= Math.abs(e.deltaY)) return // 세로 스크롤은 페이지 스크롤 그대로 두기
  e.preventDefault()
  if (Math.abs(e.deltaX) < 12) return

  clearTimeout(wheelIdleTimer)
  wheelIdleTimer = setTimeout(() => (wheelGestureActive = false), 200)

  if (wheelGestureActive) return
  wheelGestureActive = true
  if (e.deltaX > 0) nextCategory()
  else prevCategory()
}

function goToPost(postId) {
  router.push({ name: 'post-detail', params: { id: postId }, query: { from: 'dashboard' } })
}

// AI 한 줄 제목이 있으면 그대로, 아직 없으면(생성 전/재료 없음) 원문 초반부로 대신 보여줌
// "검색·저장"처럼 가운뎃점(·)으로 이어진 단어는 그 자리에서 줄바꿈되면 어색해서, 단어 연결자(word
// joiner)로 감싸 가운뎃점 앞뒤에서는 줄이 안 끊기게 하고 공백에서만 자연스럽게 끊기게 함
function previewTitle(entry) {
  const title = entry.aiTitle ? entry.aiTitle : stripSlackMarkdown(entry.content).slice(0, 40)
  return title.replace(/·/g, '⁠·⁠')
}

watch(activeIndex, playReveal)
onMounted(playReveal)
onUnmounted(() => revealTimers.forEach(clearTimeout))
</script>

<template>
  <div class="hof-card">
    <div class="hof-title">🏛️ 카테고리별 명예의 전당</div>
    <div class="hof-sub">* 반응 수 기준으로 집계</div>

    <div v-if="!isMobile" class="hof-tabs">
      <div
        v-for="(cat, idx) in CATEGORIES"
        :key="cat.value"
        class="hof-tab"
        :class="{ active: idx === activeIndex }"
        @click="selectCategory(idx)"
      >
        {{ cat.icon }} {{ cat.shortLabel }}
      </div>
    </div>
    <div v-else class="hof-pager">
      <span class="pager-btn" @click="prevCategory">◀ 이전</span>
      <span class="pager-label">{{ activeCategory.icon }} {{ activeCategory.shortLabel }}</span>
      <span class="pager-btn" @click="nextCategory">다음 ▶</span>
    </div>

    <div class="podium-swipe-area" @pointerdown="onPointerDown" @pointerup="onPointerUp" @wheel="onWheel">
      <Transition :name="slideDirection > 0 ? 'slide-next' : 'slide-prev'" mode="out-in">
        <div v-if="entries.length === 0" :key="'empty:' + activeIndex" class="hof-empty">아직 데이터가 없어요</div>
        <div v-else :key="activeIndex" class="podium-grid">
          <div
            v-for="rankIdx in podiumOrder"
            :key="rankIdx"
            class="podium-item"
            :style="{ opacity: isRevealed(rankIdx) ? 1 : 0, transform: isRevealed(rankIdx) ? 'translateY(0)' : 'translateY(24px)' }"
            @click="goToPost(entries[rankIdx].postId)"
          >
            <div class="podium-post-title">{{ previewTitle(entries[rankIdx]) }}</div>
            <div class="podium-author">{{ entries[rankIdx].userName }}</div>
            <div class="podium-metric">👍 {{ entries[rankIdx].reactionCount ?? 0 }}</div>
            <div
              class="podium-block"
              :style="{
                height: RANK_HEIGHTS[rankIdx],
                background: RANK_THEMES[rankIdx].bg,
                boxShadow: `0 3px 10px ${RANK_THEMES[rankIdx].glow}30`,
                color: RANK_TEXT_COLORS[rankIdx],
              }"
            >
              {{ rankIdx + 1 }}
            </div>
          </div>
        </div>
      </Transition>
    </div>

    <div class="hof-dots">
      <span
        v-for="(cat, idx) in CATEGORIES"
        :key="cat.value"
        class="hof-dot"
        :class="{ active: idx === activeIndex }"
        @click="selectCategory(idx)"
      ></span>
    </div>
  </div>
</template>

<style scoped>
/* 끊기지 않고 계속 부드럽게 빛이 스치는 느낌 - 너무 눈부시지 않게 은은한 하이라이트만 */
@keyframes shine-sweep {
  0% {
    background-position: 180% 0;
  }
  100% {
    background-position: -80% 0;
  }
}

.hof-card {
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 1px 5px rgba(26, 26, 46, 0.05);
  padding: 24px;
}

.hof-title {
  font-size: 15px;
  font-weight: 800;
  color: #1a1a2e;
  margin-bottom: 4px;
}

.hof-sub {
  font-size: 12px;
  color: #636e72;
  margin-bottom: 14px;
}

.hof-tabs {
  display: flex;
  gap: 4px;
  overflow-x: auto;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f2;
}

.hof-tab {
  padding: 8px 14px;
  border-radius: 999px;
  font-size: 12.5px;
  font-weight: 700;
  color: #636e72;
  white-space: nowrap;
  cursor: pointer;
}

.hof-tab.active {
  background: #f1eefc;
  color: #4a3f8f;
}

.hof-pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f2;
}

.pager-btn {
  font-size: 13px;
  font-weight: 700;
  color: #4a3f8f;
  cursor: pointer;
  white-space: nowrap;
}

.pager-label {
  font-size: 14px;
  font-weight: 800;
  color: #1a1a2e;
}

.hof-empty {
  text-align: center;
  padding: 40px 0;
  color: #a0a4ac;
  font-size: 13.5px;
}

/* 마우스 드래그/터치 스와이프로도 카테고리 전환 - pan-y라 세로 스크롤은 그대로 방해받지 않는다 */
.podium-swipe-area {
  touch-action: pan-y;
  cursor: grab;
}

.podium-swipe-area:active {
  cursor: grabbing;
}

.slide-next-enter-active,
.slide-next-leave-active,
.slide-prev-enter-active,
.slide-prev-leave-active {
  transition:
    opacity 0.28s ease,
    transform 0.28s ease;
}

.slide-next-enter-from {
  opacity: 0;
  transform: translateX(28px);
}

.slide-next-leave-to {
  opacity: 0;
  transform: translateX(-28px);
}

.slide-prev-enter-from {
  opacity: 0;
  transform: translateX(-28px);
}

.slide-prev-leave-to {
  opacity: 0;
  transform: translateX(28px);
}

.hof-dots {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 18px;
}

.hof-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: rgba(74, 63, 143, 0.2);
  cursor: pointer;
}

.hof-dot.active {
  background: #4a3f8f;
  width: 18px;
  border-radius: 4px;
}

.podium-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  align-items: end;
  padding: 0 20px;
}

.podium-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  transition:
    opacity 0.4s ease,
    transform 0.4s ease;
}

.podium-post-title {
  font-size: 13.5px;
  font-weight: 800;
  color: #1a1a2e;
  line-height: 1.4;
  text-align: center;
  min-height: 36px;
  word-break: keep-all;
  overflow-wrap: break-word;
  text-wrap: balance;
}

.podium-author {
  font-size: 11.5px;
  color: #8a8fa0;
  margin-top: 6px;
  text-align: center;
}

.podium-metric {
  font-size: 12.5px;
  font-weight: 700;
  color: #4a3f8f;
  margin-top: 6px;
}

.podium-block {
  position: relative;
  overflow: hidden;
  width: 100%;
  margin-top: 12px;
  border-radius: 10px 10px 0 0;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 12px;
  font-size: 30px;
  font-weight: 800;
}

.podium-block::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(115deg, transparent 45%, rgba(255, 255, 255, 0.4) 50%, transparent 55%);
  background-size: 250% 100%;
  animation: shine-sweep 2.4s linear infinite;
  pointer-events: none;
}

@media (max-width: 768px) {
  .podium-grid {
    padding: 0;
    gap: 10px;
  }

  .podium-post-title {
    font-size: 12px;
  }

  .podium-block {
    font-size: 22px;
  }
}
</style>

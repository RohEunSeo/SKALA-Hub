<script setup>
// 대시보드 - 카테고리별 명예의 전당 Top3 포디움 (3등→2등→1등 순차 등장, v3.html PAGE 7 DASHBOARD 재현)
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { CATEGORIES } from '../../constants/categories'
import { stripSlackMarkdown } from '../../utils/renderSlackText'
import { useIsMobile } from '../../composables/useIsMobile'

const props = defineProps({
  hallOfFame: { type: Object, required: true }, // { [categoryValue]: [{ postId, userName, isInstructor, content, reactionCount, createdAt }] }
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

function selectCategory(idx) {
  activeIndex.value = idx
}

function prevCategory() {
  activeIndex.value = (activeIndex.value - 1 + CATEGORIES.length) % CATEGORIES.length
}

function nextCategory() {
  activeIndex.value = (activeIndex.value + 1) % CATEGORIES.length
}

function goToPost(postId) {
  router.push({ name: 'post-detail', params: { id: postId } })
}

function previewTitle(content) {
  return stripSlackMarkdown(content).slice(0, 40)
}

watch(activeIndex, playReveal)
onMounted(playReveal)
onUnmounted(() => revealTimers.forEach(clearTimeout))
</script>

<template>
  <div class="hof-card">
    <div class="hof-title">🏛️ 카테고리별 명예의 전당</div>

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

    <div v-if="entries.length === 0" class="hof-empty">아직 데이터가 없어요</div>
    <div v-else class="podium-grid">
      <div
        v-for="rankIdx in podiumOrder"
        :key="rankIdx"
        class="podium-item"
        :style="{ opacity: isRevealed(rankIdx) ? 1 : 0, transform: isRevealed(rankIdx) ? 'translateY(0)' : 'translateY(24px)' }"
        @click="goToPost(entries[rankIdx].postId)"
      >
        <div class="podium-post-title">{{ previewTitle(entries[rankIdx].content) }}</div>
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

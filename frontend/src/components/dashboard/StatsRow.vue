<script setup>
// 대시보드 - 신규가입/로그인/카테고리 분포 3개 통계 카드
// 데스크탑: 3열 그리드 + 스크롤 진입 시 동시에 드로잉 애니메이션 재생
// 모바일: 이전/다음 화살표로 넘기는 카드 캐러셀(옆 카드가 살짝 보이는 peek 스타일)
import { onMounted, onUnmounted, ref } from 'vue'
import { useIsMobile } from '../../composables/useIsMobile'
import SignupLineChart from './SignupLineChart.vue'
import LoginBarChart from './LoginBarChart.vue'
import CategoryDonutChart from './CategoryDonutChart.vue'

defineProps({
  signupTrend: { type: Array, required: true },
  loginByClass: { type: Array, required: true },
  categoryDist: { type: Array, required: true },
})

const isMobile = useIsMobile()
const rowEl = ref(null)
const viewportEl = ref(null)
const revealed = ref(false)
const slideIndex = ref(0)
const SLIDE_COUNT = 3
let observer = null

onMounted(() => {
  observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((e) => {
        if (e.isIntersecting) revealed.value = true
      })
    },
    { threshold: 0.2 },
  )
  if (rowEl.value) observer.observe(rowEl.value)
})

onUnmounted(() => observer?.disconnect())

function goToSlide(index) {
  if (!viewportEl.value) return
  slideIndex.value = index
  viewportEl.value.scrollTo({ left: index * viewportEl.value.clientWidth, behavior: 'smooth' })
}

function prevSlide() {
  goToSlide(Math.max(0, slideIndex.value - 1))
}

function nextSlide() {
  goToSlide(Math.min(SLIDE_COUNT - 1, slideIndex.value + 1))
}

// 손가락으로 직접 스와이프했을 때도 아래 점 표시가 실제 위치를 따라가도록 동기화
function onScroll() {
  if (!viewportEl.value) return
  const width = viewportEl.value.clientWidth || 1
  slideIndex.value = Math.round(viewportEl.value.scrollLeft / width)
}
</script>

<template>
  <div ref="rowEl">
    <div v-if="!isMobile" class="stats-grid">
      <SignupLineChart :signup-trend="signupTrend" :revealed="revealed" />
      <LoginBarChart :login-by-class="loginByClass" :revealed="revealed" />
      <CategoryDonutChart :category-dist="categoryDist" :revealed="revealed" />
    </div>

    <div v-else class="carousel">
      <div ref="viewportEl" class="carousel-viewport" @scroll="onScroll">
        <div class="carousel-slide">
          <SignupLineChart :signup-trend="signupTrend" :revealed="revealed" />
        </div>
        <div class="carousel-slide">
          <LoginBarChart :login-by-class="loginByClass" :revealed="revealed" />
        </div>
        <div class="carousel-slide">
          <CategoryDonutChart :category-dist="categoryDist" :revealed="revealed" />
        </div>
      </div>
      <div class="carousel-nav">
        <span class="nav-arrow" @click="prevSlide">◀</span>
        <span class="nav-dots">
          <span
            v-for="i in SLIDE_COUNT"
            :key="i"
            class="dot"
            :class="{ active: i - 1 === slideIndex }"
            @click="goToSlide(i - 1)"
          ></span>
        </span>
        <span class="nav-arrow" @click="nextSlide">▶</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.stats-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 20px;
}

.carousel {
  position: relative;
}

.carousel-viewport {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  scroll-snap-type: x mandatory;
  -webkit-overflow-scrolling: touch;
  padding-bottom: 4px;
}

.carousel-viewport::-webkit-scrollbar {
  display: none;
}

/* peek 없이 다른 카드(명예의 전당 등)와 폭이 딱 맞도록 100% - 옆으로 넘길 수 있다는 건 화살표/점으로 안내 */
.carousel-slide {
  flex: 0 0 100%;
  scroll-snap-align: start;
  display: flex;
  min-width: 0;
}

.carousel-nav {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 12px;
}

.nav-arrow {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #f1eefc;
  color: #4a3f8f;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  flex-shrink: 0;
}

.nav-dots {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: rgba(74, 63, 143, 0.2);
  cursor: pointer;
}

.dot.active {
  background: #4a3f8f;
  width: 18px;
  border-radius: 4px;
}
</style>

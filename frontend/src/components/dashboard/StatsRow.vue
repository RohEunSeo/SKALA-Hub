<script setup>
// 대시보드 - 신규가입/로그인/카테고리 분포 3개 통계 카드
// 데스크탑: 3열 그리드 + 스크롤 진입 시 동시에 드로잉 애니메이션 재생
// 모바일: 이전/다음 화살표로 넘기는 카드 캐러셀(옆 카드가 살짝 보이는 peek 스타일)
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
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
// 다시 화면에 들어올 때마다 값을 1씩 올려서 차트 컴포넌트를 :key로 강제 리마운트한다 - 각 차트 내부의
// reveal 상태(라인 그리기 offset, 도넛 진행률 등)가 이전 재생 상태를 그대로 들고 있다가 재생이 안
// 되는 문제를 원천 차단하기 위함(완전히 새 인스턴스로 시작하니 항상 처음부터 다시 그려짐)
const revealKey = ref(0)
const slideIndex = ref(0)
const SLIDE_COUNT = 3
let observer = null

onMounted(() => {
  observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((e) => {
        if (e.isIntersecting) {
          revealKey.value++
          // 리마운트 직후 첫 렌더는 반드시 revealed=false(안 그려진 상태)여야 한다.
          // 예전엔 rAF를 두 번만 걸어서 처리했는데, 페이지에 다른 무거운 애니메이션(날씨 배경 구름 등)이
          // 같이 돌고 있으면 rAF 타이밍만으로는 브라우저가 false 상태를 실제로 한 번 그렸다는 게 보장 안 돼서
          // 가끔 트랜지션이 아예 재생 안 되고 최종 상태로 바로 점프해버리는 경우가 있었다. nextTick으로
          // Vue가 revealed=false를 실제 DOM에 반영할 때까지 먼저 기다린 뒤, getBoundingClientRect()로
          // 강제로 동기 레이아웃 계산(reflow)을 한 번 일으켜서 그 "안 그려진" 상태를 확정시키고, 그 다음
          // rAF에서 true로 바꾼다 - 이러면 브라우저 스케줄링과 무관하게 항상 두 상태가 실제로 구분되어
          // CSS transition이 "이전 값 → 새 값"으로 확실히 재생된다
          revealed.value = false
          nextTick(() => {
            rowEl.value?.getBoundingClientRect()
            requestAnimationFrame(() => {
              revealed.value = true
            })
          })
        } else {
          revealed.value = false
        }
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
      <SignupLineChart :key="`signup-${revealKey}`" :signup-trend="signupTrend" :revealed="revealed" />
      <LoginBarChart :key="`login-${revealKey}`" :login-by-class="loginByClass" :revealed="revealed" />
      <CategoryDonutChart :key="`cat-${revealKey}`" :category-dist="categoryDist" :revealed="revealed" />
    </div>

    <div v-else class="carousel">
      <div ref="viewportEl" class="carousel-viewport" @scroll="onScroll">
        <div class="carousel-slide">
          <SignupLineChart :key="`signup-${revealKey}`" :signup-trend="signupTrend" :revealed="revealed" />
        </div>
        <div class="carousel-slide">
          <LoginBarChart :key="`login-${revealKey}`" :login-by-class="loginByClass" :revealed="revealed" />
        </div>
        <div class="carousel-slide">
          <CategoryDonutChart :key="`cat-${revealKey}`" :category-dist="categoryDist" :revealed="revealed" />
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
/* 신규 가입자 추이는 x축에 날짜 라벨이 촘촘하게 들어가서 다른 두 카드보다 가로 폭을 조금 더 준다 */
.stats-grid {
  display: grid;
  grid-template-columns: 1.3fr 1fr 1fr;
  gap: 20px;
}

/* 화면이 좁아지기 시작하면 신규가입자 추이/가입자 분포만 2열로 두고, 카테고리별 분포는 그 아래 한 줄
   전체를 차지하게 한다 (CategoryDonutChart.vue 쪽 media query에서 grid-column:1/-1로 내려감) */
@media (max-width: 1180px) {
  .stats-grid {
    grid-template-columns: 1.3fr 1fr;
  }
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

/* 내부 차트 컴포넌트(chart-card)는 콘텐츠 크기만큼만 차지하므로, 슬라이드 폭을 그대로 채워서
   차트마다 카드 폭이 제각각(특히 카테고리별 분포가 훨씬 좁게)이 되는 걸 막는다 */
.carousel-slide > * {
  width: 100%;
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

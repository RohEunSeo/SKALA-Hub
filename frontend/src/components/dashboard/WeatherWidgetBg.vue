<script setup>
// 대시보드 "허브 단계" 카드 - 나무 이모지 뒤에 까는 아이폰 위젯 스타일 날씨 배경
// 전부 순수 CSS 애니메이션 (캔버스/외부 라이브러리 없음, 이 대시보드의 다른 차트들과 동일한 방식)
import { computed } from 'vue'

const props = defineProps({
  condition: { type: String, default: null }, // sunny/cloudy/rainy/snowy/foggy/stormy/null(로딩·실패)
  isDay: { type: Boolean, default: true },
})

// 맑은데 밤이면 "맑은 밤"(별) 테마로 따로 처리 - 나머지 날씨는 밤이어도 톤만 어둡게(overlay)
const theme = computed(() => {
  if (!props.condition) return null
  if (props.condition === 'sunny' && !props.isDay) return 'clear-night'
  return props.condition
})

const RAY_ANGLES = [0, 45, 90, 135, 180, 225, 270, 315]
const RAINDROPS = Array.from({ length: 12 }, (_, i) => i)
const SNOWFLAKES = Array.from({ length: 14 }, (_, i) => i)
const STARS = Array.from({ length: 18 }, (_, i) => i)

function seeded(i, mod) {
  // 매 렌더마다 위치가 안 바뀌게 인덱스 기반 의사난수 (Math.random 대신)
  return ((i * 37 + 11) % mod)
}
</script>

<template>
  <div class="weather-bg" :class="theme">
    <template v-if="theme === 'sunny'">
      <div class="sun-spotlight"></div>
      <div class="sun-wrap">
        <div class="sun-rays">
          <span v-for="a in RAY_ANGLES" :key="a" class="ray" :style="{ transform: `translateX(-50%) rotate(${a}deg)` }"></span>
        </div>
        <div class="sun-glow"></div>
      </div>
      <div class="cloud cloud-a"></div>
      <div class="cloud cloud-b"></div>
      <div class="cloud cloud-c"></div>
      <div class="cloud cloud-d"></div>
      <div class="cloud cloud-e"></div>
      <div class="cloud cloud-f"></div>
      <div class="cloud cloud-g"></div>
    </template>

    <template v-else-if="theme === 'clear-night'">
      <div class="moon-glow"></div>
      <span
        v-for="i in STARS"
        :key="'star:' + i"
        class="star"
        :style="{
          left: seeded(i, 100) + '%',
          top: seeded(i * 3, 70) + '%',
          animationDelay: (seeded(i * 7, 30) / 10) + 's',
        }"
      ></span>
    </template>

    <template v-else-if="theme === 'cloudy'">
      <div class="cloud cloud-a"></div>
      <div class="cloud cloud-b"></div>
      <div class="cloud cloud-c"></div>
      <div class="cloud cloud-d"></div>
      <div class="cloud cloud-e"></div>
      <div class="cloud cloud-f"></div>
      <div class="cloud cloud-g"></div>
    </template>

    <template v-else-if="theme === 'foggy'">
      <div class="fog-layer fog-a"></div>
      <div class="fog-layer fog-b"></div>
      <div class="fog-layer fog-c"></div>
    </template>

    <template v-else-if="theme === 'rainy' || theme === 'stormy'">
      <div class="cloud cloud-a dark"></div>
      <div class="cloud cloud-b dark"></div>
      <span
        v-for="i in RAINDROPS"
        :key="'drop:' + i"
        class="raindrop"
        :style="{
          left: seeded(i, 100) + '%',
          animationDelay: (seeded(i * 5, 12) / 10) + 's',
          animationDuration: (0.55 + seeded(i * 3, 4) / 10) + 's',
        }"
      ></span>
      <div v-if="theme === 'stormy'" class="lightning-flash"></div>
    </template>

    <template v-else-if="theme === 'snowy'">
      <div class="cloud cloud-a"></div>
      <div class="cloud cloud-b"></div>
      <span
        v-for="i in SNOWFLAKES"
        :key="'flake:' + i"
        class="snowflake"
        :style="{
          left: seeded(i, 100) + '%',
          animationDelay: (seeded(i * 5, 40) / 10) + 's',
          animationDuration: (3 + seeded(i * 3, 20) / 10) + 's',
        }"
      >❄</span>
    </template>
  </div>
</template>

<style scoped>
/* 사각형 색 배경을 통째로 깔면 카드 안에 "박스"가 하나 더 생긴 것처럼 보여서, 배경은 아예 투명하게
   두고(=카드 원래 흰 배경 그대로) 해·구름·비·눈 같은 개별 그림 요소만 그 위에 얹는다 */
.weather-bg {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

/* ── 맑음(낮) ── 하늘 구석(오른쪽 위)에 떠 있는 해. sun-wrap이 위치 기준점 하나를 잡아주고,
   글로우 원 + 은은한 짝대기 광선을 그 위에 같이 얹는다.
   top을 %로 두면 카드가 짧을 땐 실제 px 여백이 얼마 안 남아서 글로우 그림자(blur)가 카드
   위쪽 경계(weather-bg의 overflow:hidden)에 걸려 네모나게 잘려 보였다 - 고정 px로 확실히 띄운다 */
.sun-wrap {
  position: absolute;
  top: 18px;
  right: 10%;
  width: 36px;
  height: 36px;
}

.sun-glow {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: radial-gradient(circle at 35% 32%, #fff6d2 0%, #ffd54f 55%, #ffb300 100%);
  box-shadow: 0 0 14px 2px rgba(255, 193, 7, 0.35);
  animation: sun-pulse 3.5s ease-in-out infinite;
}

/* 광선 없이 글로우만 있으니 밋밋해 보인다는 피드백 - 대신 이전처럼 촘촘한 줄무늬 패턴이 아니라,
   가늘고 옅은 짝대기 8개만 배치해서 "해" 느낌만 살짝 더해준다 */
.sun-rays {
  position: absolute;
  inset: -10px;
  animation: sun-spin 30s linear infinite;
}

.ray {
  position: absolute;
  top: 0;
  left: 50%;
  width: 2px;
  height: 8px;
  background: rgba(255, 179, 0, 0.22);
  border-radius: 2px;
  transform-origin: 1px 28px;
}

@keyframes sun-spin {
  to {
    transform: rotate(360deg);
  }
}

/* 나무(줄기)가 실제로 햇빛을 받고 있는 느낌 - 나무 이모지가 놓이는 자리 뒤로 따뜻한 빛을 아주 은은히
   깔아준다. 카드 폭이 좁을 때(230px~) 원이 카드 경계에 닿아 잘리면서 테두리처럼 보였던 것 - 카드가
   가장 좁을 때보다도 확실히 작게 줄이고, 옅게 해서 가장자리에서 잘려도 눈에 안 띄게 한다 */
.sun-spotlight {
  position: absolute;
  top: 30%;
  left: 50%;
  width: 150px;
  height: 150px;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 213, 79, 0.22) 0%, rgba(255, 213, 79, 0) 65%);
}

@keyframes sun-pulse {
  0%,
  100% {
    box-shadow: 0 0 14px 2px rgba(255, 193, 7, 0.35);
    transform: scale(1);
  }
  50% {
    box-shadow: 0 0 18px 4px rgba(255, 193, 7, 0.5);
    transform: scale(1.06);
  }
}

/* ── 맑은 밤 ── 고정 px로 - %로 두면 카드가 짧을 때 그림자가 위쪽 경계에 걸려 잘릴 수 있다 */
.moon-glow {
  position: absolute;
  top: 28px;
  right: 18%;
  width: 46px;
  height: 46px;
  border-radius: 50%;
  background: radial-gradient(circle, #fff3c4 0%, #f0d27a 70%, transparent 78%);
  box-shadow: 0 0 16px rgba(230, 200, 110, 0.55);
}

.star {
  position: absolute;
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: #9aa3c2;
  animation: star-twinkle 2.6s ease-in-out infinite;
}

@keyframes star-twinkle {
  0%,
  100% {
    opacity: 0.25;
  }
  50% {
    opacity: 1;
  }
}

/* ── 구름 공통 (흐림/맑음/눈/비 재사용) - 입체감(밝은 위→어두운 아래 그라데이션)은 살리되 이음매 없이.
   지난번엔 몸통/퍼프가 background:inherit로 "각자 자기 박스 기준" 그라데이션을 따로 계산해서 겹치는
   자리에 색이 어긋나는 경계선이 생겼었다. 이번엔 셋 다 같은 좌표계(58×33 "캔버스") 위의 같은
   그라데이션 이미지를 background-position만 다르게 잘라서 보여주는 방식이라, 어느 조각에서 봐도
   이어지는 그라데이션이라 이음매가 안 생긴다 */
.cloud {
  position: absolute;
  width: 58px;
  height: 20px;
  border-radius: 999px;
  background-image: linear-gradient(180deg, #ffffff 0%, #d3e6f6 100%);
  background-size: 58px 33px;
  background-position: 0 -13px;
  background-repeat: no-repeat;
  filter: drop-shadow(0 2px 3px rgba(150, 180, 210, 0.35));
}

.cloud::before,
.cloud::after {
  content: '';
  position: absolute;
  border-radius: 50%;
  background-image: inherit;
  background-size: inherit;
  background-repeat: inherit;
}

.cloud::before {
  width: 26px;
  height: 26px;
  top: -13px;
  left: 8px;
  background-position: -8px 0;
}

.cloud::after {
  width: 19px;
  height: 19px;
  top: -9px;
  right: 9px;
  background-position: -30px -4px;
}

.cloud.dark {
  background-image: linear-gradient(180deg, #7a8494 0%, #4c5566 100%);
}

/* left를 %로 애니메이션해야 카드 폭이 얼마든 화면을 완전히 가로질러 지나간다(예전엔 translateX를
   고정 260px로 줘서, 카드 폭이 그보다 넓으면 절반쯤 가다 갑자기 처음으로 되감기며 사라져 보였다).
   느리게(50~70초) 돌리되, 구름을 7개로 늘리고 animation-delay를 duration 대비 거의 균등한
   간격(대략 duration/7씩)으로 벌려둬서 한 구름이 화면을 빠져나가는 시점에 항상 다음 구름이
   이미 들어와 있다 - 그래서 "끊김없이 계속" 나오는 것처럼 보인다(듬성듬성 몰렸다 비는 대신).
   큰 구름(scale 1.1~1.4)도 섞어서 빈 공간이 덜 허전해 보이게 한다. cloud-a/cloud-d는 top이
   가깝고(8%/12%) duration·delay도 가까워서 주기적으로 화면 위에서 나란히 겹쳐 지나가는 구간이
   생긴다. scale은 이제 애니메이션이 transform을 건드리지 않으니 고정값으로 둬도 안 사라진다 */
.cloud-a {
  top: 8%;
  transform: scale(1.35);
  animation: cloud-drift 62s linear infinite;
  animation-delay: 0s;
}

.cloud-b {
  top: 22%;
  transform: scale(0.7);
  opacity: 0.85;
  animation: cloud-drift 58s linear infinite;
  animation-delay: -8s;
}

.cloud-c {
  top: 30%;
  transform: scale(0.55);
  opacity: 0.7;
  animation: cloud-drift 65s linear infinite;
  animation-delay: -18s;
}

.cloud-d {
  top: 12%;
  transform: scale(1.1);
  opacity: 0.9;
  animation: cloud-drift 50s linear infinite;
  animation-delay: -26s;
}

.cloud-e {
  top: 27%;
  transform: scale(0.9);
  opacity: 0.8;
  animation: cloud-drift 60s linear infinite;
  animation-delay: -34s;
}

.cloud-f {
  top: 17%;
  transform: scale(0.6);
  opacity: 0.75;
  animation: cloud-drift 54s linear infinite;
  animation-delay: -42s;
}

.cloud-g {
  top: 24%;
  transform: scale(1);
  opacity: 0.85;
  animation: cloud-drift 57s linear infinite;
  animation-delay: -50s;
}

@keyframes cloud-drift {
  from {
    left: -30%;
  }
  to {
    left: 130%;
  }
}

/* ── 안개 ── */
.fog-layer {
  position: absolute;
  left: -20%;
  width: 140%;
  height: 22px;
  border-radius: 999px;
  background: rgba(170, 178, 190, 0.55);
  animation: fog-drift 9s ease-in-out infinite;
}

.fog-a {
  top: 22%;
  animation-duration: 8s;
}

.fog-b {
  top: 48%;
  animation-duration: 11s;
  animation-delay: -3s;
  opacity: 0.8;
}

.fog-c {
  top: 72%;
  animation-duration: 10s;
  animation-delay: -6s;
  opacity: 0.65;
}

@keyframes fog-drift {
  0%,
  100% {
    transform: translateX(-3%);
    opacity: 0.5;
  }
  50% {
    transform: translateX(3%);
    opacity: 0.85;
  }
}

/* ── 비 / 뇌우 ── */
.raindrop {
  position: absolute;
  top: -10%;
  width: 2px;
  height: 14px;
  border-radius: 2px;
  background: linear-gradient(rgba(90, 150, 220, 0), rgba(70, 130, 210, 0.9));
  animation: rain-fall 0.8s linear infinite;
}

@keyframes rain-fall {
  from {
    transform: translateY(0);
    opacity: 0.9;
  }
  to {
    transform: translateY(140px);
    opacity: 0.2;
  }
}

/* 흰 배경 위라 화면 전체를 하얗게 플래시하면 안 보이므로, 구름 아래에 노란빛이 번쩍하는
   원형 글로우로 대체 */
.lightning-flash {
  position: absolute;
  top: 30%;
  left: 50%;
  width: 160px;
  height: 160px;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 224, 102, 0.85) 0%, transparent 65%);
  opacity: 0;
  animation: lightning-strike 5.5s ease-in-out infinite;
}

@keyframes lightning-strike {
  0%,
  92%,
  100% {
    opacity: 0;
  }
  93% {
    opacity: 0.55;
  }
  94% {
    opacity: 0.05;
  }
  95% {
    opacity: 0.4;
  }
  96% {
    opacity: 0;
  }
}

/* ── 눈 ── */
.snowflake {
  position: absolute;
  top: -10%;
  color: #90bce0;
  font-size: 11px;
  animation-name: snow-fall;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
}

@keyframes snow-fall {
  from {
    transform: translateY(0) translateX(0);
    opacity: 0.95;
  }
  to {
    transform: translateY(150px) translateX(10px);
    opacity: 0.3;
  }
}
</style>

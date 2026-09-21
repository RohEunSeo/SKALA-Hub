<script setup>
// 맥 Finder 폴더 아이콘 카드 - 홈 "카테고리별 아카이브"와 피드 "SKALA 커리큘럼"이 공용으로 사용.
// 뒷판(SVG, 탭+어깨 곡선) → 문서 2장 → 앞판(그라데이션, 슬롯 내용) 순으로 겹쳐 쌓고,
// 호버하면 문서가 폴더 위로 올라오고 앞판이 살짝 열린다. 색은 카테고리 원색 하나만 넘기면 됨
import { computed } from 'vue'
import { folderBackColor, folderBodyColor, folderFrontTopColor } from '../utils/folderColors'

const props = defineProps({
  color: { type: String, required: true },
  // 선택된 폴더(커리큘럼 필터) - 호버 없이도 문서가 반쯤 나와 있는 상태 유지
  active: { type: Boolean, default: false },
})

const colorVars = computed(() => ({
  '--folder-back': folderBackColor(props.color),
  '--folder-front-top': folderFrontTopColor(props.color),
  '--folder-front-bottom': folderBodyColor(props.color),
}))
</script>

<template>
  <div class="folder-card" :class="{ active }" :style="colorVars">
    <!-- 뒷판: 카드 비율(200:170)과 viewBox 비율이 같아서 늘어나도 곡선이 왜곡되지 않음 -->
    <svg class="folder-back" viewBox="0 0 200 170" preserveAspectRatio="none" aria-hidden="true">
      <path
        d="M0,14 A12,12 0 0 1 12,2 H48 C57,2 61,4.5 67,10 C72,14 76,15 84,15 H188 A12,12 0 0 1 200,27 V158 A12,12 0 0 1 188,170 H12 A12,12 0 0 1 0,158 Z"
      />
    </svg>
    <div class="folder-paper paper-back" aria-hidden="true"></div>
    <div class="folder-paper paper-front" aria-hidden="true"></div>
    <div class="folder-front">
      <div class="folder-content">
        <slot />
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 문서가 위로 올라와야 하므로 overflow:hidden 금지. 그림자는 탭 모양을 따라가도록 drop-shadow 사용 */
.folder-card {
  position: relative;
  min-width: 0;
  aspect-ratio: 200 / 170;
  cursor: pointer;
  perspective: 700px;
  /* 카드 폭 기준 단위(cqw)로 슬롯 안 글씨/아이콘/여백이 카드와 같이 줄어들게 함 - 좁아져도 폴더 밖으로 안 튀어나옴 */
  container-type: inline-size;
  filter: drop-shadow(0 10px 12px rgba(26, 26, 46, 0.16));
  transition: transform 0.28s ease, filter 0.28s ease;
}

.folder-back {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  z-index: 0;
}

.folder-back path {
  fill: var(--folder-back);
}

/* 폴더 속 문서 - 기본은 앞판 뒤에 숨고 윗부분만 살짝 보임(첨부 이미지의 흰 띠) */
.folder-paper {
  position: absolute;
  left: 6%;
  right: 6%;
  top: 12.5%;
  height: 80%;
  border-radius: 7px;
  z-index: 1;
  box-shadow: 0 1px 3px rgba(26, 26, 46, 0.14);
  transition: transform 0.36s cubic-bezier(0.22, 1, 0.36, 1);
}

/* 앞 문서: 흰 종이 + 회색 텍스트 줄 몇 개 */
.paper-front {
  background:
    linear-gradient(#d3d7e0, #d3d7e0) 12% 12% / 44% 4px no-repeat,
    linear-gradient(#e2e5ec, #e2e5ec) 12% 24% / 70% 3px no-repeat,
    linear-gradient(#e2e5ec, #e2e5ec) 12% 33% / 58% 3px no-repeat,
    linear-gradient(#ffffff, #f2f3f8);
  transition-delay: 40ms;
}

/* 뒤 문서: 살짝 어긋나게 뽑혀서 문서가 여러 장인 느낌 */
.paper-back {
  left: 11%;
  right: 11%;
  top: 14%;
  background: linear-gradient(#f6f7fb, #e9ebf2);
}

/* 앞판: 위→아래 세로 그라데이션 + 상단 흰 하이라이트 + 아래쪽 음영 (맥 폴더의 유리 질감) */
.folder-front {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  top: 16%;
  /* 모서리 둥글기는 뒷판 SVG(반지름 12/200 = 6cqw)와 똑같이 맞춰서 뒷판이 삐져나와 보이지 않게 함 */
  border-radius: 6cqw;
  z-index: 2;
  background: linear-gradient(to bottom, var(--folder-front-top), var(--folder-front-bottom));
  box-shadow:
    inset 0 1.5px 0 rgba(255, 255, 255, 0.7),
    inset 0 -14px 12px -10px rgba(0, 0, 0, 0.12),
    0 -2px 5px rgba(26, 26, 46, 0.1);
  transform-origin: 50% 100%;
  transition: transform 0.36s cubic-bezier(0.22, 1, 0.36, 1);
}

/* 슬롯 영역 - 여백도 카드 폭에 비례 */
.folder-content {
  height: 100%;
  box-sizing: border-box;
  /* 아래 여백은 --folder-pad-bottom으로 소비하는 쪽에서 조절 가능 (내용이 많은 커리큘럼 폴더용) */
  padding: clamp(9px, 7.5cqw, 20px) clamp(8px, 8cqw, 18px) var(--folder-pad-bottom, clamp(8px, 6.5cqw, 18px));
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

/* 호버(마우스 기기 한정) / 선택 상태 */
.folder-card.active {
  transform: translateY(-4px) scale(1.03);
  filter: drop-shadow(0 14px 16px rgba(26, 26, 46, 0.2));
}

.folder-card.active .paper-front {
  transform: translateY(-14%);
}

.folder-card.active .paper-back {
  transform: translateY(-8%);
}

@media (hover: hover) {
  .folder-card:hover {
    transform: translateY(-6px) scale(1.04);
    filter: drop-shadow(0 18px 18px rgba(26, 26, 46, 0.24));
  }

  /* 문서가 한 장씩 뽑히는 느낌 - 앞 문서가 더 높이, 뒤 문서는 반대쪽으로 살짝 기울어 */
  .folder-card:hover .paper-front {
    transform: translateY(-25%) rotate(2deg);
  }

  .folder-card:hover .paper-back {
    transform: translateY(-16%) rotate(-3deg);
  }

  /* 앞판 윗면이 관객 쪽으로 살짝 기울어 폴더가 열리는 느낌 */
  .folder-card:hover .folder-front {
    transform: rotateX(-8deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .folder-card,
  .folder-paper,
  .folder-front {
    transition: none;
  }
}
</style>

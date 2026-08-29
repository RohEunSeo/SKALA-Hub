<script setup>
// 커리큘럼 탭 상단 - SKALA 4단계 + AX 폴더를 홈 화면 "카테고리별 아카이브"와 동일한
// 맥 Finder 폴더 카드로 표시. 카드를 클릭하면 해당 폴더로 필터링됨
import { CURRICULUM_STAGES } from '../constants/curriculum'
import { folderBodyColor, folderTabColor, folderTextColor } from '../utils/folderColors'

const props = defineProps({
  selectedStage: { type: String, required: true },
  counts: { type: Object, default: () => ({}) },
})

const emit = defineEmits(['select'])
</script>

<template>
  <div class="curriculum-diagram">
    <!-- folder-slot: 탭 진입 시 좌→우 순차 등장 애니메이션 전용 래퍼.
         stage-folder(실제 카드)에 직접 애니메이션을 걸면 forwards로 고정된 transform이
         hover/active의 transform을 계속 덮어써서 먹히지 않으므로 레이어를 분리함 -->
    <div v-for="(stage, index) in CURRICULUM_STAGES" :key="stage.value" class="folder-slot" :style="{ '--index': index }">
      <div
        class="stage-folder"
        :class="{ active: selectedStage === stage.value }"
        :style="{
          background: folderBodyColor(stage.color),
          '--tab-color': folderTabColor(stage.color),
        }"
        @click="emit('select', stage.value)"
      >
        <!-- 선택된 폴더 표시 - 오른쪽 위에 체크 배지가 팝인 -->
        <div
          class="selected-badge"
          :class="{ show: selectedStage === stage.value }"
          :style="{ color: stage.color }"
          aria-hidden="true"
        >
          ✓
        </div>
        <img v-if="stage.iconImage" :src="stage.iconImage" class="stage-icon stage-icon-image" alt="" />
        <div v-else class="stage-icon">{{ stage.icon }}</div>
        <div>
          <div class="stage-label" :style="{ color: folderTextColor(stage.color) }">{{ stage.label }}</div>
          <div class="stage-subtitle" :style="{ color: folderTextColor(stage.color) }">{{ stage.subtitle }}</div>
          <div class="stage-count" :style="{ color: folderTextColor(stage.color) }">
            {{ counts[stage.value] ?? 0 }}개
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.curriculum-diagram {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 18px;
  margin-bottom: 24px;
}

.folder-slot {
  margin-top: 14px;
  /* 커리큘럼 탭에 들어올 때마다(다이어그램이 새로 mount될 때) 폴더가 왼쪽부터 순서대로 떠오름 */
  opacity: 0;
  animation: stage-folder-in 0.45s cubic-bezier(0.16, 1, 0.3, 1) forwards;
  animation-delay: calc(var(--index) * 0.08s);
}

@keyframes stage-folder-in {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .folder-slot {
    opacity: 1;
    animation: none;
  }
}

/* 맥 Finder 폴더 아이콘 느낌 - 둥근 본체(진한 톤) + 왼쪽 위로 삐져나온 탭(연한 톤),
   테두리선 없이 그림자로만 입체감 (홈 화면 category-card와 동일 스타일) */
.stage-folder {
  position: relative;
  min-width: 0;
  cursor: pointer;
  border-radius: 13px;
  padding: 20px 18px 18px;
  aspect-ratio: 4 / 3;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  box-shadow: 0 10px 20px rgba(26, 26, 46, 0.15), inset 0 -14px 12px -10px rgba(0, 0, 0, 0.12);
  transition: transform 0.22s ease, box-shadow 0.22s ease;
}

.stage-folder:hover {
  transform: translateY(-6px) scale(1.04);
  box-shadow: 0 18px 28px rgba(26, 26, 46, 0.22), inset 0 -14px 12px -10px rgba(0, 0, 0, 0.12);
}

.stage-folder.active {
  transform: translateY(-4px) scale(1.03);
  box-shadow: 0 14px 24px rgba(26, 26, 46, 0.2), inset 0 -14px 12px -10px rgba(0, 0, 0, 0.12);
}

.stage-folder::before {
  content: '';
  position: absolute;
  top: -10px;
  left: 0;
  width: 46%;
  height: 22px;
  border-radius: 10px 10px 0 0;
  background: var(--tab-color);
  transition: transform 0.22s ease;
  transform-origin: bottom left;
  z-index: 2;
}

/* 마우스 올리면 위쪽 탭이 살짝 들려서 폴더가 열리는 듯한 느낌 */
.stage-folder:hover::before,
.stage-folder.active::before {
  transform: translateY(-3px) rotate(-3deg);
}

/* 선택된 폴더만 - 오른쪽 위 체크 배지가 팝인되며 나타남 */
.selected-badge {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #ffffff;
  font-size: 12px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 6px rgba(26, 26, 46, 0.2);
  transform: scale(0);
  opacity: 0;
  transition: transform 0.2s cubic-bezier(0.34, 1.56, 0.64, 1), opacity 0.2s ease;
  z-index: 3;
}

.selected-badge.show {
  transform: scale(1);
  opacity: 1;
}

.stage-icon {
  font-size: 23px;
  filter: drop-shadow(0 1px 1px rgba(0, 0, 0, 0.08));
  transition: transform 0.22s ease;
}

/* 이모지 대신 로고 이미지를 쓰는 폴더(AX) - .stage-icon의 반응형 font-size(em 기준)에 비례해서 크기를
   맞추되, 이모지보다 살짝 더 크고 아래로 내려서 다른 폴더 아이콘들과 시각적 무게가 비슷하게 보이게 함 */
.stage-icon-image {
  width: 1.22em;
  height: 1.22em;
  object-fit: contain;
  display: block;
  margin-top: 6px;
}

.stage-folder:hover .stage-icon {
  transform: scale(1.1);
}

/* 라벨/서브타이틀은 줄 수를 고정해서 - 텍스트 길이가 제각각이라도 카드 높이가 항상
   aspect-ratio대로 유지되도록 함 (넘치는 텍스트는 말줄임) */
.stage-label {
  font-weight: 700;
  font-size: 14px;
  line-height: 1.25;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.stage-subtitle {
  font-size: 12px;
  margin-top: 3px;
  opacity: 0.75;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stage-count {
  font-size: 12.5px;
  font-weight: 700;
  margin-top: 6px;
}

/* 화면이 좁아질수록 글씨/여백을 단계적으로 줄여서 좁은 카드에서도 내용이 안 넘치게 함
   (aspect-ratio는 항상 4/3 그대로 유지 - 폭에 맞춰 높이만 비례해서 줄어듦) */
@media (max-width: 900px) {
  .curriculum-diagram {
    grid-template-columns: repeat(3, 1fr);
  }

  .stage-folder {
    padding: 16px 14px 14px;
  }

  .stage-icon {
    font-size: 20px;
  }

  .stage-label {
    font-size: 12.5px;
  }

  .stage-subtitle {
    font-size: 11px;
  }

  .stage-count {
    font-size: 11.5px;
  }
}

@media (max-width: 700px) {
  .stage-label {
    font-size: 11.5px;
  }

  .stage-subtitle {
    font-size: 10.5px;
  }
}

@media (max-width: 560px) {
  .curriculum-diagram {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }

  .stage-folder {
    padding: 14px 12px 12px;
  }

  .stage-icon {
    font-size: 18px;
  }

  .stage-label {
    font-size: 11.5px;
  }

  .stage-subtitle {
    font-size: 10px;
  }

  .stage-count {
    font-size: 10.5px;
  }

  .selected-badge {
    width: 18px;
    height: 18px;
    font-size: 10px;
    top: 8px;
    right: 8px;
  }
}
</style>

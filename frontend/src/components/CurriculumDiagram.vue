<script setup>
// 커리큘럼 탭 상단 - SKALA 4단계 + AX 폴더를 홈 화면 "카테고리별 아카이브"와 동일한
// 맥 Finder 폴더 카드로 표시. 카드를 클릭하면 해당 폴더로 필터링됨
import { CURRICULUM_STAGES } from '../constants/curriculum'
import FolderCard from './FolderCard.vue'
import { folderTextColor } from '../utils/folderColors'

const props = defineProps({
  selectedStage: { type: String, required: true },
  counts: { type: Object, default: () => ({}) },
})

const emit = defineEmits(['select'])
</script>

<template>
  <div class="curriculum-diagram-wrap">
    <div class="curriculum-diagram">
      <!-- folder-slot: 탭 진입 시 좌→우 순차 등장 애니메이션 전용 래퍼.
           FolderCard(실제 카드)에 직접 애니메이션을 걸면 forwards로 고정된 transform이
           hover/active의 transform을 계속 덮어써서 먹히지 않으므로 레이어를 분리함 -->
      <div v-for="(stage, index) in CURRICULUM_STAGES" :key="stage.value" class="folder-slot" :style="{ '--index': index }">
        <FolderCard :color="stage.color" :active="selectedStage === stage.value" @click="emit('select', stage.value)">
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
          <div class="stage-info">
            <div class="stage-label" :style="{ color: folderTextColor(stage.color) }">{{ stage.label }}</div>
            <div class="stage-subtitle" :style="{ color: folderTextColor(stage.color) }">{{ stage.subtitle }}</div>
            <div class="stage-count" :style="{ color: folderTextColor(stage.color) }">
              {{ counts[stage.value] ?? 0 }}개
            </div>
          </div>
        </FolderCard>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 사이드바 펼침/접힘처럼 뷰포트는 그대로인데 "이 영역"만 좁아지는 경우가 있어서, 뷰포트 기준
   @media 대신 이 래퍼의 실제 렌더링 폭 기준 @container로 반응형을 건다 */
.curriculum-diagram-wrap {
  container: curriculum / inline-size;
}

.curriculum-diagram {
  /* 개수 텍스트가 폴더 하단에 붙지 않도록 아래 여백을 카드 폭에 비례해 넉넉히 (좁아져도 같이 줄어듦) */
  --folder-pad-bottom: clamp(10px, 8cqw, 20px);
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 18px;
  margin-bottom: 24px;
}

.folder-slot {
  /* 그리드 아이템 기본값(min-width:auto)이 라벨 텍스트의 최소 콘텐츠 폭을 기준으로 잡혀서,
     영단어라 잘 안 끊기는 라벨("Full-stack Engineering")이 있는 칸만 더 넓어지고, 그 칸만
     aspect-ratio 때문에 카드 높이도 같이 커지는 문제가 있었음 - 0으로 리셋해 5칸이 항상 동일 폭 */
  min-width: 0;
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

/* 선택된 폴더만 - 오른쪽 위 체크 배지가 팝인되며 나타남 */
.selected-badge {
  position: absolute;
  top: clamp(6px, 6cqw, 10px);
  right: clamp(6px, 6cqw, 10px);
  width: clamp(16px, 13cqw, 22px);
  height: clamp(16px, 13cqw, 22px);
  border-radius: 50%;
  background: #ffffff;
  font-size: clamp(9px, 7cqw, 12px);
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

/* overflow:hidden이 붙은 stage-label 때문에 이 아이콘/정보 블록의 flexbox 자동 최소 높이가
   0으로 취급돼서, 카드 폭이 애매하게 좁을 때 flex-shrink가 stage-label 박스를 한 줄보다 살짝
   낮게 눌러버려 "g" 같은 디센더 글자 아래쪽이 잘려 보이는 문제가 있었음 - 절대 안 눌리게 고정 */
.stage-icon,
.stage-info {
  flex-shrink: 0;
}

.stage-icon {
  font-size: clamp(13px, 12cqw, 24px);
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

.folder-slot:hover .stage-icon {
  transform: scale(1.1);
}

/* 라벨/서브타이틀 둘 다 항상 1줄 고정(넘치면 말줄임) - 예전엔 라벨이 최대 2줄까지 늘어날 수
   있어서, "Full-stack Engineering"처럼 화면 폭이 애매할 때만 유독 2줄로 줄바꿈되는 폴더가
   생기면 그 카드만 내용이 aspect-ratio 높이를 넘쳐 혼자 커 보였음. 1줄로 고정하면 라벨 텍스트
   길이/줄바꿈 여부와 무관하게 5개 카드 높이가 항상 완전히 동일함 */
.stage-label {
  font-weight: 700;
  font-size: clamp(10.5px, 8.2cqw, 15px);
  line-height: 1.25;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stage-subtitle {
  font-size: clamp(9.5px, 7cqw, 12.5px);
  margin-top: 2px;
  opacity: 0.75;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stage-count {
  font-size: clamp(10px, 7.4cqw, 13px);
  font-weight: 700;
  margin-top: 4px;
}

/* 이 영역 자체가 좁아질수록(사이드바를 펼쳐서 콘텐츠 폭만 줄어드는 경우 포함) 칸 수를 줄여서 카드가
   너무 작아지지 않게 함 - 뷰포트 폭이 아니라 .curriculum-diagram-wrap의 실제 렌더링 폭 기준(@container)이라
   사이드바 상태와 무관하게 항상 정확히 반응함. 카드 안 글씨/아이콘/여백은 FolderCard 폭 기준(cqw)으로
   알아서 같이 줄어들므로 여기서 따로 크기를 건드리지 않음 */
@container curriculum (max-width: 760px) {
  .curriculum-diagram {
    grid-template-columns: repeat(3, 1fr);
  }
}

@container curriculum (max-width: 560px) {
  .curriculum-diagram {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }
}
</style>

<script setup>
// 커리큘럼 탭 상단 - SKALA 4단계 교육과정 소개 다이어그램. 카드를 클릭하면 해당 단계로 필터링됨
import { CURRICULUM_STAGES } from '../constants/curriculum'

const props = defineProps({
  selectedStage: { type: String, required: true },
  counts: { type: Object, default: () => ({}) },
})

const emit = defineEmits(['select'])
</script>

<template>
  <div class="curriculum-diagram">
    <template v-for="(stage, index) in CURRICULUM_STAGES" :key="stage.value">
      <button
        class="stage-card"
        :class="{ active: selectedStage === stage.value }"
        :style="{ '--stage-color': stage.color, '--stage-index': index }"
        @click="emit('select', stage.value)"
      >
        <div class="stage-bar"></div>
        <div class="stage-icon">{{ stage.icon }}</div>
        <div class="stage-label">{{ stage.label }}</div>
        <div class="stage-subtitle">{{ stage.subtitle }}</div>
        <div class="stage-tags">{{ stage.subCategories.map((s) => s.label).join(' · ') }}</div>
        <div class="stage-count">{{ counts[stage.value] ?? 0 }}개 게시글</div>
      </button>
      <div v-if="index < CURRICULUM_STAGES.length - 1" class="stage-connector" aria-hidden="true">›</div>
    </template>
  </div>
</template>

<style scoped>
.curriculum-diagram {
  display: flex;
  align-items: stretch;
  gap: 4px;
  margin-bottom: 20px;
}

.stage-card {
  flex: 1;
  min-width: 0;
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  background: #ffffff;
  border: 1px solid rgba(26, 26, 46, 0.08);
  border-radius: 16px;
  padding: 18px 16px 16px;
  text-align: left;
  cursor: pointer;
  font-family: inherit;
  box-shadow: 0 2px 12px rgba(26, 26, 46, 0.05);
  transition: box-shadow 0.15s ease, transform 0.15s ease, border-color 0.15s ease;
  overflow: hidden;
  /* 커리큘럼 탭에 들어올 때마다(다이어그램이 새로 mount될 때) 카드가 순서대로 살짝 뜨며 나타남 */
  opacity: 0;
  animation: stage-card-in 0.45s cubic-bezier(0.16, 1, 0.3, 1) forwards;
  animation-delay: calc(var(--stage-index) * 0.08s);
}

@keyframes stage-card-in {
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
  .stage-card {
    opacity: 1;
    animation: none;
  }
}

.stage-card:hover {
  box-shadow: 0 4px 16px rgba(26, 26, 46, 0.1);
  transform: translateY(-2px);
}

.stage-card.active {
  border-color: var(--stage-color);
  box-shadow: 0 4px 16px rgba(26, 26, 46, 0.12);
}

.stage-bar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: var(--stage-color);
}

.stage-icon {
  font-size: 22px;
  margin-top: 4px;
}

.stage-label {
  font-size: 14.5px;
  font-weight: 700;
  color: #1a1a2e;
  line-height: 1.3;
}

.stage-subtitle {
  font-size: 12px;
  color: #636e72;
}

.stage-tags {
  margin-top: 4px;
  font-size: 11px;
  color: #9199a1;
  line-height: 1.4;
}

.stage-count {
  margin-top: 8px;
  font-size: 11.5px;
  font-weight: 700;
  color: var(--stage-color);
}

.stage-connector {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  font-size: 20px;
  font-weight: 700;
  color: rgba(26, 26, 46, 0.15);
  padding: 0 2px;
}

@media (max-width: 900px) {
  .curriculum-diagram {
    flex-wrap: wrap;
  }

  .stage-card {
    flex: 1 1 calc(50% - 4px);
    min-width: 180px;
  }

  .stage-connector {
    display: none;
  }
}

@media (max-width: 480px) {
  .stage-card {
    flex: 1 1 100%;
  }

  .stage-tags {
    display: none;
  }
}
</style>

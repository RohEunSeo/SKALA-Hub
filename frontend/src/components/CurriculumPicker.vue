<script setup>
// 커리큘럼 단계 + 하위 카테고리를 고르는 공용 팝오버 - 카드 위 카테고리 변경, 게시글 추가 모달,
// 피드 탭 퀵애드 3곳에서 재사용
import { ref, computed } from 'vue'
import { CURRICULUM_STAGES } from '../constants/curriculum'

const props = defineProps({
  initialStage: { type: String, default: null },
  initialSubCategory: { type: String, default: null },
  confirmLabel: { type: String, default: '저장' },
})

const emit = defineEmits(['confirm', 'cancel'])

const stage = ref(props.initialStage ?? CURRICULUM_STAGES[0].value)
const subCategory = ref(props.initialSubCategory ?? null)

const stageInfo = computed(() => CURRICULUM_STAGES.find((s) => s.value === stage.value))

function selectStage(value) {
  if (stage.value === value) return
  stage.value = value
  subCategory.value = null
}

function selectSubCategory(value) {
  subCategory.value = subCategory.value === value ? null : value
}

function confirm() {
  emit('confirm', { stage: stage.value, subCategory: subCategory.value })
}
</script>

<template>
  <div class="curriculum-picker" @click.stop>
    <div class="picker-section">
      <div class="picker-label">단계</div>
      <div class="picker-pills">
        <span
          v-for="s in CURRICULUM_STAGES"
          :key="s.value"
          class="picker-pill"
          :class="{ active: stage === s.value }"
          :style="stage === s.value ? { background: s.color, borderColor: s.color, color: '#fff' } : {}"
          @click="selectStage(s.value)"
        >
          {{ s.icon }} {{ s.shortLabel }}
        </span>
      </div>
    </div>

    <div v-if="stageInfo" class="picker-section">
      <div class="picker-label">하위 카테고리 <span class="optional">(선택)</span></div>
      <div class="picker-pills">
        <span
          v-for="sub in stageInfo.subCategories"
          :key="sub.value"
          class="picker-pill"
          :class="{ active: subCategory === sub.value }"
          @click="selectSubCategory(sub.value)"
        >
          {{ sub.label }}
        </span>
      </div>
    </div>

    <div class="picker-actions">
      <span class="picker-btn picker-cancel" @click="emit('cancel')">취소</span>
      <span class="picker-btn picker-confirm" @click="confirm">{{ confirmLabel }}</span>
    </div>
  </div>
</template>

<style scoped>
.curriculum-picker {
  width: 260px;
  background: #ffffff;
  border-radius: 14px;
  box-shadow: 0 8px 28px rgba(26, 26, 46, 0.18);
  border: 1px solid rgba(26, 26, 46, 0.08);
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  cursor: default;
}

.picker-label {
  font-size: 12px;
  font-weight: 700;
  color: #636e72;
  margin-bottom: 6px;
}

.optional {
  font-weight: 400;
  color: #a0a4b8;
}

.picker-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.picker-pill {
  font-size: 12px;
  font-weight: 600;
  padding: 6px 10px;
  border-radius: 999px;
  background: #fafafa;
  border: 1px solid rgba(26, 26, 46, 0.1);
  color: #1a1a2e;
  cursor: pointer;
  white-space: nowrap;
}

.picker-pill.active {
  background: #f1eefc;
  border-color: #4a3f8f;
  color: #4a3f8f;
}

.picker-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 4px;
  border-top: 1px solid rgba(26, 26, 46, 0.06);
}

.picker-btn {
  font-size: 12.5px;
  font-weight: 700;
  padding: 7px 14px;
  border-radius: 8px;
  cursor: pointer;
}

.picker-cancel {
  background: #f4f4f4;
  color: #636e72;
}

.picker-confirm {
  background: #4a3f8f;
  color: #ffffff;
}
</style>

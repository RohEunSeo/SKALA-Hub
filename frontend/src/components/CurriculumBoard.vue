<script setup>
// SKALA 커리큘럼 탭 본체 - 상단 다이어그램 + 하위 카테고리 필터 + 게시글 목록 + 관리자 "게시글 추가"
import { computed, onMounted, ref } from 'vue'
import { useCurriculumStore } from '../stores/curriculum'
import { useAuthStore } from '../stores/auth'
import { findStage } from '../constants/curriculum'
import CurriculumDiagram from './CurriculumDiagram.vue'
import CurriculumPostCard from './CurriculumPostCard.vue'
import CurriculumAddPostModal from './CurriculumAddPostModal.vue'

const curriculumStore = useCurriculumStore()
const authStore = useAuthStore()
const isAdmin = computed(() => authStore.effectiveIsAdmin)
const showAddModal = ref(false)

const currentStageInfo = computed(() => findStage(curriculumStore.selectedStage))

onMounted(async () => {
  await curriculumStore.loadCounts()
  await curriculumStore.selectStage(curriculumStore.selectedStage)
})

function selectStage(stage) {
  curriculumStore.selectStage(stage, null)
}

function selectSubCategory(sub) {
  const next = curriculumStore.selectedSubCategory === sub ? null : sub
  curriculumStore.selectStage(curriculumStore.selectedStage, next)
}

function onAdded() {
  showAddModal.value = false
  curriculumStore.refresh()
}
</script>

<template>
  <div class="curriculum-board">
    <CurriculumDiagram
      :selected-stage="curriculumStore.selectedStage"
      :counts="curriculumStore.counts"
      @select="selectStage"
    />

    <div class="curriculum-toolbar">
      <div v-if="currentStageInfo" class="sub-filter">
        <span
          class="sub-pill"
          :class="{ active: !curriculumStore.selectedSubCategory }"
          @click="selectSubCategory(null)"
        >
          전체 ({{ curriculumStore.counts[curriculumStore.selectedStage] ?? 0 }})
        </span>
        <span
          v-for="sub in currentStageInfo.subCategories"
          :key="sub.value"
          class="sub-pill"
          :class="{ active: curriculumStore.selectedSubCategory === sub.value }"
          @click="selectSubCategory(sub.value)"
        >
          {{ sub.label }} ({{ curriculumStore.subCounts[curriculumStore.selectedStage]?.[sub.value] ?? 0 }})
        </span>
      </div>
      <span v-if="isAdmin" class="add-post-btn" @click="showAddModal = true">+ 게시글 추가</span>
    </div>

    <div v-if="curriculumStore.loading" class="status-message">불러오는 중...</div>
    <div v-else-if="curriculumStore.error" class="status-message error">{{ curriculumStore.error }}</div>
    <div v-else-if="curriculumStore.currentPosts().length === 0" class="empty-state">
      <div class="empty-emoji">{{ currentStageInfo?.icon }}</div>
      <div class="empty-title">아직 등록된 게시글이 없어요</div>
      <div v-if="isAdmin" class="empty-hint">
        피드 탭의 게시글에서 📚 아이콘을 누르거나, 위 "+ 게시글 추가" 버튼으로 등록할 수 있어요.
      </div>
    </div>
    <div v-else class="post-list">
      <CurriculumPostCard
        v-for="entry in curriculumStore.currentPosts()"
        :key="entry.post.id"
        :entry="entry"
        @changed="curriculumStore.refresh()"
      />
    </div>

    <CurriculumAddPostModal v-if="showAddModal" @close="showAddModal = false" @added="onAdded" />
  </div>
</template>

<style scoped>
.curriculum-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 20px;
}

.sub-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.sub-pill {
  font-size: 12.5px;
  font-weight: 600;
  padding: 7px 12px;
  border-radius: 999px;
  background: #ffffff;
  border: 1px solid rgba(26, 26, 46, 0.1);
  color: #1a1a2e;
  cursor: pointer;
  white-space: nowrap;
}

.sub-pill.active {
  background: #f1eefc;
  border-color: #4a3f8f;
  color: #4a3f8f;
}

.add-post-btn {
  font-size: 13px;
  font-weight: 700;
  padding: 9px 16px;
  border-radius: 10px;
  background: #4a3f8f;
  color: #ffffff;
  cursor: pointer;
  white-space: nowrap;
}

.post-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.status-message {
  margin-top: 24px;
  text-align: center;
  color: #636e72;
  font-size: 14px;
}

.status-message.error {
  color: #e01e5a;
}

.empty-state {
  margin-top: 12px;
  padding: 48px 24px;
  text-align: center;
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(26, 26, 46, 0.06);
}

.empty-emoji {
  font-size: 32px;
  margin-bottom: 8px;
}

.empty-title {
  font-size: 14px;
  font-weight: 700;
  color: #1a1a2e;
}

.empty-hint {
  margin-top: 6px;
  font-size: 12.5px;
  color: #636e72;
}
</style>

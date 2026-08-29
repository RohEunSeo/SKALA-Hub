<script setup>
// 커리큘럼 탭의 게시글 카드 - 기존 PostCard를 그대로 감싸고, 관리자에게만 카테고리 변경/제외 아이콘을 오버레이
import { ref, computed } from 'vue'
import { useAuthStore } from '../stores/auth'
import { useToastStore } from '../stores/toast'
import { addCurriculumPost, excludeCurriculumPost } from '../api/admin'
import PostCard from './PostCard.vue'
import CurriculumPicker from './CurriculumPicker.vue'

const props = defineProps({
  entry: { type: Object, required: true }, // CurriculumPostResponse: { post, stage, subCategory, addedBy, createdAt }
})

const emit = defineEmits(['changed'])

const authStore = useAuthStore()
const toastStore = useToastStore()
const isAdmin = computed(() => authStore.effectiveIsAdmin)

const editing = ref(false)

async function saveCategory({ stage, subCategory }) {
  try {
    await addCurriculumPost({ postId: props.entry.post.id, stage, subCategory })
    editing.value = false
    toastStore.show('커리큘럼 카테고리를 변경했습니다')
    emit('changed')
  } catch {
    toastStore.show('카테고리 변경에 실패했습니다. 잠시 후 다시 시도해주세요.')
  }
}

async function exclude() {
  if (!confirm('이 게시글을 커리큘럼 탭에서만 제외할까요? (피드/상세 페이지에는 그대로 남습니다)')) return
  try {
    await excludeCurriculumPost(props.entry.post.id, true)
    toastStore.show('커리큘럼 탭에서 제외했습니다')
    emit('changed')
  } catch {
    toastStore.show('제외 처리에 실패했습니다. 잠시 후 다시 시도해주세요.')
  }
}
</script>

<template>
  <div class="curriculum-post-wrapper">
    <div v-if="isAdmin" class="curriculum-admin-actions">
      <span class="curriculum-admin-btn" title="카테고리 변경" @click.stop="editing = !editing">✏️</span>
      <span class="curriculum-admin-btn" title="커리큘럼에서 제외" @click.stop="exclude">🗑️</span>
    </div>
    <div v-if="editing" class="curriculum-edit-popover">
      <CurriculumPicker
        :initial-stage="entry.stage"
        :initial-sub-category="entry.subCategory"
        confirm-label="변경"
        @confirm="saveCategory"
        @cancel="editing = false"
      />
    </div>
    <PostCard :post="entry.post" />
  </div>
</template>

<style scoped>
.curriculum-post-wrapper {
  position: relative;
}

.curriculum-admin-actions {
  position: absolute;
  top: 16px;
  right: 20px;
  z-index: 3;
  display: flex;
  gap: 6px;
}

.curriculum-admin-btn {
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ffffff;
  border: 1px solid rgba(26, 26, 46, 0.08);
  border-radius: 50%;
  font-size: 13px;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(26, 26, 46, 0.12);
}

.curriculum-admin-btn:hover {
  background: #f1eefc;
}

.curriculum-edit-popover {
  position: absolute;
  top: 52px;
  right: 20px;
  z-index: 4;
}
</style>

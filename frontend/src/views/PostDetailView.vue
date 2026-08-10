<script setup>
// 게시글 상세 화면 - 마이페이지 목록 등에서 특정 게시글로 바로 이동할 때 사용
import { computed, ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppLayout from '../components/AppLayout.vue'
import AuthRequired from '../components/AuthRequired.vue'
import PostCard from '../components/PostCard.vue'
import { useAuthStore } from '../stores/auth'
import { fetchPost } from '../api/posts'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const post = ref(null)
const loading = ref(true)
const notFound = ref(false)
const loadError = ref('')

async function load() {
  if (!authStore.isAuthenticated) return
  loading.value = true
  notFound.value = false
  loadError.value = ''
  post.value = null
  try {
    const { data } = await fetchPost(route.params.id)
    post.value = data
  } catch (error) {
    if (error.response?.status === 404) {
      notFound.value = true
    } else {
      loadError.value = '게시글을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.'
    }
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => route.params.id, load)

// 마이페이지에서 들어왔으면 마이페이지로, 그 외(피드/홈 등)는 기존처럼 피드로 돌아가게 함
const fromMyPage = computed(() => route.query.from === 'mypage')
const backTarget = computed(() => (fromMyPage.value ? { name: 'mypage' } : { name: 'feed' }))
const backLabel = computed(() => (fromMyPage.value ? '← 마이페이지로 돌아가기' : '← 피드로 돌아가기'))
</script>

<template>
  <AppLayout>
    <AuthRequired v-if="!authStore.isAuthenticated" message="게시글을 보려면 SKALA 교육생 인증이 필요합니다" />
    <template v-else>
      <span class="back-link" @click="router.push(backTarget)">{{ backLabel }}</span>

      <div v-if="loading" class="status-message">불러오는 중...</div>
      <div v-else-if="notFound" class="status-message">게시글을 찾을 수 없습니다.</div>
      <div v-else-if="loadError" class="status-message">{{ loadError }}</div>
      <PostCard v-else-if="post" :post="post" :link-to-detail="false" />
    </template>
  </AppLayout>
</template>

<style scoped>
.back-link {
  display: inline-block;
  margin-bottom: 20px;
  font-size: 13px;
  color: #636e72;
  cursor: pointer;
}

.back-link:hover {
  color: #4a3f8f;
}

.status-message {
  margin-top: 24px;
  text-align: center;
  color: #636e72;
  font-size: 14px;
}
</style>

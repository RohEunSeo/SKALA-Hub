<script setup>
// 로그인이 필요한 화면에서 실제 콘텐츠 대신 보여주는 안내 - 별도 로그인 페이지 없이 슬랙 OAuth로 바로 연결
import { getSlackLoginUrl } from '../api/auth'

defineProps({
  message: { type: String, default: 'SKALA 판교 캠퍼스 교육생 인증이 필요합니다' },
})

function handleLogin() {
  window.location.href = getSlackLoginUrl()
}
</script>

<template>
  <div class="auth-required">
    <div class="auth-icon">🔒</div>
    <div class="auth-message">{{ message }}</div>
    <div class="auth-sub"><strong>SKALA 워크스페이스에 가입된 계정</strong>으로 로그인 후 이용해주세요.</div>
    <div class="auth-sub auth-sub-small">* 워크스페이스 입력 화면이 뜬다면 <strong>'theskala'</strong>를 입력해주세요.</div>
    <button class="auth-login-btn" @click="handleLogin">Slack으로 로그인</button>
  </div>
</template>

<style scoped>
.auth-required {
  background: #ffffff;
  border-radius: 16px;
  padding: 56px 32px;
  box-shadow: 0 2px 12px rgba(26, 26, 46, 0.05);
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.auth-icon {
  font-size: 32px;
  margin-bottom: 14px;
}

.auth-message {
  font-size: 16px;
  font-weight: 700;
  color: #1a1a2e;
}

.auth-sub {
  margin-top: 8px;
  font-size: 13.5px;
  color: #636e72;
  line-height: 1.5;
}

.auth-sub strong {
  color: #4a3f8f;
}

.auth-sub-small {
  font-size: 12px;
  opacity: 0.75;
}

.auth-login-btn {
  margin-top: 24px;
  padding: 12px 28px;
  background: #4a3f8f;
  color: #fff;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 8px 20px rgba(74, 63, 143, 0.24);
}

.auth-login-btn:hover {
  background: #6c5ce7;
}
</style>

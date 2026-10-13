<script setup>
// 피드 우하단 AI 챗봇 런처 - 새싹이 든 폴더 모양 플로팅 버튼 (호버하면 폴더가 살짝 열리고 새싹이 까딱)
//
// 인사 말풍선: 호버 라벨(.fab-label)은 마우스를 올려야만 보이고 모바일에선 아예 숨겨져서,
// 챗봇이 있다는 걸 아무도 모른다. 그래서 늘 떠 있는 말풍선을 따로 둔다.
// x로 닫으면 이번 화면(새로고침 전까지)만 숨고, 닫은 뒤에도 아이콘에 마우스를 올리는 동안은
// 툴팁처럼 잠깐 다시 뜬다 - localStorage에 저장하지 않아 새로고침하면 처음 상태로 돌아온다.
//
// position:fixed라 스크롤 위치와 무관하게 늘 뷰포트 우하단에 떠 있다 - 페이지 하단 여백을
// 아무리 늘려도 "그리드가 막 다 보이자마자 스크롤을 멈추는" 가장 흔한 경우엔 말풍선이 결국
// 뒤 콘텐츠(폴더 카드 등)와 같은 자리에 겹친다. 그래서 일정 시간 뒤 자동으로 접어 평소엔
// 작은 아이콘만 남긴다 (호버하면 다시 뜸 - 처음 보는 사람에게 알려주는 목적은 유지)
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { useChatStore } from '../../stores/chat'

const AUTO_HIDE_MS = 6000

const chatStore = useChatStore()

const greetingDismissed = ref(false)
const hovering = ref(false)
let greetingTimer = null

onMounted(() => {
  greetingTimer = setTimeout(() => { greetingDismissed.value = true }, AUTO_HIDE_MS)
})
onUnmounted(() => clearTimeout(greetingTimer))

// 좁아서 뜨는 "화면을 넓히면..." 안내는 닫을 수 있다 - 대신 폴더 아이콘을 다시 누르면 그 자리에서
// 다시 튀어나온다 (narrow가 풀렸다가 다시 좁아질 때도 당연히 다시 뜨도록 리셋). 이것도 같은 이유로
// 일정 시간 뒤 자동으로 접힌다
const narrowHintDismissed = ref(false)
let narrowHintTimer = null
watch(
  () => chatStore.narrow,
  (yes) => {
    clearTimeout(narrowHintTimer)
    if (yes) {
      narrowHintDismissed.value = false
      narrowHintTimer = setTimeout(() => { narrowHintDismissed.value = true }, AUTO_HIDE_MS)
    }
  },
  { immediate: true },
)
onUnmounted(() => clearTimeout(narrowHintTimer))

function onLauncherClick() {
  if (chatStore.narrow) narrowHintDismissed.value = false
  else chatStore.open()
}

// x를 누르는 순간 마우스는 아직 말풍선 위에 있다 - 말풍선이 사라지면 그 자리에서
// mouseleave가 일어날 요소 자체가 없어져서 hovering이 true로 눌어붙는다. 그대로 두면
// "닫았는데도 그 자리에 마우스만 있어도 다시 뜨는" 것처럼 보이므로 닫을 때 같이 꺼준다.
function dismissGreeting() {
  greetingDismissed.value = true
  hovering.value = false
}
</script>

<template>
  <!-- 인사 말풍선. 닫지 않았으면 계속 떠 있고(처음 보는 사람도 바로 챗봇인 걸 알아보게),
       닫았어도 아이콘에 호버하는 동안은 다시 보인다. 좁아서 안내 말풍선이 뜨는 동안은
       겹치지 않게 숨긴다 - narrow가 풀리면 지워지는 게 아니라 바로 이 인사말로 넘어간다 -->
  <Transition name="intro">
    <div
      v-if="!chatStore.isOpen && !chatStore.narrow && (!greetingDismissed || hovering)"
      class="fab-intro"
      role="status"
      @mouseenter="hovering = true"
      @mouseleave="hovering = false"
    >
      <button class="fab-intro-close" aria-label="안내 닫기" @click="dismissGreeting">✕</button>
      <p class="fab-intro-title">저는 Hub 챗봇이에요 👋</p>
      <p class="fab-intro-body">찾는 주제를 말해주시면 관련된 글을 모아 드려요</p>
    </div>
  </Transition>

  <!-- 본문 놓을 자리가 없을 만큼 좁을 때 - 아이콘은 그대로 두고 안내 말풍선만 띄운다.
       닫아도 폴더 아이콘을 다시 누르면 같은 자리에서 다시 튀어나온다 -->
  <Transition name="intro">
    <div v-if="chatStore.narrow && !narrowHintDismissed" class="fab-intro" role="status">
      <button class="fab-intro-close" aria-label="안내 닫기" @click="narrowHintDismissed = true">✕</button>
      <p class="fab-intro-body">화면을 넓히면 챗봇을 볼 수 있어요</p>
    </div>
  </Transition>

  <!-- 패널이 열려 있는 동안은 숨김 (패널 안의 X로 닫음). 좁을 때는 눌러도 패널은 안 열리고
       안내 말풍선만 다시 뜬다 -->
  <button
    v-show="!chatStore.isOpen"
    class="chat-launcher"
    :class="{ 'has-intro': !chatStore.narrow, narrow: chatStore.narrow }"
    aria-label="AI 도우미 열기"
    @click="onLauncherClick"
    @mouseenter="hovering = true"
    @mouseleave="hovering = false"
  >
    <!-- 둥근 사각형 탭+몸통 조합 - SVG 곡선/통합 경로는 이음새·각짐 문제가 계속 나와서
         가장 둥글둥글하다고 했던 단순한 두 사각형 조합으로 복귀 -->
    <span class="fab-folder">
      <span class="fab-tab"></span>
      <span class="fab-back"></span>
      <span class="fab-paper"></span>
      <span class="fab-front">
        <svg class="fab-sprout" viewBox="0 0 48 48" aria-hidden="true">
          <path d="M24 37 C24 34 24 30 24 27" fill="none" stroke="#43a047" stroke-width="3.4" stroke-linecap="round" />
          <path d="M24 27 C14.5 27 9 19.5 9.5 11 C18 11 24 16.5 24 27 Z" fill="#5fb865" stroke="#3d8b41" stroke-width="0.8" stroke-linejoin="round" />
          <path d="M24 27 C33.5 27 39 19.5 38.5 11 C30 11 24 16.5 24 27 Z" fill="#5fb865" stroke="#3d8b41" stroke-width="0.8" stroke-linejoin="round" />
        </svg>
      </span>
    </span>
    <span v-if="chatStore.savedCount" class="fab-badge">{{ chatStore.savedCount }}</span>
    <span class="fab-label">AI에게 물어보기</span>
  </button>
</template>

<style scoped>
/* 디자인 2 - 원형 테두리/배경 없이 원래 쓰던 폴더+새싹 그래픽만 떠 있는 형태.
   색은 연보라 대신 SKALA Hub 메인 보라(#4a3f8f)/서브 보라(#6c5ce7)로 진하게 - 그래픽
   자체가 버튼이라 동그란 틀이 없어도 바로 알아볼 수 있고, 브랜드 컬러와도 맞는다 */
.chat-launcher {
  position: fixed;
  right: 32px;
  bottom: 34px;
  z-index: 60;
  width: 50px;
  height: 41px;
  /* 말풍선과 같은 주기로 움직여야 둘이 붙어 다니는 것처럼 보인다 */
  animation: fab-float 3.2s ease-in-out infinite;
  padding: 0;
  border: 0;
  background: none;
  cursor: pointer;
  transition: transform 0.2s ease;
}

.chat-launcher:hover,
.chat-launcher:focus-visible {
  transform: scale(1.06);
}

.chat-launcher:focus-visible {
  outline: 2px solid #6c5ce7;
  outline-offset: 4px;
}

/* 원형 배경이 없어진 대신, 그래픽 자체에 그림자를 줘서 떠 있는 느낌을 잡아준다 */
.fab-folder {
  position: relative;
  display: block;
  width: 100%;
  height: 100%;
  perspective: 160px;
  filter: drop-shadow(0 5px 10px rgba(74, 63, 143, 0.4));
  transition: filter 0.2s ease;
}

.chat-launcher:hover .fab-folder,
.chat-launcher:focus-visible .fab-folder {
  filter: drop-shadow(0 7px 14px rgba(74, 63, 143, 0.5));
}

.fab-folder > span {
  position: absolute;
  display: block;
}

/* 가장 둥글둥글하다고 했던 단순한 두 사각형(탭+몸통) 조합. 탭 오른쪽은 clip-path로
   대각선으로 잘라내 실제 폴더 탭처럼 경사지게 보이게 한다 */
.fab-tab {
  left: 0;
  top: 0;
  width: 58%;
  height: 19%;
  border-radius: 5px 0 0 0;
  background: #b2a9e3;
  clip-path: polygon(0 0, 62% 0, 100% 100%, 0 100%);
}

.fab-back {
  left: 0;
  right: 0;
  top: 12%;
  bottom: 0;
  border-radius: 7px;
  background: #b2a9e3;
}

/* 말풍선 꼬리 - '대화하는 폴더'로 읽히게 하는 장치. 고정된 뒷면에 달아야 호버해도 안 휜다 */
.fab-back::after {
  content: '';
  position: absolute;
  right: 6px;
  bottom: -6px;
  width: 0;
  height: 0;
  border-top: 9px solid #b9b1e3;
  border-left: 10px solid transparent;
}

.fab-paper {
  left: 8%;
  right: 8%;
  top: 17%;
  height: 46%;
  border-radius: 4px;
  background: #ffffff;
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.fab-front {
  left: 0;
  right: 0;
  top: 26%;
  bottom: 0;
  border-radius: 7px;
  transform-origin: 50% 100%;
  background: linear-gradient(#cfc9f3, #b9b1e3);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.6);
  transition: transform 0.32s cubic-bezier(0.34, 1.56, 0.64, 1);
  box-sizing: border-box;
}

.fab-sprout {
  position: absolute;
  left: 50%;
  top: 40%;
  transform: translate(-50%, -50%);
  width: 16px;
  height: 16px;
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.chat-launcher:hover .fab-front,
.chat-launcher:focus-visible .fab-front {
  transform: rotateX(-22deg);
}

.chat-launcher:hover .fab-paper,
.chat-launcher:focus-visible .fab-paper {
  transform: translateY(-2px);
}

.chat-launcher:hover .fab-sprout {
  transform: translate(-50%, -50%) rotate(-8deg) scale(1.12);
}

/* 챗봇으로 저장한 글 수 배지 */
.fab-badge {
  position: absolute;
  top: -6px;
  right: -6px;
  min-width: 20px;
  height: 20px;
  padding: 0 5px;
  border-radius: 999px;
  background: #4a3f8f;
  color: #ffffff;
  font-size: 11px;
  font-weight: 600;
  line-height: 20px;
  text-align: center;
  border: 2px solid #fafafa;
  box-sizing: content-box;
}

/* 호버하면 왼쪽으로 라벨이 펼쳐짐 */
.fab-label {
  position: absolute;
  right: 60px;
  top: 50%;
  transform: translateY(-50%) translateX(8px);
  padding: 6px 12px;
  border-radius: 999px;
  background: #ffffff;
  color: #4a3f8f;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  box-shadow: 0 2px 8px rgba(26, 26, 46, 0.12);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.2s ease, transform 0.25s ease;
}

.chat-launcher:hover .fab-label {
  opacity: 1;
  transform: translateY(-50%) translateX(0);
}

/* 모바일은 호버가 없으니 라벨 생략 */
@media (max-width: 900px) {
  .fab-label {
    display: none;
  }
}

/* 말풍선이 떠 있는 동안은 호버 라벨을 숨긴다 (같은 자리라 겹친다) */
.chat-launcher.has-intro:hover .fab-label {
  opacity: 0;
}

/* 좁아서 안내만 뜨는 동안은 눌러도 안 열리므로 "AI에게 물어보기" 라벨도 안 보여준다 */
.chat-launcher.narrow:hover .fab-label {
  opacity: 0;
}

/* ── 인사 말풍선 ───────────────────────────────────────────── */
.fab-intro {
  position: fixed;
  right: 52px;          /* 런처(right 32, 폭 50)보다 왼쪽으로 */
  bottom: 85px;          /* 런처 윗변(bottom 75)보다 위로 */
  animation: fab-float 3.2s ease-in-out infinite;
  z-index: 60;
  max-width: 320px;
  padding: 10px 26px 10px 12px;
  border-radius: 12px;
  background: #f7f5fd;
  border: 1.5px solid rgba(74, 63, 143, 0.4);
  box-shadow: 0 8px 22px rgba(74, 63, 143, 0.4);
  text-align: left;
}

/* 런처를 가리키는 꼬리 */
.fab-intro::after {
  content: '';
  position: absolute;
  right: 18px;
  bottom: -4px;
  width: 8px;
  height: 8px;
  background: #f7f5fd;
  transform: rotate(45deg);
  box-shadow: 2px 2px 3px rgba(74, 63, 143, 0.1);
}

.fab-intro-title {
  margin: 0 0 3px;
  color: #4a3f8f;
  font-size: 12px;
  font-weight: 700;
}

.fab-intro-body {
  margin: 0;
  color: #4a3f8f;
  font-weight: 700;
  font-size: 11px;
  line-height: 1.5;
  white-space: nowrap;   /* 한 줄로 유지 (좁은 화면에서는 아래에서 다시 푼다) */
}

.fab-intro-close {
  position: absolute;
  top: 5px;
  right: 7px;
  padding: 2px 4px;
  border: 0;
  background: none;
  color: #9a93b5;
  font-size: 10px;
  line-height: 1;
  cursor: pointer;
}

.fab-intro-close:hover {
  color: #4a3f8f;
}

.intro-enter-active,
.intro-leave-active {
  transition: opacity 0.25s ease;
}

.intro-enter-from,
.intro-leave-to {
  opacity: 0;
}

/* 가만히 있으면 눈에 안 들어와서 아주 느리게 위아래로 뜬다 */
@keyframes fab-float {
  0%, 100% { transform: translateY(0); }
  50%      { transform: translateY(-6px); }
}

/* 좁은 화면에서는 옆에 둘 자리가 없어 런처 위로 올린다 */
@media (max-width: 900px) {
  .fab-intro {
    right: 20px;
    bottom: 85px;
    max-width: calc(100vw - 40px);
  }
  .fab-intro-body {
    white-space: normal;   /* 화면이 좁으면 두 줄이 되는 게 낫다 */
  }

}

@media (prefers-reduced-motion: reduce) {
  .chat-launcher, .fab-front, .fab-paper, .fab-sprout, .fab-label { transition: none; }
  .intro-enter-active, .intro-leave-active { transition: none; }
  .chat-launcher, .fab-intro { animation: none; }
}
</style>

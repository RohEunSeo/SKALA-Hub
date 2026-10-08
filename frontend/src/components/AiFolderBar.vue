<script setup>
// 피드 필터 줄의 "내 폴더" - 폴더 카드 그리드 + 맨 끝 ＋ 타일.
// ＋를 누르면 회색 폴더가 바로 생기고 카드 안에서 이름·색을 고친다(저장 버튼 없이 즉시 반영).
// 폴더 데이터는 stores/folders.js (서버 저장), 선택 상태는 foldersStore.selected
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useFoldersStore, FOLDER_COLORS, NEW_FOLDER_COLOR } from '../stores/folders'
import FolderCard from './FolderCard.vue'
import ConfirmDialog from './ConfirmDialog.vue'
import { folderTextColor } from '../utils/folderColors'
import { useCommunityStore } from '../stores/community'
import { useAuthStore } from '../stores/auth'

const foldersStore = useFoldersStore()
const communityStore = useCommunityStore()
const authStore = useAuthStore()

const editingId = ref(null) // 지금 카드 안에서 편집 중인 폴더 id
const nameInput = ref(null)

async function startCreate() {
  // create()는 같은 이름이 있으면 기존 폴더를 돌려주므로 기본 이름을 겹치지 않게 만든다
  const taken = new Set(foldersStore.folders.map((f) => f.name))
  let base = '새 폴더'
  for (let i = 2; taken.has(base); i += 1) base = `새 폴더 ${i}`

  // 회색 폴더를 먼저 만들고 편집 상태로 열어 그 자리에서 이름을 받는다
  const folder = await foldersStore.create(base, NEW_FOLDER_COLOR)
  if (!folder) return
  foldersStore.selected = folder.id
  editingId.value = folder.id
  await nextTick()
  nameInput.value?.[0]?.select()
}

async function startEdit(f) {
  editingId.value = f.id
  await nextTick()
  nameInput.value?.[0]?.select()
}

// 입력할 때마다 바로 반영 - 따로 저장을 누를 필요가 없다
function rename(id, value) {
  foldersStore.update(id, { name: value })
}

function recolor(id, value) {
  foldersStore.update(id, { color: value })
}

function done() {
  const f = foldersStore.byId(editingId.value)
  if (f && !f.name.trim()) foldersStore.update(f.id, { name: '새 폴더' })
  editingId.value = null
}

// 완료 버튼 없이 이름·색이 즉시 반영되므로, 바깥을 누르면 편집을 닫는다
const barEl = ref(null)

function onDocClick(e) {
  if (!editingId.value) return
  if (!barEl.value?.contains(e.target)) done()
}

onMounted(() => document.addEventListener('click', onDocClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocClick))

// ── 드래그로 순서 바꾸기 ──────────────────────────────────────
// 끌고 지나가는 즉시 배열을 바꿔서 다른 폴더들이 실시간으로 밀려난다.
// 자리 이동 애니메이션은 TransitionGroup(.fmove)이 맡는다.
const dragIndex = ref(null)

function onDragStart(index, e) {
  dragIndex.value = index
  e.dataTransfer.effectAllowed = 'move'
  // 드래그 중 기본 고스트가 반투명 박스로 보이도록 최소한만 설정
  e.dataTransfer.setData('text/plain', String(index))
}

function onDragEnter(index) {
  if (dragIndex.value === null || dragIndex.value === index) return
  foldersStore.reorder(dragIndex.value, index)
  dragIndex.value = index
}

function onDragEnd() {
  dragIndex.value = null
}

// ── 삭제 ────────────────────────────────────────────────────
// 하나씩 지우기 번거로워 "선택 삭제 / 모두 삭제"를 한 다이얼로그에서 처리한다
const confirmOpen = ref(false)
const pendingIds = ref([]) // 지우려는 폴더 id 목록

function askRemove(id) {
  pendingIds.value = [id]
  confirmOpen.value = true
}

// 여러 개를 한 번에 고르는 모드 - 켜면 폴더마다 체크박스가 생긴다
const selectMode = ref(false)
const checked = ref(new Set())

function toggleSelectMode() {
  selectMode.value = !selectMode.value
  checked.value = new Set()
  editingId.value = null
}

function toggleCheck(id) {
  const next = new Set(checked.value)
  next.has(id) ? next.delete(id) : next.add(id)
  checked.value = next
}

function askRemoveChecked() {
  if (!checked.value.size) return
  pendingIds.value = [...checked.value]
  confirmOpen.value = true
}

function askRemoveAll() {
  if (!foldersStore.folders.length) return
  pendingIds.value = foldersStore.folders.map((f) => f.id)
  confirmOpen.value = true
}

const confirmTitle = computed(() =>
  pendingIds.value.length > 1 ? `폴더 ${pendingIds.value.length}개를 삭제할까요?` : '이 폴더를 삭제할까요?',
)

const confirmMessage = computed(() => {
  const names = pendingIds.value.map((id) => foldersStore.byId(id)?.name).filter(Boolean)
  const subject = names.length > 1 ? `'${names[0]}' 외 ${names.length - 1}개` : `'${names[0] ?? ''}'`
  return `${subject} 폴더가 사라집니다. 안에 담긴 글은 삭제되지 않고 저장한 글에 그대로 남아요.`
})

function doRemove() {
  foldersStore.removeMany(pendingIds.value)
  editingId.value = null
  confirmOpen.value = false
  pendingIds.value = []
  selectMode.value = false
  checked.value = new Set()
}

</script>

<template>
  <div ref="barEl" class="fbar">
    <div class="fhead">
      <span class="ftitle">내 폴더</span>
      <div class="factions">
        <button class="fbtn" :class="{ active: !foldersStore.selected }" @click="foldersStore.selected = null">
          전체 글
        </button>
        <template v-if="foldersStore.folders.length">
          <button class="fbtn" :class="{ active: selectMode }" @click="toggleSelectMode">
            {{ selectMode ? '선택 취소' : '선택' }}
          </button>
          <button v-if="selectMode" class="fbtn fdanger" :disabled="!checked.size" @click="askRemoveChecked">
            선택 {{ checked.size }}개 삭제
          </button>
          <button v-else class="fbtn fdanger" @click="askRemoveAll">모두 삭제</button>
        </template>
        <button
          v-if="foldersStore.selected && authStore.effectiveIsAdmin"
          class="fbtn fpush"
          @click="communityStore.openPush(foldersStore.selected)"
        >
          ↑ 공유 드라이브에 Push
        </button>
      </div>
    </div>

    <!-- 끌어서 순서를 바꾸면 TransitionGroup이 나머지 폴더를 실시간으로 밀어낸다 -->
    <TransitionGroup tag="div" name="fmove" class="fgrid">
      <div
        v-for="(f, i) in foldersStore.folders"
        :key="f.id"
        class="fslot"
        :class="{ dragging: dragIndex === i }"
        draggable="true"
        @dragstart="onDragStart(i, $event)"
        @dragenter.prevent="onDragEnter(i)"
        @dragover.prevent
        @dragend="onDragEnd"
      >
        <FolderCard
          class="fcard"
          :color="f.color"
          :active="foldersStore.selected === f.id"
          role="button"
          tabindex="0"
          @click="selectMode ? toggleCheck(f.id) : (foldersStore.selected = f.id)"
          @keydown.enter="selectMode ? toggleCheck(f.id) : (foldersStore.selected = f.id)"
        >
          <!-- 지금 열어 둔 폴더 표시. 고르기 모드의 체크박스와 자리가 겹치지 않게 모드일 땐 숨긴다 -->
          <span v-if="!selectMode && foldersStore.selected === f.id" class="fpicked" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor"
                 stroke-width="3.4" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12l5 5L19 7" /></svg>
          </span>
          <button
            v-if="selectMode"
            class="fcheck"
            :class="{ on: checked.has(f.id) }"
            :aria-label="`${f.name} 선택`"
            @click.stop="toggleCheck(f.id)"
          >
            <svg v-if="checked.has(f.id)" viewBox="0 0 24 24" aria-hidden="true">
              <path d="M5 13l4 4L19 7" fill="none" stroke="currentColor" stroke-width="3.4"
                stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </button>

          <input
            v-if="editingId === f.id"
            ref="nameInput"
            class="fnameinput"
            :value="f.name"
            maxlength="20"
            aria-label="폴더 이름"
            :style="{ color: folderTextColor(f.color) }"
            @click.stop
            @input="rename(f.id, $event.target.value)"
            @keydown.enter="done"
            @keydown.esc="done"
          />
          <div v-else class="fname" :style="{ color: folderTextColor(f.color) }">
            <span v-if="f.origin" class="forigin" :title="`${f.origin}에서 clone한 폴더`">⑂</span>{{ f.name }}
          </div>

          <!-- 편집 중에는 카드 가운데 빈 공간에 색을 띄운다 -->
          <div v-if="editingId === f.id" class="swatches" @click.stop>
            <button
              v-for="c in FOLDER_COLORS"
              :key="c"
              class="swatch"
              :class="{ on: f.color === c }"
              :style="{ background: c }"
              :aria-label="`폴더 색 ${c}`"
              @click="recolor(f.id, c)"
            >
              <svg v-if="f.color === c" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M5 13l4 4L19 7" fill="none" stroke="currentColor" stroke-width="3.4"
                  stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </button>
          </div>

          <div class="fcount" :style="{ color: folderTextColor(f.color) }">글 {{ foldersStore.count(f.id) }}개</div>

          <button
            class="ficon fedit"
            :class="{ editing: editingId === f.id }"
            :aria-label="editingId === f.id ? '편집 끝내기' : '폴더 이름·색 바꾸기'"
            @click.stop="editingId === f.id ? done() : startEdit(f)"
          >
            <svg v-if="editingId === f.id" viewBox="0 0 24 24" aria-hidden="true">
              <path d="M5 13l4 4L19 7" fill="none" stroke="currentColor" stroke-width="3"
                stroke-linecap="round" stroke-linejoin="round" />
            </svg>
            <svg v-else viewBox="0 0 24 24" aria-hidden="true">
              <path d="M4 20h4L19 9a2.1 2.1 0 0 0-3-3L5 17v3z" fill="none" stroke="currentColor"
                stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
              <path d="M14.5 6.5l3 3" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
            </svg>
          </button>

          <button class="ficon ftrash" aria-label="폴더 삭제" @click.stop="askRemove(f.id)">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M4 7h16M10 4h4M9 7v12m6-12v12M6 7l1 14h10l1-14" fill="none" stroke="currentColor"
                stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </button>
        </FolderCard>
      </div>

      <!-- 폴더 모양 윤곽선의 + 타일 - 사각형이면 그리드에서 혼자 튄다 -->
      <button key="__add" class="fadd" aria-label="새 폴더 만들기" @click="startCreate">
        <svg class="fadd-shape" viewBox="0 0 200 170" preserveAspectRatio="none" aria-hidden="true">
          <path
            d="M2,14 A12,12 0 0 1 14,2 H48 C57,2 61,4.5 67,10 C72,14 76,15 84,15 H186 A12,12 0 0 1 198,27 V156 A12,12 0 0 1 186,168 H14 A12,12 0 0 1 2,156 Z"
            fill="none" stroke="currentColor" stroke-width="2.5" stroke-dasharray="7 6" />
        </svg>
        <span class="fadd-plus">＋</span>
      </button>
    </TransitionGroup>

    <ConfirmDialog
      :open="confirmOpen"
      :title="confirmTitle"
      :message="confirmMessage"
      :confirm-label="pendingIds.length > 1 ? `${pendingIds.length}개 삭제` : '삭제'"
      @confirm="doRemove"
      @cancel="confirmOpen = false"
    />
  </div>
</template>

<style scoped>
/* 카테고리 칩 줄과 섞이지 않도록 영역을 박스로 묶는다 */
.fbar {
  margin-bottom: 14px;
  padding: 12px 14px 14px;
  border: 1px solid rgba(26, 26, 46, 0.09);
  border-radius: 14px;
  background: #ffffff;
}

.fhead {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.ftitle {
  font-size: 13px;
  font-weight: 700;
  color: #1a1a2e;
}

.factions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-left: auto;
}

/* 폴더 카드와 같은 줄에 두지 않아서 카드 높이를 따라 늘어나지 않는다 */
.fbtn {
  height: 32px;
  padding: 0 14px;
  border: 1px solid rgba(26, 26, 46, 0.1);
  border-radius: 8px;
  background: #ffffff;
  color: #1a1a2e;
  font: inherit;
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease;
}

.fbtn:hover {
  background: rgba(26, 26, 46, 0.04);
}

.fbtn.active {
  border-color: #4a3f8f;
  background: #f1eefc;
  color: #4a3f8f;
}

.fgrid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 18px;
}

@media (max-width: 900px) {
  .fgrid {
    grid-template-columns: repeat(4, 1fr);
  }
}

@media (max-width: 560px) {
  .fgrid {
    grid-template-columns: repeat(2, 1fr);
  }
}

/* 홈 "카테고리별 아카이브"의 폴더 카드를 필터 크기로 줄여 쓴다.
   FolderCard가 cqw 단위라 폭만 정하면 안쪽 글씨/여백이 같은 비율로 따라 줄어든다. */
.fslot {
  position: relative;
}

/* + 타일 - 폴더 윤곽선을 그려서 그리드에서 혼자 사각형으로 튀지 않게 한다 */
.fadd {
  position: relative;
  aspect-ratio: 200 / 170;
  border: 0;
  padding: 0;
  background: transparent;
  color: rgba(26, 26, 46, 0.26);
  cursor: pointer;
  transition: color 0.15s ease;
}

.fadd-shape {
  width: 100%;
  height: 100%;
  display: block;
}

.fadd-plus {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  font-size: 22px;
  color: #8890a3;
  transition: color 0.15s ease;
}

.fadd:hover {
  color: rgba(74, 63, 143, 0.55);
}

.fadd:hover .fadd-plus {
  color: #4a3f8f;
}

.fcard {
  cursor: pointer;
  position: relative;
}

/* 카드 안에서 이름을 바로 고친다 - 테두리 없이 글씨처럼 보이게 */
.fnameinput {
  width: 100%;
  padding: 0;
  border: 0;
  border-bottom: 1px solid currentColor;
  background: transparent;
  font: inherit;
  font-size: 11.5px;
  font-weight: 700;
  outline: none;
}





.fname {
  font-size: 11.5px;
  font-weight: 700;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fcount {
  margin-top: 1px;
  font-size: 10.5px;
  opacity: 0.75;
}





.fcount {
  font-size: 11px;
  opacity: 0.7;
}

/* 수정 버튼은 폴더 오른쪽 위에 겹쳐 두고, 올려둘 때만 보인다 */









.forigin { margin-right: 2px; font-size: 12px; color: #6c5ce7; }
/* 연필·휴지통 - 항상 옅게 보이다가 올려두면 또렷해진다. 아예 숨기면 있는 줄 모른다 */
.ficon {
  position: absolute;
  z-index: 3;
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  padding: 0;
  border: 0;
  border-radius: 7px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 1px 3px rgba(26, 26, 46, 0.16);
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s ease, background 0.15s ease, transform 0.15s ease;
}

.ficon svg {
  width: 14px;
  height: 14px;
}

.fcard:hover .ficon,
.ficon:focus-visible {
  opacity: 1;
}

/* 편집 중에는 완료(체크) 버튼이 계속 보여야 팔레트를 닫는 방법을 알 수 있다 */
.fedit.editing {
  opacity: 1;
  background: #4a3f8f;
  color: #ffffff;
}

.ficon:hover {
  transform: scale(1.12);
  background: #ffffff;
}

.fedit { top: 7px; right: 7px; color: #4a3f8f; }
.ftrash { right: 7px; bottom: 7px; color: #e01e5a; }
.ftrash:hover { background: #fdeef1; }

/* 폴더 카드 안(이름과 개수 사이)에 들어가는 색 선택 */
.swatches {
  display: flex;
  flex-wrap: nowrap;
  gap: 4px;
  margin-top: 7px;
}

.swatch {
  display: grid;
  place-items: center;
  flex: none;
  width: 12px;
  height: 12px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  box-shadow: 0 0 0 1.2px rgba(255, 255, 255, 0.85);
  color: #ffffff;
  cursor: pointer;
  transition: transform 0.15s ease;
}

.swatch:hover {
  transform: scale(1.18);
}

/* 고른 색에는 체크를 넣는다 - 작은 원에서는 테두리만으로 구분이 안 된다 */
.swatch.on {
  transform: scale(1.25);
  box-shadow: 0 0 0 1.8px rgba(26, 26, 46, 0.55);
}

.swatch svg {
  width: 8px;
  height: 8px;
}

/* 선택 모드 체크박스 - 폴더 왼쪽 위 */
.fpicked {
  position: absolute;
  top: 5px;
  right: 5px;
  z-index: 3;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #4a3f8f;
  color: #fff;
  box-shadow: 0 1px 4px rgba(26, 26, 46, 0.25);
}

.fcheck {
  position: absolute;
  top: 7px;
  left: 7px;
  z-index: 3;
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: 1.5px solid rgba(26, 26, 46, 0.3);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.9);
  color: #ffffff;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
}

.fcheck.on {
  border-color: #4a3f8f;
  background: #4a3f8f;
}

.fcheck svg {
  width: 12px;
  height: 12px;
}

.fbtn:disabled {
  opacity: 0.45;
  cursor: default;
}

/* 끌어서 순서 바꾸기 - 끌리는 카드는 흐려지고 나머지가 미끄러져 자리를 내준다 */
.fslot {
  cursor: grab;
}

.fslot.dragging {
  opacity: 0.4;
}

.fmove-move {
  transition: transform 0.3s cubic-bezier(0.22, 1, 0.36, 1);
}

@media (prefers-reduced-motion: reduce) {
  .fmove-move,
  .ficon,
  .swatch {
    transition: none;
  }
}

.fdanger { color: #e01e5a; border-color: rgba(224, 30, 90, 0.3); }
.fpush { color: #4a3f8f; border-color: rgba(74, 63, 143, 0.35); }
</style>

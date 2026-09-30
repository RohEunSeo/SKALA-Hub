<script setup>
// "AI 추천" 탭 안의 내 폴더 바 - 전체 추천 / 폴더 칩 / 새 폴더 만들기(이름+색) / 수정·삭제.
// 폴더 데이터는 stores/folders.js (지금은 브라우저 localStorage 목업), 선택 상태는 foldersStore.selected
import { ref } from 'vue'
import { useFoldersStore, FOLDER_COLORS } from '../stores/folders'
import FolderIcon from './FolderIcon.vue'

const foldersStore = useFoldersStore()

const formOpen = ref(false)
const editingId = ref(null)
const name = ref('')
const color = ref(FOLDER_COLORS[0])

function startCreate() {
  editingId.value = null
  name.value = ''
  color.value = FOLDER_COLORS[foldersStore.folders.length % FOLDER_COLORS.length]
  formOpen.value = true
}

function startEdit(f) {
  editingId.value = f.id
  name.value = f.name
  color.value = f.color
  formOpen.value = true
}

function submit() {
  if (!name.value.trim()) return
  if (editingId.value) foldersStore.update(editingId.value, { name: name.value, color: color.value })
  else foldersStore.selected = foldersStore.create(name.value, color.value)?.id ?? foldersStore.selected // 만들면 바로 그 폴더로 이동
  formOpen.value = false
}

function remove() {
  if (!window.confirm('폴더를 삭제할까요? 안의 글은 삭제되지 않고 저장한 글에 그대로 남습니다.')) return
  foldersStore.remove(editingId.value)
  formOpen.value = false
}
</script>

<template>
  <div class="fbar">
    <button class="fchip" :class="{ active: !foldersStore.selected }" @click="foldersStore.selected = null">전체 추천</button>
    <div
      v-for="f in foldersStore.folders"
      :key="f.id"
      class="fchip"
      :class="{ active: foldersStore.selected === f.id }"
      role="button"
      tabindex="0"
      @click="foldersStore.selected = f.id"
      @keydown.enter="foldersStore.selected = f.id"
    >
      <FolderIcon :color="f.color" :size="18" />
      {{ f.name }}<span class="fcount">({{ foldersStore.count(f.id) }})</span>
      <button class="fedit" aria-label="폴더 수정" @click.stop="startEdit(f)">✎</button>
    </div>
    <button class="fchip fnew" @click="startCreate">＋ 새 폴더</button>

    <div v-if="formOpen" class="fform">
      <input v-model="name" maxlength="20" placeholder="폴더 이름" aria-label="폴더 이름" @keydown.enter="submit" />
      <div class="swatches">
        <button
          v-for="c in FOLDER_COLORS"
          :key="c"
          class="swatch"
          :class="{ on: color === c }"
          :style="{ background: c }"
          :aria-label="`폴더 색 ${c}`"
          @click="color = c"
        ></button>
      </div>
      <button class="fok" :disabled="!name.trim()" @click="submit">{{ editingId ? '저장' : '만들기' }}</button>
      <button class="fcancel" @click="formOpen = false">취소</button>
      <button v-if="editingId" class="fdel" @click="remove">삭제</button>
    </div>
  </div>
</template>

<style scoped>
.fbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.fchip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border: 1px solid rgba(26, 26, 46, 0.1);
  border-radius: 10px;
  background: #ffffff;
  color: #1a1a2e;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.fchip.active {
  border-color: #4a3f8f;
  background: #4a3f8f;
  color: #ffffff;
}

.fcount {
  font-size: 11px;
  opacity: 0.7;
}

.fedit {
  padding: 0 2px;
  border: 0;
  background: none;
  color: inherit;
  font-size: 12px;
  cursor: pointer;
  opacity: 0;
}

.fchip:hover .fedit,
.fedit:focus {
  opacity: 0.7;
}

.fnew {
  border-style: dashed;
  color: #4a3f8f;
}

.fform {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 12px;
  border-radius: 12px;
  background: #f1eefc;
}

.fform input {
  flex: 1;
  min-width: 140px;
  padding: 8px 10px;
  border: 1px solid #d9d4f0;
  border-radius: 8px;
  font: inherit;
  font-size: 13px;
}

.swatches {
  display: flex;
  gap: 6px;
}

.swatch {
  width: 22px;
  height: 22px;
  border: 2px solid transparent;
  border-radius: 50%;
  cursor: pointer;
}

.swatch.on {
  border-color: #4a3f8f;
}

.fform button:not(.swatch) {
  padding: 6px 12px;
  border: 0;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.fok { background: #4a3f8f; color: #fff; }
.fok:disabled { background: #b2a9e3; cursor: default; }
.fcancel { background: #e0dcf3; color: #4a3f8f; }
.fdel { margin-left: auto; background: none; color: #e01e5a; }
</style>

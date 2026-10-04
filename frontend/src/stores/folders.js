// 저장한 글 "내 폴더" - 지금은 프론트만 (브라우저 localStorage, 서버 연동 전 목업).
// 연동 시 이 스토어의 함수 시그니처는 두고 내용만 API 호출로 교체한다.
import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

const KEY = 'skala-folders'
const MAX_FOLDERS = 20
// 폴더 색 선택지 - FolderCard가 흰색을 38% 섞어 파스텔로 바꾸므로 여기엔 원색을 둔다.
// 파스텔 값을 넣으면 두 번 연해져서 색이 날아간다. 카테고리(categories.js)와 같은 채도.
export const FOLDER_COLORS = ['#E0607D', '#E8A33D', '#2BB3A3', '#5B8DEF', '#8B6FD6', '#E4762C']
// 새로 만든 폴더의 기본색 - 아직 아무 색도 고르지 않았다는 뜻의 중립 회색
export const NEW_FOLDER_COLOR = '#B0B4BF'

// 채도를 올리기 전 파스텔 색으로 만들어 둔 폴더를 같은 계열 원색으로 옮긴다
const LEGACY_COLORS = {
  '#ecb0be': '#E0607D',
  '#f1c687': '#E8A33D',
  '#7cd0c6': '#2BB3A3',
  '#99b8f5': '#5B8DEF',
  '#b7a6e6': '#8B6FD6',
  '#f0a98b': '#E4762C',
  '#e09b5b': '#E4762C',
}

function load() {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY)) ?? { folders: [], map: {} }
    saved.folders?.forEach((f) => {
      const next = LEGACY_COLORS[f.color?.toLowerCase()]
      if (next) f.color = next
    })
    return saved
  } catch {
    return { folders: [], map: {} }
  }
}

export const useFoldersStore = defineStore('folders', () => {
  const saved = load()
  const folders = ref(saved.folders) // { id, name, color }[]
  const map = ref(saved.map) // { [postId]: folderId } - 한 글은 폴더 한 곳에만
  const selected = ref(null) // AI 추천 탭에서 열어 둔 폴더 id (null이면 전체 추천) - 저장하지 않는 화면 상태

  watch([folders, map], () => {
    try { localStorage.setItem(KEY, JSON.stringify({ folders: folders.value, map: map.value })) } catch { /* 저장 불가 환경은 무시 */ }
  }, { deep: true })

  const byId = (id) => folders.value.find((f) => f.id === id) ?? null
  const folderOf = (postId) => byId(map.value[postId])
  const idsOf = (folderId) => Object.keys(map.value).filter((k) => map.value[k] === folderId).map(Number)
  const count = (folderId) => idsOf(folderId).length

  // 새 폴더 - 같은 이름이 있으면 그 폴더를 그대로 돌려줌, 개수 초과면 null
  function create(name, color = FOLDER_COLORS[folders.value.length % FOLDER_COLORS.length]) {
    const trimmed = name.trim().slice(0, 20)
    if (!trimmed) return null
    const dup = folders.value.find((f) => f.name === trimmed)
    if (dup) return dup
    if (folders.value.length >= MAX_FOLDERS) return null
    const folder = { id: Date.now().toString(36), name: trimmed, color }
    folders.value.push(folder)
    return folder
  }

  function update(id, { name, color }) {
    const f = byId(id)
    if (!f) return
    if (name?.trim()) f.name = name.trim().slice(0, 20)
    if (color) f.color = color
  }

  // 폴더 삭제 - 안의 글은 지워지지 않고 "폴더 없음"으로 돌아감
  function remove(id) {
    if (selected.value === id) selected.value = null
    folders.value = folders.value.filter((f) => f.id !== id)
    Object.keys(map.value).forEach((k) => map.value[k] === id && delete map.value[k])
  }

  // 드래그로 순서 바꾸기 - 끄는 동안 실시간으로 자리를 밀어내도록 한 칸씩 옮긴다
  function reorder(fromIndex, toIndex) {
    if (fromIndex === toIndex) return
    const list = folders.value
    if (fromIndex < 0 || toIndex < 0 || fromIndex >= list.length || toIndex >= list.length) return
    const [moved] = list.splice(fromIndex, 1)
    list.splice(toIndex, 0, moved)
  }

  // 여러 폴더를 한 번에 삭제 - 안의 글은 "폴더 없음"으로 돌아간다
  function removeMany(ids) {
    ids.forEach(remove)
  }

  // postIds를 폴더로 이동 (folderId가 null이면 폴더 해제)
  function assign(postIds, folderId) {
    postIds.forEach((id) => (folderId ? (map.value[id] = folderId) : delete map.value[id]))
  }

  return {
    folders, map, selected, byId, folderOf, idsOf, count,
    create, update, remove, removeMany, reorder, assign,
  }
})

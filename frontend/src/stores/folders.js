// 저장한 글 "내 폴더" - 지금은 프론트만 (브라우저 localStorage, 서버 연동 전 목업).
// 연동 시 이 스토어의 함수 시그니처는 두고 내용만 API 호출로 교체한다.
import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

const KEY = 'skala-folders'
const MAX_FOLDERS = 20
// 폴더 색 선택지 (핑크/노랑/초록/파랑/보라/코랄 파스텔)
export const FOLDER_COLORS = ['#ecb0be', '#f1c687', '#7cd0c6', '#99b8f5', '#b7a6e6', '#f0a98b']

function load() {
  try {
    return JSON.parse(localStorage.getItem(KEY)) ?? { folders: [], map: {} }
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

  // postIds를 폴더로 이동 (folderId가 null이면 폴더 해제)
  function assign(postIds, folderId) {
    postIds.forEach((id) => (folderId ? (map.value[id] = folderId) : delete map.value[id]))
  }

  return { folders, map, selected, byId, folderOf, idsOf, count, create, update, remove, assign }
})

// 저장한 글 "내 폴더" - 서버 저장 (chat_folders + bookmarks.folder_id)
//
// 예전에는 브라우저 localStorage에 뒀는데, 기기를 바꾸면 사라져서 "저장했다고 믿고 잃는" 일이
// 생긴다. 폴더는 북마크의 '분류'이므로 북마크와 같은 곳(서버)에 둔다.
// 화면이 기다리지 않도록 먼저 바꾸고 API를 부른다(낙관적 갱신). 실패하면 서버 상태로 되돌린다.
import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  assignToFolder,
  createFolder,
  deleteFolder,
  fetchFolders,
  updateFolder,
} from '../api/folders'

const MAX_FOLDERS = 20
// 폴더 색 선택지 - FolderCard가 흰색을 38% 섞어 파스텔로 바꾸므로 여기엔 원색을 둔다.
// 파스텔 값을 넣으면 두 번 연해져서 색이 날아간다. 카테고리(categories.js)와 같은 채도.
export const FOLDER_COLORS = ['#E0607D', '#E8A33D', '#2BB3A3', '#5B8DEF', '#8B6FD6', '#E4762C']
// 새로 만든 폴더의 기본색 - 아직 아무 색도 고르지 않았다는 뜻의 중립 회색
export const NEW_FOLDER_COLOR = '#B0B4BF'

export const useFoldersStore = defineStore('folders', () => {
  const folders = ref([]) // { id, name, color }[]
  const map = ref({}) // { [postId]: folderId } - 한 글은 폴더 한 곳에만
  const selected = ref(null) // 열어 둔 폴더 id (null이면 전체) - 저장하지 않는 화면 상태
  const loaded = ref(false)

  /** 로그인 후 한 번. 실패해도 빈 목록으로 두고 화면은 계속 동작하게 한다. */
  async function load() {
    try {
      const { data } = await fetchFolders()
      folders.value = data?.folders ?? []
      map.value = Object.fromEntries(
        Object.entries(data?.map ?? {}).map(([postId, folderId]) => [Number(postId), folderId]),
      )
    } catch {
      folders.value = []
      map.value = {}
    }
    loaded.value = true
  }

  const byId = (id) => folders.value.find((f) => f.id === id) ?? null
  const folderOf = (postId) => byId(map.value[postId])
  const idsOf = (folderId) =>
    Object.keys(map.value).filter((k) => map.value[k] === folderId).map(Number)
  const count = (folderId) => idsOf(folderId).length

  /**
   * 새 폴더. 같은 이름이 있으면 그 폴더를 그대로 돌려주고, 개수를 넘기면 null.
   * 서버가 준 진짜 id가 필요해서(바로 assign에 쓴다) 비동기다.
   */
  async function create(name, color = FOLDER_COLORS[folders.value.length % FOLDER_COLORS.length]) {
    const trimmed = (name ?? '').trim().slice(0, 20)
    if (!trimmed) return null
    const dup = folders.value.find((f) => f.name === trimmed)
    if (dup) return dup
    if (folders.value.length >= MAX_FOLDERS) return null
    try {
      const { data } = await createFolder(trimmed, color)
      folders.value = [...folders.value, data]
      return data
    } catch {
      return null
    }
  }

  async function update(id, { name, color }) {
    const f = byId(id)
    if (!f) return
    const previous = { name: f.name, color: f.color }
    if (name?.trim()) f.name = name.trim().slice(0, 20)
    if (color) f.color = color
    try {
      await updateFolder(id, { name: f.name, color: f.color })
    } catch {
      Object.assign(f, previous)
    }
  }

  // 폴더 삭제 - 안의 글은 지워지지 않고 "폴더 없음"으로 돌아감 (서버도 on delete set null)
  async function remove(id) {
    const previousFolders = folders.value
    const previousMap = { ...map.value }
    if (selected.value === id) selected.value = null
    folders.value = folders.value.filter((f) => f.id !== id)
    Object.keys(map.value).forEach((k) => map.value[k] === id && delete map.value[k])
    try {
      await deleteFolder(id)
    } catch {
      folders.value = previousFolders
      map.value = previousMap
    }
  }

  // 여러 폴더를 한 번에 삭제 - 안의 글은 "폴더 없음"으로 돌아간다
  async function removeMany(ids) {
    for (const id of ids) await remove(id)
  }

  // 드래그로 순서 바꾸기 - 끄는 동안 실시간으로 자리를 밀어내도록 한 칸씩 옮긴다
  async function reorder(fromIndex, toIndex) {
    if (fromIndex === toIndex) return
    const list = [...folders.value]
    if (fromIndex < 0 || toIndex < 0 || fromIndex >= list.length || toIndex >= list.length) return
    const [moved] = list.splice(fromIndex, 1)
    list.splice(toIndex, 0, moved)
    folders.value = list
    // 바뀐 자리만 서버에 알린다
    try {
      await Promise.all(list.map((f, i) => updateFolder(f.id, { sortOrder: i })))
    } catch { /* 순서는 부가 정보 - 실패해도 되돌리지 않는다 (다음 로드 때 서버 순서로 맞춰짐) */ }
  }

  // postIds를 폴더로 이동 (folderId가 null이면 폴더 해제)
  async function assign(postIds, folderId) {
    const previous = { ...map.value }
    postIds.forEach((id) => (folderId ? (map.value[id] = folderId) : delete map.value[id]))
    try {
      await assignToFolder(postIds, folderId ?? null)
    } catch {
      map.value = previous
    }
  }

  return {
    folders, map, selected, loaded, load, byId, folderOf, idsOf, count,
    create, update, remove, removeMany, reorder, assign,
  }
})

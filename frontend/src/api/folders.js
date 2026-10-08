// 내 폴더 API 호출 (서버: ChatFolderController)
import http from './http'

// { folders: [{id,name,color}], map: { [postId]: folderId } }
export function fetchFolders() {
  return http.get('/api/folders')
}

export function createFolder(name, color) {
  return http.post('/api/folders', { name, color })
}

export function updateFolder(id, body) {
  return http.patch(`/api/folders/${id}`, body)
}

export function deleteFolder(id) {
  return http.delete(`/api/folders/${id}`)
}

// 저장한 글을 폴더에 담기. folderId 가 null 이면 폴더에서 빼 '미분류'로
export function assignToFolder(postIds, folderId) {
  return http.post('/api/folders/assign', { postIds, folderId })
}

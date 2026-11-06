// 구글 계정 연동/로그인 팝업 (Google Identity Services 코드플로우) - index.html에 로드된 gsi/client 스크립트 사용
//
// 사용자가 동의 없이 팝업을 그냥 닫아버리면 구글이 callback을 아예 안 불러줄 때가 있다 -
// 그러면 이 Promise가 영원히 안 끝나서 호출부의 finally(버튼 재활성화)도 영영 못 돈다.
// 그래서 일정 시간 안에 응답이 없으면 강제로 reject해 버튼이 다시 눌리게 한다.
const POPUP_TIMEOUT_MS = 60_000

export function requestGoogleAuthCode() {
  return new Promise((resolve, reject) => {
    if (!window.google?.accounts?.oauth2) {
      reject(new Error('google_sdk_not_loaded'))
      return
    }
    let settled = false
    const timer = setTimeout(() => {
      if (settled) return
      settled = true
      reject(new Error('google_popup_failed'))
    }, POPUP_TIMEOUT_MS)

    const client = window.google.accounts.oauth2.initCodeClient({
      client_id: import.meta.env.VITE_GOOGLE_CLIENT_ID,
      scope: 'openid email profile',
      ux_mode: 'popup',
      callback: (response) => {
        if (settled) return
        settled = true
        clearTimeout(timer)
        if (response?.code) resolve(response.code)
        else reject(new Error('google_popup_failed'))
      },
    })
    client.requestCode()
  })
}

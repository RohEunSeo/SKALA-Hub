// 구글 계정 연동/로그인 팝업 (Google Identity Services 코드플로우) - index.html에 로드된 gsi/client 스크립트 사용
export function requestGoogleAuthCode() {
  return new Promise((resolve, reject) => {
    if (!window.google?.accounts?.oauth2) {
      reject(new Error('google_sdk_not_loaded'))
      return
    }
    const client = window.google.accounts.oauth2.initCodeClient({
      client_id: import.meta.env.VITE_GOOGLE_CLIENT_ID,
      scope: 'openid email profile',
      ux_mode: 'popup',
      callback: (response) => {
        if (response?.code) resolve(response.code)
        else reject(new Error('google_popup_failed'))
      },
    })
    client.requestCode()
  })
}

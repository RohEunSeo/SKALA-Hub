// 사이트 진입 팝업 설정 - 일회성 캠페인용. 다음에 다른 공지를 띄우고 싶으면 이 내용만 바꾸면 됨
// (코드 로직은 SitePopup.vue에 그대로 두고 재사용)
export const SITE_POPUP = {
  enabled: true,
  badge: '🚨 SKALA-Hub 중요 공지',
  title: '교육 수료 후, 운영 관련 수요조사',
  // intro/warning/surveyNote에서는 [[ ]]로 감싼 부분은 빨간 글씨, ** **로 감싼 부분은 볼드로 표시됨
  intro: '교육 수료 후, SKALA-Hub 운영 계획 관련 공지를 전해드립니다.',
  warning:
    '매니저님들을 통해 확인한 결과,\n' +
    '교육 수료 후에는 슬랙 계정이 [[비활성화]]되어 ' +
    '[[SKALA 워크스페이스에 접근할 수 없게]] 됩니다. 또한, 슬랙 로그인 기반인 **SKALA-Hub**도 이용이 제한됩니다.',
  lead: '따라서 수료 후에도 서비스를 계속 이용하실 수 있도록\n구글 계정 연동 기능을 검토 중입니다!',
  featureTitle: '구글 계정 연동이란?',
  featureDescription:
    '= 슬랙 계정과 구글 계정을 연결하여, 수료 후에도 구글 로그인으로 기존 계정에 그대로 접속할 수 있는 기능입니다.\n' +
    '(* 연동은 마이페이지에서 진행할 예정입니다)',
  surveyNote:
    '교육 수료 후, 서비스 이용 여부 관련하여 익명 설문을 준비했습니다.\n' +
    '약 1분 정도 소요되니 많은 참여 부탁드립니다! 감사합니다🙇🏻‍♀️',
  ctaLabel: '🔗 설문 참여하기',
  ctaUrl: 'https://docs.google.com/forms/d/e/1FAIpQLSedVHDojSR_5CyheItnh9dBohWAqSP4XOP0Rh9IJR34omFBxw/viewform',
  // 이 시각 이후로는 "일주일 동안 보지 않음"을 안 눌러도 자동으로 노출되지 않음
  campaignEndsAt: '2026-09-27T23:59:59+09:00',
  dismissDays: 7,
  // 캠페인이 바뀌면 이 키도 같이 바꿔야 이전 캠페인의 dismiss 기록과 안 겹침
  storageKey: 'sitePopup_googleLink_2026',
}

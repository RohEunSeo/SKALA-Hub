// 관리자 "구글 계정 연동 관리" 화면 - 교육생 1명의 연동 현황
package com.skalahub.dto;

public record GoogleLinkResponse(
        String slackId,
        String name,
        String cohort,
        String campus,
        String classNum,
        boolean googleLinked,
        String googleEmail) {
}

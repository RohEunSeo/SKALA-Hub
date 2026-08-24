// 대시보드 게시글 기여도 히트맵 - 하루 단위 게시글 수/색상 단계(0~3)
package com.skalahub.dto;

import java.time.LocalDate;

public record DashboardHeatmapDayDto(LocalDate date, long count, int level) {
}

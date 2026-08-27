// 대시보드 날씨 위젯 응답 - condition: sunny/cloudy/rainy/snowy/foggy/stormy
package com.skalahub.dto;

public record WeatherResponse(String condition, int temperature, boolean isDay, int humidity) {
}

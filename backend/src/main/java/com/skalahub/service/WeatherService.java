// 대시보드 "허브 단계" 카드 날씨 위젯용 - OpenWeather Current Weather API 호출 (키는 백엔드에만 보관)
package com.skalahub.service;

import com.skalahub.dto.WeatherResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@Service
public class WeatherService {

    // 판교(성남시 분당구 판교로 255번길 38 인근, 판교역/판교테크노밸리 일대) 좌표 고정
    // - users.campus 컬럼이 사실상 전원 "판교"라 캠퍼스 분기 불필요
    private static final double PANGYO_LAT = 37.4008;
    private static final double PANGYO_LON = 127.1119;

    private final RestClient restClient = RestClient.create();
    private final String apiKey;

    public WeatherService(@Value("${openweather.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    public WeatherResponse getPangyoWeather() {
        JsonNode response = restClient.get()
                .uri("https://api.openweathermap.org/data/2.5/weather?lat={lat}&lon={lon}&units=metric&appid={key}",
                        PANGYO_LAT, PANGYO_LON, apiKey)
                .retrieve()
                .body(JsonNode.class);

        JsonNode weather = response.path("weather").path(0);
        String icon = weather.path("icon").asString("");
        int temperature = (int) Math.round(response.path("main").path("temp").asDouble(0));
        int humidity = response.path("main").path("humidity").asInt(0);
        boolean isDay = !icon.endsWith("n");

        return new WeatherResponse(themeFromConditionCode(weather.path("id").asInt(800)), temperature, isDay, humidity);
    }

    // OpenWeather 날씨 조건 코드(id) -> 위젯 배경 테마 단순화
    // 800(맑음)/801(구름 조금)까지는 실제로는 화창하게 느껴지는 경우가 많아 sunny로 분류하고,
    // 802~804(흐림 계열)만 cloudy로 둔다
    private String themeFromConditionCode(int code) {
        if (code == 800 || code == 801) return "sunny";
        if (code >= 802 && code <= 804) return "cloudy";
        if (code >= 701 && code <= 781) return "foggy";
        if (code >= 300 && code <= 599) return "rainy";
        if (code >= 600 && code <= 699) return "snowy";
        if (code >= 200 && code <= 299) return "stormy";
        return "cloudy";
    }
}

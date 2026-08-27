// 대시보드 날씨 위젯 API - 로그인 불필요(SecurityConfig에서 permitAll)
package com.skalahub.controller;

import com.skalahub.dto.WeatherResponse;
import com.skalahub.service.WeatherService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping("/api/weather/pangyo")
    public WeatherResponse getPangyoWeather() {
        return weatherService.getPangyoWeather();
    }
}

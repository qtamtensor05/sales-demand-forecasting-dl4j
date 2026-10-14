package com.dl4j.salesforecast.api;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
class HttpConfig {
    @Bean
    RestTemplate forecastRestTemplate(RestTemplateBuilder builder,
            @Value("${forecast.connect-timeout-ms}") long connectTimeout,
            @Value("${forecast.read-timeout-ms}") long readTimeout) {
        return builder.setConnectTimeout(Duration.ofMillis(connectTimeout))
                .setReadTimeout(Duration.ofMillis(readTimeout)).build();
    }
}

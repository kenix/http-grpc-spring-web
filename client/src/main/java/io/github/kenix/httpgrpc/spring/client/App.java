package io.github.kenix.httpgrpc.spring.client;

import java.time.Duration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * @author zzhao
 */
@SpringBootApplication
class App {

  public static void main(String[] args) {
    SpringApplication.run(App.class, args);
  }

  @Bean
  @ConfigurationProperties(prefix = "app.rest-template")
  RestTemplateSettings restTemplateSettings() {
    return new RestTemplateSettings();
  }

  @Bean
  public RestTemplate restTemplate(RestTemplateBuilder restTemplateBuilder,
      RestTemplateSettings settings) {
    return restTemplateBuilder
        .setConnectTimeout(Duration.ofMillis(settings.getConnectionTimeoutMs()))
        .setReadTimeout(Duration.ofMillis(restTemplateSettings().getReadTimeoutMs()))
        .build();
  }
}

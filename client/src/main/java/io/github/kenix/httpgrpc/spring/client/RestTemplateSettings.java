package io.github.kenix.httpgrpc.spring.client;

import lombok.Getter;
import lombok.Setter;

/**
 * @author zzhao
 */
@Getter
@Setter
public class RestTemplateSettings {

  private int connectionTimeoutMs = 3000;

  private int readTimeoutMs = 3000;
}

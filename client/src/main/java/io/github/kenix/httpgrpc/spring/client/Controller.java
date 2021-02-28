package io.github.kenix.httpgrpc.spring.client;

import io.github.kenix.grpc.greeter.api.GreeterProto.HelloReply;
import java.util.Collections;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 * @author zzhao
 */
@RequiredArgsConstructor
@Slf4j
@RestController
public class Controller {

  private final RestTemplate restTemplate;

  @SneakyThrows
  @GetMapping("hi/{name}")
  String hi(@PathVariable String name) {
    log.info("<hi> {}", name);
    final HttpHeaders headers = new HttpHeaders();
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_OCTET_STREAM));

    final HttpEntity<String> entity = new HttpEntity<>(headers);

    final ResponseEntity<byte[]> resp =
        restTemplate.exchange("http://localhost:8080/v1/greeter/" + name, HttpMethod.GET, entity,
            byte[].class);

    return HelloReply.newBuilder().mergeFrom(resp.getBody()).build().getMessage();
  }

  @SneakyThrows
  @ExceptionHandler
  private void handleThrowable(Throwable t, HttpServletResponse resp) {
    resp.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
    resp.getWriter().write(t.getMessage());
    resp.getWriter().flush();
  }
}

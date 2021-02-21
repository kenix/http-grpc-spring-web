package io.github.kenix.httpgrpc.boot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.HttpRule;
import com.google.protobuf.DescriptorProtos.FileOptions;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.GeneratedMessageV3;
import io.grpc.BindableService;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;

/**
 * Automatically configures {@link SimpleUrlHandlerMapping} with dynamically generated {@link
 * TranscoderController}s. See also the conditions.
 *
 * @author zzhao
 */
@Lazy(value = false)
@Slf4j
@Configuration
@ConditionalOnBean({BindableService.class, FileDescriptor.class})
@RequiredArgsConstructor
public class HttpGrpcAutoConfiguration {

  private static final EnumSet<HttpMethod> HTTP_METHODS_USE_BODY = EnumSet.of(
      HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH
  );
  private final ApplicationContext appCtx;
  private final ObjectMapper objectMapper;

  /**
   * Configures one {@link SimpleUrlHandlerMapping} with order -1, that should land before other
   * handler mappings.
   *
   * @return simple URL handler mapping
   */
  @Bean
  public SimpleUrlHandlerMapping simpleUrlHandlerMapping() {
    final SimpleUrlHandlerMapping handlerMapping = new SimpleUrlHandlerMapping();
    handlerMapping.setOrder(-1);
    final Map<String, TranscoderController> controllerByUrl = new HashMap<>();

    final Map<String, FileDescriptor> fileDescByName =
        this.appCtx.getBeansOfType(FileDescriptor.class);

    final Map<String, BindableService> serviceByName =
        this.appCtx.getBeansOfType(BindableService.class).values()
            .stream()
            .collect(Collectors.toMap(
                svc -> svc.bindService().getServiceDescriptor().getName(),
                Function.identity()));

    fileDescByName.values()
        .forEach(fileDesc -> {
          final FileOptions fileOptions = fileDesc.getOptions();

          fileDesc.getServices()
              .stream()
              .filter(serviceDesc -> serviceByName.containsKey(serviceDesc.getFullName()))
              .forEach(serviceDesc -> {
                final BindableService service =
                    serviceByName.get(serviceDesc.getFullName());
                serviceDesc.getMethods().forEach(methodDesc -> {
                  final Class<? extends GeneratedMessageV3> reqClass =
                      getClass(fileOptions, methodDesc.getInputType().getName());
                  methodDesc.getOptions().getAllFields().values()
                      .stream()
                      .filter(f -> f instanceof HttpRule)
                      .map(f -> (HttpRule) f)
                      .forEach(httpRule -> {
                        final HttpMethod httpMethod =
                            HttpMethod.valueOf(httpRule.getPatternCase().name());
                        final TranscoderController controller =
                            createController(httpMethod, service, reqClass, methodDesc);
                        if (HTTP_METHODS_USE_BODY.contains(httpMethod)) {
                          controller.setBody(httpRule.getBody());
                        }
                        controllerByUrl.put(getUrl(httpRule, httpMethod), controller);
                      });
                });
              });
        });

    handlerMapping.setUrlMap(controllerByUrl);
    return handlerMapping;
  }

  private String getUrl(HttpRule httpRule, HttpMethod httpMethod) {
    switch (httpMethod) {
      case GET:
        return httpRule.getGet();
      case POST:
        return httpRule.getPost();
      case DELETE:
        return httpRule.getDelete();
      case PUT:
        return httpRule.getPut();
      case PATCH:
        return httpRule.getPatch();
      default:
        throw new UnsupportedOperationException("no support for " + httpMethod);
    }
  }

  private TranscoderController createController(HttpMethod httpMethod, BindableService service,
      Class<? extends GeneratedMessageV3> reqClass, MethodDescriptor methodDesc) {
    final TranscoderController controller =
        new TranscoderController(httpMethod, service, reqClass, methodDesc, this.objectMapper);
    controller.setSupportedMethods(httpMethod.name());
    return controller;
  }

  @SneakyThrows
  private Class<? extends GeneratedMessageV3> getClass(FileOptions fileOptions, String typeName) {
    final String javaPackage = fileOptions.getJavaPackage();
    final String outerClassname = fileOptions.getJavaOuterClassname();
    final String typeClassName = fileOptions.getJavaMultipleFiles()
        ? typeName
        : outerClassname + "$" + typeName;

    return (Class<? extends GeneratedMessageV3>) Class.forName(javaPackage + "." + typeClassName);
  }
}

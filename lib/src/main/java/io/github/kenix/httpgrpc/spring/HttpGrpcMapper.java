package io.github.kenix.httpgrpc.spring;

import static io.github.kenix.httpgrpc.spring.Util.HTTP_METHODS_WITH_BODY;
import static io.github.kenix.httpgrpc.spring.Util.getUrl;

import com.google.api.HttpRule;
import com.google.protobuf.DescriptorProtos.FileOptions;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Message;
import io.github.kenix.httpgrpc.spring.strategy.ServerCallStrategy;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Automatically configures {@link RequestMappingHandlerMapping} with dynamically generated {@link
 * TranscoderController}s using {@link ServerCallStrategyResolver}.
 *
 * @author zzhao
 */
@Slf4j
@RequiredArgsConstructor
public class HttpGrpcMapper implements ApplicationContextAware, DisposableBean {

  private static final Class<TranscoderController> CONTROLLER_CLASS = TranscoderController.class;
  private static final String CONTROLLER_METHOD = "handleRequest";
  private static final Method MTD = Optional.ofNullable(ReflectionUtils.findMethod(
      CONTROLLER_CLASS, CONTROLLER_METHOD, HttpServletRequest.class, HttpServletResponse.class)
  ).orElseThrow(() -> new IllegalStateException("cannot find "));

  private ApplicationContext appCtx;

  private ServerCallStrategyResolver serverCallStrategyResolver;

  @Override
  public void destroy() throws Exception {
    if (this.serverCallStrategyResolver != null) {
      this.serverCallStrategyResolver.done();
    }
  }

  private <T> T getBean(Class<T> type) {
    try {
      return this.appCtx.getBean(type);
    } catch (NoSuchBeanDefinitionException e) {
      log.warn("<getBean> bean of type {} not found", type);
      return null;
    }
  }

  /**
   * Configures {@link RequestMappingHandlerMapping}.
   *
   * @param evt a {@link ContextRefreshedEvent}
   * @throws Exception when something wrong
   */
  @EventListener
  public void map(ContextRefreshedEvent evt) throws Exception {
    final RequestMappingHandlerMapping mapping = // mandatory
        this.appCtx.getBean(RequestMappingHandlerMapping.class);

    // mandatory
    final GrpcServerDescriptor grpcServerDesc = this.appCtx.getBean(GrpcServerDescriptor.class);
    final List<FileDescriptor> fileDescriptors = grpcServerDesc.getFileDescriptors();
    if (CollectionUtils.isEmpty(fileDescriptors)) {
      log.info("<map> no file descriptors found, no transcoder setup");
      return;
    }

    this.serverCallStrategyResolver = new ServerCallStrategyResolver(
        grpcServerDesc, getBean(ServerMethodDefinitionInterceptor.class));

    fileDescriptors.forEach(fileDesc -> {
      final FileOptions fileOptions = fileDesc.getOptions();
      fileDesc.getServices()
          .forEach(serviceDesc -> serviceDesc.getMethods().forEach(methodDesc -> {
            final Class<? extends Message> reqClass =
                getClass(fileOptions, methodDesc.getInputType().getName());
            final Class<? extends Message> respClass =
                getClass(fileOptions, methodDesc.getOutputType().getName());
            methodDesc.getOptions().getAllFields().values()
                .stream()
                .filter(f -> f instanceof HttpRule)
                .map(f -> (HttpRule) f)
                .forEach(httpRule -> {
                  processHttpRule(httpRule, reqClass, respClass, methodDesc, mapping);
                  httpRule.getAdditionalBindingsList().forEach(hr ->
                      processHttpRule(hr, reqClass, respClass, methodDesc, mapping));
                });
          }));
    });
  }

  private void processHttpRule(HttpRule httpRule, Class<? extends Message> reqClass,
      Class<? extends Message> respClass, MethodDescriptor methodDesc,
      RequestMappingHandlerMapping mapping) {
    final Optional<ServerCallStrategy> callStrategy =
        this.serverCallStrategyResolver.lookup(methodDesc, reqClass, respClass);
    if (!callStrategy.isPresent()) {
      log.warn("<processHttpRule> no server call strategy found for {}", methodDesc.getFullName());
      return;
    }

    final HttpMethod httpMethod = HttpMethod.valueOf(httpRule.getPatternCase().name());
    final TranscoderController controller =
        createController(httpMethod, reqClass, methodDesc, callStrategy.get());

    if (HTTP_METHODS_WITH_BODY.contains(httpMethod)) {
      controller.setBody(httpRule.getBody());
    }
    final RequestMappingInfo mappingInfo =
        RequestMappingInfo.paths(getUrl(httpRule, httpMethod))
            .methods(RequestMethod.valueOf(httpMethod.name()))
            .build();
    log.info("<map> {}", mappingInfo.toString());
    mapping.registerMapping(mappingInfo, controller, MTD);
  }

  private TranscoderController createController(HttpMethod httpMethod,
      Class<? extends Message> reqClass, MethodDescriptor methodDesc,
      ServerCallStrategy callStrategy) {
    final TranscoderController controller =
        new TranscoderController(httpMethod, reqClass, methodDesc, callStrategy);
    controller.setSupportedMethods(httpMethod.name());
    return controller;
  }

  @SneakyThrows
  @SuppressWarnings("unchecked")
  private Class<? extends Message> getClass(FileOptions fileOptions, String typeName) {
    final String javaPackage = fileOptions.getJavaPackage();
    final String outerClassname = fileOptions.getJavaOuterClassname();
    final String typeClassName = fileOptions.getJavaMultipleFiles()
        ? typeName
        : outerClassname + "$" + typeName;

    return (Class<? extends Message>) Class.forName(javaPackage + "." + typeClassName);
  }

  @Override
  public void setApplicationContext(@NonNull ApplicationContext appCtx) throws BeansException {
    this.appCtx = appCtx;
  }
}

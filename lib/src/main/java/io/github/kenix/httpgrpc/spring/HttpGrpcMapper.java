package io.github.kenix.httpgrpc.spring;

import static io.github.kenix.httpgrpc.spring.Util.HTTP_METHODS_WITH_BODY;
import static io.github.kenix.httpgrpc.spring.Util.getUrl;

import com.google.api.HttpRule;
import com.google.protobuf.DescriptorProtos.FileOptions;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.GeneratedMessageV3;
import io.grpc.Server;
import io.grpc.ServerMethodDefinition;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.serverfactory.GrpcServerLifecycle;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Automatically configures {@link RequestMappingHandlerMapping} with dynamically generated {@link
 * TranscoderController}s.
 *
 * @author zzhao
 */
@Slf4j
@RequiredArgsConstructor
public class HttpGrpcMapper implements ApplicationContextAware {

  private static final Class<TranscoderController> CONTROLLER_CLASS = TranscoderController.class;
  private static final String CONTROLLER_METHOD = "handleRequest";
  private static final Method MTD = Optional.ofNullable(ReflectionUtils.findMethod(
      CONTROLLER_CLASS, CONTROLLER_METHOD, HttpServletRequest.class, HttpServletResponse.class)
  ).orElseThrow(() -> new IllegalStateException("cannot find "));
  private static final Pattern P = Pattern.compile("/");

  private ApplicationContext appCtx;

  /**
   * Configures {@link RequestMappingHandlerMapping}.
   */
  @EventListener
  public void map(ContextRefreshedEvent evt) throws Exception {
    final RequestMappingHandlerMapping mapping =
        this.appCtx.getBean(RequestMappingHandlerMapping.class);

    final Map<String, FileDescriptor> fileDescByName =
        this.appCtx.getBeansOfType(FileDescriptor.class);

    //final Server server = InternalServer.SERVER_CONTEXT_KEY.get();
    final GrpcServerLifecycle lifecycle = this.appCtx.getBean(GrpcServerLifecycle.class);
    final Field field = lifecycle.getClass().getDeclaredField("server");
    field.setAccessible(true);
    final Server server = (Server) field.get(lifecycle);

    final Map<String, ServerMethodDefinition<?, ?>> serviceMethods =
        server.getImmutableServices().stream()
            .flatMap(ssd -> ssd.getMethods().stream())
            .collect(
                Collectors.toMap(smd ->
                        P.matcher(smd.getMethodDescriptor().getFullMethodName()).replaceAll("."),
                    Function.identity())
            );

    fileDescByName.values()
        .forEach(fileDesc -> {
          final FileOptions fileOptions = fileDesc.getOptions();
          fileDesc.getServices()
              .forEach(serviceDesc -> serviceDesc.getMethods().forEach(methodDesc -> {
                final Class<? extends GeneratedMessageV3> reqClass =
                    getClass(fileOptions, methodDesc.getInputType().getName());
                final String fullName = methodDesc.getFullName();
                methodDesc.getOptions().getAllFields().values()
                    .stream()
                    .filter(f -> f instanceof HttpRule)
                    .map(f -> (HttpRule) f)
                    .forEach(httpRule -> {
                      processHttpRule(httpRule, reqClass, methodDesc, serviceMethods, mapping);
                      httpRule.getAdditionalBindingsList().forEach(hr ->
                          processHttpRule(hr, reqClass, methodDesc, serviceMethods, mapping));
                    });
              }));
        });
  }

  private void processHttpRule(HttpRule httpRule, Class<? extends GeneratedMessageV3> reqClass,
      MethodDescriptor methodDesc, Map<String, ServerMethodDefinition<?, ?>> serviceMethods,
      RequestMappingHandlerMapping mapping) {
    final String fullName = methodDesc.getFullName();
    final HttpMethod httpMethod =
        HttpMethod.valueOf(httpRule.getPatternCase().name());
    final TranscoderController controller =
        createController(httpMethod, serviceMethods.get(fullName), reqClass,
            methodDesc);
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
      ServerMethodDefinition<?, ?> svcMthdDef, Class<? extends GeneratedMessageV3> reqClass,
      MethodDescriptor methodDesc) {
    final TranscoderController controller =
        new TranscoderController(httpMethod, svcMthdDef, reqClass, methodDesc);
    controller.setSupportedMethods(httpMethod.name());
    return controller;
  }

  @SneakyThrows
  @SuppressWarnings("unchecked")
  private Class<? extends GeneratedMessageV3> getClass(FileOptions fileOptions, String typeName) {
    final String javaPackage = fileOptions.getJavaPackage();
    final String outerClassname = fileOptions.getJavaOuterClassname();
    final String typeClassName = fileOptions.getJavaMultipleFiles()
        ? typeName
        : outerClassname + "$" + typeName;

    return (Class<? extends GeneratedMessageV3>) Class.forName(javaPackage + "." + typeClassName);
  }

  @Override
  public void setApplicationContext(@NonNull ApplicationContext appCtx) throws BeansException {
    this.appCtx = appCtx;
  }
}

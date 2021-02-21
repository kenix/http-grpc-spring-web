package io.github.kenix.httpgrpc.boot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.HttpHeaders;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Empty;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.GeneratedMessageV3.Builder;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonFormat.Printer;
import io.grpc.BindableService;
import io.grpc.Status;
import io.grpc.Status.Code;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.AbstractController;

/**
 * Transcodes HTTP request into gRPC request, invokes corresponding method of the given service
 * object and transcodes reply back to HTTP response. Also refer to https://cloud.google.com/endpoints/docs/grpc/transcoding
 *
 * @author zzhao
 */
@Slf4j
@RequiredArgsConstructor
public class TranscoderController extends AbstractController {

  public static final String CONTENT_TYPE_PROTOBUF = MediaType.APPLICATION_OCTET_STREAM_VALUE;
  public static final String CONTENT_TYPE_JSON = MediaType.APPLICATION_JSON_VALUE;
  public static final String WILDCARD = "*";
  public static final String CHARSET = StandardCharsets.UTF_8.name();
  private static final Printer PRINTER = JsonFormat.printer().omittingInsignificantWhitespace();
  private final HttpMethod httpMethod;
  private final BindableService service;
  private final Class<? extends GeneratedMessageV3> reqClass;
  private final MethodDescriptor methodDesc;
  private final ObjectMapper objectMapper;

  @Setter
  private String body;

  @SuppressWarnings("all")
  @Override
  protected ModelAndView handleRequestInternal(HttpServletRequest req, HttpServletResponse resp)
      throws Exception {
    final Message message;
    if (HttpMethod.GET == this.httpMethod || HttpMethod.DELETE == this.httpMethod) {
      if (this.reqClass == Empty.class) {
        message = Empty.getDefaultInstance();
      } else {
        final Map<String, Object> pathVars = getPathVars(req);
        final Builder builder = getBuilder(this.reqClass);
        this.methodDesc.getInputType().getFields().forEach(fieldDesc ->
            builder.setField(fieldDesc, pathVars.get(fieldDesc.getName())));
        message = builder.build();
      }
    } else { // POST, PUT, PATCH
      final Builder builder = getBuilder(this.reqClass);
      switch (req.getContentType().toLowerCase()) {
        case CONTENT_TYPE_JSON:
          JsonFormat.parser().merge(req.getReader(), builder);
          break;
        case CONTENT_TYPE_PROTOBUF:
          builder.mergeFrom(req.getInputStream());
          break;
        default:
          notAcceptable(resp);
          return null; // no further processing
      }

      if (this.body.equals(WILDCARD)) { // should not be repeated field
        final Map<String, Object> pathVars = getPathVars(req);
        this.methodDesc.getInputType().getFields().forEach(fieldDesc -> {
          if (!builder.hasField(fieldDesc) && pathVars.containsKey(fieldDesc.getName())) {
            builder.setField(fieldDesc, pathVars.get(fieldDesc.getName()));
          }
        });
      }
      message = builder.build();
    }

    final Method method = Stream.of(service.getClass().getDeclaredMethods())
        .filter(m -> m.getName().equalsIgnoreCase(methodDesc.getName()))
        .findFirst()
        .orElseThrow(() -> new NoSuchMethodException(methodDesc.getName()));
    method.invoke(this.service, message, new StreamObserverHttp(this.objectMapper, req, resp));

    return null; // no view resolving
  }

  @SuppressWarnings("all")
  private Map<String, Object> getPathVars(HttpServletRequest req) {
    return (Map<String, Object>) req.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
  }

  private void notAcceptable(HttpServletResponse resp) {
    resp.setStatus(HttpStatus.NOT_ACCEPTABLE.value());
  }

  @SuppressWarnings("all")
  @SneakyThrows
  private Builder getBuilder(Class<? extends GeneratedMessageV3> clazz) {
    return (Builder) clazz.getMethod("newBuilder").invoke(null);
  }

  /**
   * {@link StreamObserver} transcoding {@link GeneratedMessageV3} or {@link Status} to HTTP
   * response.
   */
  @RequiredArgsConstructor
  private static class StreamObserverHttp implements StreamObserver<GeneratedMessageV3> {

    private final ObjectMapper objectMapper;
    private final HttpServletRequest req;
    private final HttpServletResponse resp;
    private GeneratedMessageV3 value;

    @Override
    public void onNext(GeneratedMessageV3 value) {
      this.value = value;
    }

    @SneakyThrows
    @Override
    public void onError(Throwable t) {
      final Status status;
      if (t instanceof StatusException) {
        final StatusException statusEx = (StatusException) t;
        status = statusEx.getStatus();
      } else if (t instanceof StatusRuntimeException) {
        final StatusRuntimeException statusRuntimeEx = (StatusRuntimeException) t;
        status = statusRuntimeEx.getStatus();
      } else {
        status = Status.fromCode(Code.INTERNAL).withCause(t.getCause());
      }
      wireResponse(toHttpStatus(status.getCode()), status);
    }

    private HttpStatus toHttpStatus(Code code) {
      switch (code) {
        case INVALID_ARGUMENT:
          return HttpStatus.BAD_REQUEST;
        case FAILED_PRECONDITION:
          return HttpStatus.PRECONDITION_FAILED;
        case NOT_FOUND:
          return HttpStatus.NOT_FOUND;
        case UNAVAILABLE:
          return HttpStatus.SERVICE_UNAVAILABLE;
        case UNAUTHENTICATED:
        case PERMISSION_DENIED:
          return HttpStatus.FORBIDDEN;
        case UNIMPLEMENTED:
          return HttpStatus.NOT_IMPLEMENTED;
        default:
          return HttpStatus.INTERNAL_SERVER_ERROR;
      }
    }

    @SneakyThrows
    @Override
    public void onCompleted() {
      wireResponse(HttpStatus.OK, this.value);
    }

    @SneakyThrows
    private void wireResponse(HttpStatus httpStatus, Object payload) {
      this.resp.setStatus(httpStatus.value());
      this.resp.setCharacterEncoding(CHARSET);

      if (payload instanceof Status) {
        this.resp.setContentType(CONTENT_TYPE_JSON);
        this.resp.getWriter().write(this.objectMapper.writeValueAsString(payload));
        this.resp.getWriter().flush();
      } else {
        final String respContentType = getResponseContentType();
        this.resp.setContentType(respContentType);

        switch (respContentType) {
          case CONTENT_TYPE_JSON:
            this.resp.getWriter().write(
                StringEscapeUtils.unescapeJava(PRINTER.print((GeneratedMessageV3) payload)));
            this.resp.getWriter().flush();
            break;
          case CONTENT_TYPE_PROTOBUF:
            this.resp.getOutputStream().write(((GeneratedMessageV3) payload).toByteArray());
            this.resp.getOutputStream().flush();
            break;
          default:
            throw new UnsupportedOperationException("no support for " + respContentType);
        }
      }
    }

    private String getResponseContentType() {
      final String accept = this.req.getHeader(HttpHeaders.ACCEPT);
      if (StringUtils.hasText(accept)) {
        if (accept.contains(CONTENT_TYPE_PROTOBUF)) {
          return CONTENT_TYPE_PROTOBUF;
        }
      }

      return CONTENT_TYPE_JSON;
    }
  }
}

package io.github.kenix.httpgrpc.spring;

import static io.github.kenix.httpgrpc.spring.Util.HTTP_METHODS_NO_BODY;
import static io.github.kenix.httpgrpc.spring.Util.HTTP_METHODS_WITH_BODY;
import static io.github.kenix.httpgrpc.spring.Util.SUPPORTED_METHODS;
import static io.github.kenix.httpgrpc.spring.Util.grpcStatus;
import static io.github.kenix.httpgrpc.spring.Util.protoStatus;
import static io.github.kenix.httpgrpc.spring.Util.setFields;
import static io.github.kenix.httpgrpc.spring.Util.setNonMessageField;
import static io.github.kenix.httpgrpc.spring.Util.toHttpStatus;
import static java.util.Collections.emptyMap;

import com.google.common.collect.Sets;
import com.google.common.net.HttpHeaders;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.Type;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Empty;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.Message;
import com.google.protobuf.Message.Builder;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonFormat.Printer;
import io.grpc.Metadata;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerMethodDefinition;
import io.grpc.Status;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
  private static final Set<String> SUPPORTED_CONTENT_TYPES = Sets.newHashSet(
      CONTENT_TYPE_JSON, CONTENT_TYPE_PROTOBUF
  );
  private static final Printer PRINTER = JsonFormat.printer().omittingInsignificantWhitespace();

  private final HttpMethod httpMethod;
  private final ServerMethodDefinition<?, ?> methodDef;
  private final Class<? extends Message> reqClass;
  private final MethodDescriptor methodDesc;

  @Setter
  private String body;

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  protected ModelAndView handleRequestInternal(@NonNull HttpServletRequest req,
      @NonNull HttpServletResponse resp) throws Exception {
    if (!SUPPORTED_METHODS.contains(this.httpMethod)) {
      methodNotAllowed(resp); // double tap
      return null;
    }

    final String responseContentType = getResponseContentType(req);
    if (responseContentType == null) {
      notSupported(resp);
      return null;
    }

    // https://cloud.google.com/endpoints/docs/grpc-service-config/reference/rpc/google.api#httprule
    final Optional<Message> message = HTTP_METHODS_NO_BODY.contains(this.httpMethod)
        ? getMessageNoBody(req)
        : HTTP_METHODS_WITH_BODY.contains(this.httpMethod)
            ? getMessageWithBody(req)
            : Optional.empty();
    if (message.isPresent()) {
      final ServerCallHandler<?, ?> callHandler = this.methodDef.getServerCallHandler();
      final DirectServerCall call = new DirectServerCall(this.methodDef.getMethodDescriptor());
      final Listener listener = callHandler.startCall(call, new Metadata());

      listener.onMessage(message.get());
      listener.onHalfClose();
      listener.onComplete();

      if (call.getStatus().isOk()) {
        onSuccess((GeneratedMessageV3) call.getMessage(), responseContentType, resp);
      } else {
        onError(call.getStatus().asException(), responseContentType, resp);
      }
    } else {
      notAcceptable(resp);
    }

    return null; // no view resolving
  }

  @SneakyThrows
  private Optional<Message> getMessageWithBody(HttpServletRequest req) {
    final String contentType = req.getContentType().toLowerCase();
    if (!SUPPORTED_CONTENT_TYPES.contains(contentType)) {
      return Optional.empty();
    }

    final Message.Builder builder = getBuilder(this.reqClass);
    final Map<String, Object> pathVars = getPathVars(req);
    final Descriptor inputType = this.methodDesc.getInputType();

    if (this.body.equals(WILDCARD)) {
      // request body to request type
      fromReqBody(builder, contentType, req);
    } else {
      // request body to a field
      final FieldDescriptor bodyField = inputType.findFieldByName(this.body);
      if (bodyField == null || bodyField.getType() != Type.MESSAGE) { // must be message type
        return Optional.empty();
      }
      final Message.Builder fieldBuilder = builder.getFieldBuilder(bodyField);
      fromReqBody(fieldBuilder, contentType, req);
      builder.setField(bodyField, fieldBuilder.build());
    }

    inputType.getFields().stream()
        .filter(f -> f.getType() != Type.MESSAGE)
        .forEach(fieldDesc -> // can overwrite in case of wildcard body
            setNonMessageField(builder, fieldDesc, fieldDesc.getName(), pathVars, emptyMap()));

    return Optional.of(builder.build());
  }

  @SneakyThrows
  private void fromReqBody(Message.Builder builder, String contentType, HttpServletRequest req) {
    if (contentType.equals(CONTENT_TYPE_JSON)) {
      JsonFormat.parser().merge(req.getReader(), builder);
    } else {
      builder.mergeFrom(req.getInputStream());
    }
  }

  private Optional<Message> getMessageNoBody(HttpServletRequest req) {
    if (this.reqClass == Empty.class) {
      return Optional.of(Empty.getDefaultInstance());
    } else {
      final Map<String, Object> pathVars = getPathVars(req);
      final Map<String, String[]> paramMap = req.getParameterMap();
      final Message.Builder builder = getBuilder(this.reqClass);
      setFields(builder, this.methodDesc.getInputType().getFields(), pathVars, paramMap);
      return Optional.of(builder.build());
    }
  }

  private String getResponseContentType(HttpServletRequest req) {
    final String accept = req.getHeader(HttpHeaders.ACCEPT);
    if (StringUtils.hasText(accept)) {
      if (accept.contains(CONTENT_TYPE_PROTOBUF)) {
        return CONTENT_TYPE_PROTOBUF;
      } else if (accept.contains(CONTENT_TYPE_JSON)) {
        return CONTENT_TYPE_JSON;
      } else {
        return "*/*".equals(accept) ? CONTENT_TYPE_JSON : null;
      }
    }

    return CONTENT_TYPE_JSON;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getPathVars(HttpServletRequest req) {
    return (Map<String, Object>) req.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
  }

  private void notAcceptable(HttpServletResponse resp) {
    resp.setStatus(HttpStatus.NOT_ACCEPTABLE.value());
  }

  private void notSupported(HttpServletResponse resp) {
    resp.setStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
  }

  private void methodNotAllowed(HttpServletResponse resp) {
    resp.setStatus(HttpStatus.METHOD_NOT_ALLOWED.value());
  }

  @SneakyThrows
  private Message.Builder getBuilder(Class<? extends Message> clazz) {
    return (Builder) clazz.getMethod("newBuilder").invoke(null);
  }

  /**
   * Handles exceptions and translates it into proper responses.
   */
  @ExceptionHandler(Throwable.class)
  public void handleStatusRuntimeEx(Throwable t, HttpServletRequest req,
      HttpServletResponse resp) {
    final Status status = grpcStatus(t);
    wireResponse(toHttpStatus(status.getCode()), protoStatus(status),
        getResponseContentType(req), resp);
  }

  private void onError(Throwable t, String responseContentType, HttpServletResponse resp) {
    final Status status = grpcStatus(t);
    wireResponse(toHttpStatus(status.getCode()), protoStatus(status), responseContentType, resp);
  }

  private void onSuccess(GeneratedMessageV3 val, String responseContentType,
      HttpServletResponse resp) {
    wireResponse(HttpStatus.OK, val, responseContentType, resp);
  }

  @SneakyThrows
  private void wireResponse(HttpStatus httpStatus, GeneratedMessageV3 payload,
      String responseContentType, HttpServletResponse resp) {
    resp.setStatus(httpStatus.value());
    resp.setCharacterEncoding(CHARSET);

    resp.setContentType(responseContentType);

    switch (responseContentType) {
      case CONTENT_TYPE_JSON:
        resp.getWriter().write(PRINTER.print(payload));
        resp.getWriter().flush();
        break;
      case CONTENT_TYPE_PROTOBUF:
        resp.getOutputStream().write(payload.toByteArray());
        resp.getOutputStream().flush();
        break;
      default:
        throw new UnsupportedOperationException("no support for " + responseContentType);
    }
  }
}

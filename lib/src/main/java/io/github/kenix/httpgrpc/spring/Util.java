package io.github.kenix.httpgrpc.spring;

import com.google.api.HttpRule;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.Type;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Message.Builder;
import com.google.rpc.Status;
import io.grpc.Status.Code;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.SneakyThrows;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

/**
 * Utility class containing some facilitating methods for processing protobuf fields, mapping status
 * etc.
 *
 * @author zzhao
 */
final class Util {

  public static final EnumSet<HttpMethod> HTTP_METHODS_NO_BODY =
      EnumSet.of(HttpMethod.GET, HttpMethod.DELETE);
  public static final EnumSet<HttpMethod> HTTP_METHODS_WITH_BODY =
      EnumSet.of(HttpMethod.POST, HttpMethod.PATCH, HttpMethod.PUT);
  public static final EnumSet<HttpMethod> SUPPORTED_METHODS;

  static {
    SUPPORTED_METHODS = EnumSet.copyOf(HTTP_METHODS_NO_BODY);
    SUPPORTED_METHODS.addAll(HTTP_METHODS_WITH_BODY);
  }

  private Util() {
    throw new AssertionError("not for instantiation or inheritance");
  }

  /**
   * Reflectively creates a message builder.
   *
   * @return a {@link Builder}.
   */
  @SneakyThrows
  public static Message.Builder getBuilder(Class<? extends Message> clazz) {
    return (Builder) clazz.getMethod("newBuilder").invoke(null);
  }

  /**
   * Gets URL depending HTTP method.
   */
  public static String getUrl(HttpRule httpRule, HttpMethod httpMethod) {
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

  /**
   * Maps {@link Code} to {@link HttpStatus}.
   */
  public static HttpStatus toHttpStatus(Code code) {
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

  /**
   * Creates {@link io.grpc.Status} from {@link Throwable}.
   */
  public static io.grpc.Status grpcStatus(Throwable t) {
    if (t instanceof StatusException) {
      return ((StatusException) t).getStatus();
    }

    if (t instanceof StatusRuntimeException) {
      return ((StatusRuntimeException) t).getStatus();
    }

    if (t instanceof InvalidProtocolBufferException) {
      return io.grpc.Status
          .fromCode(Code.INVALID_ARGUMENT)
          .withDescription(t.getMessage());
    }

    return io.grpc.Status
        .fromCode(Code.INTERNAL)
        .withDescription(t.getMessage())
        .withCause(t.getCause());
  }

  /**
   * Creates {@link Status} from {@link io.grpc.Status}.
   */
  public static Status protoStatus(io.grpc.Status status) {
    final Status.Builder builder = Status.newBuilder()
        .setCode(status.getCode().value());
    if (status.getDescription() != null) {
      builder.setMessage(status.getDescription());
    }
    if (status.getCause() != null) {
      builder.addDetails(Any.newBuilder().setValue(
          ByteString.copyFromUtf8(status.getCause().getMessage())).build());
    }

    return builder.build();
  }

  /**
   * Sets fields values from path variable map and parameter map.
   */
  public static void setFields(Builder builder, List<FieldDescriptor> fields,
      Map<String, Object> pathVars, Map<String, String[]> paramMap) {
    setFields(builder, "", fields, pathVars, paramMap);
  }

  private static void setFields(Builder builder, String namePath, List<FieldDescriptor> fields,
      Map<String, Object> pathVars, Map<String, String[]> paramMap) {
    fields.forEach(fieldDesc -> {
      final String fieldName = makeFieldName(namePath, fieldDesc.getName());
      if (fieldDesc.getType() == Type.MESSAGE) {
        final Builder fieldBuilder = builder.getFieldBuilder(fieldDesc);
        setFields(fieldBuilder, fieldName, fieldDesc.getMessageType().getFields(),
            pathVars, paramMap);
        builder.setField(fieldDesc, fieldBuilder.build());
      } else {
        setNonMessageField(builder, fieldDesc, fieldName, pathVars, paramMap);
      }
    });
  }

  /**
   * Sets protobuf field values when its type is not MESSAGE.
   */
  public static void setNonMessageField(Builder builder, FieldDescriptor fieldDesc,
      String fieldName, Map<String, Object> pathVars, Map<String, String[]> paramMap) {
    if (fieldDesc.getType() == Type.MESSAGE) {
      throw new IllegalArgumentException("cannot deal with " + Type.MESSAGE);
    }

    // TODO handle all types correctly
    if (fieldDesc.getType() == Type.ENUM) {
      final EnumDescriptor enumType = fieldDesc.getEnumType();
      if (fieldDesc.isRepeated()) { // can only from parameters
        withParamValues(fieldName, paramMap, v -> builder.addRepeatedField(fieldDesc,
            enumType.findValueByName(v)));
      } else {
        getValue(fieldName, pathVars, paramMap).map(String::valueOf).ifPresent(v ->
            builder.setField(fieldDesc, getEnumValue(enumType, v)
                .orElseThrow(() -> io.grpc.Status.INVALID_ARGUMENT
                    .withDescription("no enum " + enumType.getName() + " found for " + v)
                    .asRuntimeException())));
      }
    } else {
      if (fieldDesc.isRepeated()) { // can only from parameters
        withParamValues(fieldName, paramMap, v -> builder.addRepeatedField(fieldDesc, v));
      } else {
        getValue(fieldName, pathVars, paramMap).ifPresent(v -> builder.setField(fieldDesc, v));
      }
    }
  }

  private static Optional<EnumValueDescriptor> getEnumValue(EnumDescriptor enumType, String v) {
    if (isDigit(v)) {
      return Optional.ofNullable(enumType.findValueByNumber(Integer.parseInt(v)));
    } else {
      return Optional.ofNullable(enumType.findValueByName(v));
    }
  }

  private static boolean isDigit(String str) {
    for (int i = 0; i < str.length(); i++) {
      if (!Character.isDigit(str.charAt(i))) {
        return false;
      }
    }

    return true;
  }

  private static void withParamValues(String name, Map<String, String[]> paramMap,
      Consumer<String> consumer) {
    final String[] vals = paramMap.get(name);
    if (vals != null) {
      for (final String val : vals) {
        consumer.accept(val);
      }
    }
  }

  private static String makeFieldName(String namePath, String fieldName) {
    return StringUtils.hasText(namePath) ? namePath + "." + fieldName : fieldName;
  }

  private static Optional<Object> getValue(String name, Map<String, Object> pathVars,
      Map<String, String[]> paramMap) {
    return Optional.ofNullable(
        Optional
            .ofNullable(pathVars.get(name))
            .orElseGet(() -> {
              final String[] vals = paramMap.get(name);
              if (vals != null && vals.length > 0) {
                return vals[0];
              }
              return null;
            })
    );
  }
}

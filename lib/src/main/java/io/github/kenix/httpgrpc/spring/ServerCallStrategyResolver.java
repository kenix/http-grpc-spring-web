package io.github.kenix.httpgrpc.spring;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.ServiceDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.Message.Builder;
import io.github.kenix.httpgrpc.spring.strategy.ServerCallStrategy;
import io.github.kenix.httpgrpc.spring.strategy.ServerCallStrategyDirect;
import io.github.kenix.httpgrpc.spring.strategy.ServerCallStrategyLocalRouting;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.Marshaller;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.health.v1.HealthGrpc.HealthBlockingStub;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

/**
 * Resolves {@link ServerCallStrategy} depending on which information can be obtained from {@link
 * GrpcServerDescriptor} and {@link ServerMethodDefinitionInterceptor}.
 * <p>
 * First try to obtain {@link ServerMethodDefinition}s from {@link GrpcServerDescriptor} and {@link
 * ServerMethodDefinitionInterceptor} in this order. When obtained, they will be used - direct
 * server call. Otherwise call will be made by routing to the local gRPC server port.
 * </p>
 *
 * @author zzhao
 */
@Slf4j
public class ServerCallStrategyResolver {

  private static final Pattern P = Pattern.compile("/");

  private ManagedChannel managedChannel;
  private Map<String, ServerMethodDefinition<?, ?>> serviceMethods;

  /**
   * Constructs the resolver using given parameters.
   *
   * @param grpcServerDesc mandatory
   * @param defsInterceptor can be null
   */
  @SneakyThrows
  public ServerCallStrategyResolver(GrpcServerDescriptor grpcServerDesc,
      ServerMethodDefinitionInterceptor defsInterceptor) {
    // prefer injected ServerMethodDefinition
    List<ServerMethodDefinition<?, ?>> defs = grpcServerDesc.getServerMethodDefinitions();
    if (!CollectionUtils.isEmpty(defs)) {
      this.serviceMethods = defs
          .stream()
          .collect(Collectors.toMap(this::mapMethodName, Function.identity()));
      return;
    }

    // all following approaches need call into local grpc server
    this.managedChannel = ManagedChannelBuilder.forAddress(
        "localhost", grpcServerDesc.getPort()).usePlaintext().build();
    final List<String> services = grpcServerDesc.getFileDescriptors()
        .stream()
        .flatMap(fd -> fd.getServices().stream())
        .map(ServiceDescriptor::getFullName)
        .collect(Collectors.toList());

    log.info("<ServerCallStrategyResolver> {}", services);

    // try ServerMethodDefinitionInterceptor
    if (defsInterceptor != null) {
      // call health service and trigger interceptor
      final HealthBlockingStub healthStub = HealthGrpc.newBlockingStub(this.managedChannel);
      services.forEach(s -> {
        final HealthCheckResponse check =
            healthStub.check(HealthCheckRequest.newBuilder().setService(s).build());
        if (check.getStatus() != ServingStatus.SERVING) {
          log.warn("<ServerCallStrategyResolver> {} not ready", s);
        }
      });

      final List<ServerServiceDefinition> defsFound = defsInterceptor.getServiceDefinitions();
      if (!CollectionUtils.isEmpty(defsFound)) {
        defs = defsFound
            .stream()
            .flatMap(s -> s.getMethods().stream())
            .collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(defs)) {
          this.serviceMethods = defs
              .stream()
              .collect(Collectors.toMap(this::mapMethodName, Function.identity()));
          done(); // don't need channel anymore
        }
      }
    }

    // try ProtoReflectionService, don't bother, same info from FileDescriptors
    // just invoke the call on channel
  }

  private String mapMethodName(ServerMethodDefinition<?, ?> smd) {
    return P.matcher(smd.getMethodDescriptor().getFullMethodName()).replaceAll(".");
  }

  /**
   * Looks up a {@link ServerCallStrategy} for given parameters.
   */
  Optional<ServerCallStrategy> lookup(Descriptors.MethodDescriptor desc,
      Class<? extends Message> reqClass,
      Class<? extends Message> respClass) {
    if (!CollectionUtils.isEmpty(this.serviceMethods)) {
      final ServerMethodDefinition<?, ?> mtdDef = this.serviceMethods.get(desc.getFullName());
      return mtdDef == null ? Optional.empty() : Optional.of(new ServerCallStrategyDirect(mtdDef));
    }

    return Optional.of(new ServerCallStrategyLocalRouting(
        this.managedChannel, createCallMethodDescriptor(desc, reqClass, respClass)));
  }

  private MethodDescriptor<Message, Message> createCallMethodDescriptor(
      Descriptors.MethodDescriptor desc, Class<? extends Message> reqClass,
      Class<? extends Message> respClass) {
    return MethodDescriptor.<Message, Message>newBuilder()
        .setFullMethodName(desc.getService().getFullName() + "/" + desc.getName())
        .setType(determineType(desc))
        .setRequestMarshaller(createMarshaller(reqClass))
        .setResponseMarshaller(createMarshaller(respClass))
        .build();
  }

  private Marshaller<Message> createMarshaller(Class<? extends Message> clazz) {
    final Builder builder = Util.getBuilder(clazz);
    return new Marshaller<Message>() {
      @Override
      public InputStream stream(Message value) {
        return new ByteArrayInputStream(value.toByteArray());
      }

      @SneakyThrows
      @Override
      public Message parse(InputStream stream) {
        return builder.mergeFrom(stream).build();
      }
    };
  }

  private MethodType determineType(Descriptors.MethodDescriptor desc) {
    if (desc.isClientStreaming() && desc.isServerStreaming()) {
      return MethodType.BIDI_STREAMING;
    }
    if (desc.isServerStreaming()) {
      return MethodType.SERVER_STREAMING;
    }
    if (desc.isClientStreaming()) {
      return MethodType.CLIENT_STREAMING;
    }
    return MethodType.UNARY;
  }

  /**
   * Shutdown internal {@link ManagedChannel} if found active.
   */
  void done() {
    if (this.managedChannel != null) {
      try {
        if (!this.managedChannel.shutdown().awaitTermination(10, TimeUnit.SECONDS)) {
          this.managedChannel.shutdownNow();
        }
        log.info("<done> managed grpc channel shutdown");
        this.managedChannel = null;
      } catch (InterruptedException e) {
        log.error("<done> shutdown managed grpc channel interrupted", e);
        Thread.currentThread().interrupt();
      }
    }
  }
}

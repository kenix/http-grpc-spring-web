package io.github.kenix.httpgrpc.spring;

import io.grpc.InternalServer;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerServiceDefinition;
import java.util.List;
import java.util.concurrent.Semaphore;
import lombok.Getter;

/**
 * Always simply forwards gRPC calls except the first one, which also records all {@link
 * ServerServiceDefinition}s that can be obtained from that gRPC server in the running context.
 *
 * @author zzhao
 */
public class ServerMethodDefinitionInterceptor implements ServerInterceptor {

  // one time sync
  private final Semaphore taskKey = new Semaphore(1);

  @Getter
  private List<ServerServiceDefinition> serviceDefinitions;

  @Override
  public <ReqT, RespT> Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
      ServerCallHandler<ReqT, RespT> next) {
    if (this.taskKey.tryAcquire()) {
      this.serviceDefinitions = InternalServer.SERVER_CONTEXT_KEY.get().getImmutableServices();
    }

    return next.startCall(call, headers);
  }
}

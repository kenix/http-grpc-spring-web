package io.github.kenix.httpgrpc.spring.strategy;

import com.google.protobuf.Message;
import io.grpc.Metadata;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerMethodDefinition;
import lombok.RequiredArgsConstructor;

/**
 * A server call strategy using direct server call.
 *
 * @author zzhao
 */
@RequiredArgsConstructor
public class ServerCallStrategyDirect implements ServerCallStrategy {

  private final ServerMethodDefinition<?, ?> methodDef;

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public Message call(Message message) {
    final ServerCallHandler<?, ?> callHandler = this.methodDef.getServerCallHandler();
    final DirectServerCall call = new DirectServerCall(this.methodDef.getMethodDescriptor());
    final Listener listener = callHandler.startCall(call, new Metadata());

    listener.onMessage(message);
    listener.onHalfClose();
    listener.onComplete();

    if (call.getStatus().isOk()) {
      return (Message) call.getMessage();
    } else {
      throw call.getStatus().asRuntimeException();
    }
  }
}

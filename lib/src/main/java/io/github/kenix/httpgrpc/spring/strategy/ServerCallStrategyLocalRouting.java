package io.github.kenix.httpgrpc.spring.strategy;

import com.google.protobuf.Message;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

/**
 * A server call strategy using local routing.
 *
 * @author zzhao
 */
@RequiredArgsConstructor
public class ServerCallStrategyLocalRouting implements ServerCallStrategy {

  private final ManagedChannel managedChannel;

  private final MethodDescriptor<Message, Message> methodDescriptor;

  @SneakyThrows
  @Override
  public Message call(Message message) {
    final ClientCall<Message, Message> clientCall =
        this.managedChannel.newCall(this.methodDescriptor, CallOptions.DEFAULT);
    final ClientCallOnceListener<Message> listener = new ClientCallOnceListener<>();
    clientCall.start(listener, new Metadata());
    clientCall.sendMessage(message);
    clientCall.halfClose();
    clientCall.request(1);

    listener.getLatch().await(2, TimeUnit.SECONDS);
    final Status status = listener.getStatus();
    if (status.isOk()) {
      return listener.getMessage();
    } else {
      throw status.asRuntimeException();
    }
  }
}

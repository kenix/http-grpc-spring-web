package io.github.kenix.httpgrpc.spring;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.Status;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Simulates a server call.
 *
 * @author zzhao
 */
@Getter
@RequiredArgsConstructor
public class DirectServerCall<ReqT, RespT> extends ServerCall<ReqT, RespT> {

  private final MethodDescriptor<ReqT, RespT> desc;

  private Object message;

  private Metadata headers;

  private Status status;

  @Override
  public void request(int numMessages) {
    //
  }

  @Override
  public void sendHeaders(Metadata headers) {
    this.headers = headers;
  }

  @Override
  public void sendMessage(RespT message) {
    this.message = message;
  }

  @Override
  public void close(Status status, Metadata trailers) {
    this.status = status;
  }

  @Override
  public boolean isCancelled() {
    // TODO cancel if timeout
    return false;
  }

  @Override
  public MethodDescriptor<ReqT, RespT> getMethodDescriptor() {
    return this.desc;
  }
}

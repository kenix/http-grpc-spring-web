package io.github.kenix.httpgrpc.spring.strategy;

import io.grpc.ClientCall.Listener;
import io.grpc.Metadata;
import io.grpc.Status;
import java.util.concurrent.CountDownLatch;
import lombok.Getter;

/**
 * A client call listener for unary server calls.
 *
 * @author zzhao
 */
@Getter
class ClientCallOnceListener<T> extends Listener<T> {

  private final CountDownLatch latch = new CountDownLatch(1);
  private T message;
  private Metadata headers;
  private Status status;
  private Metadata trailers;

  @Override
  public void onHeaders(Metadata headers) {
    this.headers = headers;
    super.onHeaders(headers);
  }

  @Override
  public void onMessage(T message) {
    this.message = message;
    super.onMessage(message);
  }

  @Override
  public void onClose(Status status, Metadata trailers) {
    this.status = status;
    this.trailers = trailers;
    this.latch.countDown();
    super.onClose(status, trailers);
  }

  @Override
  public void onReady() {
    super.onReady();
  }
}

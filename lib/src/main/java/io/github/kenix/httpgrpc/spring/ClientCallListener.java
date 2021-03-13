package io.github.kenix.httpgrpc.spring;

import io.grpc.ClientCall.Listener;
import io.grpc.Metadata;
import io.grpc.Status;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import lombok.Getter;

/**
 * A client call listener for streaming server responses.
 *
 * @author zzhao
 */
@Getter
class ClientCallListener<T> extends Listener<T> {

  private final List<T> messages;
  private final CountDownLatch latch;
  private Metadata headers;
  private Status status;
  private Metadata trailers;

  public ClientCallListener(int expectedMessages) {
    this.messages = new ArrayList<>(expectedMessages);
    this.latch = new CountDownLatch(1);
  }

  @Override
  public void onHeaders(Metadata headers) {
    this.headers = headers;
    super.onHeaders(headers);
  }

  @Override
  public void onMessage(T message) {
    this.messages.add(message);
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

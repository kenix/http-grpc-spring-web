/*
 * Created at 13:00 on 20.11.20
 */
package io.github.kenix.httpgrpc.spring.example;

import io.github.kenix.grpc.greeter.api.GreeterGrpc.GreeterImplBase;
import io.github.kenix.grpc.greeter.api.GreeterProto.HelloReply;
import io.github.kenix.grpc.greeter.api.GreeterProto.HelloRequest;
import io.github.kenix.grpc.greeter.api.GreeterProto.HelloRequestFrom;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;


@RequiredArgsConstructor
@Slf4j
public class ServiceImpl extends GreeterImplBase {

  @Override
  public void sayHello(HelloRequest req, StreamObserver<HelloReply> respOb) {
    if (log.isDebugEnabled()) {
      log.debug("<sayHello> ...");
    }

    final StringBuilder sb = new StringBuilder(1024);
    sb.append("hello, ").append(req.getName());
    if (req.hasSub()) {
      sb.append(", sub: ").append(req.getSub().toString());
    }
    sb.append(", planet: ").append(req.getPlanet());
    sb.append(", roles: ").append(req.getRoleList());

    final HelloReply reply = HelloReply.newBuilder()
        .setMessage(sb.toString())
        .build();

    respOb.onNext(reply);
    respOb.onCompleted();

    if (log.isDebugEnabled()) {
      log.debug("<sayHello> done");
    }

  }

  @Override
  public void sayHelloFrom(HelloRequestFrom req, StreamObserver<HelloReply> respOb) {
    if (log.isDebugEnabled()) {
      log.debug("<sayHelloFrom> ...");
    }

    final StringBuilder sb = new StringBuilder(1024);
    sb.append("hello, ").append(req.getName());
    if (req.hasSub()) {
      sb.append(", sub: ").append(req.getSub().toString());
    }
    sb.append(", from: ").append(req.getFrom());

    final HelloReply reply = HelloReply.newBuilder()
        .setMessage(sb.toString())
        .build();
    respOb.onNext(reply);
    respOb.onCompleted();

    if (log.isDebugEnabled()) {
      log.debug("<sayHelloFrom> done");
    }

  }
}

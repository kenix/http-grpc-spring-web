/*
 * Created at 13:00 on 20.11.20
 */
package io.github.kenix.httpgrpc.spring.example;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import net.kenix.grpc.greeter.api.GreeterGrpc.GreeterImplBase;
import net.kenix.grpc.greeter.api.GreeterProto.HelloReply;
import net.kenix.grpc.greeter.api.GreeterProto.HelloRequest;
import net.kenix.grpc.greeter.api.GreeterProto.HelloRequestFrom;


@RequiredArgsConstructor
@Slf4j
@GrpcService
public class ServiceImpl extends GreeterImplBase {

  @Override
  public void sayHello(HelloRequest req, StreamObserver<HelloReply> respOb) {
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
  }

  @Override
  public void sayHelloFrom(HelloRequestFrom req, StreamObserver<HelloReply> respOb) {
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
  }
}

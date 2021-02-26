package io.github.kenix.httpgrpc.spring.example;

import com.google.protobuf.Descriptors;
import io.envoyproxy.pgv.ReflectiveValidatorIndex;
import io.envoyproxy.pgv.grpc.ValidatingServerInterceptor;
import io.github.kenix.httpgrpc.spring.HttpGrpcMapper;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import net.kenix.grpc.greeter.api.GreeterProto;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * @author zzhao
 */
@SpringBootApplication
class App {

  public static void main(String[] args) {
    SpringApplication.run(App.class, args);
  }

  @GrpcGlobalServerInterceptor
  @Bean
  ValidatingServerInterceptor validatingServerInterceptor() {
    return new ValidatingServerInterceptor(new ReflectiveValidatorIndex());
  }

  @Bean
  Descriptors.FileDescriptor fileDescriptor() {
    return GreeterProto.getDescriptor();
  }

  @Bean
  HttpGrpcMapper httpGrpcMapper() {
    return new HttpGrpcMapper();
  }
}

package io.github.kenix.httpgrpc.spring.example;

import com.google.protobuf.Descriptors.FileDescriptor;
import io.envoyproxy.pgv.ReflectiveValidatorIndex;
import io.envoyproxy.pgv.grpc.ValidatingServerInterceptor;
import io.github.kenix.grpc.greeter.api.GreeterProto;
import io.github.kenix.httpgrpc.spring.GrpcServerDescriptor;
import io.github.kenix.httpgrpc.spring.HttpGrpcMapper;
import io.grpc.ServerMethodDefinition;
import java.util.Collections;
import java.util.List;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

/**
 * @author zzhao
 */
@Profile({"!intercept"})
@SpringBootApplication
class AppLocalRouting {

  public static void main(String[] args) {
    SpringApplication.run(AppLocalRouting.class, args);
  }

  @GrpcGlobalServerInterceptor
  @Bean
  ValidatingServerInterceptor validatingServerInterceptor() {
    return new ValidatingServerInterceptor(new ReflectiveValidatorIndex());
  }

  @Bean
  @GrpcService
  ServiceImpl service() {
    return new ServiceImpl();
  }

  @Bean
  HttpGrpcMapper httpGrpcMapper() {
    return new HttpGrpcMapper();
  }

  @Bean
  GrpcServerDescriptor grpcServerDescriptor(@Value("${grpc.server.port}") int port) {
    return new GrpcServerDescriptor() {
      @Override
      public List<FileDescriptor> getFileDescriptors() {
        return Collections.singletonList(GreeterProto.getDescriptor());
      }

      @Override
      public List<ServerMethodDefinition<?, ?>> getServerMethodDefinitions() {
        return Collections.emptyList();
      }

      @Override
      public int getPort() {
        return port;
      }
    };
  }
}

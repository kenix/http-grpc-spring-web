# http-grpc-spring-web

A simplistic spring-web component that automatically configures spring-mvc controllers that
transcodes HTTP requests, invokes corresponding gRPC service method and transcodes the reply back to
HTTP response.

## Usage

Show me how: refer to the module [**example**][2] with its tests.

### Dependency

Maven:

```xml

<dependency>
  <groupId>io.github.kenix</groupId>
  <artifactId>http-grpc-spring-web</artifactId>
  <version>0.1.0-rc.4</version>
</dependency>
```

Gradle:

```groovy
dependencies {
  implementation 'io.github.kenix:http-grpc-spring-web:0.1.0-rc.4'
}
```

### Plumbing

After implementing a gRPC service, provide following beans:

`HttpGrpcMapper` responsible for discovering gRPC services and registering transcoder controllers

`GrpcServerDescriptor` used by `HttpGrpcMapper` to discover gRPC services. Currently, only support single server. If necessary, can easily support multiple servers.
    
* `List<FileDescriptor>` is mandatory in order to find all message types of gRPC requests. TODO: either injection or enabling ProtoReflectionService
* one of following alternatives:
  
    1. `List<ServerMethodDefinition>` transcoded call is made directly
    1. gRPC server port and one of following:
        
        1. a gRPC global server interceptor `ServerMethodDefinitionInterceptor` and enabling gRPC `HealthService` Invoked the first time (service health check) it will collect all `ServerMethodDefinition`s, after that only forward calls. This enables direct transcoded calls.
        1. nothing else, transcoded call will not be direct, but routed internally using an embedded gRPC client. This has performance impact.

## Integration

* distributed tracing: out of the box with `spring-cloud-starter-sleuth`, see also module __client__

## TODO

Refer to open issues.

[1]: https://github.com/grpc/grpc-java/issues/7927
[2]: example/src/main/java/io/github/kenix/httpgrpc/spring/example/App.java
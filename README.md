# http-grpc-spring-boot-starter

A very simplistic spring boot starter that automatically configures spring-mvc controllers that
transcodes HTTP requests, invokes corresponding gRPC service method and transcodes the reply back to
HTTP response.

It's simple to use, also fun to develop. Let's see how far it goes.

## Usage

After implementing a gRPC service, provide the `FileDescriptor` as a spring bean.

## TODO

- [ ] add test projects and turn into multi-module project
- [ ] refactor using more abstractions
- [ ] use `RequestMappingHandlerMapping`
- [ ] support WebFlow
- [ ] better integration with web-mvc: interceptor (including `io.grpc.ServerInterceptor`), security

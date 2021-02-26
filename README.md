# http-grpc-spring-web

A simplistic spring-web component that automatically configures spring-mvc controllers that
transcodes HTTP requests, invokes corresponding gRPC service method and transcodes the reply back to
HTTP response.

It's simple to use, also fun to develop. Let's see how far it goes.

Currently, it depends on `net.devh:grpc-spring-boot-starter` in order to access gRPC `Server`
from `GrpcServerLifecycle` using reflection. Will ask google about their comment
on: `io.grpc.Server.SERVER_CONTEXT_KEY`

```java
/**
 * Key for accessing the {@link Server} instance inside server RPC {@link Context}. It's
 * unclear to us what users would need. If you think you need to use this, please file an
 * issue for us to discuss a public API.
 */
static final Context.Key<Server> SERVER_CONTEXT_KEY=Context.key("io.grpc.Server");
```

## Usage

### dependency

Maven:

```xml

<dependency>
  <groupId>io.github.kenix</groupId>
  <artifactId>http-grpc-spring-web</artifactId>
  <version>0.1.0-rc.2</version>
</dependency>
```

Gradle:

```groovy
dependencies {
  implementation 'io.github.kenix:http-grpc-spring-web:0.1.0-rc.2'
}
```

After implementing a gRPC service, provide following beans:

* `FileDescriptor`
* `HttpGrpcMapper`

Also refer to the sub-module `example` with its single test.

## TODO

- [ ] support WebFlux
- [ ] integration spring security

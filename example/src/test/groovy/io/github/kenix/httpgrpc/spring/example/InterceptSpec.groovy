package io.github.kenix.httpgrpc.spring.example

import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration

/**
 * @author zzhao
 */
@ContextConfiguration(initializers = ConfigFileApplicationContextInitializer)
@SpringBootTest(classes = App, properties = ['grpc.server.port=55333'])
@ActiveProfiles(['test', 'intercept'])
class InterceptSpec extends HttpGrpcSpec {
}

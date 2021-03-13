package io.github.kenix.httpgrpc.spring.example

import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration

/**
 * @author zzhao
 */
@ContextConfiguration(initializers = ConfigFileApplicationContextInitializer)
@SpringBootTest(classes = AppLocalRouting, properties = ['grpc.server.port=55334'])
@ActiveProfiles(['test', 'local'])
class LocalRoutingSpec extends HttpGrpcSpec {
}

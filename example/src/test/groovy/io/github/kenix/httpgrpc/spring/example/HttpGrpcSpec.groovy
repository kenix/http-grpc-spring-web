package io.github.kenix.httpgrpc.spring.example

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.util.JsonFormat
import net.kenix.grpc.greeter.api.GreeterProto
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

/**
 * @author zzhao
 */
@ContextConfiguration(initializers = ConfigDataApplicationContextInitializer)
@SpringBootTest(classes = App)
class HttpGrpcSpec extends Specification {

  @Autowired
  WebApplicationContext wac

  @Autowired
  ObjectMapper objectMapper

  MockMvc mockMvc

  def setup() {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).build()
  }

  def 'get /v1/greeter/{name} unsupported media type'() {
    when: 'json'
    def result = this.mockMvc.perform(
        get("/v1/greeter/foo")
            .accept(MediaType.APPLICATION_XML)
    ).andReturn()
    then:
    result.response.status == HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()
  }

  @Unroll
  def 'get /v1/greeter/#fragment bad request'() {
    when: 'json'
    def result = this.mockMvc.perform(
        get("/v1/greeter/$fragment")
            .accept(MediaType.APPLICATION_JSON)
    ).andReturn()
    then:
    noExceptionThrown()
    result.response.status == HttpStatus.BAD_REQUEST.value()

    when: 'protobuf'
    result = this.mockMvc.perform(
        get("/v1/greeter/$fragment")
            .accept(MediaType.APPLICATION_OCTET_STREAM)
    ).andReturn()
    then:
    noExceptionThrown()
    result.response.status == HttpStatus.BAD_REQUEST.value()

    where:
    fragment << ['foo1', 'foo?planet=Sun']
  }

  @Unroll
  def 'get /v1/greeter/#fragment'() {
    when: 'json'
    def result = this.mockMvc.perform(
        get("/v1/greeter/$fragment")
            .accept(MediaType.APPLICATION_JSON)
    ).andReturn()
    then:
    noExceptionThrown()

    when:
    def content = result.response.contentAsString
    def builder = GreeterProto.HelloReply.newBuilder()
    JsonFormat.parser().merge(content, builder)
    def reply = builder.build()
    then:
    predicate(reply.message)

    when: 'protobuf'
    result = this.mockMvc.perform(
        get("/v1/greeter/$fragment")
            .accept(MediaType.APPLICATION_OCTET_STREAM)
    ).andReturn()
    then:
    noExceptionThrown()

    when:
    content = result.response.contentAsByteArray
    builder = GreeterProto.HelloReply.newBuilder()
    builder.mergeFrom(content)
    reply = builder.build()
    then:
    predicate(reply.message)

    where:
    fragment                                            | predicate
    'foo'                                               | { String m -> m.contains('hello, foo') }
    'foo?planet=Mars'                                   | { String m -> m.contains('Mars') }
    'foo?role=Person'                                   | { String m -> m.contains('Person') }
    'foo?sub.subfield=bar'                              | { String m -> m.contains('bar') }
    'foo?planet=Earth&sub.subfield=bar&role=R1&role=R2' | { String m ->
      ['hello, foo', 'Earth', 'bar', 'R1', 'R2'].every { m.contains(it) }
    }
  }

  def 'post /v1/greeter not acceptable content type'() {
    when: 'json'
    def result = this.mockMvc.perform(
        post("/v1/greeter")
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_XML)
            .content('<foo/>')
    ).andReturn()
    then:
    noExceptionThrown()
    result.response.status == HttpStatus.NOT_ACCEPTABLE.value()
  }

  def 'post /v1/greeter unsupported media type'() {
    when: 'json'
    def result = this.mockMvc.perform(
        post("/v1/greeter")
            .accept(MediaType.APPLICATION_XML)
    ).andReturn()
    then:
    result.response.status == HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()
  }

  def 'post /v1/greeter bad request'() {
    when: 'json'
    def result = this.mockMvc.perform(
        post("/v1/greeter")
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .content('{"name":"foo","sub":{"planet":"Sun"}}')
    ).andReturn()
    then:
    noExceptionThrown()
    result.response.status == HttpStatus.BAD_REQUEST.value()
  }

  def 'post /v1/greeter'() {
    def path = '/v1/greeter'
    def payload = '{"name":"foo","sub":{"planet":"Mars"}}'
    def parts = ['hello, foo', 'Mars']

    when: 'json'
    def result = this.mockMvc.perform(
        post(path)
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload)
    ).andReturn()
    then:
    noExceptionThrown()

    when:
    def content = result.response.contentAsString
    def builder = GreeterProto.HelloReply.newBuilder()
    JsonFormat.parser().merge(content, builder)
    def reply = builder.build()
    then:
    parts.every { reply.message.contains(it) }

    when: 'json'
    result = this.mockMvc.perform(
        post(path)
            .accept(MediaType.APPLICATION_OCTET_STREAM)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload)
    ).andReturn()
    then:
    noExceptionThrown()

    when:
    content = result.response.contentAsByteArray
    builder = GreeterProto.HelloReply.newBuilder()
    builder.mergeFrom(content)
    reply = builder.build()
    then:
    parts.every { reply.message.contains(it) }
  }

  def 'post /v1/greeter/{name}'() {
    given:
    def path = '/v1/greeter/foo'
    def payload = '{"planet":"Mars","fromField":666}'
    def parts = ['hello, foo', 'Mars', '666']

    when: 'json'
    def result = this.mockMvc.perform(
        post(path)
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload)
    ).andReturn()
    then:
    noExceptionThrown()

    when:
    def content = result.response.contentAsString
    def builder = GreeterProto.HelloReply.newBuilder()
    JsonFormat.parser().merge(content, builder)
    def reply = builder.build()
    then:
    parts.every { reply.message.contains(it) }

    when: 'proto'
    result = this.mockMvc.perform(
        post(path)
            .accept(MediaType.APPLICATION_OCTET_STREAM)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload)
    ).andReturn()
    then:
    noExceptionThrown()

    when:
    content = result.response.contentAsByteArray
    builder = GreeterProto.HelloReply.newBuilder()
    builder.mergeFrom(content)
    reply = builder.build()
    then:
    parts.every { reply.message.contains(it) }
  }

  def 'post /v1/greeter/{name}/{from}'() {
    given:
    def path = '/v1/greeter/foo/Zurich'
    def payload = '{"planet":"Mars","fromField":666}'
    def parts = ['hello, foo', 'Mars', '666', 'Zurich']

    when: 'json'
    def result = this.mockMvc.perform(
        post(path)
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload)
    ).andReturn()
    then:
    noExceptionThrown()

    when:
    def content = result.response.contentAsString
    def builder = GreeterProto.HelloReply.newBuilder()
    JsonFormat.parser().merge(content, builder)
    def reply = builder.build()
    then:
    parts.every { reply.message.contains(it) }

    when: 'proto'
    result = this.mockMvc.perform(
        post(path)
            .accept(MediaType.APPLICATION_OCTET_STREAM)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload)
    ).andReturn()
    then:
    noExceptionThrown()

    when:
    content = result.response.contentAsByteArray
    builder = GreeterProto.HelloReply.newBuilder()
    builder.mergeFrom(content)
    reply = builder.build()
    then:
    parts.every { reply.message.contains(it) }
  }
}

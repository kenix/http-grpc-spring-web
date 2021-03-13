package io.github.kenix.httpgrpc.spring.strategy;

import com.google.protobuf.Message;

/**
 * A strategy interface for invoking server calls.
 *
 * @author zzhao
 */
public interface ServerCallStrategy {

  /**
   * Invokes server call with the given request message.
   *
   * @param message request message
   * @return response message
   */
  Message call(Message message);
}

package io.github.kenix.httpgrpc.spring;

import com.google.protobuf.Descriptors.FileDescriptor;
import io.grpc.ServerMethodDefinition;
import java.util.Collections;
import java.util.List;

/**
 * Describes a gRPC server with the port it is running on with its underlying {@link
 * FileDescriptor}s.
 * <p>
 * If possible, with all related {@link ServerMethodDefinition}s, in which case transcoded call will
 * be directly made against a matching {@link io.grpc.ServerCallHandler}.
 * </p>
 * <p>
 * Another way is to register the {@link ServerMethodDefinitionInterceptor} as a global gRPC server
 * interceptor to enable discovering {@link ServerMethodDefinition}s once after server start. Refer
 * to {@link ServerMethodDefinitionInterceptor} for more information.
 * </p>
 * <p>
 * If none  {@link ServerMethodDefinition}s can be discovered, calls will be routed locally to the
 * gRPC port.
 * </p>
 * Summary of valid setups:
 * <ul>
 *   <li>mandatory: {@link #getFileDescriptors()}</li>
 *   <li>either {@link #getServerMethodDefinitions()}</li>
 *   <li>or {@link #getPort()} w/o {@link ServerMethodDefinitionInterceptor}</li>
 * </ul>
 *
 * @author zzhao
 */
public interface GrpcServerDescriptor {

  /**
   * Gets {@link FileDescriptor}s.
   *
   * @return file descriptors used by the provided gRPC service.
   */
  List<FileDescriptor> getFileDescriptors();

  /**
   * Gets {@link ServerMethodDefinition}s.
   *
   * @return server method definitions if possible
   */
  default List<ServerMethodDefinition<?, ?>> getServerMethodDefinitions() {
    return Collections.emptyList();
  }

  /**
   * Gets gRPC server port.
   *
   * @return the gRPC server port.
   */
  int getPort();
}

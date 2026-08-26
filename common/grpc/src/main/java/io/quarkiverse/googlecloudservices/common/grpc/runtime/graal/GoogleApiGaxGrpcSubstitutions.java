package io.quarkiverse.googlecloudservices.common.grpc.runtime.graal;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import com.google.api.core.ApiFunction;
import com.google.api.gax.grpc.ChannelPrimer;
import com.google.api.gax.grpc.GrpcHeaderInterceptor;
import com.google.api.gax.grpc.GrpcInterceptorProvider;
import com.google.api.gax.rpc.HeaderProvider;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;

/**
 * Restored after being removed on the (untested) theory that GAX might have gained native-image
 * support of its own (`Check if GRPC native support is sufficient`). It hadn't, at least not for
 * consumers that follow this module's own stated intent of excluding grpc-netty-shaded in favor
 * of plain grpc-netty: the real {@code InstantiatingGrpcChannelProvider.createSingleChannel()}
 * reaches, via {@code createDecoratedChannelBuilder()}, into ALTS/DirectPath channel-building code
 * that references {@code io.grpc.netty.shaded.*} classes. Without this substitution, GraalVM's
 * static analysis fails hard on that unreachable class as soon as a consumer's classpath actually
 * lacks grpc-netty-shaded -- exactly the classpath shape this module's own gax-grpc exclusion
 * (see the sibling pom.xml) is meant to produce.
 * <p>
 * The removal wasn't caught by CI because every native IT that happened to exercise a
 * common/grpc-based extension also depended on Spanner, whose own google-cloud-spanner dependency
 * pulls grpc-netty-shaded back onto the classpath unexcluded -- masking the gap. See
 * integration-tests/firebase's newly-added native profile (previously missing entirely, so
 * -Pnative silently built a plain JVM jar there) for a native IT that reproduces this without
 * Spanner in the mix.
 */
final class GoogleApiGaxGrpcSubstitutions {
}

@TargetClass(className = "com.google.api.gax.grpc.InstantiatingGrpcChannelProvider")
final class Target_com_google_api_gax_grpc_InstantiatingGrpcChannelProvider {

    @Alias
    private Executor executor;
    @Alias
    private HeaderProvider headerProvider;
    @Alias
    private GrpcInterceptorProvider interceptorProvider;
    @Alias
    private String endpoint;
    @Alias
    private Integer maxInboundMessageSize;
    @Alias
    private Integer maxInboundMetadataSize;
    @Alias
    private Duration keepAliveTime;
    @Alias
    private Duration keepAliveTimeout;
    @Alias
    private Boolean keepAliveWithoutCalls;
    @Alias
    private ChannelPrimer channelPrimer;
    @Alias
    private ApiFunction<ManagedChannelBuilder, ManagedChannelBuilder> channelConfigurator;

    @Substitute
    private ManagedChannel createSingleChannel() throws IOException {
        GrpcHeaderInterceptor headerInterceptor = new GrpcHeaderInterceptor(this.headerProvider.getHeaders());
        Target_com_google_api_gax_grpc_GrpcMetadataHandlerInterceptor metadataHandlerInterceptor = new Target_com_google_api_gax_grpc_GrpcMetadataHandlerInterceptor();
        int colon = this.endpoint.lastIndexOf(58);
        if (colon < 0) {
            throw new IllegalStateException("invalid endpoint - should have been validated: " + this.endpoint);
        } else {
            int port = Integer.parseInt(this.endpoint.substring(colon + 1));
            String serviceAddress = this.endpoint.substring(0, colon);

            ManagedChannelBuilder builder = ManagedChannelBuilder.forAddress(serviceAddress, port);
            builder = ((ManagedChannelBuilder) builder).disableServiceConfigLookUp()
                    .intercept(new ClientInterceptor[] { new Target_com_google_api_gax_grpc_GrpcChannelUUIDInterceptor() })
                    .intercept(new ClientInterceptor[] { headerInterceptor })
                    .intercept(new ClientInterceptor[] { metadataHandlerInterceptor })
                    .userAgent(headerInterceptor.getUserAgentHeader()).executor(this.executor);
            if (this.maxInboundMetadataSize != null) {
                builder.maxInboundMetadataSize(this.maxInboundMetadataSize);
            }

            if (this.maxInboundMessageSize != null) {
                builder.maxInboundMessageSize(this.maxInboundMessageSize);
            }

            if (this.keepAliveTime != null) {
                builder.keepAliveTime(this.keepAliveTime.toMillis(), TimeUnit.MILLISECONDS);
            }

            if (this.keepAliveTimeout != null) {
                builder.keepAliveTimeout(this.keepAliveTimeout.toMillis(), TimeUnit.MILLISECONDS);
            }

            if (this.keepAliveWithoutCalls != null) {
                builder.keepAliveWithoutCalls(this.keepAliveWithoutCalls);
            }

            if (this.interceptorProvider != null) {
                builder.intercept(this.interceptorProvider.getInterceptors());
            }

            if (this.channelConfigurator != null) {
                builder = (ManagedChannelBuilder) this.channelConfigurator.apply(builder);
            }

            ManagedChannel managedChannel = builder.build();
            if (this.channelPrimer != null) {
                this.channelPrimer.primeChannel(managedChannel);
            }

            return managedChannel;
        }
    }
}

@TargetClass(className = "com.google.api.gax.grpc.GrpcMetadataHandlerInterceptor")
final class Target_com_google_api_gax_grpc_GrpcMetadataHandlerInterceptor implements ClientInterceptor {

    @Alias()
    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, final CallOptions callOptions, Channel next) {
        throw new UnsupportedOperationException("Alias should not be called");
    }
}

@TargetClass(className = "com.google.api.gax.grpc.GrpcChannelUUIDInterceptor")
final class Target_com_google_api_gax_grpc_GrpcChannelUUIDInterceptor implements ClientInterceptor {

    @Alias
    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions, Channel channel) {
        throw new UnsupportedOperationException("Alias should not be called");
    }
}

package io.quarkiverse.googlecloudservices.common.grpc.runtime.graal;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

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
import io.grpc.ChannelCredentials;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;

/**
 * quarkus-opentelemetry-exporter-gcp ships its own substitution for the same target class, for the
 * same reason this one exists (grpc-alts is not native-image friendly). GraalVM's
 * {@code AnnotationSubstitutionProcessor} refuses two substitutions for the same target method, so
 * an application depending on both extensions fails to build a native image with "conflicts with
 * previously registered". {@code onlyWith} here makes this substitution back off when that one is
 * present, so the app builds either way; this substitution now also carries the mTLS channel
 * credentials support that extension's version has (see {@link #createMtlsChannelCredentials()}),
 * copied over from the same commit ancestor, so nothing is lost when the other extension is
 * absent and this one is used standalone.
 */
final class NoOtelExporterGcpSubstitutionPredicate implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        try {
            this.getClass().getClassLoader().loadClass(
                    "io.quarkiverse.opentelemetry.exporter.gcp.runtime.graal.InstantiatingGrpcChannelProviderSubstitutions");
            return false;
        } catch (Exception ignored) {
            return true;
        }
    }
}

@TargetClass(className = "com.google.api.gax.grpc.InstantiatingGrpcChannelProvider", onlyWith = NoOtelExporterGcpSubstitutionPredicate.class)
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
            //            Object builder;
            //            if (this.isDirectPathEnabled(serviceAddress) && this.credentials instanceof ComputeEngineCredentials) {
            //                builder = ComputeEngineChannelBuilder.forAddress(serviceAddress, port);
            //                ((ManagedChannelBuilder)builder).keepAliveTime(3600L, TimeUnit.SECONDS);
            //                ((ManagedChannelBuilder)builder).keepAliveTimeout(20L, TimeUnit.SECONDS);
            //                ImmutableMap<String, Object> pickFirstStrategy = ImmutableMap.of("pick_first", ImmutableMap.of());
            //                ImmutableMap<String, Object> childPolicy = ImmutableMap.of("childPolicy", ImmutableList.of(pickFirstStrategy));
            //                ImmutableMap<String, Object> grpcLbPolicy = ImmutableMap.of("grpclb", childPolicy);
            //                ImmutableMap<String, Object> loadBalancingConfig = ImmutableMap.of("loadBalancingConfig", ImmutableList.of(grpcLbPolicy));
            //                ((ManagedChannelBuilder)builder).defaultServiceConfig(loadBalancingConfig);
            //            } else {
            //                builder = ManagedChannelBuilder.forAddress(serviceAddress, port);
            //            }

            ChannelCredentials channelCredentials;
            try {
                channelCredentials = this.createMtlsChannelCredentials();
            } catch (GeneralSecurityException e) {
                throw new IOException(e);
            }

            ManagedChannelBuilder builder;
            if (channelCredentials != null) {
                builder = Grpc.newChannelBuilder(this.endpoint, channelCredentials);
            } else {
                builder = ManagedChannelBuilder.forAddress(serviceAddress, port);
            }
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

    @Alias
    ChannelCredentials createMtlsChannelCredentials() throws IOException, GeneralSecurityException {
        throw new UnsupportedOperationException("Alias should not be called");
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

class GoogleApiGaxGrpcSubstitutions {
}

package com.xxplatform.api.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.xxplatform.api.client.ingestion.IngestionQueryAdapter;
import com.xxplatform.api.client.plate.PlateQueryAdapter;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.generated.model.*;
import com.xxplatform.api.service.ingestion.*;
import com.xxplatform.api.service.plate.*;
import java.time.Clock;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;

/** DEV2 wiring. DEV1/DEV4 supply the existing trusted port implementations. */
@Configuration(proxyBeanMethods = false)
@Profile("!ingestion-mock")
public class DataIntegrationConfiguration {
    private static <T> T required(ObjectProvider<T> provider, String name) {
        T value = provider.getIfAvailable();
        if (value == null) throw new DataApiException(ErrorCode.SYSTEM_UNAVAILABLE, null,
                name + " is not connected");
        return value;
    }

    // Late resolution keeps an unconfigured application usable without fabricating data.
    // Forward the original domain exception, including permission and cursor failures.
    private static <T> T bridge(Class<T> type, ObjectProvider<T> provider) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "DependencyBridge[" + type.getSimpleName() + "]";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> throw new IllegalStateException(method.getName());
                        };
                    }
                    try {
                        return method.invoke(required(provider, type.getSimpleName()), args);
                    } catch (InvocationTargetException error) {
                        throw error.getCause();
                    }
                }));
    }

    @Bean
    DataServiceBindings dataServiceBindings(ObjectProvider<DataServiceBindings.SharedAccess> access,
            ObjectProvider<IngestionDomainGateway.AtomicSubmission> submissions,
            ObjectProvider<IngestionQueryAdapter.Client> ingestion,
            ObjectProvider<IngestionQueryAdapter.WindowPolicy> windows,
            ObjectProvider<PlateQueryAdapter.Client> plate,
            ObjectProvider<PlateQueryAdapter.TargetLookup> targets, ObjectMapper mapper) {
        return new DataServiceBindings(bridge(DataServiceBindings.SharedAccess.class, access),
                bridge(IngestionDomainGateway.AtomicSubmission.class, submissions),
                bridge(IngestionQueryAdapter.Client.class, ingestion),
                bridge(IngestionQueryAdapter.WindowPolicy.class, windows),
                bridge(PlateQueryAdapter.Client.class, plate),
                bridge(PlateQueryAdapter.TargetLookup.class, targets), mapper);
    }

    @Bean IngestionQueryService ingestionQueryService(DataServiceBindings bindings) {
        return new IngestionQueryService(bindings.access(), bindings.reads());
    }
    @Bean IngestionBatchSubmissionService ingestionSubmissionService(DataServiceBindings bindings) {
        return bindings.submission();
    }
    @Bean IngestionBatchReplayService ingestionReplayService(DataServiceBindings bindings) {
        return bindings.replay();
    }
    @Bean IngestionBackfillService ingestionBackfillService(DataServiceBindings bindings) {
        return bindings.backfill();
    }
    @Bean PlateQueryService plateQueryService(DataServiceBindings bindings) { return bindings.plate(); }

    /** Injected by DEV1 exports; task creation and file publication remain shared. */
    @Bean CaptureExportSourceAdapter captureExportSourceAdapter(DataServiceBindings bindings,
            ObjectProvider<CaptureExportSourceAdapter.ContextLookup> contexts, ObjectProvider<Clock> clocks) {
        return bindings.exports(bridge(CaptureExportSourceAdapter.ContextLookup.class, contexts),
                clocks.getIfAvailable(Clock::systemUTC));
    }

    @Bean SimpleModule dataIntegrationInputs() {
        return new SimpleModule("data-integration-inputs")
                .addDeserializer(IngestionBatchInput.class, new IngestionBatchInputDeserializer())
                .addDeserializer(ReplayInput.class, new IngestionReplayInputDeserializer())
                .addDeserializer(BackfillInput.class, new IngestionBackfillInputDeserializer())
                .addDeserializer(CaptureSearchRequest.class, new CaptureSearchInputDeserializer());
    }
}

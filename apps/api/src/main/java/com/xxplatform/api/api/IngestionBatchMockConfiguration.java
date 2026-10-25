package com.xxplatform.api.api;

import com.xxplatform.api.client.ingestion.InMemoryIngestionBatchQuery;
import com.xxplatform.api.service.ingestion.IngestionBatchSubmissionService;
import com.xxplatform.api.generated.model.IngestionBatchInput;
import com.xxplatform.api.generated.model.ReplayInput;
import com.xxplatform.api.generated.model.BackfillInput;
import com.xxplatform.api.service.ingestion.IngestionBackfillService;
import com.xxplatform.api.service.ingestion.IngestionBatchReplayService;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.xxplatform.api.service.ingestion.IngestionQueryService;
import com.xxplatform.api.service.ingestion.IngestionQueryService.BatchQuery;
import com.xxplatform.api.service.ingestion.IngestionQueryService.ReadAccess;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("dev & ingestion-mock")
public class IngestionBatchMockConfiguration {
    @Bean
    IngestionBatchSubmissionService ingestionBatchSubmissionService(org.springframework.beans.factory.ObjectProvider<DataServiceBindings> bindings) {
        DataServiceBindings bound = bindings.getIfAvailable();
        if (bound != null) return bound.submission();
        // No fixed mock identity or task success is enabled for writes.
        return IngestionBatchSubmissionService.unavailable();
    }

    @Bean
    IngestionBatchReplayService ingestionBatchReplayService(org.springframework.beans.factory.ObjectProvider<DataServiceBindings> bindings) {
        DataServiceBindings bound = bindings.getIfAvailable();
        if (bound != null) return bound.replay();
        return IngestionBatchReplayService.unavailable();
    }

    @Bean
    IngestionBackfillService ingestionBackfillService(org.springframework.beans.factory.ObjectProvider<DataServiceBindings> bindings) {
        DataServiceBindings bound = bindings.getIfAvailable();
        if (bound != null) return bound.backfill();
        return IngestionBackfillService.unavailable();
    }

    @Bean
    SimpleModule ingestionBackfillInputModule() {
        return new SimpleModule("m03-ingestion-backfill-input")
                .addDeserializer(BackfillInput.class, new IngestionBackfillInputDeserializer());
    }

    @Bean
    SimpleModule ingestionReplayInputModule() {
        return new SimpleModule("m03-ingestion-replay-input")
                .addDeserializer(ReplayInput.class, new IngestionReplayInputDeserializer());
    }

    @Bean
    SimpleModule ingestionInputModule() {
        return new SimpleModule("m03-ingestion-input")
                .addDeserializer(IngestionBatchInput.class, new IngestionBatchInputDeserializer());
    }

    @Bean
    ReadAccess ingestionMockReadAccess(Environment environment, org.springframework.beans.factory.ObjectProvider<DataServiceBindings> bindings) {
        DataServiceBindings bound = bindings.getIfAvailable();
        if (bound != null) return bound.access();
        // A fixed synthetic identity is for local development only.
        String address = environment.getProperty("server.address", "");
        if (!Set.of("127.0.0.1", "::1").contains(address)) {
            throw new IllegalStateException(
                    "ingestion-mock requires --server.address=127.0.0.1 or ::1");
        }
        return InMemoryIngestionBatchQuery::demoScope;
    }

    @Bean
    BatchQuery ingestionMockQuery(org.springframework.beans.factory.ObjectProvider<DataServiceBindings> bindings) {
        DataServiceBindings bound = bindings.getIfAvailable();
        if (bound != null) return bound.reads();
        return new InMemoryIngestionBatchQuery(InMemoryIngestionBatchQuery.demoRows());
    }

    @Bean
    IngestionQueryService ingestionBatchListService(ReadAccess access, BatchQuery query) {
        return new IngestionQueryService(access, query);
    }
}

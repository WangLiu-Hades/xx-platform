package com.xxplatform.api.api;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.xxplatform.api.generated.model.CaptureSearchRequest;
import com.xxplatform.api.service.plate.PlateQueryService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("dev & ingestion-mock")
public class PlateQueryConfiguration {
    @Bean PlateQueryService week2PlateChainService(org.springframework.beans.factory.ObjectProvider<DataServiceBindings> bindings) {
        DataServiceBindings bound = bindings.getIfAvailable();
        return bound == null ? PlateQueryService.unavailable() : bound.plate();
    }
    @Bean SimpleModule week2CaptureInputModule() {
        return new SimpleModule("week2-capture-input").addDeserializer(
                CaptureSearchRequest.class, new CaptureSearchInputDeserializer());
    }
}

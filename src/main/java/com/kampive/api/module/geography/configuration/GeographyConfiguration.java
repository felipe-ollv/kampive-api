package com.kampive.api.module.geography.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.kampive.api.module.geography.application.port.in.GeographyCatalog;
import com.kampive.api.module.geography.application.port.out.GeographyRepository;
import com.kampive.api.module.geography.application.usecase.GeographyService;

@Configuration(proxyBeanMethods = false)
public class GeographyConfiguration {
    @Bean GeographyCatalog geographyCatalog(GeographyRepository repository) {
        return new GeographyService(repository);
    }
}

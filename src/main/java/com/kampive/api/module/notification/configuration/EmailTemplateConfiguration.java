package com.kampive.api.module.notification.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.kampive.api.module.notification.application.port.in.EmailTemplates;
import com.kampive.api.module.notification.application.port.out.EmailTemplateRepository;
import com.kampive.api.module.notification.application.usecase.EmailTemplateService;

@Configuration
public class EmailTemplateConfiguration {
    @Bean EmailTemplates emailTemplates(EmailTemplateRepository repository) {
        return new EmailTemplateService(repository);
    }
}

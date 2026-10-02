package com.kampive.api.module.user.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import com.kampive.api.module.user.application.port.in.UserAccounts;
import com.kampive.api.module.user.application.port.out.UserRepository;
import com.kampive.api.module.user.application.usecase.UserService;

@Configuration(proxyBeanMethods=false)
public class UserConfiguration {
    @Bean @Primary
    UserAccounts userAccounts(UserRepository repository) { return new UserService(repository); }
}

package com.kampive.api.module.identity.configuration;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.kampive.api.module.identity.application.port.in.IdentityUseCases;
import com.kampive.api.module.identity.application.port.out.CredentialStore;
import com.kampive.api.module.identity.application.port.out.PasswordHasher;
import com.kampive.api.module.identity.application.usecase.IdentityService;
import com.kampive.api.module.identity.domain.IdentityFailure;
import com.kampive.api.module.user.application.port.in.UserAccounts;
import com.kampive.api.module.user.application.port.in.UserAccounts.UserView;

@Configuration(proxyBeanMethods=false)
public class IdentityConfiguration {
    @Bean
    IdentityUseCases identityUseCases(UserAccounts users, CredentialStore credentials,
            PasswordHasher passwords, PlatformTransactionManager manager) {
        var core=new IdentityService(users,credentials,passwords);
        var transaction=new TransactionTemplate(manager);
        return new IdentityUseCases() {
            public UserView register(Registration command) {
                try { return transaction.execute(status -> core.register(command)); }
                catch (DataIntegrityViolationException exception) {
                    throw new IdentityFailure(IdentityFailure.Reason.REGISTRATION_CONFLICT);
                }
            }
            public UserView authenticate(String email,String password) { return core.authenticate(email,password); }
            public List<LegalDocument> currentDocuments() { return core.currentDocuments(); }
        };
    }
}

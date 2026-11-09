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
    UserAccounts userAccounts(UserRepository repository, org.springframework.transaction.PlatformTransactionManager manager) {
        var core=new UserService(repository);
        var tx=new org.springframework.transaction.support.TransactionTemplate(manager);
        return new UserAccounts() {
            public java.util.Optional<UserView> findActiveByEmail(String email) { return core.findActiveByEmail(email); }
            public java.util.Optional<UserView> findActiveByUuid(java.util.UUID uuid) { return core.findActiveByUuid(uuid); }
            public UserView create(String name,String email,java.util.UUID country,java.util.UUID style) { return core.create(name,email,country,style); }
            public UserView update(java.util.UUID actor,ProfileInput input) {
                try { return tx.execute(status -> core.update(actor,input)); }
                catch(org.springframework.dao.DataIntegrityViolationException e) {
                    throw new com.kampive.api.module.user.domain.UserFailure(com.kampive.api.module.user.domain.UserFailure.Reason.INVALID_INPUT);
                }
            }
            public void delete(java.util.UUID actor) { tx.executeWithoutResult(status -> core.delete(actor)); }
        };
    }
}

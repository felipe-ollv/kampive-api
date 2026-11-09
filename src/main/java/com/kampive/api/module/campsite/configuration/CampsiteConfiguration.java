package com.kampive.api.module.campsite.configuration;

import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.kampive.api.module.campsite.application.port.in.Campsites;
import com.kampive.api.module.campsite.application.port.out.CampsiteRepository;
import com.kampive.api.module.campsite.application.usecase.CampsiteService;
import com.kampive.api.module.campsite.domain.CampsiteFailure;
import com.kampive.api.module.user.application.port.in.UserAccounts;

@Configuration(proxyBeanMethods=false)
public class CampsiteConfiguration {
    @Bean Campsites campsites(CampsiteRepository repository, UserAccounts users, PlatformTransactionManager manager) {
        var core=new CampsiteService(repository,users);
        var tx=new TransactionTemplate(manager);
        // Detail mapping loads related rows. Bind all JDBC reads to one connection
        // so concurrent detail requests cannot exhaust the pool waiting for a second one.
        var readTx=new TransactionTemplate(manager);
        readTx.setReadOnly(true);
        return new Campsites() {
            private <T> T write(Supplier<T> action) {
                try { return tx.execute(status -> action.get()); }
                catch (DataIntegrityViolationException exception) {
                    throw new CampsiteFailure(CampsiteFailure.Reason.INVALID_INPUT);
                }
            }
            public Catalog catalog() { return core.catalog(); }
            public Page<City> cities(Query q) { return core.cities(q); }
            public Page<Summary> search(Query q) { return core.search(q); }
            public Detail get(UUID uuid) { return readTx.execute(status -> core.get(uuid)); }
            public Page<Summary> mine(UUID actor,Query q) { return core.mine(actor,q); }
            public Detail owned(UUID actor,UUID uuid) { return readTx.execute(status -> core.owned(actor,uuid)); }
            public Submission register(UUID actor,Content c) { return write(() -> core.register(actor,c)); }
            public Submission suggest(UUID actor,SuggestionInput input) { return write(() -> core.suggest(actor,input)); }
            public Submission claim(UUID actor,UUID uuid,ClaimInput input) { return write(() -> core.claim(actor,uuid,input)); }
            public Detail update(UUID actor,UUID uuid,Content c) { return write(() -> core.update(actor,uuid,c)); }
            public Detail submit(UUID actor,UUID uuid) { return write(() -> core.submit(actor,uuid)); }
            public Detail archive(UUID actor,UUID uuid) { return write(() -> core.archive(actor,uuid)); }
        };
    }
}

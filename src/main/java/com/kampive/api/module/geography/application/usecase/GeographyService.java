package com.kampive.api.module.geography.application.usecase;

import java.util.List;
import java.util.UUID;
import com.kampive.api.module.geography.application.port.in.GeographyCatalog;
import com.kampive.api.module.geography.application.port.out.GeographyRepository;
import com.kampive.api.module.geography.domain.GeographyFailure;

public final class GeographyService implements GeographyCatalog {
    private final GeographyRepository repository;
    public GeographyService(GeographyRepository repository) { this.repository = repository; }
    public List<Continent> continents() { return List.copyOf(repository.continents()); }
    public CountryPage countries(CountryQuery query) {
        long total = repository.countryCount(query);
        var items = query.offset() >= total ? List.<Country>of() : repository.countries(query);
        return new CountryPage(items, query.page(), query.size(), total,
            total / query.size() + (total % query.size() == 0 ? 0 : 1));
    }
    public Country country(UUID uuid) {
        return repository.country(uuid).orElseThrow(() ->
            new GeographyFailure(GeographyFailure.Reason.COUNTRY_NOT_FOUND));
    }
}

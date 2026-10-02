package com.kampive.api.module.geography.application.port.in;

import java.util.List;
import java.util.UUID;
import com.kampive.api.module.geography.domain.GeographyFailure;

public interface GeographyCatalog {
    record Continent(UUID uuid, String name) {}
    record Country(UUID uuid, String name, String isoCode, String imageUrl,
                   UUID continentUuid, String continentName, long campsiteCount) {}
    record CountryQuery(UUID continentUuid, String query, int page, int size) {
        public CountryQuery {
            if (page < 0 || page > 10000 || size < 1 || size > 100 || (query != null && query.length() > 120)) {
                throw new GeographyFailure(GeographyFailure.Reason.INVALID_QUERY);
            }
            query = query == null ? "" : query.strip();
        }
        public long offset() { return (long) page * size; }
    }
    record CountryPage(List<Country> items, int page, int size, long totalElements, long totalPages) {
        public CountryPage { items = List.copyOf(items); }
    }
    List<Continent> continents();
    CountryPage countries(CountryQuery query);
    Country country(UUID uuid);
}

package com.kampive.api.module.geography.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.kampive.api.module.geography.application.port.in.GeographyCatalog.*;

public interface GeographyRepository {
    List<Continent> continents();
    List<Country> countries(CountryQuery query);
    long countryCount(CountryQuery query);
    Optional<Country> country(UUID uuid);
}

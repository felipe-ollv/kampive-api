package com.kampive.api.module.geography.adapter.in.web;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Schema;
import com.kampive.api.module.geography.application.port.in.GeographyCatalog;
import com.kampive.api.module.geography.application.port.in.GeographyCatalog.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Geography", description = "Public, read-only country and continent catalog")
public class GeographyController {
    private final GeographyCatalog catalog;
    public GeographyController(GeographyCatalog catalog) { this.catalog = catalog; }
    @GetMapping("/continents")
    @Operation(summary = "List continents", description = "Alphabetical order. Returns an empty list when the catalog is not provisioned.")
    public List<Continent> continents() { return catalog.continents(); }

    @GetMapping("/countries")
    @Operation(summary = "Search countries", description = "Alphabetical order with UUID tie-breaker. Only published campsites are counted. Unknown continent UUID returns an empty page.")
    public CountryPage countries(
            @RequestParam(required = false) UUID continentUuid,
            @Parameter(description = "Case-insensitive literal substring of country name or ISO code", schema = @Schema(maxLength = 120))
            @RequestParam(required = false) String query,
            @Parameter(schema = @Schema(minimum = "0", maximum = "10000"))
            @RequestParam(defaultValue = "0") int page,
            @Parameter(schema = @Schema(minimum = "1", maximum = "100"))
            @RequestParam(defaultValue = "20") int size) {
        return catalog.countries(new CountryQuery(continentUuid, query, page, size));
    }
    @GetMapping("/countries/{countryUuid}")
    @Operation(summary = "Get a country by UUID")
    public Country country(@PathVariable UUID countryUuid) { return catalog.country(countryUuid); }
}

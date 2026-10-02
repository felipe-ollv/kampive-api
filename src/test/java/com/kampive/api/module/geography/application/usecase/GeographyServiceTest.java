package com.kampive.api.module.geography.application.usecase;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.kampive.api.module.geography.application.port.in.GeographyCatalog.*;
import com.kampive.api.module.geography.application.port.out.GeographyRepository;
import com.kampive.api.module.geography.domain.GeographyFailure;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeographyServiceTest {
    GeographyRepository repository = mock(GeographyRepository.class);
    GeographyService service = new GeographyService(repository);
    @Test void validatesBoundsAndNormalizesSearch() {
        assertThat(new CountryQuery(null, "  Brazil  ", 0, 20).query()).isEqualTo("Brazil");
        assertThat(new CountryQuery(null, null, 0, 20).query()).isEmpty();
        for (int[] bounds : new int[][]{{-1,20},{10001,20},{0,0},{0,101}}) {
            assertThatThrownBy(() -> new CountryQuery(null,null,bounds[0],bounds[1]))
                .isInstanceOf(GeographyFailure.class);
        }
        assertThatThrownBy(() -> new CountryQuery(null,"x".repeat(121),0,20))
            .isInstanceOf(GeographyFailure.class);
    }
    @Test void returnsTotalPagesAndPreservesFilter() {
        var query = new CountryQuery(UUID.randomUUID(),"br",1,2);
        var country = new Country(UUID.randomUUID(),"Brazil","BR",null,query.continentUuid(),"South America",1);
        when(repository.countryCount(query)).thenReturn(3L);
        when(repository.countries(query)).thenReturn(List.of(country));
        var result = service.countries(query);
        assertThat(result.items()).containsExactly(country);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(3);
        verify(repository).countries(query);
    }
    @Test void outOfRangePageKeepsTotalWithoutLoadingItems() {
        var query = new CountryQuery(null,null,4,20);
        when(repository.countryCount(query)).thenReturn(3L);
        var result = service.countries(query);
        assertThat(result.items()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(3);
        verify(repository,never()).countries(any());
    }
    @Test void missingCountryRaisesNotFound() {
        UUID uuid = UUID.randomUUID();
        when(repository.country(uuid)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.country(uuid)).isInstanceOf(GeographyFailure.class)
            .hasMessage("COUNTRY_NOT_FOUND");
    }
}

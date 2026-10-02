package com.kampive.api.module.geography.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import com.kampive.api.module.geography.application.port.in.GeographyCatalog.*;
import com.kampive.api.module.geography.application.port.out.GeographyRepository;

@Repository
public class JdbcGeographyRepository implements GeographyRepository {
    private static final String SELECT_COUNTRY = """
        SELECT c.uuid_country, c.name, c.iso_code, c.image_url,
               co.uuid_continent, co.name AS continent_name,
               COALESCE(summary.campsite_count, 0) AS campsite_count
        FROM kampive.country c
        JOIN kampive.continent co ON co.uuid_continent = c.uuid_continent
        LEFT JOIN kampive.vw_country_campsite_summary summary ON summary.uuid_country = c.uuid_country
        """;
    private static final String FILTER = """
        WHERE (CAST(:continent AS uuid) IS NULL OR c.uuid_continent = :continent)
          AND (position(lower(:query) in lower(c.name)) > 0
               OR position(lower(:query) in lower(c.iso_code)) > 0)
        """;
    private final JdbcClient jdbc;
    public JdbcGeographyRepository(JdbcClient jdbc) { this.jdbc = jdbc; }
    public List<Continent> continents() {
        return jdbc.sql("SELECT uuid_continent, name FROM kampive.continent ORDER BY lower(name), uuid_continent")
            .query((rs, row) -> new Continent(rs.getObject("uuid_continent", UUID.class), rs.getString("name"))).list();
    }
    public List<Country> countries(CountryQuery query) {
        return filtered(SELECT_COUNTRY + FILTER + " ORDER BY lower(c.name), c.uuid_country LIMIT :limit OFFSET :offset", query)
            .param("limit", query.size()).param("offset", query.offset()).query(this::map).list();
    }
    public long countryCount(CountryQuery query) {
        return filtered("SELECT count(*) FROM kampive.country c " + FILTER, query).query(Long.class).single();
    }
    public Optional<Country> country(UUID uuid) {
        return jdbc.sql(SELECT_COUNTRY + " WHERE c.uuid_country = :uuid").param("uuid", uuid)
            .query(this::map).optional();
    }
    private JdbcClient.StatementSpec filtered(String sql, CountryQuery query) {
        // Explicit type lets PostgreSQL infer a nullable UUID parameter correctly.
        return jdbc.sql(sql).param("continent", query.continentUuid(), Types.OTHER).param("query", query.query());
    }
    private Country map(ResultSet rs, int row) throws SQLException {
        return new Country(rs.getObject("uuid_country", UUID.class), rs.getString("name"),
            rs.getString("iso_code"), rs.getString("image_url"), rs.getObject("uuid_continent", UUID.class),
            rs.getString("continent_name"), rs.getLong("campsite_count"));
    }
}

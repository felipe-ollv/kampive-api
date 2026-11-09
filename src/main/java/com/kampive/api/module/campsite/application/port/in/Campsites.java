package com.kampive.api.module.campsite.application.port.in;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.HashSet;
import java.util.UUID;
import com.kampive.api.module.campsite.domain.CampsiteFailure;
import static com.kampive.api.module.campsite.domain.CampsiteFailure.Reason.INVALID_INPUT;

public interface Campsites {
    record Option(UUID uuid, String name, String icon) {}
    record Currency(UUID uuid, String name, String isoCode, String symbol) {}
    record Catalog(List<Option> categories, List<Option> amenities, List<Option> settings,
                   List<Option> contactTypes, List<Currency> currencies) {}
    record City(UUID uuid, String name, UUID divisionUuid, String divisionName, UUID countryUuid, String countryName) {}
    record Page<T>(List<T> items, int page, int size, long totalElements, long totalPages) {
        public Page { items = List.copyOf(items); }
    }
    record Query(UUID countryUuid, UUID categoryUuid, UUID amenityUuid, UUID settingUuid,
                 String query, int page, int size, UUID cityUuid, UUID divisionUuid) {
        public Query(UUID countryUuid, UUID categoryUuid, UUID amenityUuid, UUID settingUuid,
                     String query, int page, int size, UUID cityUuid) {
            this(countryUuid,categoryUuid,amenityUuid,settingUuid,query,page,size,cityUuid,null);
        }
        public Query(UUID countryUuid, UUID categoryUuid, UUID amenityUuid, UUID settingUuid,
                     String query, int page, int size) {
            this(countryUuid,categoryUuid,amenityUuid,settingUuid,query,page,size,null);
        }
        public Query {
            if (page < 0 || page > 10000 || size < 1 || size > 50) invalid();
            query = text(query, 120, false);
        }
        public long offset() { return (long) page * size; }
    }
    record Photo(String url, String description) {
        public Photo { url = text(url, 2048, true); if (!url.matches("/api/v1/campsite-photos/[0-9a-f-]{36}\\.jpg")) https(url); description = text(description,250,false); }
    }
    record Contact(UUID typeUuid, String value) {
        public Contact {
            if (typeUuid == null) invalid();
            value = text(value,500,true);
            // Contacts are plain text; consumers must not interpret arbitrary values as executable URLs.
            if (value.chars().anyMatch(Character::isISOControl)) invalid();
        }
    }
    record Content(String name, String description, UUID categoryUuid, UUID cityUuid,
                   String streetAddress, String addressNumber, String addressComplement,
                   String neighborhood, String postalCode, String locationNotes,
                   BigDecimal latitude, BigDecimal longitude, BigDecimal altitudeMeters,
                   BigDecimal startingPricePerNight, UUID currencyUuid,
                   List<UUID> amenityUuids, List<UUID> settingUuids,
                   List<Contact> contacts, List<Photo> photos, String cityName) {
        public Content(String name, String description, UUID categoryUuid, UUID cityUuid,
                       String streetAddress, String addressNumber, String addressComplement,
                       String neighborhood, String postalCode, String locationNotes,
                       BigDecimal latitude, BigDecimal longitude, BigDecimal altitudeMeters,
                       BigDecimal startingPricePerNight, UUID currencyUuid,
                       List<UUID> amenityUuids, List<UUID> settingUuids,
                       List<Contact> contacts, List<Photo> photos) {
            this(name,description,categoryUuid,cityUuid,streetAddress,addressNumber,addressComplement,
                neighborhood,postalCode,locationNotes,latitude,longitude,altitudeMeters,
                startingPricePerNight,currencyUuid,amenityUuids,settingUuids,contacts,photos,"");
        }
        public Content {
            cityName = text(cityName,120,false);
            name = text(name,180,true);
            if (name.length() < 3 || categoryUuid == null) invalid();
            description = text(description,10000,false);
            streetAddress = text(streetAddress,200,false); addressNumber = text(addressNumber,30,false);
            addressComplement = text(addressComplement,150,false); neighborhood = text(neighborhood,120,false);
            postalCode = text(postalCode,20,false); locationNotes = text(locationNotes,2000,false);
            if ((latitude == null) != (longitude == null)) invalid();
            number(latitude,"-90","90",6); number(longitude,"-180","180",6);
            number(altitudeMeters,"-999999.99","999999.99",2);
            number(startingPricePerNight,"0","9999999999.99",2);
            if ((startingPricePerNight == null) != (currencyUuid == null)) invalid();
            amenityUuids = unique(amenityUuids,50); settingUuids = unique(settingUuids,20);
            contacts = unique(contacts,10); photos = unique(photos,9);
            if (contacts.stream().map(Contact::typeUuid).distinct().count() != contacts.size()
                || photos.stream().map(Photo::url).distinct().count() != photos.size()) invalid();
        }
        public boolean readyForReview() {
            return (cityUuid != null || !cityName.isEmpty()) && !streetAddress.isEmpty()
                && !description.isEmpty() && !contacts.isEmpty() && !photos.isEmpty();
        }
    }
    record Summary(UUID uuid, String name, String status, String categoryName, String cityName,
                   String countryName, UUID countryUuid, BigDecimal startingPricePerNight,
                   String currencyCode, String coverUrl, BigDecimal averageRating, long reviewCount,
                   boolean featured, boolean verified) {}
    record Detail(Summary summary, Content content) {}
    Catalog catalog();
    Page<City> cities(Query query);
    Page<Summary> search(Query query);
    Detail get(UUID uuid);
    Page<Summary> mine(UUID actor, Query query);
    Detail owned(UUID actor, UUID uuid);
    record SuggestionInput(String name, String instagramUrl, String googleMapsUrl) {
        public SuggestionInput {
            name = text(name,180,true);
            instagramUrl = text(instagramUrl,500,true);
            googleMapsUrl = text(googleMapsUrl,2000,true);
            if (name.length() < 3) invalid();
            suggestionUrl(instagramUrl,true); suggestionUrl(googleMapsUrl,false);
        }
    }
    private static void suggestionUrl(String value, boolean instagram) {
        https(value);
        URI uri = URI.create(value);
        if (uri.getPort() != -1 && uri.getPort() != 443) invalid();
        String host = uri.getHost().toLowerCase(java.util.Locale.ROOT), path = uri.getPath();
        boolean valid;
        if (instagram) {
            String profile = path.replaceAll("^/|/$", "");
            valid = List.of("instagram.com","www.instagram.com").contains(host)
                && path.matches("/[A-Za-z0-9._]{1,30}/?")
                && !List.of("p","reel","reels","stories","explore","accounts","direct").contains(profile.toLowerCase(java.util.Locale.ROOT));
        } else {
            valid = (host.equals("maps.app.goo.gl") && path.length()>1)
                || (host.equals("goo.gl") && path.startsWith("/maps/"))
                || (List.of("google.com","www.google.com","google.com.br","www.google.com.br").contains(host)
                    && (path.equals("/maps") || path.startsWith("/maps/")))
                || (List.of("maps.google.com","maps.google.com.br").contains(host)
                    && (path.length()>1 || (uri.getQuery()!=null && !uri.getQuery().isEmpty())));
        }
        if (!valid) invalid();
    }
    record ClaimInput(String contact, String evidence) {
        public ClaimInput { contact = text(contact,500,true); evidence = text(evidence,4000,true); }
    }
    record Submission(UUID uuid, String status) {}
    Submission register(UUID actor, Content content);
    Submission suggest(UUID actor, SuggestionInput input);
    Submission claim(UUID actor, UUID uuid, ClaimInput input);
    Detail update(UUID actor, UUID uuid, Content content);
    Detail submit(UUID actor, UUID uuid);
    Detail archive(UUID actor, UUID uuid);

    private static void invalid() { throw new CampsiteFailure(INVALID_INPUT); }
    private static String text(String value, int max, boolean required) {
        if (value != null && value.length() > max) invalid();
        String result = value == null ? "" : value.strip();
        if (required && result.isEmpty()) invalid();
        return result;
    }
    private static <T> List<T> unique(List<T> values, int max) {
        if (values == null) return List.of();
        if (values.size() > max || values.stream().anyMatch(java.util.Objects::isNull) || new HashSet<>(values).size() != values.size()) invalid();
        return List.copyOf(values);
    }
    private static void number(BigDecimal value, String min, String max, int scale) {
        if (value != null && (value.compareTo(new BigDecimal(min)) < 0 || value.compareTo(new BigDecimal(max)) > 0
            || value.stripTrailingZeros().scale() > scale)) invalid();
    }
    private static void https(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) invalid();
        } catch (IllegalArgumentException exception) { invalid(); }
    }
}

package com.kampive.api.module.review.application.port.in;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import com.kampive.api.module.campsite.application.port.in.Campsites.Page;
import com.kampive.api.module.campsite.application.port.in.Campsites.Option;
public interface Reviews {
    record Options(List<Option> highlights,List<Option> campingMethods) {}
    record Input(int rating,String text,UUID campingMethodUuid,List<UUID> highlightUuids,List<String> photos) {
        public Input {
            text=text==null?"":text.strip();
            highlightUuids=highlightUuids==null?List.of():List.copyOf(highlightUuids);
            photos=photos==null?List.of():List.copyOf(photos);
            if(rating<1 || rating>5 || text.length()<50 || text.length()>1000 || highlightUuids.size()>20
                || new java.util.HashSet<>(highlightUuids).size()!=highlightUuids.size() || photos.size()>3)
                throw new IllegalArgumentException("Invalid review");
        }
    }
    record Item(UUID uuid,UUID campsiteUuid,String campsiteName,String authorName,java.math.BigDecimal rating,String text,
                String campingMethod,String status,Instant date,List<String> highlights,List<String> photos) {}
    record Editable(UUID uuid,UUID campsiteUuid,String campsiteName,Input input) {}
    Editable get(UUID actor,UUID review);
    Receipt update(UUID actor,UUID review,Input input);
    void delete(UUID actor,UUID review);
    record Receipt(UUID uuid,String status) {}
    Options options();
    Page<Item> list(UUID campsite,int page,int size);
    Page<Item> mine(UUID actor,int page,int size);
    Receipt create(UUID actor,UUID campsite,Input input);
}

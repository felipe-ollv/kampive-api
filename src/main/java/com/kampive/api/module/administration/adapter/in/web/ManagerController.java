package com.kampive.api.module.administration.adapter.in.web;

import java.sql.Types;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.kampive.api.module.campsite.application.port.in.Campsites.Content;
import com.kampive.api.module.administration.adapter.out.persistence.ManagerQueries;

@RestController
@RequestMapping("/api/v1/admin")
public class ManagerController {
    public record Page(List<JsonNode> items,int page,long totalElements,long totalPages) {}
    public record Decision(@Min(0) long revision,boolean accept,@NotNull @Size(max=4000) String notes,
        @NotBlank @Size(max=4000) String response,UUID categoryUuid,UUID cityUuid,UUID existingUuid) {}
    public record Save(@Min(0) long revision,@NotNull Content content) {}
    public record Status(@Min(0) long revision,@NotBlank String status) {}
    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final TransactionTemplate transaction;
    public ManagerController(JdbcClient jdbc,JsonMapper json,PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.json=json;this.transaction=new TransactionTemplate(manager);
        this.transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    @GetMapping("/{collection:requests|campings|reviews|users|history}")
    public ResponseEntity<Page> list(@PathVariable String collection,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size,
        @RequestParam(defaultValue="") String query,@RequestParam(defaultValue="all") String status,
        @RequestParam(defaultValue="all") String kind,@RequestParam(defaultValue="") String id,
        @RequestParam(defaultValue="") String userId,@RequestParam(defaultValue="") String ownerId,
        @RequestParam(defaultValue="") String from,@RequestParam(defaultValue="") String to) {
        if(page<0 || size<1 || size>100 || query.length()>200 || id.length()>100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        try {
            if(!from.isEmpty()) java.time.LocalDate.parse(from);
            if(!to.isEmpty()) java.time.LocalDate.parse(to);
        } catch(java.time.format.DateTimeParseException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
        String projection=switch(collection) {
            case "requests" -> ManagerQueries.REQUESTS; case "campings" -> id.isEmpty() ? ManagerQueries.compactCamps() : ManagerQueries.CAMPS;
            case "history" -> ManagerQueries.HISTORIES; case "reviews" -> ManagerQueries.REVIEWS; case "users" -> ManagerQueries.USERS;
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        };
        // History and editable content are only needed when opening a specific record.
        projection=projection.replace(ManagerQueries.HISTORY,"'[]'::jsonb");
        String filter="""
            WHERE (:status='all' OR payload->>'status'=:status OR (:status='available' AND payload->>'status'<>'archived'))
            AND (:kind='all' OR payload->>'kind'=:kind) AND (:id='' OR payload->>'id'=:id)
            AND (:userId='' OR payload->>'userId'=:userId) AND (:ownerId='' OR payload->>'ownerId'=:ownerId)
            AND (:from='' OR left(payload->>'date',10)>=:from) AND (:to='' OR left(payload->>'date',10)<=:to)
            AND strpos(translate(lower(concat_ws(' ',payload->>'name',payload->>'city',payload->>'email',
                payload->>'id',payload->>'userName',payload->>'campName',payload->>'text')),
                'áàâãäéèêëíìîïóòôõöúùûüç','aaaaaeeeeiiiiooooouuuuc'),:query)>0
            """;
        String normalized=java.text.Normalizer.normalize(query.strip().toLowerCase(Locale.ROOT),java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","");
        Map<String,Object> parameters=Map.of("status",status,"kind",kind,"id",id,"userId",userId,"ownerId",ownerId,"from",from,"to",to,"query",normalized);
        String source=" FROM ("+projection+") results "+filter;
        Page result=transaction.execute(tx -> {
            long total=jdbc.sql("SELECT count(*)"+source).params(parameters).query(Long.class).single();
            long pages=(total+size-1)/size;
            int current=(int)Math.min(page,Math.max(0,pages-1));
            var items=jdbc.sql("SELECT payload::text"+source+" ORDER BY ordering,tie LIMIT :size OFFSET :offset")
                .params(parameters).param("size",size).param("offset",current*(long)size)
                .query((r,n)->json.readTree(r.getString(1))).list();
            return new Page(items,current,total,pages);
        });
        return ResponseEntity.ok().header("Cache-Control","no-store").body(result);
    }
    @PostMapping("/requests/{kind:suggestion|claim|registration}/{id}/decision")
    public ResponseEntity<Void> decide(Authentication auth,@PathVariable String kind,@PathVariable UUID id,@Valid @RequestBody Decision d) {
        jdbc.sql("SELECT kampive.manager_decide(:actor,:kind,:id,:revision,:accept,:notes,:response,:category,:city,:existing)")
            .param("actor",actor(auth)).param("kind",kind).param("id",id).param("revision",d.revision()).param("accept",d.accept())
            .param("notes",d.notes().strip()).param("response",d.response().strip()).param("category",d.categoryUuid(),Types.OTHER)
            .param("city",d.cityUuid(),Types.OTHER).param("existing",d.existingUuid(),Types.OTHER).query((r,n)->r.getObject(1)).list();
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/campings")
    public Map<String,UUID> create(Authentication auth,@Valid @RequestBody Save body) { return save(auth,null,body); }
    @PutMapping("/campings/{id}")
    public Map<String,UUID> save(Authentication auth,@PathVariable UUID id,@Valid @RequestBody Save body) {
        UUID saved=jdbc.sql("SELECT kampive.manager_save_camping(:actor,:id,:revision,CAST(:content AS jsonb))")
            .param("actor",actor(auth)).param("id",id,Types.OTHER).param("revision",body.revision())
            .param("content",json.writeValueAsString(body.content())).query(UUID.class).single();
        return Map.of("id",saved);
    }
    @PostMapping("/campings/{id}/status")
    public ResponseEntity<Void> campStatus(Authentication auth,@PathVariable UUID id,@Valid @RequestBody Status body) {
        jdbc.sql("SELECT kampive.manager_camping_status(:actor,:id,:revision,:status)").param("actor",actor(auth))
            .param("id",id).param("revision",body.revision()).param("status",body.status()).query((r,n)->r.getObject(1)).list();
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/reviews/{id}/status")
    public ResponseEntity<Void> reviewStatus(Authentication auth,@PathVariable UUID id,@Valid @RequestBody Status body) {
        jdbc.sql("SELECT kampive.manager_review(:actor,:id,:revision,:status)").param("actor",actor(auth))
            .param("id",id).param("revision",body.revision()).param("status",body.status()).query((r,n)->r.getObject(1)).list();
        return ResponseEntity.noContent().build();
    }
    private UUID actor(Authentication auth) { return UUID.fromString(auth.getName()); }
}

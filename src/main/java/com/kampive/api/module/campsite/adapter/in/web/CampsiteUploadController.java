package com.kampive.api.module.campsite.adapter.in.web;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;
import com.kampive.api.module.campsite.application.port.in.Campsites;
import com.kampive.api.module.campsite.application.port.in.Campsites.*;
import com.kampive.api.module.user.application.port.in.UserAccounts;
import com.kampive.api.module.campsite.domain.CampsiteFailure;
import static com.kampive.api.module.campsite.domain.CampsiteFailure.Reason.*;

@RestController
@RequestMapping("/api/v1")
public class CampsiteUploadController {
    private final Campsites campsites;
    private final UserAccounts users;
    private final JsonMapper json;
    private final JdbcClient jdbc;
    private final Path directory;
    public CampsiteUploadController(Campsites campsites,UserAccounts users,JsonMapper json,JdbcClient jdbc,
            @Value("${app.uploads.directory:/app/uploads}") String directory) {
        this.campsites=campsites; this.users=users; this.json=json; this.jdbc=jdbc; this.directory=Path.of(directory);
    }
    @PostMapping(value="/campsite-registrations",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Submission> register(Authentication auth,@RequestParam String content,@RequestParam String order,
            @RequestParam(value="photos",required=false) List<MultipartFile> photos) throws IOException {
        UUID actor=active(auth);
        List<Path> created=new ArrayList<>();
        try {
            Content body=prepare(content,order,photos,List.of(),created);
            return ResponseEntity.status(201).body(campsites.register(actor,body));
        } catch(Exception e) { cleanup(created); throw e; }
    }
    @PutMapping(value="/campsites/{uuid}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Detail update(Authentication auth,@PathVariable UUID uuid,@RequestParam String content,@RequestParam String order,
            @RequestParam(value="photos",required=false) List<MultipartFile> photos) throws IOException {
        UUID actor=active(auth);
        Detail previous=campsites.owned(actor,uuid);
        if(!List.of("draft","rejected").contains(previous.summary().status())) throw new CampsiteFailure(INVALID_STATE);
        List<Path> created=new ArrayList<>();
        try { return campsites.update(actor,uuid,prepare(content,order,photos,previous.content().photos(),created)); }
        catch(Exception e) { cleanup(created); throw e; }
    }
    @PostMapping(value="/admin/photos",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Photo administrativeUpload(@RequestParam MultipartFile photo) throws IOException {
        List<Path> created=new ArrayList<>();
        try { return new Photo(store(photo,created),""); }
        catch(Exception e) { cleanup(created); throw e; }
    }
    private Content prepare(String raw,String rawOrder,List<MultipartFile> incoming,List<Photo> previous,List<Path> created) throws IOException {
        if(raw.length()>100_000 || rawOrder.length()>1000) throw new CampsiteFailure(INVALID_INPUT);
        Content body; String[] order;
        try { body=json.readValue(raw,Content.class); order=json.readValue(rawOrder,String[].class); }
        catch(RuntimeException e) { throw new CampsiteFailure(INVALID_INPUT); }
        List<MultipartFile> files=incoming==null?List.of():incoming;
        if(order==null || files.size()>9 || order.length>9 || order.length!=files.size()+body.photos().size()
                || new HashSet<>(Arrays.asList(order)).size()!=order.length) throw new CampsiteFailure(INVALID_INPUT);
        if(!previous.containsAll(body.photos())) throw new CampsiteFailure(INVALID_INPUT);
        // Validate the order before writing any image.
        for(String ref:order) {
            if(ref==null || !ref.matches("(file|existing):[0-8]")) throw new CampsiteFailure(INVALID_INPUT);
            int index=Integer.parseInt(ref.substring(ref.indexOf(':')+1));
            if(index>=(ref.startsWith("file:")?files.size():body.photos().size())) throw new CampsiteFailure(INVALID_INPUT);
        }
        List<Photo> uploaded=new ArrayList<>();
        for(MultipartFile file:files) uploaded.add(new Photo(store(file,created),""));
        List<Photo> ordered=new ArrayList<>();
        for(String ref:order) {
            int index=Integer.parseInt(ref.substring(ref.indexOf(':')+1));
            ordered.add((ref.startsWith("file:")?uploaded:body.photos()).get(index));
        }
        return new Content(body.name(),body.description(),body.categoryUuid(),body.cityUuid(),body.streetAddress(),
            body.addressNumber(),body.addressComplement(),body.neighborhood(),body.postalCode(),body.locationNotes(),
            body.latitude(),body.longitude(),body.altitudeMeters(),body.startingPricePerNight(),body.currencyUuid(),
            body.amenityUuids(),body.settingUuids(),body.contacts(),ordered,body.cityName());
    }
    private String store(MultipartFile file,List<Path> created) throws IOException {
        if(file.isEmpty() || file.getSize()>10*1024*1024) throw new CampsiteFailure(INVALID_INPUT);
        try(var stream=file.getInputStream(); var input=ImageIO.createImageInputStream(stream)) {
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext()) throw new CampsiteFailure(INVALID_INPUT);
            var reader=readers.next();
            try {
                String format=reader.getFormatName();
                if(!format.equalsIgnoreCase("JPEG") && !format.equalsIgnoreCase("PNG")) throw new CampsiteFailure(INVALID_INPUT);
                reader.setInput(input);
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<1 || height<1 || (long)width*height>40_000_000) throw new CampsiteFailure(INVALID_INPUT);
                BufferedImage original=reader.read(0);
                double scale=Math.min(1d,1920d/Math.max(width,height));
                var image=new BufferedImage(Math.max(1,(int)(width*scale)),Math.max(1,(int)(height*scale)),BufferedImage.TYPE_INT_RGB);
                var g=image.createGraphics();
                try {
                    g.setColor(java.awt.Color.WHITE); g.fillRect(0,0,image.getWidth(),image.getHeight());
                    g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.drawImage(original,0,0,image.getWidth(),image.getHeight(),null);
                } finally { g.dispose(); }
                Files.createDirectories(directory);
                Path target=directory.resolve(UUID.randomUUID()+".jpg");
                created.add(target);
                var writer=ImageIO.getImageWritersByFormatName("jpeg").next();
                try(var fileOutput=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW);
                    var output=ImageIO.createImageOutputStream(fileOutput)) {
                    writer.setOutput(output);
                    var parameters=writer.getDefaultWriteParam();
                    parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT); parameters.setCompressionQuality(0.7f);
                    writer.write(null,new IIOImage(image,null,null),parameters);
                } finally { writer.dispose(); }
                return "/api/v1/campsite-photos/"+target.getFileName();
            } finally { reader.dispose(); }
        }
    }
    @GetMapping("/campsite-photos/{filename}")
    public ResponseEntity<org.springframework.core.io.Resource> photo(@PathVariable String filename,Authentication auth) {
        if(!filename.matches("[0-9a-f-]{36}\\.jpg")) throw new CampsiteFailure(NOT_FOUND);
        UUID actor=auth==null?null:UUID.fromString(auth.getName());
        if(actor!=null && users.findActiveByUuid(actor).isEmpty()) actor=null;
        boolean allowed=jdbc.sql("""
            SELECT EXISTS (SELECT 1 FROM kampive.administrator a JOIN kampive.app_user u USING(uuid_app_user) WHERE a.uuid_app_user=:actor AND a.revoked_at IS NULL AND u.deactivated_at IS NULL AND u.email_verified_at IS NOT NULL) OR EXISTS (SELECT 1 FROM kampive.campsite_photo p JOIN kampive.campsite c USING (uuid_campsite)
              WHERE p.file_url=:url AND (c.status='published' OR c.uuid_submitted_by=:actor OR
                (c.uuid_owner=:actor AND EXISTS (SELECT 1 FROM kampive.campsite_claim cl
                  WHERE cl.uuid_campsite=c.uuid_campsite AND cl.uuid_claimant=:actor AND cl.status='approved'))))
            """).param("url","/api/v1/campsite-photos/"+filename).param("actor",actor,java.sql.Types.OTHER).query(Boolean.class).single();
        Path file=directory.resolve(filename);
        if(!allowed || !Files.isRegularFile(file)) throw new CampsiteFailure(NOT_FOUND);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).header("X-Content-Type-Options","nosniff")
            .header("Cache-Control","private, no-store").body(new org.springframework.core.io.FileSystemResource(file));
    }
    private UUID active(Authentication auth) {
        UUID actor=UUID.fromString(auth.getName());
        if(users.findActiveByUuid(actor).isEmpty()) throw new CampsiteFailure(UNAUTHENTICATED);
        return actor;
    }
    private void cleanup(List<Path> paths) throws IOException { for(Path path:paths) Files.deleteIfExists(path); }
}

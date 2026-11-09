package com.kampive.api.module.review.adapter.in.web;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import com.kampive.api.module.review.application.port.in.Reviews;
import static org.junit.jupiter.api.Assertions.*;
class ReviewPhotoTest {
    @Test void inputBoundsAndDuplicateHighlights() {
        UUID uuid=UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,()->new Reviews.Input(6,"A".repeat(50),null,List.of(),List.of()));
        assertThrows(IllegalArgumentException.class,()->new Reviews.Input(5,"A".repeat(49),null,List.of(),List.of()));
        assertThrows(IllegalArgumentException.class,()->new Reviews.Input(5,"A".repeat(1001),null,List.of(),List.of()));
        assertThrows(IllegalArgumentException.class,()->new Reviews.Input(5,"A".repeat(50),null,List.of(uuid,uuid),List.of()));
        assertThrows(IllegalArgumentException.class,()->new Reviews.Input(5,"A".repeat(50),null,List.of(),Collections.nCopies(4,"x")));
    }
    @Test void reencodesRealImageAndRejectsInvalidData() throws Exception {
        var out=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(800,400,BufferedImage.TYPE_INT_RGB),"png",out);
        String result=ReviewPhoto.normalize("data:image/png;base64,"+Base64.getEncoder().encodeToString(out.toByteArray()));
        assertTrue(result.startsWith("data:image/jpeg;base64,"));
        var image=ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(result.split(",")[1])));
        assertEquals(512,image.getWidth()); assertEquals(256,image.getHeight());
        for(String invalid:List.of("https://example.test/photo","file:///tmp/photo","data:image/jpeg;base64,YWJj","data:image/jpeg;base64,"+"A".repeat(2_800_000)))
            assertThrows(org.springframework.web.server.ResponseStatusException.class,()->ReviewPhoto.normalize(invalid));
    }
}

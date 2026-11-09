package com.kampive.api.module.user.adapter.in.web;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.kampive.api.module.user.domain.UserFailure;
class ProfilePhotoTest {
    @Test void rejectsExternalChangesLocalFilesAndInvalidImages() {
        for(String value:java.util.List.of("https://example.test/photo.jpg","file:///tmp/photo","data:image/jpeg;base64,bm90LWEtcGhvdG8="))
            assertThrows(UserFailure.class,() -> ProfilePhoto.normalize(value,null));
        assertNull(ProfilePhoto.normalize(null,"https://example.test/photo.jpg"));
        assertEquals("https://example.test/photo.jpg",ProfilePhoto.normalize("https://example.test/photo.jpg","https://example.test/photo.jpg"));
    }
    @Test void persistsSmallNormalizedImage() throws Exception {
        var image=new java.awt.image.BufferedImage(1024,768,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var bytes=new java.io.ByteArrayOutputStream(); javax.imageio.ImageIO.write(image,"png",bytes);
        String result=ProfilePhoto.normalize("data:image/png;base64,"+java.util.Base64.getEncoder().encodeToString(bytes.toByteArray()),null);
        assertTrue(result.startsWith("data:image/jpeg;base64,"));
        var decoded=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(result.substring(result.indexOf(',')+1))));
        assertEquals(512,decoded.getWidth()); assertEquals(384,decoded.getHeight());
    }
}

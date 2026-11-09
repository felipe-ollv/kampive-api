package com.kampive.api.module.notification.application.usecase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.kampive.api.module.notification.application.port.out.EmailTemplateRepository;
import static org.junit.jupiter.api.Assertions.*;

class EmailTemplateServiceTest {
    Map<String,String> values(String code) {
        var values=new HashMap<>(Map.of("first_name","Felipe <&> \"Teste\"", "logo_url","https://example.test/logo.png", "support_url","https://example.test/support"));
        if(code.equals("password-reset")) values.putAll(Map.of("reset_url","https://example.test/reset?token=abc&source=email","expires_minutes","30"));
        if(code.equals("email-verification")) values.put("verify_url","https://example.test/verify?token=abc");
        return values;
    }
    EmailTemplateService service(String code) throws Exception {
        var template=new EmailTemplateRepository.Template(UUID.randomUUID(),1,"Kampive",Files.readString(Path.of("emails",code+".html")),Files.readString(Path.of("emails",code+".txt")));
        return new EmailTemplateService((c,l)->Optional.of(template));
    }
    @Test void rendersBothActualTemplatesWithEscapedHtmlAndPlainText() throws Exception {
        for(String code:List.of("password-reset","password-changed","email-verification")) {
            var rendered=service(code).render(code,values(code));
            assertTrue(rendered.html().contains("Felipe &lt;&amp;&gt; &quot;Teste&quot;"));
            assertTrue(rendered.text().contains("Felipe <&> \"Teste\""));
            assertFalse(rendered.html().contains("{{"));
            assertTrue(rendered.html().contains("max-width:560px"));
            assertTrue(rendered.html().contains("@media screen"));
            if(code.equals("password-reset")) {
                assertTrue(rendered.html().contains("token=abc&amp;source=email"));
                assertTrue(rendered.text().contains("token=abc&source=email"));
            }
        }
    }
    @Test void rejectsMissingExtraAndUnsafeParametersWithoutEchoingTokens() throws Exception {
        var service=service("password-reset");
        var input=values("password-reset");
        input.remove("first_name");
        assertThrows(IllegalArgumentException.class,()->service.render("password-reset",input));
        for(String url:List.of("javascript:alert(1)","http://example.test","https://user:secret@example.test","https://example.test/\r\nsecret")) {
            var unsafe=values("password-reset"); unsafe.put("reset_url",url);
            var error=assertThrows(IllegalArgumentException.class,()->service.render("password-reset",unsafe));
            assertFalse(error.getMessage().contains(url));
        }
        var extra=values("password-reset"); extra.put("password","secret");
        assertThrows(IllegalArgumentException.class,()->service.render("password-reset",extra));
    }
    @Test void doesNotEvaluateVariablesInsideUserData() throws Exception {
        var input=values("password-changed"); input.put("first_name","{{support_url}}");
        assertTrue(service("password-changed").render("password-changed",input).text().contains("Olá, {{support_url}}!"));
    }
    @Test void failsClosedForMissingTemplateOrUnknownPlaceholders() {
        var missing=new EmailTemplateService((c,l)->Optional.empty());
        assertThrows(IllegalStateException.class,()->missing.render("password-changed",values("password-changed")));
        var bad=new EmailTemplateService((c,l)->Optional.of(new EmailTemplateRepository.Template(UUID.randomUUID(),1,"Subject","{{password}}","Text")));
        assertThrows(IllegalStateException.class,()->bad.render("password-changed",values("password-changed")));
    }
}

package com.kampive.api.module.notification.application.usecase;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import com.kampive.api.module.notification.application.port.in.EmailTemplates;
import com.kampive.api.module.notification.application.port.out.EmailTemplateRepository;

public final class EmailTemplateService implements EmailTemplates {
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{([a-z_]+)}}");
    private final EmailTemplateRepository repository;
    public EmailTemplateService(EmailTemplateRepository repository) { this.repository=repository; }
    public Rendered render(String code, Map<String,String> variables) {
        Set<String> required = switch (code) {
            case "admin-request" -> Set.of("event_title","request_type","request_id","campsite_name");
            case "password-reset" -> Set.of("first_name","logo_url","support_url","reset_url","expires_minutes");
            case "email-verification" -> Set.of("first_name","logo_url","support_url","verify_url");
            case "password-changed" -> Set.of("first_name","logo_url","support_url");
            default -> throw new IllegalArgumentException("Unknown email template");
        };
        if (variables == null || !variables.keySet().equals(required)) invalid();
        // Snapshot the input and never include values (especially tokens) in errors or logs.
        for (String key : required) {
            String value = variables.get(key);
            if (value == null || value.isBlank() || value.length()>4096 || value.chars().anyMatch(Character::isISOControl)) invalid();
            if (key.endsWith("_url")) https(value);
        }
        Map<String,String> values = Map.copyOf(variables);
        if (values.containsKey("first_name") && values.get("first_name").length()>180) invalid();
        if (values.containsKey("expires_minutes") && !values.get("expires_minutes").matches("[1-9][0-9]{0,3}")) invalid();
        var template = repository.active(code,"pt-BR")
            .orElseThrow(()->new IllegalStateException("Active email template unavailable"));
        if (template.subject().contains("\r") || template.subject().contains("\n")) invalid();
        return new Rendered(template.uuid(),template.version(),replace(template.subject(),values,false),
            replace(template.html(),values,true),replace(template.text(),values,false));
    }
    private static String replace(String source,Map<String,String> values,boolean html) {
        var matcher=VARIABLE.matcher(source);
        var result=new StringBuilder();
        while (matcher.find()) {
            String value=values.get(matcher.group(1));
            if(value==null) throw new IllegalStateException("Unknown email template variable");
            matcher.appendReplacement(result,java.util.regex.Matcher.quoteReplacement(html?escape(value):value));
        }
        matcher.appendTail(result);
        // Validate the template, not substituted user text: no expressions or nested interpolation.
        String literal=VARIABLE.matcher(source).replaceAll("");
        if(literal.contains("{{") || literal.contains("}}")) throw new IllegalStateException("Invalid email template syntax");
        return result.toString();
    }
    private static String escape(String value) {
        return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
            .replace("\"","&quot;").replace("'","&#39;");
    }
    private static void https(String value) {
        try {
            URI uri=URI.create(value);
            if(!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null) invalid();
        } catch(IllegalArgumentException e) { invalid(); }
    }
    private static void invalid() { throw new IllegalArgumentException("Invalid email template parameters"); }
}

package com.kampive.api.shared.adapter.in.web.error;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.http.HttpServletResponse;

/** Fixed problem responses: no request, exception or credential data is reflected. */
public final class SecurityProblemWriter {
    private SecurityProblemWriter() {}

    public static void write(HttpServletResponse response, int status, String title)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
            "{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status + "}");
    }
}

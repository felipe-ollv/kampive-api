package com.kampive.api.shared.adapter.in.web.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {
    @Test
    void unexpectedErrorDoesNotExposeDetails() {
        var response = new ApiExceptionHandler().handleUnexpected(
            new IllegalStateException("password=secret; SELECT * FROM app_user"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getDetail()).isEqualTo("An unexpected error occurred.");
        assertThat(response.getBody().getProperties()).isNull();
    }

    @Test
    void malformedRequestDoesNotReflectInput() throws Exception {
        var error = new HttpMessageNotReadableException("sensitive submitted value",
            new MockHttpInputMessage(new byte[0]));
        var response = new ApiExceptionHandler().handleException(error,
            new ServletWebRequest(new MockHttpServletRequest()));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().toString()).doesNotContain("sensitive submitted value");
    }
}

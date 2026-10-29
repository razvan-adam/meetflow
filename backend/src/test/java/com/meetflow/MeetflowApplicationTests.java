package com.meetflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class MeetflowApplicationTests {

    @Autowired
    MockMvcTester mvc;

    @Test
    void healthCheckIsPublic() {
        assertThat(mvc.get().uri("/api/health"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("UP");
    }

    @Test
    void otherEndpointsRequireAuthentication() {
        assertThat(mvc.get().uri("/api/teams")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    // Spring forwards every error to /error; if that dispatch needed a login, anonymous users
    // would get 401 instead of the real status (e.g. a validation error on sign-up).
    @Test
    void errorsKeepTheirStatusForAnonymousUsers() {
        assertThat(mvc.get().uri("/error").with(request -> {
                    request.setDispatcherType(DispatcherType.ERROR);
                    request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 403);
                    return request;
                }))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() {
        assertThat(mvc.get().uri("/api/env").with(user("member"))).hasStatus(HttpStatus.NOT_FOUND);
    }
}

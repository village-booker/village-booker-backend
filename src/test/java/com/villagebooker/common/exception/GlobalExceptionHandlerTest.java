package com.villagebooker.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void notFoundReturns404WithMessageAndInstance() throws Exception {
        mvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Listing 5 not found"))
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.instance").value("/test/not-found"));
    }

    @Test
    void conflictReturns409() throws Exception {
        mvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Dates taken"))
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.params").doesNotExist());
    }

    @Test
    void paramsAreSentForTheTranslatedText() throws Exception {
        mvc.perform(get("/test/bad-request"))
                .andExpect(jsonPath("$.code").value("PASSWORD_TOO_LONG"))
                .andExpect(jsonPath("$.params.maxBytes").value(72));
    }

    @Test
    void badRequestAndForbiddenMapToTheirStatuses() throws Exception {
        mvc.perform(get("/test/bad-request")).andExpect(status().isBadRequest());
        mvc.perform(get("/test/forbidden")).andExpect(status().isForbidden());
    }

    @Test
    void optimisticLockReturns409() throws Exception {
        mvc.perform(get("/test/optimistic"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void dataIntegrityReturns409WithoutDatabaseDetails() throws Exception {
        mvc.perform(get("/test/integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(not(containsString("uk_app_user_email"))))
                .andExpect(jsonPath("$.code").value("DATA_CONFLICT"));
    }

    @Test
    void unexpectedExceptionReturns500WithoutInternalDetails() throws Exception {
        mvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Unexpected error."))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    @Test
    void validationReturns400WithFieldErrors() throws Exception {
        mvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\", \"email\": \"not-an-email\", \"nick\": \"a\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed."))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.name.code").value("NotBlank"))
                .andExpect(jsonPath("$.errors.name.params").doesNotExist())
                .andExpect(jsonPath("$.errors.email.code").value("Email"))
                .andExpect(jsonPath("$.errors.email.params").doesNotExist())
                .andExpect(jsonPath("$.errors.nick.code").value("Size"))
                .andExpect(jsonPath("$.errors.nick.params.min").value(2))
                .andExpect(jsonPath("$.errors.nick.params.max").value(5))
                .andExpect(jsonPath("$.instance").value("/test/validate"));
    }

    @Test
    void malformedJsonStays400() throws Exception {
        mvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/not-found")
        void notFound() {
            throw new NotFoundException(ErrorCode.USER_NOT_FOUND, "Listing 5 not found");
        }

        @GetMapping("/test/conflict")
        void conflict() {
            throw new ConflictException(ErrorCode.EMAIL_ALREADY_REGISTERED, "Dates taken");
        }

        @GetMapping("/test/bad-request")
        void badRequest() {
            throw new BadRequestException(ErrorCode.PASSWORD_TOO_LONG, "Too long", Map.of("maxBytes", 72));
        }

        @GetMapping("/test/forbidden")
        void forbidden() {
            throw new ForbiddenException(ErrorCode.ADMIN_CANNOT_BE_HOST, "Not your listing");
        }

        @GetMapping("/test/optimistic")
        void optimistic() {
            throw new ObjectOptimisticLockingFailureException("Booking", 1L);
        }

        @GetMapping("/test/integrity")
        void integrity() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint uk_app_user_email");
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException("secret internal detail");
        }

        @PostMapping("/test/validate")
        void validate(@Valid @RequestBody TestRequest body) {
        }
    }

    record TestRequest(@NotBlank String name, @Email String email, @Size(min = 2, max = 5) String nick) {
    }
}

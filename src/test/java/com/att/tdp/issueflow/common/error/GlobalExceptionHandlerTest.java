package com.att.tdp.issueflow.common.error;

import com.att.tdp.issueflow.common.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for GlobalExceptionHandler using standaloneSetup — no Spring context needed.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new ExceptionThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    // ── Tiny test controller ─────────────────────────────────────────────────

    @RestController
    @RequestMapping("/test-ex")
    static class ExceptionThrowingController {

        @GetMapping("/not-found")
        void notFound() { throw new NotFoundException("Entity not found"); }

        @GetMapping("/conflict")
        void conflict() { throw new ConflictException("Resource already exists"); }

        @GetMapping("/forbidden")
        void forbidden() { throw new ForbiddenException("Access denied"); }

        @GetMapping("/validation-error")
        void validation() { throw new ValidationException("Invalid input value"); }

        @GetMapping("/business-rule")
        void businessRule() { throw new BusinessRuleException("Cannot update a DONE ticket"); }

        @GetMapping("/optimistic-lock")
        void optimisticLock() {
            throw new ObjectOptimisticLockingFailureException("Ticket", 1L);
        }

        @GetMapping("/max-upload")
        void maxUpload() { throw new MaxUploadSizeExceededException(10_485_760L); }

        @GetMapping("/constraint-violation")
        void constraintViolation() {
            throw new ConstraintViolationException("violated", Set.of());
        }

        @GetMapping("/generic-error")
        void genericError() { throw new RuntimeException("Something unexpected"); }

        @PostMapping("/validated-body")
        void validatedBody(@Valid @RequestBody ValidatedBody body) {}

        @PostMapping("/json-body")
        void jsonBody(@RequestBody Map<String, Object> ignored) {}

        record ValidatedBody(@NotBlank String name, @NotNull Integer count) {}
    }

    // ── Domain exception tests ───────────────────────────────────────────────

    @Test
    void notFound_returns404_withCorrectShape() throws Exception {
        mockMvc.perform(get("/test-ex/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Entity not found"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/test-ex/not-found"));
    }

    @Test
    void conflict_returns409() throws Exception {
        mockMvc.perform(get("/test-ex/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Resource already exists"));
    }

    @Test
    void forbidden_returns403() throws Exception {
        mockMvc.perform(get("/test-ex/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void validationException_returns422() throws Exception {
        mockMvc.perform(get("/test-ex/validation-error"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value("Invalid input value"));
    }

    @Test
    void businessRuleException_returns422() throws Exception {
        mockMvc.perform(get("/test-ex/business-rule"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value("Cannot update a DONE ticket"));
    }

    // ── Concurrency ──────────────────────────────────────────────────────────

    @Test
    void optimisticLock_returns409_withStandardMessage() throws Exception {
        mockMvc.perform(get("/test-ex/optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message")
                        .value("Resource was modified by another user, please retry"));
    }

    // ── HTTP / multipart ─────────────────────────────────────────────────────

    @Test
    void maxUploadSize_returns413() throws Exception {
        mockMvc.perform(get("/test-ex/max-upload"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.message").value("File exceeds the maximum allowed upload size"));
    }

    // ── Validation ───────────────────────────────────────────────────────────

    @Test
    void constraintViolation_returns400() throws Exception {
        mockMvc.perform(get("/test-ex/constraint-violation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void methodArgumentNotValid_returns400_withFieldErrorsInDetails() throws Exception {
        mockMvc.perform(post("/test-ex/validated-body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/test-ex/json-body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not valid json }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Malformed JSON request"));
    }

    // ── Catch-all ────────────────────────────────────────────────────────────

    @Test
    void genericError_returns500_withCorrelationIdInMessage() throws Exception {
        mockMvc.perform(get("/test-ex/generic-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message",
                        startsWith("An unexpected error occurred. Reference:")));
    }
}

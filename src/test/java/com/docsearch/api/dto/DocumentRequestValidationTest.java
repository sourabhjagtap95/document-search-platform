package com.docsearch.api.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validation rules exercised directly against the Bean Validation engine — no Spring
 * context, so these are fast and independent of the web layer.
 */
class DocumentRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void openValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private static DocumentRequest valid() {
        return new DocumentRequest("Title", "Content", "Author", "search", List.of("tag"));
    }

    private static Set<String> fieldsInError(Object target) {
        return validator.validate(target).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    // ---------- DocumentRequest: everything required ----------

    @Test
    void acceptsAFullyPopulatedRequest() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void rejectsABlankTitle(String title) {
        DocumentRequest request = new DocumentRequest(title, "c", "a", "search", List.of());

        assertThat(fieldsInError(request)).contains("title");
    }

    @Test
    void reportsEveryMissingFieldAtOnceRatherThanTheFirst() {
        DocumentRequest request = new DocumentRequest(null, null, null, null, List.of());

        assertThat(fieldsInError(request))
                .containsExactlyInAnyOrder("title", "content", "author", "category");
    }

    @Test
    void rejectsAnOverlongTitle() {
        DocumentRequest request = new DocumentRequest(
                "x".repeat(201), "c", "a", "search", List.of());

        assertThat(fieldsInError(request)).contains("title");
    }

    @Test
    void acceptsATitleAtExactlyTheLimit() {
        DocumentRequest request = new DocumentRequest(
                "x".repeat(200), "c", "a", "search", List.of());

        assertThat(fieldsInError(request)).doesNotContain("title");
    }

    @ParameterizedTest
    @ValueSource(strings = {"search", "data-engineering", "machine_learning", "Level 3"})
    void acceptsReasonableCategories(String category) {
        DocumentRequest request = new DocumentRequest("t", "c", "a", category, List.of());

        assertThat(fieldsInError(request)).doesNotContain("category");
    }

    @ParameterizedTest
    @ValueSource(strings = {"search/engine", "a,b", "drop;table", "<script>", "emoji-🎉"})
    void rejectsCategoriesWithUnexpectedCharacters(String category) {
        DocumentRequest request = new DocumentRequest("t", "c", "a", category, List.of());

        assertThat(fieldsInError(request)).contains("category");
    }

    @Test
    void rejectsMoreThanTwentyTags() {
        List<String> tags = Collections.nCopies(21, "tag");
        DocumentRequest request = new DocumentRequest("t", "c", "a", "search", tags);

        assertThat(fieldsInError(request)).contains("tags");
    }

    @Test
    void rejectsABlankTagInsideAnOtherwiseValidList() {
        DocumentRequest request = new DocumentRequest(
                "t", "c", "a", "search", List.of("ok", " "));

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void allowsAnAbsentTagListEntirely() {
        DocumentRequest request = new DocumentRequest("t", "c", "a", "search", null);

        assertThat(validator.validate(request)).isEmpty();
    }

    // ---------- DocumentPatchRequest: everything optional ----------

    @Test
    void anEmptyPatchIsValidBecauseEveryFieldIsOptional() {
        // The reason DocumentPatchRequest exists: @NotBlank fails on null, so reusing
        // DocumentRequest here would reject every field a caller deliberately omitted.
        DocumentPatchRequest patch = new DocumentPatchRequest(null, null, null, null, null);

        assertThat(validator.validate(patch)).isEmpty();
    }

    @Test
    void aPatchChangingOnlyTheTitleIsValid() {
        DocumentPatchRequest patch = new DocumentPatchRequest(
                "New title", null, null, null, null);

        assertThat(validator.validate(patch)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t"})
    void aSuppliedButBlankFieldIsStillRejected(String blank) {
        DocumentPatchRequest patch = new DocumentPatchRequest(blank, null, null, null, null);

        assertThat(fieldsInError(patch)).contains("title");
    }

    @Test
    void patchStillEnforcesLengthLimits() {
        DocumentPatchRequest patch = new DocumentPatchRequest(
                "x".repeat(201), null, null, null, null);

        assertThat(fieldsInError(patch)).contains("title");
    }

    @Test
    void patchStillEnforcesTheCategoryCharacterSet() {
        DocumentPatchRequest patch = new DocumentPatchRequest(
                null, null, null, "not/allowed", null);

        assertThat(fieldsInError(patch)).contains("category");
    }

    @Test
    void violationMessagesNameTheFieldSoTheApiResponseIsActionable() {
        Set<ConstraintViolation<DocumentRequest>> violations =
                validator.validate(new DocumentRequest(null, "c", "a", "search", List.of()));

        assertThat(violations).singleElement()
                .satisfies(violation -> assertThat(violation.getMessage()).isEqualTo("title is required"));
    }
}

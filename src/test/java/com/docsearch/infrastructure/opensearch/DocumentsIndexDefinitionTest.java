package com.docsearch.infrastructure.opensearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the mapping decisions in place.
 *
 * <p>These read as pedantic until someone "tidies up" the index definition and a
 * dashboard starts reporting wrong counts without any error being raised. Every
 * assertion below encodes a decision that is expensive to get wrong and invisible
 * when it is.
 */
class DocumentsIndexDefinitionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode definition() throws IOException {
        try (InputStream in = new ClassPathResource("opensearch/documents-index.json").getInputStream()) {
            return MAPPER.readTree(in);
        }
    }

    private static JsonNode field(String name) throws IOException {
        return definition().path("mappings").path("properties").path(name);
    }

    @Test
    void isValidJsonWithSettingsAndMappings() throws IOException {
        JsonNode root = definition();

        assertThat(root.has("settings")).isTrue();
        assertThat(root.has("mappings")).isTrue();
    }

    @Test
    void rejectsUnknownFieldsInsteadOfInventingMappingsForThem() throws IOException {
        // dynamic=strict makes an unmapped field a loud 400 at index time rather than a
        // silently auto-typed field that nobody chose.
        assertThat(definition().path("mappings").path("dynamic").asText()).isEqualTo("strict");
    }

    @ParameterizedTest
    @CsvSource({
            "title,     text",
            "content,   text",
            "author,    keyword",
            "category,  keyword",
            "tags,      keyword",
            "createdAt, date",
            "updatedAt, date",
            "id,        keyword"
    })
    void mapsEachFieldToItsIntendedType(String name, String expectedType) throws IOException {
        assertThat(field(name).path("type").asText()).isEqualTo(expectedType);
    }

    @Test
    void categoryAndTagsAreKeywordSoFilteringAndAggregationAreExact() throws IOException {
        // Dynamic mapping made these analyzed text, which is why `term` on them returned
        // nothing and aggregations would have counted word fragments.
        for (String name : List.of("category", "tags")) {
            assertThat(field(name).path("type").asText())
                    .as("%s must be keyword", name)
                    .isEqualTo("keyword");
            assertThat(field(name).has("fields"))
                    .as("%s needs no sub-field; it is never full-text searched", name)
                    .isFalse();
        }
    }

    @Test
    void categoryAndTagsNormaliseCaseSoFiltersAreNotCaseSensitive() throws IOException {
        for (String name : List.of("category", "tags")) {
            assertThat(field(name).path("normalizer").asText())
                    .as("%s should normalise case", name)
                    .isEqualTo("lowercase_exact");
        }
    }

    @Test
    void contentHasNoKeywordSubFieldBecauseNothingWouldUseIt() throws IOException {
        // Dynamic mapping gave content a .keyword twin that silently drops any value over
        // 256 characters — pure index bloat for a field nobody filters or sorts on.
        assertThat(field("content").has("fields")).isFalse();
    }

    @Test
    void titleKeepsAKeywordSubFieldBecauseItIsSortedOn() throws IOException {
        assertThat(field("title").path("fields").path("keyword").path("type").asText())
                .isEqualTo("keyword");
        assertThat(field("title").path("fields").path("keyword").path("ignore_above").asInt())
                .isEqualTo(256);
    }

    @Test
    void authorIsExactByDefaultWithATextSubFieldForNameSearches() throws IOException {
        // Grouping authors must be exact, but "find documents by Nair" should still work.
        assertThat(field("author").path("type").asText()).isEqualTo("keyword");
        assertThat(field("author").path("fields").path("text").path("type").asText()).isEqualTo("text");
    }

    @Test
    void textFieldsUseTheCustomAnalyzerRatherThanTheDefault() throws IOException {
        assertThat(field("title").path("analyzer").asText()).isEqualTo("document_text");
        assertThat(field("content").path("analyzer").asText()).isEqualTo("document_text");
    }

    @Test
    void theCustomAnalyzerChainIsOrderedSoEachFilterSeesWhatItNeeds() throws IOException {
        JsonNode analyzer = definition().path("settings").path("analysis")
                .path("analyzer").path("document_text");

        assertThat(analyzer.path("tokenizer").asText()).isEqualTo("standard");

        List<String> filters = MAPPER.convertValue(analyzer.path("filter"), List.class);
        assertThat(filters).containsExactly(
                "split_camel_case", "flatten_graph", "apostrophe",
                "lowercase", "english_stop", "english_stemmer");

        // split_camel_case must run before lowercase: it splits on case changes, so
        // lowercasing first would destroy the only signal it has.
        assertThat(filters.indexOf("split_camel_case")).isLessThan(filters.indexOf("lowercase"));
        // flatten_graph must follow the graph-producing filter for index-time use.
        assertThat(filters.indexOf("flatten_graph")).isEqualTo(filters.indexOf("split_camel_case") + 1);
    }

    @Test
    void stripsApostrophesLeftBehindByThePossessiveSplit() throws IOException {
        // word_delimiter_graph strips the s from "OpenSearch's" in the split parts, but
        // preserve_original keeps "OpenSearch'" complete with the apostrophe — so the
        // exact product name would not match. The apostrophe filter cleans that up, and
        // must run after the split that produces it.
        List<String> filters = MAPPER.convertValue(
                definition().path("settings").path("analysis")
                        .path("analyzer").path("document_text").path("filter"),
                List.class);

        assertThat(filters).contains("apostrophe");
        assertThat(filters.indexOf("apostrophe")).isGreaterThan(filters.indexOf("flatten_graph"));
    }

    @Test
    void splitsOnCaseChangeAndKeepsTheOriginalToken() throws IOException {
        JsonNode filter = definition().path("settings").path("analysis")
                .path("filter").path("split_camel_case");

        assertThat(filter.path("type").asText()).isEqualTo("word_delimiter_graph");
        assertThat(filter.path("split_on_case_change").asBoolean()).isTrue();
        // Without preserve_original, "OpenSearch" would be stored only as open + search,
        // so searching the exact product name would stop working.
        assertThat(filter.path("preserve_original").asBoolean()).isTrue();
    }

    @Test
    void staysSingleShardWithNoReplicaSoASingleNodeClusterIsGreen() throws IOException {
        JsonNode index = definition().path("settings").path("index");

        assertThat(index.path("number_of_shards").asInt()).isEqualTo(1);
        assertThat(index.path("number_of_replicas").asInt()).isZero();
    }
}

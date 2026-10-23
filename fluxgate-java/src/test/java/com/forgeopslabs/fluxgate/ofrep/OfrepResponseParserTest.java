package com.forgeopslabs.fluxgate.ofrep;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgeopslabs.fluxgate.ErrorCode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.forgeopslabs.fluxgate.EdgeContract.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OfrepResponseParserTest {

    private final OfrepResponseParser parser = new OfrepResponseParser(new ObjectMapper());

    private static byte[] bytes(String body) {
        return body.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void parsesSuccessObjectWithMetadata() {
        OfrepFlagResult result = parser.parseFlag(bytes(fixture("flag-string.json")), "requested");

        assertThat(result.key()).isEqualTo("banner-color");
        assertThat(result.value().asText()).isEqualTo("blue");
        assertThat(result.reason()).isEqualTo("SPLIT");
        assertThat(result.variant()).isEqualTo("blue");
        assertThat(result.metadata()).isEqualTo(Map.of("experiment", "banner-2026"));
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void fillsMissingKeyAndReasonAndTreatsJsonNullAsNoValue() {
        OfrepFlagResult result = parser.parseFlag(bytes("{\"value\": null}"), "requested");

        assertThat(result.key()).isEqualTo("requested");
        assertThat(result.value()).isNull();
        assertThat(result.reason()).isEqualTo("UNKNOWN");
        assertThat(result.metadata()).isEmpty();
    }

    @Test
    void unknownWireErrorCodeInBulkEntryIsGeneral() {
        OfrepBulkResult result = parser.parseBulk(bytes(
                "{\"flags\": [{\"key\": \"f\", \"errorCode\": \"PARSE_ERROR\", \"errorDetails\": \"bad rule\"}]}"),
                "etag-1");

        OfrepFlagResult flag = result.flags().get(0);
        assertThat(flag.errorCode()).isEqualTo(ErrorCode.GENERAL);
        assertThat(flag.reason()).isEqualTo("ERROR");
        assertThat(flag.errorDetails()).isEqualTo("bad rule");
        assertThat(result.etag()).isEqualTo("etag-1");
        assertThat(result.notModified()).isFalse();
    }

    @Test
    void rejectsNonObjectBodiesAndEntries() {
        assertThatThrownBy(() -> parser.parseFlag(bytes("[]"), "f"))
                .isInstanceOfSatisfying(OfrepException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GENERAL);
                    assertThat(e.isRetryable()).isFalse();
                });
        assertThatThrownBy(() -> parser.parseBulk(bytes("{\"flags\": [true]}"), null))
                .isInstanceOf(OfrepException.class)
                .hasMessageContaining("not an object");
    }

    @Test
    void errorResponseKeepsStatusAndOfrepDetails() {
        OfrepException e = parser.error(404, bytes(fixture("error-flag-not-found.json")));

        assertThat(e.getStatus()).isEqualTo(404);
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FLAG_NOT_FOUND);
        assertThat(e.isRetryable()).isFalse();
        assertThat(e.getMessage()).isEqualTo("Edge returned HTTP 404: The requested feature flag does not exist");
    }

    @Test
    void errorResponseWithEmptyOrNonObjectBody() {
        assertThat(parser.error(504, new byte[0]).getMessage()).isEqualTo("Edge returned HTTP 504");
        assertThat(parser.error(504, new byte[0]).isRetryable()).isTrue();
        assertThat(parser.error(400, bytes("\"oops\"")).getErrorCode()).isEqualTo(ErrorCode.INVALID_CONTEXT);
    }
}

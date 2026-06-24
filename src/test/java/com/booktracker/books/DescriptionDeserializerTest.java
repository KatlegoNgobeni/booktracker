package com.booktracker.books;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Unit tests for {@link DescriptionDeserializer} — the custom Jackson deserializer
 * that handles Open Library's polymorphic {@code description} field.
 *
 * <p>The field can be:
 * <ol>
 *   <li>A plain string: {@code "description": "Some text..."}</li>
 *   <li>An object with a {@code value} key: {@code "description": {"type": "/type/text", "value": "Some text..."}}</li>
 *   <li>Absent (null after deserialization)</li>
 *   <li>An unrecognized shape (e.g. a JSON array) — should produce null, not throw</li>
 * </ol>
 *
 * <p>No Spring context needed — pure Jackson deserialization with ObjectMapper.
 *
 * <p>BOOK-03 acceptance criteria covered here.
 */
class DescriptionDeserializerTest {

    /** Minimal DTO that applies the deserializer to its description field. */
    static class TestDto {
        @JsonDeserialize(using = DescriptionDeserializer.class)
        public String description;
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * BOOK-03: A plain-string description deserializes to that string value.
     */
    @Test
    void plainString_deserializesToString() throws Exception {
        String json = """
                {
                  "description": "A story about a fox"
                }
                """;

        TestDto dto = objectMapper.readValue(json, TestDto.class);

        assertThat(dto.description).isEqualTo("A story about a fox");
    }

    /**
     * BOOK-03: An object-form description ({"type":"/type/text","value":"X"}) deserializes to "X".
     */
    @Test
    void objectForm_deserializesToValueString() throws Exception {
        String json = """
                {
                  "description": {"type": "/type/text", "value": "A story about a fox"}
                }
                """;

        TestDto dto = objectMapper.readValue(json, TestDto.class);

        assertThat(dto.description).isEqualTo("A story about a fox");
    }

    /**
     * BOOK-03: An absent description field deserializes to null (no throw).
     */
    @Test
    void absentDescription_deserializesToNull() throws Exception {
        String json = "{}";

        TestDto dto = objectMapper.readValue(json, TestDto.class);

        assertThat(dto.description).isNull();
    }

    /**
     * BOOK-03: An unrecognized shape (JSON array) deserializes to null without throwing.
     */
    @Test
    void unknownShape_deserializesToNullWithoutThrowing() {
        String json = """
                {
                  "description": ["some", "array"]
                }
                """;

        assertThatNoException().isThrownBy(() -> {
            TestDto dto = objectMapper.readValue(json, TestDto.class);
            assertThat(dto.description).isNull();
        });
    }
}

package com.booktracker.books;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

/**
 * Custom Jackson deserializer for Open Library's polymorphic {@code description} field.
 *
 * <p>Open Library's work detail endpoint returns the description field in two possible shapes:
 * <ul>
 *   <li>Plain string: {@code "description": "Some text..."}</li>
 *   <li>Object form: {@code "description": {"type": "/type/text", "value": "Some text..."}}</li>
 * </ul>
 *
 * <p>Unknown shapes (arrays, nested objects without a {@code value} key, etc.) are
 * treated as absent — this method returns {@code null} rather than throwing, ensuring
 * defensive handling of BOOK-03 quirks.
 *
 * <p>Annotate the target field with {@code @JsonDeserialize(using = DescriptionDeserializer.class)}.
 */
public class DescriptionDeserializer extends StdDeserializer<String> {

    public DescriptionDeserializer() {
        super(String.class);
    }

    @Override
    public String deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        JsonNode node = jp.getCodec().readTree(jp);
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isObject() && node.has("value")) {
            return node.get("value").asText();
        }
        return null;
    }
}

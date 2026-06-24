package com.booktracker.books;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

/**
 * Jackson DTO for Open Library's {@code GET /works/{olKey}.json} response.
 *
 * <p>Annotated with {@code @JsonIgnoreProperties(ignoreUnknown = true)} so that
 * additional fields in the Open Library response (e.g. {@code authors} objects,
 * {@code subjects}, {@code links}) are safely ignored without throwing (BOOK-03).
 *
 * <p>The {@code description} field uses {@link DescriptionDeserializer} to handle
 * Open Library's polymorphic description shape — either a plain string or an
 * {@code {"type": "/type/text", "value": "..."}} object (BOOK-03).
 *
 * <p>Authors are intentionally not mapped here. The works endpoint returns authors
 * as an array of objects ({@code [{"author":{"key":"/authors/OL123A"}}]}), not plain
 * name strings. Resolving author names would require additional API calls to the
 * authors endpoint — out of scope for Phase 3. The {@code authors} column in the
 * {@code books} table is nullable and will be stored as null on the detail write path.
 *
 * <p>Column mapping for persistence (via {@link BookEntity}):
 * <ul>
 *   <li>{@code key}             → {@link BookEntity#openLibraryKey} (stored as-is from search)</li>
 *   <li>{@code title}           → {@link BookEntity#title}</li>
 *   <li>{@code description}     → not persisted (detail-display only)</li>
 *   <li>{@code number_of_pages} → {@link BookEntity#pageCount} (nullable)</li>
 *   <li>{@code covers[0]}       → {@link BookEntity#coverId} as {@code String.valueOf()} (nullable)</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenLibraryWorkResponse {

    @JsonProperty("key")
    private String key;

    @JsonProperty("title")
    private String title;

    @JsonProperty("description")
    @JsonDeserialize(using = DescriptionDeserializer.class)
    private String description;

    /** Maps {@code number_of_pages} from the work response — nullable (frequently absent). */
    @JsonProperty("number_of_pages")
    private Integer pageCount;

    /**
     * Cover image IDs from the work response — nullable (absent for many works).
     * Only the first element is used as {@code coverId} in the entity.
     */
    @JsonProperty("covers")
    private List<Integer> covers;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public void setPageCount(Integer pageCount) {
        this.pageCount = pageCount;
    }

    public List<Integer> getCovers() {
        return covers;
    }

    public void setCovers(List<Integer> covers) {
        this.covers = covers;
    }
}

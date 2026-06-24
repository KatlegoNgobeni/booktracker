package com.booktracker.books;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Jackson DTO for the Open Library {@code /search.json} response envelope.
 *
 * <p>Plain class with a no-arg constructor (Jackson requirement — not a record).
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} ensures that any extra
 * fields Open Library adds in the future do not break deserialization (T-03-03).
 *
 * <p>Only the fields needed for {@code BookSearchResultDto} are mapped here;
 * all other response fields are silently ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenLibrarySearchResponse {

    @JsonProperty("numFound")
    private Integer numFound;

    @JsonProperty("docs")
    private List<SearchDoc> docs;

    public OpenLibrarySearchResponse() {}

    public Integer getNumFound() {
        return numFound;
    }

    public void setNumFound(Integer numFound) {
        this.numFound = numFound;
    }

    public List<SearchDoc> getDocs() {
        return docs;
    }

    public void setDocs(List<SearchDoc> docs) {
        this.docs = docs;
    }

    /**
     * Represents a single book document in the Open Library search response.
     *
     * <p>All fields are nullable — Open Library omits fields for older or
     * incomplete records (CLAUDE.md §Open Library API — Known Quirks).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SearchDoc {

        @JsonProperty("key")
        private String key;

        @JsonProperty("title")
        private String title;

        @JsonProperty("author_name")
        private List<String> authorName;

        @JsonProperty("cover_i")
        private Integer coverI;

        @JsonProperty("first_publish_year")
        private Integer firstPublishYear;

        public SearchDoc() {}

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

        public List<String> getAuthorName() {
            return authorName;
        }

        public void setAuthorName(List<String> authorName) {
            this.authorName = authorName;
        }

        public Integer getCoverI() {
            return coverI;
        }

        public void setCoverI(Integer coverI) {
            this.coverI = coverI;
        }

        public Integer getFirstPublishYear() {
            return firstPublishYear;
        }

        public void setFirstPublishYear(Integer firstPublishYear) {
            this.firstPublishYear = firstPublishYear;
        }
    }
}

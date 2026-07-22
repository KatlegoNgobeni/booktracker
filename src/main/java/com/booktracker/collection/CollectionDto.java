package com.booktracker.collection;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Summary DTO for a collection (no book list — just metadata + count).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CollectionDto {

    private String id;
    private String name;
    private boolean isPublic;
    private int bookCount;
    private String createdAt;

    public CollectionDto() {}

    public CollectionDto(String id, String name, boolean isPublic, int bookCount, String createdAt) {
        this.id = id;
        this.name = name;
        this.isPublic = isPublic;
        this.bookCount = bookCount;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }

    public int getBookCount() { return bookCount; }
    public void setBookCount(int bookCount) { this.bookCount = bookCount; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}

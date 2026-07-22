package com.booktracker.collection;

import java.util.List;

/**
 * Detail DTO for a collection, including the list of ol_keys.
 */
public class CollectionDetailDto {

    private String id;
    private String name;
    private boolean isPublic;
    private List<String> olKeys;
    private String createdAt;

    public CollectionDetailDto() {}

    public CollectionDetailDto(String id, String name, boolean isPublic, List<String> olKeys, String createdAt) {
        this.id = id;
        this.name = name;
        this.isPublic = isPublic;
        this.olKeys = olKeys;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }

    public List<String> getOlKeys() { return olKeys; }
    public void setOlKeys(List<String> olKeys) { this.olKeys = olKeys; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}

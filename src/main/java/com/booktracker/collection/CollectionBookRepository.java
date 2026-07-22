package com.booktracker.collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CollectionBookRepository extends JpaRepository<CollectionBookEntity, CollectionBookId> {

    List<CollectionBookEntity> findAllByIdCollectionId(UUID collectionId);

    @Modifying
    @Query("DELETE FROM CollectionBookEntity cb WHERE cb.id.collectionId = :collectionId AND cb.id.bookOlKey = :bookOlKey")
    int deleteByIdCollectionIdAndIdBookOlKey(@Param("collectionId") UUID collectionId,
                                             @Param("bookOlKey") String bookOlKey);
}

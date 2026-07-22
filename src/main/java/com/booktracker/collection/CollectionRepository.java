package com.booktracker.collection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollectionRepository extends JpaRepository<CollectionEntity, UUID> {

    List<CollectionEntity> findAllByUserId(UUID userId);

    Optional<CollectionEntity> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndName(UUID userId, String name);
}

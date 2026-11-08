package com.devteria.identityservice.repository;

import com.devteria.identityservice.entity.SongEmbedding;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SongEmbeddingRepository extends JpaRepository<SongEmbedding, String> {

    List<SongEmbedding> findByNormalizedTitleContainingIgnoreCase(String keyword);

    boolean existsBySongId(String songId);

    Optional<SongEmbedding> findBySongId(String songId);

    @Query("SELECT se FROM SongEmbedding se WHERE se.normalizedTitle LIKE %:keyword% OR se.searchText LIKE %:keyword%")
    List<SongEmbedding> searchByKeyword(@Param("keyword") String keyword);

    @EntityGraph(attributePaths = {"song"})
    @Query("SELECT se FROM SongEmbedding se")
    List<SongEmbedding> findAllWithSong();
}

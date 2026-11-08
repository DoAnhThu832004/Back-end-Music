package com.devteria.identityservice.repository;

import com.devteria.identityservice.entity.Genre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import com.devteria.identityservice.dto.response.GenrePlayCountResponse;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

@Repository
public interface GenreRepository extends JpaRepository<Genre,String> {
    Optional<Genre> findByName(String name);
    Optional<Genre> findByKeyG(String keyG);

    @Query("SELECT new com.devteria.identityservice.dto.response.GenrePlayCountResponse(g.id, g.name, COALESCE(SUM(s.playCount), 0)) " +
           "FROM Genre g LEFT JOIN g.songs s GROUP BY g.id, g.name ORDER BY COALESCE(SUM(s.playCount), 0) DESC")
    List<GenrePlayCountResponse> getGenrePlayCounts();
}

package com.devteria.identityservice.repository;

import com.devteria.identityservice.entity.Artist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArtistRepository extends JpaRepository<Artist, String> {
    Optional<Artist> findByName(String name);
    List<Artist> findByNameContainingIgnoreCase(String keyword);
    Page<Artist> findByNameContainingIgnoreCase(String keyword, Pageable pageable);

    /**
     * Lấy Top N nghệ sĩ nổi bật nhất của một thể loại nhạc,
     * xếp hạng theo tổng lượt nghe (playCount) của tất cả bài hát thuộc thể loại đó.
     * Dùng cho luồng Cold Start khi user đã chọn preferredGenres.
     */
    @Query("SELECT a FROM Artist a JOIN a.song s JOIN s.genres g " +
           "WHERE g.id = :genreId " +
           "GROUP BY a " +
           "ORDER BY SUM(s.playCount) DESC")
    List<Artist> findTopArtistsByGenreId(@Param("genreId") String genreId, Pageable pageable);

    /**
     * Tải nhiều Artist theo danh sách ID trong một query duy nhất.
     * Dùng trong bước Hydration của pipeline Aggregation.
     */
    List<Artist> findAllByIdIn(List<String> ids);
}


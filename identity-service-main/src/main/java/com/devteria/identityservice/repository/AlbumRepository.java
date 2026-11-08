package com.devteria.identityservice.repository;

import com.devteria.identityservice.entity.Album;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlbumRepository extends JpaRepository<Album, String> {
    @EntityGraph(attributePaths = {"songs"})
    Page<Album> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"songs"}) // Kết hợp tối ưu N+1 luôn
    Page<Album> findByNameContainingIgnoreCase(String keyword, Pageable pageable);

    /**
     * Lấy Top N album nổi bật nhất của một thể loại nhạc,
     * xếp hạng theo tổng lượt nghe (playCount) của tất cả bài hát trong album đó.
     * Dùng cho luồng Cold Start khi user đã chọn preferredGenres.
     */
    @Query("SELECT a FROM Album a JOIN a.songs s JOIN s.genres g " +
           "WHERE g.id = :genreId " +
           "GROUP BY a " +
           "ORDER BY SUM(s.playCount) DESC")
    List<Album> findTopAlbumsByGenreId(@Param("genreId") String genreId, Pageable pageable);

    /**
     * Tải nhiều Album theo danh sách ID trong một query duy nhất.
     * Dùng trong bước Hydration của pipeline Aggregation.
     */
    List<Album> findAllByIdIn(List<String> ids);

    /**
     * Lấy danh sách Album có chứa ít nhất 1 bài hát thuộc genre chỉ định.
     * Dùng cho tính năng lọc nội dung theo thể loại ở frontend.
     */
    @Query("SELECT DISTINCT a FROM Album a JOIN a.songs s JOIN s.genres g WHERE g.id = :genreId")
    Page<Album> findByGenreId(@Param("genreId") String genreId, Pageable pageable);
}

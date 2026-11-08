package com.devteria.identityservice.repository;

import com.devteria.identityservice.entity.Song;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SongRepository extends JpaRepository<Song, String> {
    Optional<Song> findByName(String name);
    Page<Song> findByNameContainingIgnoreCase(String keyword, Pageable pageable);
    List<Song> findTop10ByOrderByPlayCountDesc();
    Page<Song> findByPlaylists_Id(String playlistId, Pageable pageable);
    Page<Song> findByGenres_Id(String genreId, Pageable pageable);
    List<Song> findTop10ByGenres_IdOrderByPlayCountDesc(String genreId);

    /**
     * Tải nhiều bài hát theo danh sách ID trong một query duy nhất.
     */
    List<Song> findByIdIn(List<String> ids);

    /**
     * Tải nhiều bài hát theo danh sách ID trong một query duy nhất kèm quan hệ artists và genres.
     */
    @EntityGraph(attributePaths = {"artists", "genres"})
    List<Song> findWithDetailsByIdIn(List<String> ids);

    /**
     * Tải danh sách bài hát có lượt nghe cao nhất (Trending) với phân trang động kèm quan hệ artists và genres.
     */
    @EntityGraph(attributePaths = {"artists", "genres"})
    Page<Song> findAllByOrderByPlayCountDesc(Pageable pageable);

    /**
     * Tải bài hát kèm sẵn quan hệ artists và genres để tránh LazyInitializationException trong luồng Async AI.
     */
    @EntityGraph(attributePaths = {"artists", "genres"})
    Optional<Song> findWithDetailsById(String id);

    /**
     * Tải toàn bộ bài hát kèm đầy đủ quan hệ để xử lý batch embedding không bị LazyInitializationException.
     */
    @EntityGraph(attributePaths = {"artists", "genres", "album", "year"})
    @Query("SELECT s FROM Song s")
    List<Song> findAllWithDetails();
}

package com.devteria.identityservice.repository;

import com.devteria.identityservice.entity.Playlist;
import com.devteria.identityservice.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlaylistRepository extends JpaRepository<Playlist,String> {
    List<Playlist> findByUserOrderByCreatedAtDesc(User user);

    Page<Playlist> findByTitleContainingIgnoreCase(String key, Pageable pageable);

    @Query("SELECT p FROM Playlist p JOIN p.user u JOIN u.roles r WHERE r.name = 'ADMIN' ORDER BY p.createdAt DESC")
    List<Playlist> findAllAdminPlaylists();

    /**
     * Lấy danh sách Playlist của ADMIN có chứa ít nhất 1 bài hát thuộc genre chỉ định.
     * Dùng cho tính năng lọc nội dung theo thể loại ở frontend.
     */
    @Query("SELECT DISTINCT p FROM Playlist p JOIN p.user u JOIN u.roles r JOIN p.songPlayList s JOIN s.genres g WHERE r.name = 'ADMIN' AND g.id = :genreId")
    Page<Playlist> findAdminPlaylistsByGenreId(@Param("genreId") String genreId, Pageable pageable);
}

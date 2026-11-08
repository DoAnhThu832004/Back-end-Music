package com.devteria.identityservice.repository;

import com.devteria.identityservice.entity.SongSimilarity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SongSimilarityRepository extends JpaRepository<SongSimilarity, Long> {

    /**
     * Lấy tất cả cặp bài tương đồng với bài A, sắp xếp theo điểm giảm dần.
     * Dùng trong bước Candidate Generation của luồng Online.
     */
    @Query("SELECT ss FROM SongSimilarity ss WHERE ss.songAId = :songId " +
            "ORDER BY ss.similarityScore DESC")
    List<SongSimilarity> findBySongAIdOrderByScoreDesc(@Param("songId") String songId);

    /**
     * Lấy cả hai chiều (A→B và B→A) của một bài hát, giới hạn số kết quả qua Pageable.
     * Cần thiết vì chúng ta chỉ lưu một chiều (songAId < songBId).
     * Dùng Pageable để chỉ lấy top-N bài tương tự nhất thay vì load toàn bộ vào RAM.
     */
    @Query("SELECT ss FROM SongSimilarity ss WHERE ss.songAId = :songId OR ss.songBId = :songId " +
            "ORDER BY ss.similarityScore DESC")
    List<SongSimilarity> findAllRelatedToSong(@Param("songId") String songId, Pageable pageable);

    /**
     * Xóa toàn bộ dữ liệu tương đồng để tính lại mỗi đêm.
     * Dùng native DELETE để bỏ qua Hibernate dirty-tracking, hiệu quả hơn deleteAll().
     */
    @Modifying
    @Query(value = "DELETE FROM song_similarity", nativeQuery = true)
    void truncateTable();
}

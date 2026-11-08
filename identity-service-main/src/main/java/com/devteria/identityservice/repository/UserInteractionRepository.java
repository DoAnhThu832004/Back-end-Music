package com.devteria.identityservice.repository;

import com.devteria.identityservice.entity.Song;
import com.devteria.identityservice.entity.User;
import com.devteria.identityservice.entity.UserInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface UserInteractionRepository extends JpaRepository<UserInteraction, String> {

    /**
     * Truy vấn bản ghi tương tác duy nhất giữa một người dùng cụ thể và một bài hát cụ thể.
     * Phương thức này đóng vai trò cốt lõi trong việc kiểm tra sự tồn tại của dữ liệu
     * trước khi tiến hành cập nhật hoặc tạo mới điểm số tương tác.
     * Cấu trúc trả về Optional giúp ngăn chặn triệt để lỗi tham chiếu rỗng trong quá trình xử lý logic.
     */
    Optional<UserInteraction> findByUserAndSong(User user, Song song);

    Optional<UserInteraction> findByUserAndSongAndInteractionType(User user, Song song, String interactionType);

    Page<UserInteraction> findByUserAndInteractionTypeOrderByUpdatedAtDesc(User user, String interactionType, Pageable pageable);

    /**
     * Truy xuất toàn bộ danh sách các tương tác đã được ghi nhận của một người dùng.
     * Phương thức này hỗ trợ trích xuất ma trận dữ liệu cục bộ phục vụ cho các thuật toán
     * phân tích hành vi cá nhân hóa ngoài phạm vi của bộ máy Apache Mahout nếu cần thiết.
     */
    List<UserInteraction> findAllByUser(User user);

    /**
     * Truy xuất toàn bộ danh sách các tương tác liên quan đến một bài hát cụ thể.
     * Tập dữ liệu này hữu ích trong việc tính toán các chỉ số thống kê tổng thể
     * như độ phổ biến thực tế của vật phẩm dựa trên trọng số tương tác thay vì chỉ đếm số lượt nghe.
     */
    List<UserInteraction> findAllBySong(Song song);

    /**
     * Xác minh sự tồn tại của bất kỳ tương tác nào giữa người dùng và bài hát.
     * Truy vấn này được tối ưu hóa ở mức cơ sở dữ liệu để trả về kết quả boolean
     * nhanh chóng mà không cần tải toàn bộ đối tượng thực thể vào bộ nhớ máy chủ.
     */
    boolean existsByUserAndSong(User user, Song song);

    /**
     * Kiểm tra user có bất kỳ lịch sử tương tác nào không.
     * Dùng trong bước Cold Start Check của luồng Online.
     */
    @Query("SELECT COUNT(ui) > 0 FROM UserInteraction ui WHERE ui.user.id = :userId")
    boolean existsByUserId(@Param("userId") String userId);

    /**
     * Lấy tất cả tương tác của một user theo userId (dùng String ID).
     * Phục vụ cho bước lấy User Profile trong luồng Online.
     */
    @Query("SELECT ui FROM UserInteraction ui WHERE ui.user.id = :userId")
    List<UserInteraction> findAllByUserId(@Param("userId") String userId);

    /**
     * Lấy toàn bộ dữ liệu tương tác dưới dạng mảng [userId, songId, ratingScore, interactionType].
     * Dùng trong bước Aggregation của luồng Offline để xây dựng ma trận điểm.
     */
    @Query("SELECT ui.user.id, ui.song.id, ui.ratingScore, ui.interactionType " +
            "FROM UserInteraction ui")
    List<Object[]> findAllRawInteractionData();

    /**
     * Cập nhật điểm số tương tác một cách trực tiếp thông qua câu lệnh thao tác dữ liệu tùy chỉnh.
     * Cấu trúc này vượt qua cơ chế theo dõi trạng thái của Hibernate để tối ưu hóa
     * hiệu suất khi cần xử lý cập nhật hàng loạt dữ liệu trong các tác vụ nền.
     */
    @Modifying
    @Query("UPDATE UserInteraction ui SET ui.ratingScore = :newScore WHERE ui.user = :user AND ui.song = :song")
    int updateRatingScoreDirectly(@Param("user") User user, @Param("song") Song song, @Param("newScore") Float newScore);

    /**
     * Xóa bỏ toàn bộ dữ liệu tương tác của một người dùng khi tài khoản bị hủy bỏ
     * nhằm đảm bảo tính toàn vẹn tham chiếu và giải phóng không gian lưu trữ.
     */
    @Modifying
    @Query("DELETE FROM UserInteraction ui WHERE ui.user = :user")
    void deleteAllByUser(@Param("user") User user);

    @Modifying
    @Query("DELETE FROM UserInteraction ui WHERE ui.interactionType = :type OR ui.interactionType IS NULL")
    void deleteByInteractionTypeOrNull(@Param("type") String type);
}
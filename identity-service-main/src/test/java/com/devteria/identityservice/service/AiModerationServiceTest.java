package com.devteria.identityservice.service;

import com.devteria.identityservice.dto.response.ContentModerationResponse;
import com.devteria.identityservice.entity.Comment;
import com.devteria.identityservice.entity.Report;
import com.devteria.identityservice.entity.Song;
import com.devteria.identityservice.entity.User;
import com.devteria.identityservice.enums.ModerationAction;
import com.devteria.identityservice.enums.ModerationFlag;
import com.devteria.identityservice.enums.ReportStatus;
import com.devteria.identityservice.enums.TargetType;
import com.devteria.identityservice.repository.CommentRepository;
import com.devteria.identityservice.repository.ReportRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiModerationServiceTest {

    @Mock
    private GeminiClientService geminiClientService;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private ReportRepository reportRepository;

    @InjectMocks
    private AiModerationService aiModerationService;

    private Comment sampleComment;
    private Song sampleSong;
    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        sampleUser.setId("user-1");
        sampleUser.setUsername("testuser");

        sampleSong = new Song();
        sampleSong.setId("song-1");
        sampleSong.setName("Chạy Ngay Đi");

        sampleComment = new Comment();
        sampleComment.setId("comment-100");
        sampleComment.setText("Bài này beat cháy vãi chưởng!");
        sampleComment.setUser(sampleUser);
        sampleComment.setSong(sampleSong);
    }

    @Test
    @DisplayName("Kiểm duyệt nội dung: Bình luận an toàn trả về flag SAFE và action ALLOW")
    void moderateContent_safeComment_returnsSafeAndAllow() {
        ContentModerationResponse expectedResponse = ContentModerationResponse.builder()
                .flag(ModerationFlag.SAFE)
                .suggestedAction(ModerationAction.ALLOW)
                .confidence(0.95)
                .reason("Bình luận khen ngợi có dùng tiếng lóng tích cực")
                .build();

        when(geminiClientService.generateStructuredOutput(anyString(), anyString(), any(), eq(ContentModerationResponse.class)))
                .thenReturn(Optional.of(expectedResponse));

        Optional<ContentModerationResponse> result = aiModerationService.moderateContent("Bài này beat cháy vãi chưởng!");

        assertThat(result).isPresent();
        assertThat(result.get().getFlag()).isEqualTo(ModerationFlag.SAFE);
        assertThat(result.get().getSuggestedAction()).isEqualTo(ModerationAction.ALLOW);
    }

    @Test
    @DisplayName("Kiểm duyệt nội dung: Bình luận độc hại/lăng mạ trả về flag TOXIC và action AUTO_HIDE")
    void moderateContent_toxicComment_returnsAutoHide() {
        ContentModerationResponse toxicResponse = ContentModerationResponse.builder()
                .flag(ModerationFlag.TOXIC)
                .suggestedAction(ModerationAction.AUTO_HIDE)
                .confidence(0.99)
                .reason("Chửi bới và xúc phạm cá nhân thô tục")
                .build();

        when(geminiClientService.generateStructuredOutput(anyString(), anyString(), any(), eq(ContentModerationResponse.class)))
                .thenReturn(Optional.of(toxicResponse));

        Optional<ContentModerationResponse> result = aiModerationService.moderateContent("Hát như đấm vào tai cút mẹ đi con chó");

        assertThat(result).isPresent();
        assertThat(result.get().getFlag()).isEqualTo(ModerationFlag.TOXIC);
        assertThat(result.get().getSuggestedAction()).isEqualTo(ModerationAction.AUTO_HIDE);
    }

    @Test
    @DisplayName("Gemini lỗi mạng hoặc quota: Fail-Open trả về Optional.empty() không làm crash hệ thống")
    void moderateContent_geminiFails_failOpenGracefully() {
        when(geminiClientService.generateStructuredOutput(anyString(), anyString(), any(), eq(ContentModerationResponse.class)))
                .thenReturn(Optional.empty());

        Optional<ContentModerationResponse> result = aiModerationService.moderateContent("Bình luận bất kỳ");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Async Moderation: Khi phát hiện vi phạm AUTO_HIDE -> Tự động tạo Report và ẩn bình luận")
    void moderateCommentAsync_whenToxicComment_createsReportAndHidesComment() {
        sampleComment.setText("Đồ rác rưởi phế vật!");
        when(commentRepository.findById("comment-100")).thenReturn(Optional.of(sampleComment));

        ContentModerationResponse toxicResponse = ContentModerationResponse.builder()
                .flag(ModerationFlag.TOXIC)
                .suggestedAction(ModerationAction.AUTO_HIDE)
                .confidence(0.98)
                .reason("Lăng mạ người khác là rác rưởi phế vật")
                .build();

        when(geminiClientService.generateStructuredOutput(anyString(), anyString(), any(), eq(ContentModerationResponse.class)))
                .thenReturn(Optional.of(toxicResponse));

        aiModerationService.moderateCommentAsync("comment-100", "user-1");

        // 1. Kiểm tra Report đã được lưu
        ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository, times(1)).save(reportCaptor.capture());
        Report savedReport = reportCaptor.getValue();
        assertThat(savedReport.getUser()).isEqualTo(sampleUser);
        assertThat(savedReport.getTargetType()).isEqualTo(TargetType.SONG);
        assertThat(savedReport.getTargetId()).isEqualTo("song-1");
        assertThat(savedReport.getStatus()).isEqualTo(ReportStatus.PENDING);
        assertThat(savedReport.getDescription()).contains("[AI Auto-Flag: TOXIC");

        // 2. Kiểm tra comment đã bị ẩn nội dung
        ArgumentCaptor<Comment> commentCaptor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository, times(1)).save(commentCaptor.capture());
        Comment hiddenComment = commentCaptor.getValue();
        assertThat(hiddenComment.getText()).contains("[Bình luận đã bị ẩn do vi phạm tiêu chuẩn cộng đồng: TOXIC]");
    }

    @Test
    @DisplayName("Async Moderation: Khi bình luận an toàn (SAFE) -> Không tạo Report và không sửa comment")
    void moderateCommentAsync_whenSafeComment_noReportAndNoCommentModification() {
        when(commentRepository.findById("comment-100")).thenReturn(Optional.of(sampleComment));

        ContentModerationResponse safeResponse = ContentModerationResponse.builder()
                .flag(ModerationFlag.SAFE)
                .suggestedAction(ModerationAction.ALLOW)
                .confidence(0.95)
                .reason("Bình luận khen ngợi")
                .build();

        when(geminiClientService.generateStructuredOutput(anyString(), anyString(), any(), eq(ContentModerationResponse.class)))
                .thenReturn(Optional.of(safeResponse));

        aiModerationService.moderateCommentAsync("comment-100", "user-1");

        verify(reportRepository, never()).save(any());
        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Dataset 100 câu tiếng Việt chuẩn bị đầy đủ 40 NORMAL, 30 SLANG_SAFE, 30 VIOLATION")
    void evaluationDataset_containsExactly100ItemsWithCorrectCategories() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        InputStream is = getClass().getResourceAsStream("/vietnamese_comments_eval.json");
        assertThat(is).as("File vietnamese_comments_eval.json phải tồn tại trong test resources").isNotNull();

        List<Map<String, Object>> dataset = mapper.readValue(is, new TypeReference<>() {});
        assertThat(dataset).hasSize(100);

        long normalCount = dataset.stream().filter(item -> "NORMAL".equals(item.get("category"))).count();
        long slangCount = dataset.stream().filter(item -> "SLANG_SAFE".equals(item.get("category"))).count();
        long violationCount = dataset.stream().filter(item -> "VIOLATION".equals(item.get("category"))).count();

        assertThat(normalCount).isEqualTo(40);
        assertThat(slangCount).isEqualTo(30);
        assertThat(violationCount).isEqualTo(30);
    }
}

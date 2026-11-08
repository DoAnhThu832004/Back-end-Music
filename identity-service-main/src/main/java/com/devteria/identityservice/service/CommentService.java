package com.devteria.identityservice.service;

import lombok.extern.slf4j.Slf4j;

import com.devteria.identityservice.dto.request.CommentCreationRequest;
import com.devteria.identityservice.dto.request.CommentUpdateRequest;
import com.devteria.identityservice.dto.response.CommentResponse;
import com.devteria.identityservice.entity.Comment;
import com.devteria.identityservice.entity.Song;
import com.devteria.identityservice.entity.User;
import com.devteria.identityservice.exception.AppException;
import com.devteria.identityservice.exception.ErrorCode;
import com.devteria.identityservice.repository.CommentRepository;
import com.devteria.identityservice.repository.SongRepository;
import com.devteria.identityservice.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.devteria.identityservice.dto.response.PageResponse;
import com.devteria.identityservice.mapper.PagingMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class CommentService {
    private final CommentRepository commentRepository;
    private final SongRepository songRepository;
    private final UserRepository userRepository;
    private final AiModerationService aiModerationService;

    public CommentService(CommentRepository commentRepository,
                          SongRepository songRepository,
                          UserRepository userRepository,
                          AiModerationService aiModerationService) {
        this.commentRepository = commentRepository;
        this.songRepository = songRepository;
        this.userRepository = userRepository;
        this.aiModerationService = aiModerationService;
    }

    @Transactional
    public CommentResponse createComment(String songId, CommentCreationRequest request) {

        String username = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new AppException(ErrorCode.SONG_NOT_EXISTED));

        Comment comment = new Comment();
        comment.setText(request.getText());
        comment.setUser(user);
        comment.setSong(song);

        if (request.getParentId() != null) {
            Comment parentComment = commentRepository.findById(request.getParentId())
                    .orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));
            comment.setParent(parentComment);
        }

        Comment savedComment = commentRepository.save(comment);

        // Async AI Moderation (Fail-Open)
        try {
            aiModerationService.moderateCommentAsync(savedComment.getId(), user.getId());
        } catch (Exception e) {
            log.error("Khong the kich hoat kiem duyet AI async cho comment: {}", savedComment.getId(), e);
        }

        return toCommentResponse(savedComment);
    }
    public PageResponse<CommentResponse> getCommentsBySong(String songId, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("createdAt").descending());
        Page<Comment> commentPage = commentRepository.findAllBySongId(songId, pageable);

        List<CommentResponse> commentResponses = commentPage.getContent().stream()
                .filter(comment -> comment.getParent() == null)
                .map(CommentService::toCommentResponse)
                .toList();

        return PagingMapper.toPageResponse(commentPage, commentResponses);
    }
    @Transactional
    public CommentResponse updateComment(String id, CommentUpdateRequest request) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUsername = authentication != null ? authentication.getName() : null;
        if (currentUsername == null || !comment.getUser().getUsername().equals(currentUsername)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        mapRequestToComment(comment, request);
        comment.setEdited(true);

        return toCommentResponse(commentRepository.save(comment));
    }

    @Transactional
    public void deleteComment(String id) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        String currentUsername = authentication != null ? authentication.getName() : null;

        if (!isAdmin && (currentUsername == null || !comment.getUser().getUsername().equals(currentUsername))) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        commentRepository.deleteById(id);
    }
    private void mapRequestToComment(Comment comment, CommentUpdateRequest request) {
        if (request == null) return;
        if (request.getText() != null && !request.getText().isEmpty()) {
            comment.setText(request.getText());
        }
    }

    public Comment toComment(CommentCreationRequest request) {
        if (request == null) return null;
        Comment comment = new Comment();
        comment.setText(request.getText());
        return comment;
    }

    public static CommentResponse toCommentResponse(Comment comment) {
        if (comment == null) return null;

        CommentResponse response = new CommentResponse();
        response.setId(comment.getId());
        response.setText(comment.getText());
        response.setEdited(comment.isEdited());
        //response.setCreateAt(comment.getCreateAt());

        if (comment.getUser() != null) {
            response.setUsername(comment.getUser().getUsername());
            String currentUsername = SecurityContextHolder
                    .getContext()
                    .getAuthentication()
                    .getName();

            response.setOwner(
                    comment.getUser().getUsername().equals(currentUsername)
            );
        }

        if (comment.getSong() != null) {
            // Sá»­a lá»—i 2 & 3: KhĂ´ng setSongId vĂ  láº¥y tĂªn bĂ i hĂ¡t báº±ng getName() thay vĂ¬ getTitle()
            response.setSongTitle(comment.getSong().getName());
        }
        if (comment.getParent() != null) {
            response.setParentId(comment.getParent().getId());
        }
        if (comment.getReplies() != null) {
            response.setReplies(comment.getReplies().stream()
                    .map(CommentService::toCommentResponse)
                    .toList());
        }

        return response;
    }
}

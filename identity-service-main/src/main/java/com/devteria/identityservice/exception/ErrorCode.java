package com.devteria.identityservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import lombok.Getter;

@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_KEY(1001, "Uncategorized error", HttpStatus.BAD_REQUEST),
    USER_EXISTED(1002, "User existed", HttpStatus.BAD_REQUEST),
    USERNAME_INVALID(1003, "Username must be at least {min} characters", HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD(1004, "Password must be at least {min} characters", HttpStatus.BAD_REQUEST),
    USER_NOT_EXISTED(1005, "User not existed", HttpStatus.NOT_FOUND),
    UNAUTHENTICATED(1006, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(1007, "You do not have permission", HttpStatus.FORBIDDEN),
    INVALID_DOB(1008, "Your age must be at least {min}", HttpStatus.BAD_REQUEST),
    SONG_NOT_EXISTED(2005, "Song not existed", HttpStatus.NOT_FOUND),
    UPLOAD_FAILED(2007, "Failed to upload files to Cloudinary", HttpStatus.BAD_REQUEST),
    GENRE_NOT_EXISTED(2006, "Genre not existed", HttpStatus.NOT_FOUND),
    PLAYLIST_NOT_EXISTED(2001, "Playlist not existed", HttpStatus.NOT_FOUND),
    ALBUM_NOT_EXISTED(2002, "Album not existed", HttpStatus.NOT_FOUND),
    ARTIST_NOT_EXISTED(2003, "Artist not existed", HttpStatus.NOT_FOUND),
    WRONG_PASSWORD(1009, "Old password is incorrect", HttpStatus.BAD_REQUEST),
    PASSWORD_MISMATCH(1010, "New password and confirm password do not match", HttpStatus.BAD_REQUEST),
    USER_BLOCKED(1011, "Your account has been blocked by admin", HttpStatus.FORBIDDEN),
    SESSION_NOT_EXISTED(2008, "Chat session not existed", HttpStatus.NOT_FOUND),
    AI_RATE_LIMIT_EXCEEDED(3001, "AI request rate limit exceeded", HttpStatus.TOO_MANY_REQUESTS),
    AI_SESSION_BUSY(3002, "Another AI stream is in progress for this session", HttpStatus.CONFLICT),
    AI_SERVER_BUSY(3003, "AI service is currently busy or degraded", HttpStatus.SERVICE_UNAVAILABLE)
    ;

    ErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;
}

package com.devteria.identityservice.dto.response;

public class GenrePlayCountResponse {
    private String genreId;
    private String genreName;
    private Long totalPlayCount;

    public GenrePlayCountResponse() {
    }

    public GenrePlayCountResponse(String genreId, String genreName, Long totalPlayCount) {
        this.genreId = genreId;
        this.genreName = genreName;
        this.totalPlayCount = totalPlayCount;
    }

    public String getGenreId() {
        return genreId;
    }

    public void setGenreId(String genreId) {
        this.genreId = genreId;
    }

    public String getGenreName() {
        return genreName;
    }

    public void setGenreName(String genreName) {
        this.genreName = genreName;
    }

    public Long getTotalPlayCount() {
        return totalPlayCount;
    }

    public void setTotalPlayCount(Long totalPlayCount) {
        this.totalPlayCount = totalPlayCount;
    }
}

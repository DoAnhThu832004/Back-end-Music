package com.devteria.identityservice.dto.response;

import java.util.List;

public class PageResponse<T> {
    private int currentPage;
    private int totalPages;
    private int pageSize;
    private long totalElements;
    private List<T> result;

    public PageResponse() {
    }

    public PageResponse(int currentPage, int totalPages, int pageSize, long totalElements, List<T> result) {
        this.currentPage = currentPage;
        this.totalPages = totalPages;
        this.pageSize = pageSize;
        this.totalElements = totalElements;
        this.result = result;
    }

    public int getCurrentPage() { return currentPage; }
    public void setCurrentPage(int currentPage) { this.currentPage = currentPage; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }

    public long getTotalElements() { return totalElements; }
    public void setTotalElements(long totalElements) { this.totalElements = totalElements; }

    public List<T> getResult() { return result; }
    public void setResult(List<T> result) { this.result = result; }
}

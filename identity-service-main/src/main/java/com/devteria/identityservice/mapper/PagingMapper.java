package com.devteria.identityservice.mapper;

import com.devteria.identityservice.dto.response.PageResponse;
import org.springframework.data.domain.Page;
import java.util.List;

public class PagingMapper {
    public static <T, R> PageResponse<R> toPageResponse(Page<T> pageData, List<R> mappedContent) {
        PageResponse<R> response = new PageResponse<>();
        response.setCurrentPage(pageData.getNumber() + 1);
        response.setPageSize(pageData.getSize());
        response.setTotalPages(pageData.getTotalPages());
        response.setTotalElements(pageData.getTotalElements());
        response.setResult(mappedContent);
        return response;
    }
}

package com.devteria.identityservice.controller;

import com.devteria.identityservice.dto.request.ApiResponse;
import com.devteria.identityservice.service.InteractionAggregationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/recommendations")
public class RecommendationTriggerController {

    private final InteractionAggregationService aggregationService;

    public RecommendationTriggerController(InteractionAggregationService aggregationService) {
        this.aggregationService = aggregationService;
    }

    @PostMapping("/trigger-full-pipeline")
    public ApiResponse<String> triggerFullPipeline() {
        aggregationService.runNightlyRecommendationJob();

        return ApiResponse.<String>builder()
                .code(1000)
                .message("Pipeline offline đã chạy xong. Kiểm tra log để biết chi tiết.")
                .result("PIPELINE_COMPLETE")
                .build();
    }

    @PostMapping("/trigger-sync")
    public ApiResponse<String> triggerSyncOnly() {
        aggregationService.syncRawInteractions();

        return ApiResponse.<String>builder()
                .code(1000)
                .message("Sync dữ liệu tương tác thô hoàn tất.")
                .result("SYNC_COMPLETE")
                .build();
    }

    @PostMapping("/trigger-similarity")
    public ApiResponse<String> triggerSimilarityOnly() {
        aggregationService.computeAndStoreSimilarity();

        return ApiResponse.<String>builder()
                .code(1000)
                .message("Tính toán Song Similarity hoàn tất. Bảng song_similarity đã được cập nhật.")
                .result("SIMILARITY_COMPLETE")
                .build();
    }

    @Deprecated
    @PostMapping("/trigger-aggregation")
    public ApiResponse<String> triggerAggregationJobManually() {
        aggregationService.syncRawInteractions();

        return ApiResponse.<String>builder()
                .code(1000)
                .message("Tiến trình tổng hợp điểm số tương tác đã được thực thi")
                .result("MA_TRAN_DA_DUOC_CAP_NHAT")
                .build();
    }
}
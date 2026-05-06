package com.harness.controller;

import com.harness.dto.ApiResponse;
import com.harness.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard", description = "대시보드 통계 API")
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "전체 통계 조회 (상품 현황 + 플랫폼별 등록 성공/실패)")
    @GetMapping("/stats")
    public ApiResponse<DashboardService.StatsDto> getStats() {
        return ApiResponse.ok(dashboardService.getStats());
    }
}

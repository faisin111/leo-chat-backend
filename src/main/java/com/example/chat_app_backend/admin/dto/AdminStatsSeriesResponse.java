package com.example.chat_app_backend.admin.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record AdminStatsSeriesResponse(
    LocalDate from, LocalDate to, int days, Map<String, List<AdminStatsPoint>> series) {}

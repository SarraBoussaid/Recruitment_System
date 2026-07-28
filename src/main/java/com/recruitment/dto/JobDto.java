package com.recruitment.dto;

public record JobDto(
        int id,
        String title,
        String company,
        String industry,
        String description,
        String location,
        String type,
        String salary
) {}

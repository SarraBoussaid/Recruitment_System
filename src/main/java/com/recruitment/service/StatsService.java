package com.recruitment.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.recruitment.dto.StatsDto;

@Service
public class StatsService {

    private final JdbcTemplate jdbc;

    public StatsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public StatsDto getStats() {
        Integer openJobs = jdbc.queryForObject(
                "SELECT COUNT(*) FROM jobs WHERE status = 'open'",
                Integer.class
        );
        Integer companies = jdbc.queryForObject(
                "SELECT COUNT(*) FROM companies",
                Integer.class
        );
        Integer applicationsThisWeek = jdbc.queryForObject(
                "SELECT COUNT(*) FROM applications WHERE applied_at >= NOW() - INTERVAL 7 DAY",
                Integer.class
        );

        return new StatsDto(
                openJobs != null ? openJobs : 0,
                companies != null ? companies : 0,
                applicationsThisWeek != null ? applicationsThisWeek : 0
        );
    }
}

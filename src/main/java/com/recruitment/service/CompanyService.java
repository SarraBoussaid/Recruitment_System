package com.recruitment.service;

import com.recruitment.dto.CompanyDto;
import com.recruitment.exception.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.util.List;

@Service
public class CompanyService {

    private final JdbcTemplate jdbc;

    public CompanyService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CompanyDto> findAllWithJobCounts() {
        String sql = """
                SELECT c.id, c.name, c.industry, COUNT(j.id) AS jobs
                FROM companies c
                LEFT JOIN jobs j ON j.company_id = c.id AND j.status = 'open'
                GROUP BY c.id, c.name, c.industry
                ORDER BY c.name
                """;

        return jdbc.query(sql, (rs, rowNum) -> new CompanyDto(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("industry"),
                rs.getInt("jobs")
        ));
    }

    public int findOrCreate(String name, String industry) {
        String trimmedName = name.trim();
        Integer existingId = jdbc.query(
                "SELECT id FROM companies WHERE LOWER(name) = LOWER(?)",
                rs -> rs.next() ? rs.getInt("id") : null,
                trimmedName
        );

        if (existingId != null) {
            return existingId;
        }

        String industryValue = industry != null && !industry.isBlank() ? industry.trim() : null;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO companies (name, industry) VALUES (?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, trimmedName);
            ps.setString(2, industryValue);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new ApiException(500, "Could not create company.");
        }
        return key.intValue();
    }
}

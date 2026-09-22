package com.gz199.web;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;

import java.util.HashMap;
import java.util.Map;

/**
 * 数据库连接健康检查：实际执行 SELECT VERSION() 验证 MySQL 连通性。
 * 院校详情在 schools 有数据后优先读 MySQL。
 */
@RestController
@RequestMapping("/api/db")
public class DbHealthController {

    private final JdbcTemplate jdbc;

    public DbHealthController(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> m = new HashMap<>();
        try {
            m.put("ok", true);
            m.put("mysqlVersion", jdbc.queryForObject("SELECT VERSION()", String.class));
            m.put("database", jdbc.queryForObject("SELECT DATABASE()", String.class));
            m.put("schools", jdbc.queryForObject("SELECT COUNT(*) FROM schools", Integer.class));
            m.put("catalog", jdbc.queryForObject("SELECT COUNT(*) FROM school_catalog", Integer.class));
            m.put("programs", jdbc.queryForObject("SELECT COUNT(*) FROM school_programs", Integer.class));
            m.put("admissions", jdbc.queryForObject("SELECT COUNT(*) FROM admission_stats", Integer.class));
            m.put("nationLines", jdbc.queryForObject("SELECT COUNT(*) FROM nation_lines", Integer.class));
            m.put("packs", jdbc.queryForObject("SELECT COUNT(*) FROM school_data_packs", Integer.class));
        } catch (Exception e) {
            m.put("ok", false);
            m.put("error", e.getMessage());
        }
        return m;
    }
}

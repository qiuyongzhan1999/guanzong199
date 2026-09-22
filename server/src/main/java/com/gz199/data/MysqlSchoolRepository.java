package com.gz199.data;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MySQL 读路径。有库数据则优先于 JSON。
 */
@Service
public class MysqlSchoolRepository {
    private final JdbcTemplate jdbc;

    public MysqlSchoolRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public boolean available() {
        try {
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM schools", Integer.class);
            return n != null && n > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public Map<String, Object> findProgram(String schoolCode, int year, String majorCode, String studyMode) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT school_code AS schoolCode, year, major_code AS majorCode, major_name AS majorName,
                       study_mode AS studyMode, study_mode_label AS studyModeLabel,
                       tuition_text AS tuitionText, plan_text AS planText, duration_text AS durationText,
                       source_url AS sourceUrl, source_name AS sourceName, checked_at AS checkedAt
                FROM school_programs
                WHERE school_code=? AND year=? AND major_code=? AND study_mode=?
                LIMIT 1
                """,
                schoolCode, year, majorCode, studyMode
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> findAdmission(String schoolCode, int year, String majorCode, String studyMode) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT id, school_code AS schoolCode, year, major_code AS majorCode, study_mode AS studyMode,
                       reexam_min_score AS reexamMinScore, min_score AS minScore, max_score AS maxScore,
                       admit_count AS admitCount, reexam_count AS reexamCount,
                       pending_note AS pendingNote, source_url AS sourceUrl, source_name AS sourceName
                FROM admission_stats
                WHERE school_code=? AND year=? AND major_code=? AND study_mode=?
                LIMIT 1
                """,
                schoolCode, year, majorCode, studyMode
        );
        if (rows.isEmpty()) return null;
        Map<String, Object> adm = new HashMap<>(rows.get(0));
        Object id = adm.remove("id");
        List<Map<String, Object>> bands = jdbc.queryForList(
                """
                SELECT score_label AS scoreLabel, score_min AS scoreMin, score_max AS scoreMax,
                       reexam_count AS reexamCount, admit_count AS admitCount
                FROM admission_score_bands WHERE admission_id=? ORDER BY sort_order, id
                """,
                id
        );
        adm.put("scoreBands", bands);
        return adm;
    }

    public Map<String, Object> findNationRow(int year, String majorCode) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT year, family, major_codes_json, a_total AS aTotal, a_english AS aEnglish,
                       a_comprehensive AS aComprehensive, b_total AS bTotal, b_english AS bEnglish,
                       b_comprehensive AS bComprehensive, source_url AS sourceUrl, source_name AS sourceName,
                       synced_at AS syncedAt
                FROM nation_lines WHERE year=?
                """,
                year
        );
        for (Map<String, Object> row : rows) {
            if (codesContain(row.get("major_codes_json"), majorCode) || majorCode.equals(String.valueOf(row.get("family")))) {
                return row;
            }
        }
        return null;
    }

    public Integer latestNationYear() {
        List<Integer> ys = jdbc.query(
                "SELECT year FROM nation_lines ORDER BY year DESC LIMIT 1",
                (rs, i) -> rs.getInt(1)
        );
        return ys.isEmpty() ? null : ys.get(0);
    }

    /**
     * 择校排序用。provinces 为空表示不限。省份按名称包含匹配（北京 / 北京市）。
     */
    public List<Map<String, Object>> listYearStatsForMatch(
            List<String> majorCodes,
            String studyMode,
            List<String> provinces
    ) {
        if (majorCodes == null || majorCodes.isEmpty()) return List.of();
        List<Object> args = new ArrayList<>();
        args.add(studyMode);
        String sql = """
                SELECT s.sch_id AS schId, s.code AS schoolCode, s.name AS name, s.province AS province,
                       s.city AS city, s.logo_url AS logo, s.is_double_first AS doubleFirst, s.is_self_line AS selfLine,
                       y.major_code AS majorCode, y.study_mode AS studyMode, y.year AS year,
                       y.reexam_min_score AS reexamMinScore, y.min_score AS minScore,
                       y.max_score AS maxScore, y.admit_count AS admitCount, y.reexam_count AS reexamCount
                FROM school_year_stats y
                JOIN schools s ON s.code = y.school_code
                WHERE y.study_mode = ?
                  AND y.major_code IN (%s)
                """.formatted(placeholders(majorCodes.size()));
        args.addAll(majorCodes);
        sql = sql + provinceSql(provinces, args);
        return jdbc.queryForList(sql, args.toArray());
    }

    public List<Map<String, Object>> listCatalogForMatch(
            List<String> majorCodes,
            String studyMode,
            List<String> provinces
    ) {
        if (majorCodes == null || majorCodes.isEmpty()) return List.of();
        List<Object> args = new ArrayList<>();
        args.add(studyMode);
        String sql = """
                SELECT s.sch_id AS schId, s.code AS schoolCode, s.name AS name, s.province AS province,
                       s.city AS city, s.logo_url AS logo, s.is_double_first AS doubleFirst, s.is_self_line AS selfLine,
                       c.major_code AS majorCode, c.major_name AS majorName, c.study_mode AS studyMode
                FROM school_catalog c
                JOIN schools s ON s.code = c.school_code
                WHERE c.study_mode = ?
                  AND c.major_code IN (%s)
                """.formatted(placeholders(majorCodes.size()));
        args.addAll(majorCodes);
        sql = sql + provinceSql(provinces, args);
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args.toArray());
        Map<String, Map<String, Object>> uniq = new java.util.LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String key = String.valueOf(row.get("schoolCode")) + "|" + row.get("majorCode") + "|" + row.get("studyMode");
            uniq.putIfAbsent(key, row);
        }
        return new ArrayList<>(uniq.values());
    }

    /** 爬虫若只写了 admission_stats、还没进逐年包，择校仍能用来排序。 */
    public List<Map<String, Object>> listAdmissionsForMatch(
            List<String> majorCodes,
            String studyMode,
            List<String> provinces
    ) {
        if (majorCodes == null || majorCodes.isEmpty()) return List.of();
        List<Object> args = new ArrayList<>();
        args.add(studyMode);
        String sql = """
                SELECT s.sch_id AS schId, s.code AS schoolCode, s.name AS name, s.province AS province,
                       s.city AS city, s.logo_url AS logo, s.is_double_first AS doubleFirst, s.is_self_line AS selfLine,
                       a.major_code AS majorCode, a.study_mode AS studyMode, a.year AS year,
                       a.reexam_min_score AS reexamMinScore, a.min_score AS minScore,
                       a.max_score AS maxScore, a.admit_count AS admitCount, a.reexam_count AS reexamCount
                FROM admission_stats a
                JOIN schools s ON s.code = a.school_code
                WHERE a.study_mode = ?
                  AND a.major_code IN (%s)
                """.formatted(placeholders(majorCodes.size()));
        args.addAll(majorCodes);
        sql = sql + provinceSql(provinces, args);
        return jdbc.queryForList(sql, args.toArray());
    }

    private static String provinceSql(List<String> provinces, List<Object> args) {
        if (provinces == null || provinces.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(" AND (");
        for (int i = 0; i < provinces.size(); i++) {
            if (i > 0) sb.append(" OR ");
            sb.append("s.province = ? OR s.province LIKE ?");
            String name = provinces.get(i);
            args.add(name);
            args.add(name + "%");
        }
        sb.append(")");
        return sb.toString();
    }

    private static String placeholders(int n) {
        return String.join(",", java.util.Collections.nCopies(n, "?"));
    }

    public List<Integer> years() {
        List<Integer> fromNation = jdbc.query(
                "SELECT DISTINCT year FROM nation_lines ORDER BY year DESC",
                (rs, i) -> rs.getInt(1)
        );
        if (!fromNation.isEmpty()) return fromNation;
        return jdbc.query(
                "SELECT DISTINCT year FROM school_programs ORDER BY year DESC",
                (rs, i) -> rs.getInt(1)
        );
    }

    public Map<String, Object> loadPack(String schoolCode, String majorCode, String studyMode) {
        List<Map<String, Object>> heads = jdbc.queryForList(
                """
                SELECT id, school_code AS schoolCode, school_name AS schoolName, major_code AS majorCode,
                       major_name AS majorName, study_mode AS studyMode, study_mode_label AS studyModeLabel,
                       start_year AS startYear, end_year AS endYear, skill, provider,
                       major_info_json, exam_rules_json, data_source_json, fetched_at AS fetchedAt
                FROM school_data_packs
                WHERE school_code=? AND major_code=? AND study_mode=?
                LIMIT 1
                """,
                schoolCode, majorCode, studyMode
        );
        if (heads.isEmpty()) return null;
        Map<String, Object> pack = new HashMap<>(heads.get(0));
        Object packId = pack.remove("id");
        Object majorInfo = pack.remove("major_info_json");
        Object examRules = pack.remove("exam_rules_json");
        Object dataSource = pack.remove("data_source_json");
        pack.put("major_info", parseJson(majorInfo));
        pack.put("exam_rules", parseJson(examRules));
        pack.put("data_source", parseJson(dataSource));

        List<Map<String, Object>> years = jdbc.queryForList(
                """
                SELECT year, status, tuition_text, plan_text, duration_text,
                       program_source_url, program_source_name,
                       reexam_min_score, min_score, max_score, admit_count, reexam_count,
                       nation_a_total, nation_a_english, nation_a_comprehensive,
                       nation_b_total, nation_b_english, nation_b_comprehensive,
                       nation_source_url, nation_source_name,
                       admission_source_url, admission_source_name,
                       pending_note, score_bands_json, data_confidence
                FROM school_year_stats
                WHERE pack_id=? ORDER BY year DESC
                """,
                packId
        );
        List<Map<String, Object>> yearly = new ArrayList<>();
        for (Map<String, Object> y : years) {
            Map<String, Object> row = new HashMap<>();
            row.put("year", y.get("year"));
            row.put("status", y.get("status"));
            row.put("tuition_text", y.get("tuition_text"));
            row.put("plan_text", y.get("plan_text"));
            row.put("duration_text", y.get("duration_text"));
            row.put("program_source_url", y.get("program_source_url"));
            row.put("program_source_name", y.get("program_source_name"));
            row.put("reexam_min_score", y.get("reexam_min_score"));
            row.put("min_score", y.get("min_score"));
            row.put("max_score", y.get("max_score"));
            row.put("admit_count", y.get("admit_count"));
            row.put("reexam_count", y.get("reexam_count"));
            row.put("nation_a_total", y.get("nation_a_total"));
            row.put("nation_a_english", y.get("nation_a_english"));
            row.put("nation_a_comprehensive", y.get("nation_a_comprehensive"));
            row.put("nation_b_total", y.get("nation_b_total"));
            row.put("nation_b_english", y.get("nation_b_english"));
            row.put("nation_b_comprehensive", y.get("nation_b_comprehensive"));
            row.put("nation_source_url", y.get("nation_source_url"));
            row.put("nation_source_name", y.get("nation_source_name"));
            row.put("admission_source_url", y.get("admission_source_url"));
            row.put("admission_source_name", y.get("admission_source_name"));
            row.put("pending_note", y.get("pending_note"));
            row.put("data_confidence", y.get("data_confidence"));
            Object bands = parseJson(y.get("score_bands_json"));
            row.put("score_bands", bands instanceof List ? bands : Collections.emptyList());
            yearly.add(row);
        }
        pack.put("yearly_data", yearly);
        return pack;
    }

    public boolean packHasUsefulData(String schoolCode, String majorCode, String studyMode) {
        Integer hits = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM school_year_stats
                WHERE school_code=? AND major_code=? AND study_mode=?
                  AND (
                    reexam_min_score IS NOT NULL OR min_score IS NOT NULL OR admit_count IS NOT NULL
                    OR nation_a_total IS NOT NULL
                    OR (tuition_text IS NOT NULL AND tuition_text NOT IN ('','待查','待同步'))
                  )
                """,
                Integer.class, schoolCode, majorCode, studyMode
        );
        return hits != null && hits >= 2;
    }

    public void upsertPack(Map<String, Object> pack) {
        String schoolCode = str(pack.get("schoolCode"));
        String majorCode = str(pack.get("majorCode"));
        String studyMode = str(pack.get("studyMode"));
        jdbc.update(
                """
                INSERT INTO school_data_packs (
                  school_code, school_name, major_code, major_name, study_mode, study_mode_label,
                  start_year, end_year, skill, provider, major_info_json, exam_rules_json,
                  data_source_json, fetched_at
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                  school_name=VALUES(school_name), major_name=VALUES(major_name),
                  study_mode_label=VALUES(study_mode_label), start_year=VALUES(start_year),
                  end_year=VALUES(end_year), skill=VALUES(skill), provider=VALUES(provider),
                  major_info_json=VALUES(major_info_json), exam_rules_json=VALUES(exam_rules_json),
                  data_source_json=VALUES(data_source_json), fetched_at=VALUES(fetched_at)
                """,
                schoolCode,
                pack.get("schoolName"),
                majorCode,
                pack.get("majorName"),
                studyMode,
                pack.get("studyModeLabel"),
                pack.get("startYear"),
                pack.get("endYear"),
                pack.get("skill"),
                pack.get("provider"),
                toJson(pack.get("major_info")),
                toJson(pack.get("exam_rules")),
                toJson(pack.get("data_source")),
                pack.get("fetchedAt")
        );
        Long packId = jdbc.queryForObject(
                "SELECT id FROM school_data_packs WHERE school_code=? AND major_code=? AND study_mode=?",
                Long.class, schoolCode, majorCode, studyMode
        );
        jdbc.update(
                "DELETE FROM school_year_stats WHERE school_code=? AND major_code=? AND study_mode=?",
                schoolCode, majorCode, studyMode
        );
        Object yearlyObj = pack.get("yearly_data");
        if (!(yearlyObj instanceof List<?> list) || packId == null) return;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> y = (Map<String, Object>) raw;
            jdbc.update(
                    """
                    INSERT INTO school_year_stats (
                      pack_id, school_code, major_code, study_mode, year, status,
                      tuition_text, plan_text, duration_text,
                      program_source_url, program_source_name,
                      reexam_min_score, min_score, max_score, admit_count, reexam_count,
                      nation_a_total, nation_a_english, nation_a_comprehensive,
                      nation_b_total, nation_b_english, nation_b_comprehensive,
                      nation_source_url, nation_source_name,
                      admission_source_url, admission_source_name,
                      pending_note, score_bands_json, data_confidence
                    ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    packId, schoolCode, majorCode, studyMode, y.get("year"), y.get("status"),
                    y.get("tuition_text"), y.get("plan_text"), y.get("duration_text"),
                    y.get("program_source_url"), y.get("program_source_name"),
                    y.get("reexam_min_score"), y.get("min_score"), y.get("max_score"),
                    y.get("admit_count"), y.get("reexam_count"),
                    y.get("nation_a_total"), y.get("nation_a_english"), y.get("nation_a_comprehensive"),
                    y.get("nation_b_total"), y.get("nation_b_english"), y.get("nation_b_comprehensive"),
                    y.get("nation_source_url"), y.get("nation_source_name"),
                    y.get("admission_source_url"), y.get("admission_source_name"),
                    y.get("pending_note"), toJson(y.get("score_bands")), y.get("data_confidence")
            );
        }
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static Object parseJson(Object raw) {
        if (raw == null) return null;
        String s = String.valueOf(raw);
        if (s.isBlank()) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(s, Object.class);
        } catch (Exception e) {
            return s;
        }
    }

    private static String toJson(Object v) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(v == null ? Map.of() : v);
        } catch (Exception e) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean codesContain(Object json, String code) {
        Object parsed = parseJson(json);
        if (parsed instanceof List<?> list) {
            return list.stream().anyMatch(c -> code.equals(String.valueOf(c)));
        }
        return false;
    }

    public List<Map<String, Object>> listNationLines(Integer year, String majorCode) {
        StringBuilder sql = new StringBuilder("SELECT year, family, major_codes_json, a_total AS aTotal, a_english AS aEnglish, a_comprehensive AS aComprehensive, b_total AS bTotal, b_english AS bEnglish, b_comprehensive AS bComprehensive, source_url AS sourceUrl, source_name AS sourceName, synced_at AS syncedAt FROM nation_lines WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (year != null) {
            sql.append(" AND year=?");
            args.add(year);
        }
        sql.append(" ORDER BY year DESC, family");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        if (majorCode == null) return rows;
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (codesContain(row.get("major_codes_json"), majorCode) || majorCode.equals(String.valueOf(row.get("family")))) {
                out.add(row);
            }
        }
        return out;
    }

    public List<Map<String, Object>> listCatalogBySchool(String schoolCode) {
        return jdbc.queryForList(
            "SELECT school_code AS schoolCode, major_code AS majorCode, major_name AS majorName, study_mode AS studyMode FROM school_catalog WHERE school_code=?",
            schoolCode
        );
    }
}


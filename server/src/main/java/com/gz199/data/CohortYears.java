package com.gz199.data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 考研届别：入学年。初试在上一自然年 12 月。
 * 例：2026 届 ← 2025-12 考试；2027 届 ← 2026-12 考试。
 * 展示截止到「已经考过初试」的最新届，等于当前日历年。
 * 2026 年 9 月时 2027 届的 12 月考试还没开始，近 5 届是 2022–2026。
 */
public final class CohortYears {
    public static final int SPAN = 5;

    private CohortYears() {}

    /** 最近一届已开考的入学年。下一届 12 月考试未开始前不计入。 */
    public static int targetCohort() {
        return LocalDate.now().getYear();
    }

    public static int examYearOf(int cohort) {
        return cohort - 1;
    }

    public static int windowStart(int endCohort) {
        return endCohort - (SPAN - 1);
    }

    /** 从新到旧：目标届 … 目标届-4 */
    public static List<Integer> windowYears() {
        return windowYears(targetCohort());
    }

    public static List<Integer> windowYears(int endCohort) {
        List<Integer> out = new ArrayList<>(SPAN);
        for (int y = endCohort; y >= windowStart(endCohort); y--) {
            out.add(y);
        }
        return out;
    }

    public static boolean inWindow(Integer year) {
        return inWindow(year, targetCohort());
    }

    public static boolean inWindow(Integer year, int endCohort) {
        if (year == null) return false;
        int start = windowStart(endCohort);
        return year >= start && year <= endCohort;
    }

    public static List<Map<String, Object>> filterYearRows(List<Map<String, Object>> rows) {
        return filterYearRows(rows, targetCohort());
    }

    public static List<Map<String, Object>> filterYearRows(List<Map<String, Object>> rows, int endCohort) {
        if (rows == null || rows.isEmpty()) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Integer y = asInt(row.get("year"));
            if (inWindow(y, endCohort)) out.add(row);
        }
        out.sort(Comparator.comparingInt((Map<String, Object> row) -> {
            Integer y = asInt(row.get("year"));
            return y == null ? 0 : y;
        }).reversed());
        return out;
    }

    private static Integer asInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

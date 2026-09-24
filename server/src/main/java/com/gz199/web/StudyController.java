package com.gz199.web;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 学习时长记录：按 用户×日期 累加秒数与浏览单词数。
 * 含每日目标、连续打卡天数、月历热力（不含奖励计划/成就徽章）。
 * userId 为前端设备 ID（当前无登录体系，头字段 X-UserId）。
 */
@RestController
@RequestMapping("/api/study")
public class StudyController {

    private static final int DEFAULT_TARGET = 30;
    private static final int MIN_TARGET = 5;
    private static final int MAX_TARGET = 500;
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private final JdbcTemplate jdbc;

    public StudyController(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 上报本次学习：durationSec 秒 + 本次浏览单词数 */
    @PostMapping("/report")
    public Map<String, Object> report(@RequestHeader(value = "X-UserId", required = false) String userId,
                                      @RequestBody Map<String, Object> body) {
        Map<String, Object> m = new HashMap<>();
        String uid = valid(userId);
        if (uid == null) {
            m.put("ok", false);
            m.put("error", "missing user id");
            return m;
        }
        int dur = toInt(body.get("durationSec"));
        int words = toInt(body.get("wordsSeen"));
        if (dur <= 0 && words <= 0) {
            m.put("ok", true);
            m.put("ignored", true);
            return m;
        }
        String today = LocalDate.now().format(ISO);
        jdbc.update(
                "INSERT INTO study_record (user_id, study_date, duration_sec, words_seen) VALUES (?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE duration_sec=duration_sec+VALUES(duration_sec), words_seen=words_seen+VALUES(words_seen)",
                uid, today, dur, words);
        m.put("ok", true);
        m.put("date", today);
        m.put("streak", calcStreak(uid));
        return m;
    }

    /** 今日学习情况 */
    @GetMapping("/today")
    public Map<String, Object> today(@RequestHeader(value = "X-UserId", required = false) String userId) {
        Map<String, Object> m = new HashMap<>();
        String uid = valid(userId);
        String today = LocalDate.now().format(ISO);
        if (uid == null) {
            m.put("date", today);
            m.put("durationSec", 0);
            m.put("wordsSeen", 0);
            m.put("dailyTarget", DEFAULT_TARGET);
            return m;
        }
        Map<String, Object> row = rowOf(uid, today);
        m.put("date", today);
        m.put("durationSec", row == null ? 0 : ((Number) row.get("durationSec")).intValue());
        m.put("wordsSeen", row == null ? 0 : ((Number) row.get("wordsSeen")).intValue());
        m.put("dailyTarget", getDailyTarget(uid));
        return m;
    }

    /** 统计：今日/本周/累计 + 连续打卡 + 每日目标 */
    @GetMapping("/statistics")
    public Map<String, Object> statistics(@RequestHeader(value = "X-UserId", required = false) String userId,
                                          @RequestParam(defaultValue = "total") String range) {
        Map<String, Object> m = new HashMap<>();
        String uid = valid(userId);
        if (uid == null) {
            m.put("todaySec", 0);
            m.put("weekSec", 0);
            m.put("totalSec", 0);
            m.put("todayWords", 0);
            m.put("studyDays", 0);
            m.put("streak", 0);
            m.put("maxStreak", 0);
            m.put("dailyTarget", DEFAULT_TARGET);
            m.put("goalPct", 0);
            return m;
        }
        LocalDate today = LocalDate.now();
        String todayStr = today.format(ISO);
        String weekStart = today.minusDays(6).format(ISO);

        Integer todaySec = jdbc.queryForObject(
                "SELECT COALESCE(SUM(duration_sec),0) FROM study_record WHERE user_id=? AND study_date=?",
                Integer.class, uid, todayStr);
        Integer weekSec = jdbc.queryForObject(
                "SELECT COALESCE(SUM(duration_sec),0) FROM study_record WHERE user_id=? AND study_date>=? AND study_date<=?",
                Integer.class, uid, weekStart, todayStr);
        Integer totalSec = jdbc.queryForObject(
                "SELECT COALESCE(SUM(duration_sec),0) FROM study_record WHERE user_id=?",
                Integer.class, uid);
        Integer todayWords = jdbc.queryForObject(
                "SELECT COALESCE(SUM(words_seen),0) FROM study_record WHERE user_id=? AND study_date=?",
                Integer.class, uid, todayStr);
        Integer studyDays = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT study_date) FROM study_record WHERE user_id=? AND (duration_sec>0 OR words_seen>0)",
                Integer.class, uid);

        int target = getDailyTarget(uid);
        int words = todayWords == null ? 0 : todayWords;
        int goalPct = target <= 0 ? 0 : Math.min(100, (int) Math.round(words * 100.0 / target));

        m.put("todaySec", todaySec);
        m.put("weekSec", weekSec);
        m.put("totalSec", totalSec);
        m.put("todayWords", todayWords);
        m.put("studyDays", studyDays);
        m.put("streak", calcStreak(uid));
        m.put("maxStreak", calcMaxStreak(uid));
        m.put("dailyTarget", target);
        m.put("goalPct", goalPct);
        return m;
    }

    /** 读取每日目标 */
    @GetMapping("/pref")
    public Map<String, Object> getPref(@RequestHeader(value = "X-UserId", required = false) String userId) {
        Map<String, Object> m = new HashMap<>();
        String uid = valid(userId);
        m.put("dailyTarget", uid == null ? DEFAULT_TARGET : getDailyTarget(uid));
        m.put("ok", true);
        return m;
    }

    /** 设置每日目标：30 / 50 / 100 / 自定义（5～500） */
    @PostMapping("/pref")
    public Map<String, Object> setPref(@RequestHeader(value = "X-UserId", required = false) String userId,
                                       @RequestBody Map<String, Object> body) {
        Map<String, Object> m = new HashMap<>();
        String uid = valid(userId);
        if (uid == null) {
            m.put("ok", false);
            m.put("error", "missing user id");
            return m;
        }
        int target = toInt(body.get("dailyTarget"));
        if (target < MIN_TARGET || target > MAX_TARGET) {
            m.put("ok", false);
            m.put("error", "dailyTarget must be " + MIN_TARGET + "~" + MAX_TARGET);
            return m;
        }
        jdbc.update(
                "INSERT INTO user_study_pref (user_id, daily_target) VALUES (?,?) " +
                        "ON DUPLICATE KEY UPDATE daily_target=VALUES(daily_target)",
                uid, target);
        m.put("ok", true);
        m.put("dailyTarget", target);
        return m;
    }

    /** 月历热力：每天 words_seen（有学习记录则计入） */
    @GetMapping("/calendar")
    public Map<String, Object> calendar(@RequestHeader(value = "X-UserId", required = false) String userId,
                                        @RequestParam(required = false) String month) {
        Map<String, Object> m = new HashMap<>();
        String uid = valid(userId);
        YearMonth ym;
        try {
            ym = (month == null || month.isBlank()) ? YearMonth.now() : YearMonth.parse(month.trim());
        } catch (Exception e) {
            ym = YearMonth.now();
        }
        String from = ym.atDay(1).format(ISO);
        String to = ym.atEndOfMonth().format(ISO);
        m.put("month", ym.toString());
        m.put("daysInMonth", ym.lengthOfMonth());
        m.put("firstWeekday", ym.atDay(1).getDayOfWeek().getValue()); // 1=Mon … 7=Sun

        Map<Integer, Integer> dayWords = new HashMap<>();
        if (uid != null) {
            jdbc.query(
                    "SELECT study_date AS d, words_seen AS n, duration_sec AS s FROM study_record " +
                            "WHERE user_id=? AND study_date>=? AND study_date<=?",
                    rs -> {
                        while (rs.next()) {
                            LocalDate d = rs.getDate("d").toLocalDate();
                            int n = rs.getInt("n");
                            int s = rs.getInt("s");
                            if (n > 0 || s > 0) {
                                dayWords.put(d.getDayOfMonth(), Math.max(n, 1));
                            }
                        }
                        return null;
                    }, uid, from, to);
        }
        List<Map<String, Object>> days = new ArrayList<>();
        for (int i = 1; i <= ym.lengthOfMonth(); i++) {
            Map<String, Object> cell = new HashMap<>();
            cell.put("day", i);
            int n = dayWords.getOrDefault(i, 0);
            cell.put("words", n);
            cell.put("level", heatLevel(n));
            days.add(cell);
        }
        m.put("days", days);
        m.put("streak", uid == null ? 0 : calcStreak(uid));
        return m;
    }

    private int heatLevel(int words) {
        if (words <= 0) return 0;
        if (words < 5) return 1;
        if (words < 15) return 2;
        if (words < 30) return 3;
        return 4;
    }

    private int getDailyTarget(String uid) {
        try {
            Integer t = jdbc.queryForObject(
                    "SELECT daily_target FROM user_study_pref WHERE user_id=?",
                    Integer.class, uid);
            return t == null ? DEFAULT_TARGET : t;
        } catch (Exception e) {
            return DEFAULT_TARGET;
        }
    }

    /** 连续打卡：以「有学习记录」的自然日计；今天没学仍可接昨天 streak */
    private int calcStreak(String uid) {
        Set<LocalDate> days = studyDays(uid);
        if (days.isEmpty()) return 0;
        LocalDate d = LocalDate.now();
        if (!days.contains(d)) {
            d = d.minusDays(1);
            if (!days.contains(d)) return 0;
        }
        int streak = 0;
        while (days.contains(d)) {
            streak++;
            d = d.minusDays(1);
        }
        return streak;
    }

    private int calcMaxStreak(String uid) {
        Set<LocalDate> days = studyDays(uid);
        if (days.isEmpty()) return 0;
        int max = 0;
        int cur = 0;
        LocalDate prev = null;
        List<LocalDate> sorted = new ArrayList<>(days);
        sorted.sort(LocalDate::compareTo);
        for (LocalDate d : sorted) {
            if (prev != null && d.equals(prev.plusDays(1))) {
                cur++;
            } else {
                cur = 1;
            }
            if (cur > max) max = cur;
            prev = d;
        }
        return max;
    }

    private Set<LocalDate> studyDays(String uid) {
        Set<LocalDate> set = new HashSet<>();
        jdbc.query(
                "SELECT study_date FROM study_record WHERE user_id=? AND (duration_sec>0 OR words_seen>0)",
                rs -> {
                    while (rs.next()) {
                        set.add(rs.getDate(1).toLocalDate());
                    }
                    return null;
                }, uid);
        return set;
    }

    private Map<String, Object> rowOf(String uid, String date) {
        return jdbc.query("SELECT duration_sec AS durationSec, words_seen AS wordsSeen FROM study_record WHERE user_id=? AND study_date=?",
                rs -> {
                    if (!rs.next()) return null;
                    Map<String, Object> row = new HashMap<>();
                    row.put("durationSec", rs.getInt("durationSec"));
                    row.put("wordsSeen", rs.getInt("wordsSeen"));
                    return row;
                }, uid, date);
    }

    private String valid(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > 64 ? t.substring(0, 64) : t;
    }

    private int toInt(Object o) {
        if (o == null) return 0;
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (Exception e) {
            return 0;
        }
    }
}

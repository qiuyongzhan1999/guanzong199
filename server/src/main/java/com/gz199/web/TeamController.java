package com.gz199.web;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 组队打卡：创建队伍→邀请码加入→每日打卡→连续天数。
 * 无排行榜（用户明确不要）。userId 为设备 ID（X-UserId 头）。
 */
@RestController
@RequestMapping("/api/team")
public class TeamController {

    private final JdbcTemplate jdbc;
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public TeamController(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 创建队伍 */
    @PostMapping("/create")
    public Map<String, Object> create(@RequestHeader(value = "X-UserId", required = false) String userId,
                                      @RequestBody Map<String, Object> body) {
        Map<String, Object> m = new HashMap<>();
        String uid = valid(userId);
        if (uid == null) {
            m.put("ok", false);
            m.put("error", "missing user id");
            return m;
        }
        String name = body.get("name") == null ? "" : String.valueOf(body.get("name")).trim();
        if (name.isEmpty() || name.length() > 128) {
            m.put("ok", false);
            m.put("error", "队伍名称不能为空且不超过128字");
            return m;
        }
        int dailyTarget = Math.min(Math.max(toInt(body.get("dailyTargetWords")), 5), 200);
        String invite = genInviteCode();
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        // 事务：插入队伍 + 创建者为 leader
        jdbc.update("INSERT INTO team (name, creator_id, current_members, daily_target_words, start_date, invite_code) VALUES (?,?,1,?,?,?)",
                name, uid, dailyTarget, today, invite);
        Long teamId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("INSERT INTO team_member (team_id, user_id, role) VALUES (?,?,'leader')", teamId, uid);
        m.put("ok", true);
        m.put("team", teamVo(teamId, uid));
        return m;
    }

    /** 通过邀请码加入 */
    @PostMapping("/join")
    public Map<String, Object> join(@RequestHeader(value = "X-UserId", required = false) String userId,
                                    @RequestBody Map<String, Object> body) {
        Map<String, Object> m = new HashMap<>();
        String uid = valid(userId);
        if (uid == null) {
            m.put("ok", false);
            m.put("error", "missing user id");
            return m;
        }
        String code = body.get("inviteCode") == null ? "" : String.valueOf(body.get("inviteCode")).trim().toUpperCase();
        if (code.isEmpty()) {
            m.put("ok", false);
            m.put("error", "缺少邀请码");
            return m;
        }
        List<Long> teams = jdbc.query("SELECT id FROM team WHERE invite_code=? AND status=1", (rs, i) -> rs.getLong(1), code);
        if (teams.isEmpty()) {
            m.put("ok", false);
            m.put("error", "邀请码无效或队伍已解散");
            return m;
        }
        Long teamId = teams.get(0);
        Integer max = jdbc.queryForObject("SELECT max_members FROM team WHERE id=?", Integer.class, teamId);
        Integer cur = jdbc.queryForObject("SELECT current_members FROM team WHERE id=?", Integer.class, teamId);
        Integer joined = jdbc.queryForObject(
                "SELECT COUNT(*) FROM team_member WHERE team_id=? AND user_id=? AND is_active=1", Integer.class, teamId, uid);
        if (joined > 0) {
            m.put("ok", false);
            m.put("error", "你已在队伍中");
            return m;
        }
        if (cur >= max) {
            m.put("ok", false);
            m.put("error", "队伍已满");
            return m;
        }
        jdbc.update("INSERT INTO team_member (team_id, user_id, role) VALUES (?,?,'member')", teamId, uid);
        jdbc.update("UPDATE team SET current_members=current_members+1 WHERE id=?", teamId);
        m.put("ok", true);
        m.put("team", teamVo(teamId, uid));
        return m;
    }

    /** 我的队伍 */
    @GetMapping("/my-teams")
    public Map<String, Object> myTeams(@RequestHeader(value = "X-UserId", required = false) String userId) {
        Map<String, Object> m = new HashMap<>();
        String uid = valid(userId);
        if (uid == null) {
            m.put("ok", true);
            m.put("items", new ArrayList<>());
            return m;
        }
        List<Map<String, Object>> items = jdbc.query(
                "SELECT t.id, t.name, t.max_members, t.current_members, t.daily_target_words AS dailyTarget, " +
                        "t.invite_code AS inviteCode, t.status, t.created_at AS createdAt, " +
                        "tm.team_streak AS streak, tm.total_checkin_days AS totalCheckinDays, tm.role " +
                        "FROM team_member tm JOIN team t ON t.id=tm.team_id " +
                        "WHERE tm.user_id=? AND tm.is_active=1 AND t.status=1 ORDER BY t.created_at DESC",
                (rs, i) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("name", rs.getString("name"));
                    row.put("maxMembers", rs.getInt("max_members"));
                    row.put("currentMembers", rs.getInt("current_members"));
                    row.put("dailyTarget", rs.getInt("dailyTarget"));
                    row.put("inviteCode", rs.getString("inviteCode"));
                    row.put("status", rs.getInt("status"));
                    row.put("streak", rs.getInt("streak"));
                    row.put("totalCheckinDays", rs.getInt("totalCheckinDays"));
                    row.put("role", rs.getString("role"));
                    return row;
                }, uid);
        m.put("ok", true);
        m.put("items", items);
        return m;
    }

    /** 邀请码预览（加入前） */
    @GetMapping("/by-invite")
    public Map<String, Object> byInvite(@RequestParam String code) {
        Map<String, Object> m = new HashMap<>();
        String c = code == null ? "" : code.trim().toUpperCase();
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT id, name, current_members, max_members AS maxMembers, daily_target_words AS dailyTarget, status, start_date AS startDate " +
                        "FROM team WHERE invite_code=? AND status=1",
                (rs, i) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("name", rs.getString("name"));
                    row.put("currentMembers", rs.getInt("current_members"));
                    row.put("maxMembers", rs.getInt("maxMembers"));
                    row.put("dailyTarget", rs.getInt("dailyTarget"));
                    row.put("status", rs.getInt("status"));
                    row.put("startDate", rs.getString("startDate"));
                    return row;
                }, c);
        if (rows.isEmpty()) {
            m.put("ok", false);
            m.put("error", "邀请码无效");
            return m;
        }
        m.put("ok", true);
        m.put("team", rows.get(0));
        return m;
    }

    /** 打卡（学习完成后调用） */
    @PostMapping("/checkin")
    public Map<String, Object> checkin(@RequestHeader(value = "X-UserId", required = false) String userId,
                                       @RequestBody Map<String, Object> body) {
        Map<String, Object> m = new HashMap<>();
        String uid = valid(userId);
        if (uid == null) {
            m.put("ok", false);
            m.put("error", "missing user id");
            return m;
        }
        Long teamId = body.get("teamId") == null ? null : Long.valueOf(String.valueOf(body.get("teamId")));
        int words = Math.max(toInt(body.get("wordsLearned")), 0);
        if (teamId == null) {
            m.put("ok", false);
            m.put("error", "缺少队伍");
            return m;
        }
        // 必须是队伍成员
        Integer member = jdbc.queryForObject(
                "SELECT COUNT(*) FROM team_member WHERE team_id=? AND user_id=? AND is_active=1", Integer.class, teamId, uid);
        if (member == null || member == 0) {
            m.put("ok", false);
            m.put("error", "你不在该队伍中");
            return m;
        }
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        Integer target = jdbc.queryForObject("SELECT daily_target_words FROM team WHERE id=? AND status=1", Integer.class, teamId);
        if (target == null) {
            m.put("ok", false);
            m.put("error", "队伍不存在或已解散");
            return m;
        }
        Integer done = jdbc.queryForObject(
                "SELECT COUNT(*) FROM team_checkin WHERE team_id=? AND user_id=? AND checkin_date=?", Integer.class, teamId, uid, today);
        if (done != null && done > 0) {
            m.put("ok", false);
            m.put("error", "今天已打卡");
            return m;
        }
        int completed = words >= target ? 1 : 0;
        jdbc.update("INSERT INTO team_checkin (team_id, user_id, checkin_date, words_learned, is_completed) VALUES (?,?,?,?,?)",
                teamId, uid, today, words, completed);
        // 连续天数：昨天是否打卡
        String yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        Integer yest = jdbc.queryForObject(
                "SELECT COUNT(*) FROM team_checkin WHERE team_id=? AND user_id=? AND checkin_date=? AND is_completed=1",
                Integer.class, teamId, uid, yesterday);
        int streak;
        if (yest != null && yest > 0) {
            jdbc.update("UPDATE team_member SET team_streak=team_streak+1, total_checkin_days=total_checkin_days+1 WHERE team_id=? AND user_id=?",
                    teamId, uid);
            streak = jdbc.queryForObject("SELECT team_streak FROM team_member WHERE team_id=? AND user_id=?", Integer.class, teamId, uid);
        } else {
            jdbc.update("UPDATE team_member SET team_streak=1, total_checkin_days=total_checkin_days+1 WHERE team_id=? AND user_id=?",
                    teamId, uid);
            streak = 1;
        }
        m.put("ok", true);
        m.put("streak", streak);
        m.put("isCompleted", completed);
        m.put("wordsLearned", words);
        m.put("dailyTarget", target);
        return m;
    }

    /** 队伍打卡日历：返回指定月份每天打卡的成员数 */
    @GetMapping("/{teamId}/calendar")
    public Map<String, Object> calendar(@PathVariable Long teamId,
                                        @RequestParam(defaultValue = "") String month) {
        Map<String, Object> m = new HashMap<>();
        YearMonth ym;
        try {
            ym = (month == null || month.isBlank())
                    ? YearMonth.now()
                    : YearMonth.parse(month.trim());
        } catch (Exception e) {
            m.put("ok", false);
            m.put("error", "month 格式应为 yyyy-MM");
            return m;
        }
        String start = ym.atDay(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String end = ym.atEndOfMonth().format(DateTimeFormatter.ISO_LOCAL_DATE);
        List<Map<String, Object>> members = jdbc.query(
                "SELECT tm.user_id AS userId, tm.role, tm.team_streak AS streak, tm.total_checkin_days AS totalCheckinDays " +
                        "FROM team_member tm WHERE tm.team_id=? AND tm.is_active=1 ORDER BY tm.joined_at",
                (rs, i) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("userId", rs.getString("userId"));
                    row.put("role", rs.getString("role"));
                    row.put("streak", rs.getInt("streak"));
                    row.put("totalCheckinDays", rs.getInt("totalCheckinDays"));
                    return row;
                }, teamId);
        Map<String, Integer> dayCount = new HashMap<>();
        jdbc.query(
                "SELECT checkin_date AS d, COUNT(*) AS n FROM team_checkin WHERE team_id=? AND checkin_date>=? AND checkin_date<=? AND is_completed=1 GROUP BY checkin_date",
                rs -> {
                    while (rs.next()) {
                        dayCount.put(rs.getString("d"), rs.getInt("n"));
                    }
                    return null;
                }, teamId, start, end);
        // 构建日历：每日打卡成员数
        Map<String, Object> days = new LinkedHashMap<>();
        for (int day = 1; day <= ym.lengthOfMonth(); day++) {
            String d = ym.atDay(day).format(DateTimeFormatter.ISO_LOCAL_DATE);
            days.put(String.valueOf(day), dayCount.getOrDefault(d, 0));
        }
        m.put("ok", true);
        m.put("month", ym.toString());
        m.put("days", days);
        m.put("members", members);
        return m;
    }

    /** 队伍成员列表（含真实用户昵称占位：设备号前4位） */
    @GetMapping("/{teamId}/members")
    public Map<String, Object> members(@PathVariable Long teamId) {
        Map<String, Object> m = new HashMap<>();
        List<Map<String, Object>> list = jdbc.query(
                "SELECT tm.user_id AS userId, tm.role, tm.team_streak AS streak, tm.total_checkin_days AS totalCheckinDays, tm.joined_at AS joinedAt " +
                        "FROM team_member tm WHERE tm.team_id=? AND tm.is_active=1 ORDER BY tm.joined_at",
                (rs, i) -> {
                    Map<String, Object> row = new HashMap<>();
                    String uid = rs.getString("userId");
                    row.put("userId", uid);
                    row.put("displayName", uid.length() > 4 ? "同学" + uid.substring(0, 4) : "同学" + uid);
                    row.put("role", rs.getString("role"));
                    row.put("streak", rs.getInt("streak"));
                    row.put("totalCheckinDays", rs.getInt("totalCheckinDays"));
                    row.put("joinedAt", rs.getString("joinedAt"));
                    return row;
                }, teamId);
        m.put("ok", true);
        m.put("items", list);
        return m;
    }

    private Map<String, Object> teamVo(Long teamId, String uid) {
        return jdbc.query(
                "SELECT id, name, creator_id AS creatorId, max_members AS maxMembers, current_members AS currentMembers, " +
                        "daily_target_words AS dailyTarget, start_date AS startDate, status, invite_code AS inviteCode " +
                        "FROM team WHERE id=?",
                rs -> {
                    if (!rs.next()) return null;
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("name", rs.getString("name"));
                    row.put("creatorId", rs.getString("creatorId"));
                    row.put("maxMembers", rs.getInt("maxMembers"));
                    row.put("currentMembers", rs.getInt("currentMembers"));
                    row.put("dailyTarget", rs.getInt("dailyTarget"));
                    row.put("startDate", rs.getString("startDate"));
                    row.put("status", rs.getInt("status"));
                    row.put("inviteCode", rs.getString("inviteCode"));
                    return row;
                }, teamId);
    }

    private String genInviteCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            String code = sb.toString();
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM team WHERE invite_code=?", Integer.class, code);
            if (n == null || n == 0) return code;
        }
        return String.valueOf(System.currentTimeMillis()).substring(4);
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

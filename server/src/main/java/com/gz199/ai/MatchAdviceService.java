package com.gz199.ai;

import com.gz199.config.DeepSeekProperties;
import com.gz199.data.CohortYears;
import com.gz199.data.MysqlSchoolRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 智能择校：用库内近年分数打「稳 / 冲 / 难」并排序，DeepSeek 只根据这张表写建议。
 * 不联网，不要求模型输出 JSON。
 */
@Service
public class MatchAdviceService {
    private static final Logger log = LoggerFactory.getLogger(MatchAdviceService.class);
    private static final int ADVICE_ROW_CAP = 60;
    /** 下限至少高出对照分这么多分，才算「稳」。 */
    private static final int SAFE_GAP = 10;
    private static final Set<String> B_PROVINCES = Set.of(
            "内蒙古", "广西", "海南", "贵州", "云南", "西藏", "甘肃", "青海", "宁夏", "新疆"
    );
    private static final Map<String, String> MAJOR_NAMES = Map.of(
            "125100", "工商管理",
            "125200", "公共管理",
            "125300", "会计",
            "125400", "旅游管理",
            "125500", "图书情报",
            "125601", "工程管理",
            "125602", "项目管理",
            "125603", "工业工程与管理",
            "125604", "物流工程与管理",
            "125700", "审计"
    );

    private final MysqlSchoolRepository mysql;
    private final DeepSeekClient deepSeek;
    private final DeepSeekProperties deepSeekProps;
    private volatile String promptCache;

    public MatchAdviceService(
            MysqlSchoolRepository mysql,
            DeepSeekClient deepSeek,
            DeepSeekProperties deepSeekProps
    ) {
        this.mysql = mysql;
        this.deepSeek = deepSeek;
        this.deepSeekProps = deepSeekProps;
    }

    /** 只排序打标签，不调模型（进页先出列表）。 */
    public Map<String, Object> rank(Map<String, Object> body) {
        Map<String, Object> out = buildRank(body);
        out.put("advice", null);
        out.put("adviceBlocks", List.of());
        return out;
    }

    /** 排序后写建议（可二次请求，前端先展示列表再拉建议）。 */
    public Map<String, Object> advise(Map<String, Object> body) {
        Map<String, Object> out = buildRank(body);
        return fillAdvice(out);
    }

    /** 流式择校建议：onDelta 收到逐块文本，返回完整建议文本。无法生成时抛异常（消息可直接展示）。 */
    @SuppressWarnings("unchecked")
    public String streamAdvice(Map<String, Object> body, java.util.function.Consumer<String> onDelta) throws Exception {
        Map<String, Object> out = buildRank(body);
        if (out.get("error") != null) {
            throw new IllegalStateException(str(out.get("error")));
        }
        List<Map<String, Object>> ranked = (List<Map<String, Object>>) out.getOrDefault("ranked", List.of());
        List<Map<String, Object>> unmatched = (List<Map<String, Object>>) out.getOrDefault("unmatched", List.of());

        if (ranked.isEmpty() && unmatched.isEmpty()) {
            return "所选省份里，库中没有这个专业、这个学习方式的学校。";
        }
        if (ranked.isEmpty()) {
            return "这些学校还没有复试线或拟录取最低分，无法分成稳 / 冲 / 难。有分数后再排。";
        }
        if (!deepSeekProps.canCall()) {
            throw new IllegalStateException("未配置 DEEPSEEK_API_KEY，暂时无法生成文字建议。");
        }
        return deepSeek.streamChat(systemPrompt(), userPrompt(
                (Integer) out.get("scoreMin"),
                (Integer) out.get("scoreMax"),
                str(out.get("majorName")),
                str(out.get("studyModeLabel")),
                stringList(out.get("provinces")),
                str(out.get("nationNote")),
                ranked,
                unmatched,
                asInt(out.get("steadyCount")) == null ? 0 : asInt(out.get("steadyCount")),
                asInt(out.get("reachCount")) == null ? 0 : asInt(out.get("reachCount")),
                asInt(out.get("hardCount")) == null ? 0 : asInt(out.get("hardCount"))
        ), 4096, onDelta);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fillAdvice(Map<String, Object> out) {
        if (out.get("error") != null) return out;
        List<Map<String, Object>> ranked = (List<Map<String, Object>>) out.getOrDefault("ranked", List.of());
        List<Map<String, Object>> unmatched = (List<Map<String, Object>>) out.getOrDefault("unmatched", List.of());

        if (ranked.isEmpty() && unmatched.isEmpty()) {
            putAdvice(out, "所选省份里，库中没有这个专业、这个学习方式的学校。");
            return out;
        }
        if (ranked.isEmpty()) {
            putAdvice(out, "这些学校还没有复试线或拟录取最低分，无法分成稳 / 冲 / 难。有分数后再排。");
            return out;
        }

        if (!deepSeekProps.canCall()) {
            out.put("advice", null);
            out.put("adviceBlocks", List.of());
            out.put("adviceNote", "未配置 DEEPSEEK_API_KEY。下面已按稳 → 冲 → 难排好，没有生成文字建议。");
            return out;
        }

        try {
            String advice = deepSeek.chat(systemPrompt(), userPrompt(
                    (Integer) out.get("scoreMin"),
                    (Integer) out.get("scoreMax"),
                    str(out.get("majorName")),
                    str(out.get("studyModeLabel")),
                    stringList(out.get("provinces")),
                    str(out.get("nationNote")),
                    ranked,
                    unmatched,
                    asInt(out.get("steadyCount")) == null ? 0 : asInt(out.get("steadyCount")),
                    asInt(out.get("reachCount")) == null ? 0 : asInt(out.get("reachCount")),
                    asInt(out.get("hardCount")) == null ? 0 : asInt(out.get("hardCount"))
            ));
            putAdvice(out, advice);
        } catch (Exception e) {
            log.warn("match advice failed: {}", e.getMessage());
            out.put("advice", null);
            out.put("adviceBlocks", List.of());
            out.put("adviceNote", "DeepSeek 建议暂时没生成，列表仍按稳 → 冲 → 难排列。");
        }
        return out;
    }

    private Map<String, Object> buildRank(Map<String, Object> body) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("disclaimer", "建议仅供参考，招生计划和分数线以院校官方公告为准。");

        if (!mysql.available()) {
            out.put("error", "数据库还没有院校数据，无法排序。");
            out.put("ranked", List.of());
            out.put("unmatched", List.of());
            return out;
        }

        String majorCode = str(body.get("majorCode"));
        String studyMode = str(body.get("studyMode"));
        if (studyMode.isBlank()) studyMode = "fulltime";
        Integer scoreMin = asInt(body.get("scoreMin"));
        Integer scoreMax = asInt(body.get("scoreMax"));
        Integer score = asInt(body.get("score"));
        if (scoreMin == null) scoreMin = score;
        if (scoreMax == null) scoreMax = score;
        if (scoreMin != null && scoreMax != null && scoreMin > scoreMax) {
            int swap = scoreMin;
            scoreMin = scoreMax;
            scoreMax = swap;
        }
        List<String> provinces = stringList(body.get("provinces"));
        List<String> codes = expandMajor(majorCode);

        if (codes.isEmpty()) {
            out.put("error", "请选择专业。");
            out.put("ranked", List.of());
            out.put("unmatched", List.of());
            return out;
        }
        if (!"fulltime".equals(studyMode) && !"parttime".equals(studyMode)) {
            out.put("error", "学习方式只能是全日制或非全日制。");
            out.put("ranked", List.of());
            out.put("unmatched", List.of());
            return out;
        }
        if (scoreMin == null || scoreMax == null || scoreMin < 1 || scoreMax > 300) {
            out.put("error", "请填写预估分数区间，下限和上限都要在 1–300（管综 200 + 英语 100）。");
            out.put("ranked", List.of());
            out.put("unmatched", List.of());
            return out;
        }

        List<Map<String, Object>> yearRows = mysql.listYearStatsForMatch(codes, studyMode, provinces);
        List<Map<String, Object>> admitRows = mysql.listAdmissionsForMatch(codes, studyMode, provinces);
        List<Map<String, Object>> catalogRows = mysql.listCatalogForMatch(codes, studyMode, provinces);

        Map<String, List<Map<String, Object>>> byKey = new LinkedHashMap<>();
        for (Map<String, Object> row : yearRows) {
            byKey.computeIfAbsent(keyOf(row), k -> new ArrayList<>()).add(row);
        }
        for (Map<String, Object> row : admitRows) {
            List<Map<String, Object>> bucket = byKey.computeIfAbsent(keyOf(row), k -> new ArrayList<>());
            int y = yearOf(row);
            boolean exists = bucket.stream().anyMatch(item -> yearOf(item) == y);
            if (!exists) bucket.add(row);
        }

        List<Map<String, Object>> ranked = new ArrayList<>();
        List<Map<String, Object>> unmatched = new ArrayList<>();
        for (List<Map<String, Object>> years : byKey.values()) {
            Map<String, Object> card = toCard(years, scoreMin, scoreMax);
            if (card.get("benchmark") == null) unmatched.add(card);
            else ranked.add(card);
        }

        Set<String> seen = new java.util.HashSet<>();
        ranked.forEach(row -> seen.add(keyOf(row)));
        unmatched.forEach(row -> seen.add(keyOf(row)));
        for (Map<String, Object> row : catalogRows) {
            if (seen.add(keyOf(row))) {
                unmatched.add(emptyCard(row));
            }
        }

        ranked.sort(Comparator
                .comparingInt((Map<String, Object> row) -> bandRank(str(row.get("band"))))
                .thenComparing(Comparator.comparingInt((Map<String, Object> row) -> (Integer) row.get("gap")).reversed())
                .thenComparing(row -> str(row.get("name"))));
        int order = 1;
        int steady = 0;
        int reach = 0;
        int hard = 0;
        for (Map<String, Object> row : ranked) {
            row.put("order", order++);
            String band = str(row.get("band"));
            if ("稳".equals(band)) steady++;
            else if ("冲".equals(band)) reach++;
            else if ("难".equals(band)) hard++;
        }
        unmatched.sort(Comparator.comparing(row -> str(row.get("name"))));

        String majorName = majorName(majorCode);
        String modeLabel = "parttime".equals(studyMode) ? "非全日制" : "全日制";
        out.put("scoreMin", scoreMin);
        out.put("scoreMax", scoreMax);
        out.put("majorCode", majorCode);
        out.put("majorName", majorName);
        out.put("studyMode", studyMode);
        out.put("studyModeLabel", modeLabel);
        out.put("provinces", provinces);
        out.put("ranked", ranked);
        out.put("unmatched", unmatched);
        out.put("rankedCount", ranked.size());
        out.put("unmatchedCount", unmatched.size());
        out.put("steadyCount", steady);
        out.put("reachCount", reach);
        out.put("hardCount", hard);

        String nationNote = nationNote(codes, provinces);
        out.put("nationNote", nationNote);
        return out;
    }

    private static void putAdvice(Map<String, Object> out, String advice) {
        String text = advice == null ? "" : advice.trim();
        out.put("advice", text);
        out.put("adviceBlocks", layout(text));
    }

    /** 只保留对照表；丢掉总览 / 稳冲难 / 注意事项等段落。 */
    static List<Map<String, String>> layout(String raw) {
        String text = raw == null ? "" : raw.replace("\r\n", "\n").trim();
        text = text.replaceAll("(?m)^```[a-zA-Z]*\\s*", "").replace("```", "");
        text = text.replace("**", "").replaceAll("(?m)^#{1,6}\\s*", "").trim();

        StringBuilder table = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            String trimmed = line.trim();
            if (!isTableLine(trimmed)) continue;
            if (table.length() > 0) table.append('\n');
            table.append(trimmed);
        }

        List<Map<String, String>> blocks = new ArrayList<>();
        String body = table.toString().trim();
        if (!body.isEmpty()) {
            Map<String, String> block = new LinkedHashMap<>();
            block.put("title", "对照表");
            block.put("body", body);
            blocks.add(block);
        } else if (!text.isBlank()) {
            // 模型没按表格输出时，整段仍展示，避免空白
            Map<String, String> one = new LinkedHashMap<>();
            one.put("title", "对照表");
            one.put("body", text);
            blocks.add(one);
        }
        return blocks;
    }

    private static boolean isTableLine(String line) {
        if (line == null || !line.startsWith("|")) return false;
        String inner = line.replace("|", "").replace("-", "").replace(":", "").trim();
        return !inner.isEmpty() || line.contains("---");
    }

    private Map<String, Object> toCard(List<Map<String, Object>> years, int scoreMin, int scoreMax) {
        int cohort = CohortYears.targetCohort();
        List<Map<String, Object>> inWindow = years.stream()
                .filter(row -> CohortYears.inWindow(asInt(row.get("year")), cohort))
                .sorted(Comparator.comparingInt((Map<String, Object> row) -> yearOf(row)).reversed())
                .toList();
        if (inWindow.isEmpty()) {
            return emptyCard(years.get(0));
        }
        years = inWindow;
        Map<String, Object> minRow = null;
        Map<String, Object> lineRow = null;
        for (Map<String, Object> row : years) {
            if (minRow == null && asInt(row.get("minScore")) != null) minRow = row;
            if (lineRow == null && asInt(row.get("reexamMinScore")) != null) lineRow = row;
        }
        Map<String, Object> base = years.get(0);
        Map<String, Object> used = minRow != null ? minRow : lineRow;
        if (used == null) return emptyCard(base);

        boolean lineOnly = minRow == null;
        int benchmark = lineOnly ? asInt(used.get("reexamMinScore")) : asInt(used.get("minScore"));
        int gap = scoreMin - benchmark;
        String band = bandOf(benchmark, scoreMin, scoreMax);
        Integer reexam = asInt(used.get("reexamMinScore"));
        Integer minScore = asInt(used.get("minScore"));
        Integer admit = asInt(used.get("admitCount"));
        Integer prevAdmit = previousAdmit(years, yearOf(used));

        List<String> signals = new ArrayList<>();
        if (!lineOnly && reexam != null && minScore - reexam >= 20) {
            signals.add("复试线虚低：拟录取最低分高出复试线 " + (minScore - reexam) + " 分");
        }
        if (admit != null && admit < 10) {
            signals.add("录取人数很少（" + admit + " 人），一年波动会很大");
        } else if (admit != null && admit < 20) {
            signals.add("录取人数偏少（" + admit + " 人）");
        }
        if (admit != null && prevAdmit != null && prevAdmit > 0) {
            double drop = (prevAdmit - admit) / (double) prevAdmit;
            if (drop >= 0.25) {
                signals.add("缩招：录取人数从 " + prevAdmit + " 降到 " + admit);
            }
        }
        if (lineOnly) {
            signals.add("仅复试线，没有拟录取最低分，难度可能被低估");
        }

        Map<String, Object> card = baseCard(used);
        card.put("year", yearOf(used));
        card.put("benchmark", benchmark);
        card.put("benchmarkLabel", lineOnly ? "复试线" : "拟录取最低分");
        card.put("lineOnly", lineOnly);
        card.put("gap", gap);
        card.put("gapNote", gapNote(benchmark, scoreMin, scoreMax));
        card.put("reexamMinScore", reexam);
        card.put("minScore", minScore);
        card.put("maxScore", asInt(used.get("maxScore")));
        card.put("admitCount", admit);
        Integer reexamCount = asInt(used.get("reexamCount"));
        card.put("reexamCount", reexamCount);
        String recentMin = recentExtreme(years, "minScore", lineOnly ? "reexamMinScore" : "minScore", true);
        String recentMax = recentExtreme(years, "maxScore", "maxScore", false);
        String recentMinAvg = recentAverage(years, "minScore", lineOnly ? "reexamMinScore" : "minScore");
        String admitRate = recentAdmitRate(years);
        card.put("recentMinScores", recentMin);
        card.put("recentMaxScores", recentMax);
        card.put("recentMinAvg", recentMinAvg);
        card.put("admitRate", admitRate);
        card.put("signals", signals);
        card.put("band", band);
        card.put("evidence", evidenceOf(used, benchmark, scoreMin, scoreMax, admit, lineOnly, band, recentMin, recentMax, admitRate));
        return card;
    }

    /**
     * 近三年只取一个数：wantMin=true 取各年有效分中的最小；false 取最大。
     * 每年优先 primary，没有再用 fallback。
     */
    private static String recentExtreme(List<Map<String, Object>> years, String primary, String fallback, boolean wantMin) {
        List<Map<String, Object>> sorted = years.stream()
                .sorted(Comparator.comparingInt(MatchAdviceService::yearOf))
                .toList();
        Integer extreme = null;
        int from = Math.max(0, sorted.size() - 3);
        for (int i = from; i < sorted.size(); i++) {
            Map<String, Object> row = sorted.get(i);
            Integer v = asInt(row.get(primary));
            if (v == null) v = asInt(row.get(fallback));
            if (v == null) continue;
            if (extreme == null) extreme = v;
            else if (wantMin) extreme = Math.min(extreme, v);
            else extreme = Math.max(extreme, v);
        }
        return extreme == null ? "—" : String.valueOf(extreme);
    }

    /** 近三年各年最低分的算术平均（四舍五入为整数）。 */
    private static String recentAverage(List<Map<String, Object>> years, String primary, String fallback) {
        List<Map<String, Object>> sorted = years.stream()
                .sorted(Comparator.comparingInt(MatchAdviceService::yearOf))
                .toList();
        int from = Math.max(0, sorted.size() - 3);
        double sum = 0;
        int n = 0;
        for (int i = from; i < sorted.size(); i++) {
            Map<String, Object> row = sorted.get(i);
            Integer v = asInt(row.get(primary));
            if (v == null) v = asInt(row.get(fallback));
            if (v == null) continue;
            sum += v;
            n++;
        }
        if (n == 0) return "—";
        return String.valueOf((int) Math.round(sum / n));
    }

    private static String admitRateOf(Integer admit, Integer reexamCount) {
        if (admit == null || reexamCount == null || reexamCount <= 0) return "—";
        long pct = Math.round(100.0 * admit / reexamCount);
        if (pct > 100) pct = 100;
        return pct + "%";
    }

    /** 近三年平均录取率：有录取和进复试人数的年份，先算各年录取/进复试，再取平均。 */
    private static String recentAdmitRate(List<Map<String, Object>> years) {
        List<Map<String, Object>> sorted = years.stream()
                .sorted(Comparator.comparingInt(MatchAdviceService::yearOf))
                .toList();
        int from = Math.max(0, sorted.size() - 3);
        double sum = 0;
        int n = 0;
        for (int i = from; i < sorted.size(); i++) {
            Map<String, Object> row = sorted.get(i);
            Integer admit = asInt(row.get("admitCount"));
            Integer reexam = asInt(row.get("reexamCount"));
            if (admit == null || reexam == null || reexam <= 0) continue;
            double pct = 100.0 * admit / reexam;
            if (pct > 100) pct = 100;
            sum += pct;
            n++;
        }
        if (n == 0) return "—";
        return Math.round(sum / n) + "%";
    }

    private static String evidenceOf(
            Map<String, Object> used,
            int benchmark,
            int scoreMin,
            int scoreMax,
            Integer admit,
            boolean lineOnly,
            String band,
            String recentMin,
            String recentMax,
            String admitRate
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append(yearOf(used)).append("年")
                .append(lineOnly ? "复试线" : "拟录取最低分")
                .append(benchmark)
                .append("，你区间")
                .append(scoreMin).append("–").append(scoreMax)
                .append("，").append(gapNote(benchmark, scoreMin, scoreMax))
                .append("，档位").append(band)
                .append("，近三年最低分").append(recentMin)
                .append("，近三年最高分").append(recentMax)
                .append("，近三年平均录取率").append(admitRate);
        if (admit != null) sb.append("，录取").append(admit).append("人");
        return sb.toString();
    }

    private static Integer previousAdmit(List<Map<String, Object>> years, int usedYear) {
        Integer bestYear = null;
        Integer bestAdmit = null;
        for (Map<String, Object> row : years) {
            int y = yearOf(row);
            Integer admit = asInt(row.get("admitCount"));
            if (y >= usedYear || admit == null) continue;
            if (bestYear == null || y > bestYear) {
                bestYear = y;
                bestAdmit = admit;
            }
        }
        return bestAdmit;
    }

    private static Map<String, Object> emptyCard(Map<String, Object> row) {
        Map<String, Object> card = baseCard(row);
        card.put("benchmark", null);
        card.put("gap", null);
        card.put("band", "无法比较");
        card.put("signals", List.of("缺少复试线或拟录取最低分"));
        card.put("lineOnly", false);
        return card;
    }

    private static Map<String, Object> baseCard(Map<String, Object> row) {
        Map<String, Object> card = new LinkedHashMap<>();
        String majorCode = str(row.get("majorCode"));
        card.put("schId", str(row.get("schId")));
        card.put("schoolCode", str(row.get("schoolCode")));
        card.put("name", str(row.get("name")));
        card.put("province", str(row.get("province")));
        String city = str(row.get("city"));
        if (!city.isBlank()) card.put("city", city);
        card.put("logo", row.get("logo"));
        card.put("majorCode", majorCode);
        String named = str(row.get("majorName"));
        card.put("majorName", named.isBlank() ? majorName(majorCode) : named);
        card.put("studyMode", str(row.get("studyMode")));
        card.put("doubleFirst", truthy(row.get("doubleFirst")));
        card.put("selfLine", truthy(row.get("selfLine")));
        return card;
    }

    /**
     * 稳 / 冲 / 难：只保留三档。
     * 难：上限仍低于对照分；稳：下限至少高出对照分 SAFE_GAP；其余为冲。
     */
    private static String bandOf(int benchmark, int scoreMin, int scoreMax) {
        if (scoreMax < benchmark) return "难";
        if (scoreMin - benchmark >= SAFE_GAP) return "稳";
        return "冲";
    }

    private static int bandRank(String band) {
        if ("稳".equals(band)) return 0;
        if ("冲".equals(band)) return 1;
        if ("难".equals(band)) return 2;
        return 3;
    }

    private static String gapNote(int benchmark, int scoreMin, int scoreMax) {
        if (scoreMax < benchmark) return "上限低于对照分 " + (benchmark - scoreMax) + " 分";
        if (scoreMin - benchmark >= SAFE_GAP) return "下限高出对照分 " + (scoreMin - benchmark) + " 分";
        if (benchmark >= scoreMin && benchmark <= scoreMax) return "对照分落在你的区间内";
        if (scoreMin < benchmark) return "下限低 " + (benchmark - scoreMin) + " 分，上限可够到";
        return "下限高出对照分 " + (scoreMin - benchmark) + " 分";
    }

    private String nationNote(List<String> codes, List<String> provinces) {
        Integer year = mysql.latestNationYear();
        if (year == null) return "";
        Map<String, Object> row = null;
        for (String code : codes) {
            row = mysql.findNationRow(year, code);
            if (row != null) break;
        }
        if (row == null) return "";
        boolean anyB = provinces.isEmpty() || provinces.stream().anyMatch(this::isB);
        boolean anyA = provinces.isEmpty() || provinces.stream().anyMatch(p -> !isB(p));
        if (anyA && anyB) {
            return year + " 年国家线 A 类 " + row.get("aTotal") + "，B 类 " + row.get("bTotal")
                    + "。所选省份两类都有，不能合成一条线。";
        }
        if (anyB) {
            return year + " 年国家线 B 类总分 " + row.get("bTotal") + "。";
        }
        return year + " 年国家线 A 类总分 " + row.get("aTotal") + "。";
    }

    private boolean isB(String province) {
        String name = str(province);
        for (String b : B_PROVINCES) {
            if (name.startsWith(b)) return true;
        }
        return false;
    }

    private String userPrompt(
            int scoreMin,
            int scoreMax,
            String majorName,
            String modeLabel,
            List<String> provinces,
            String nationNote,
            List<Map<String, Object>> ranked,
            List<Map<String, Object>> unmatched,
            int steady,
            int reach,
            int hard
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("我的分数区间：").append(scoreMin).append("–").append(scoreMax).append(" / 300\n");
        int cohort = CohortYears.targetCohort();
        sb.append("对照年份：近").append(CohortYears.SPAN).append("年（")
                .append(CohortYears.windowStart(cohort)).append("–").append(cohort)
                .append("）。年份=入学年；初试在上一自然年12月。例：").append(cohort)
                .append(" 初试 ").append(CohortYears.examYearOf(cohort)).append("年12月；")
                .append(cohort + 1).append(" 尚未开考。\n");
        sb.append("报考专业：").append(majorName).append("\n");
        sb.append("学习方式：").append(modeLabel).append("\n");
        sb.append("省份：").append(provinces.isEmpty() ? "不限" : String.join("、", provinces)).append("\n");
        if (nationNote != null && !nationNote.isBlank()) sb.append(nationNote).append("\n");
        sb.append("标签规则：下限高出对照分≥").append(SAFE_GAP).append(" 为稳；上限低于对照分为难；其余为冲。\n");
        sb.append("共 ").append(ranked.size()).append(" 所能比较：稳 ").append(steady)
                .append("、冲 ").append(reach).append("、难 ").append(hard).append("。\n");
        sb.append("顺序已按稳 → 冲 → 难排好，不要改顺序、不要改标签。\n\n");
        sb.append("<ranked_list>\n");
        int n = 0;
        for (Map<String, Object> row : ranked) {
            if (n >= ADVICE_ROW_CAP) {
                sb.append("…其余 ").append(ranked.size() - n).append(" 所未列出，不要编造。\n");
                break;
            }
            n++;
            sb.append(row.get("order")).append(". ")
                    .append(row.get("name")).append(" ")
                    .append(row.get("province")).append(" ")
                    .append(row.get("majorName")).append(" ")
                    .append("标签").append(row.get("band"))
                    .append(" 近三年最低分").append(row.get("recentMinScores"))
                    .append(" 近三年最高分").append(row.get("recentMaxScores"))
                    .append(" 录取率").append(row.get("admitRate"))
                    .append(" ").append(row.get("evidence"));
            Object signals = row.get("signals");
            if (signals instanceof List<?> list && !list.isEmpty()) {
                sb.append("；").append(joinSignals(list));
            }
            sb.append("\n");
        }
        sb.append("</ranked_list>\n");
        if (!unmatched.isEmpty()) {
            sb.append("\n暂无对照分、不参与稳冲难的学校：");
            int shown = 0;
            for (Map<String, Object> row : unmatched) {
                if (shown >= 20) {
                    sb.append("等 ").append(unmatched.size()).append(" 所");
                    break;
                }
                if (shown > 0) sb.append("、");
                sb.append(row.get("name"));
                shown++;
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static String joinSignals(List<?> list) {
        StringBuilder sb = new StringBuilder();
        for (Object item : list) {
            if (sb.length() > 0) sb.append("；");
            sb.append(item);
        }
        return sb.toString();
    }

    private String systemPrompt() {
        String cached = promptCache;
        if (cached != null) return cached;
        try {
            ClassPathResource res = new ClassPathResource("skills/kaoyan-match-advice.md");
            String text = new String(res.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            promptCache = text;
            return text;
        } catch (Exception e) {
            log.warn("advice prompt missing: {}", e.getMessage());
            promptCache = "根据给定的学校表写择校建议。不要编造数字，不要输出 JSON。";
            return promptCache;
        }
    }

    private static List<String> expandMajor(String majorCode) {
        if ("1256".equals(majorCode)) {
            return List.of("125601", "125602", "125603", "125604");
        }
        if (MAJOR_NAMES.containsKey(majorCode)) return List.of(majorCode);
        return List.of();
    }

    private static String majorName(String code) {
        if ("1256".equals(code)) return "工程管理";
        return MAJOR_NAMES.getOrDefault(code, code);
    }

    private static String keyOf(Map<String, Object> row) {
        return str(row.get("schoolCode")) + "|" + str(row.get("majorCode")) + "|" + str(row.get("studyMode"));
    }

    private static int yearOf(Map<String, Object> row) {
        Integer y = asInt(row.get("year"));
        return y == null ? 0 : y;
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                String s = str(item);
                if (!s.isBlank()) out.add(s);
            }
        }
        return out;
    }

    private static boolean truthy(Object v) {
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        return "1".equals(str(v)) || "true".equalsIgnoreCase(str(v));
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static Integer asInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try {
            String s = String.valueOf(v).trim();
            if (s.isEmpty() || "null".equals(s)) return null;
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

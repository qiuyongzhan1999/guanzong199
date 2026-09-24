package com.gz199.web;

import com.gz199.ai.DeepSeekClient;
import com.gz199.ai.MatchAdviceService;
import com.gz199.config.DeepSeekProperties;
import com.gz199.data.CohortYears;
import com.gz199.data.JsonDataStore;
import com.gz199.data.MysqlSchoolRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class SchoolDataController {
    private final JsonDataStore store;
    private final MysqlSchoolRepository mysql;
    private final MatchAdviceService matchAdvice;
    private final DeepSeekClient deepSeek;
    private final DeepSeekProperties deepSeekProps;

    public SchoolDataController(
            JsonDataStore store,
            MysqlSchoolRepository mysql,
            MatchAdviceService matchAdvice,
            DeepSeekClient deepSeek,
            DeepSeekProperties deepSeekProps
    ) {
        this.store = store;
        this.mysql = mysql;
        this.matchAdvice = matchAdvice;
        this.deepSeek = deepSeek;
        this.deepSeekProps = deepSeekProps;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> m = new HashMap<>();
        m.put("ok", true);
        m.put("dataDir", store.dataDir().toString());
        m.put("deepseekConfigured", deepSeekProps.canCall());
        m.put("mysqlReady", mysql.available());
        m.put("hint", deepSeekProps.canCall()
                ? "择校建议走 DeepSeek（关闭思考与网页搜索）。详情只读数据库。"
                : "未配置 DEEPSEEK_API_KEY，择校仍可按分数排序，但没有文字建议");
        return m;
    }

    @GetMapping("/ai/ping")
    public Map<String, Object> aiPing() {
        Map<String, Object> m = new HashMap<>();
        if (!deepSeekProps.canCall()) {
            m.put("ok", false);
            m.put("error", "未配置 DEEPSEEK_API_KEY");
            return m;
        }
        try {
            m.put("ok", true);
            m.put("model", deepSeekProps.getModel());
            m.put("reply", deepSeek.ping());
            return m;
        } catch (Exception e) {
            m.put("ok", false);
            m.put("error", e.getMessage());
            return m;
        }
    }

    /** 智能择校：库内分数打稳/冲/难并立刻返回列表。 */
    @PostMapping("/match")
    public Map<String, Object> match(@RequestBody Map<String, Object> body) {
        return matchAdvice.rank(body == null ? Map.of() : body);
    }

    /** 智能择校文字建议（较慢，前端列表出来后再调）。 */
    @PostMapping("/match/advice")
    public Map<String, Object> matchAdviceBody(@RequestBody Map<String, Object> body) {
        return matchAdvice.advise(body == null ? Map.of() : body);
    }

    @GetMapping("/nation-lines")
    public List<Map<String, Object>> nationLines(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String majorCode
    ) {
        return mysql.listNationLines(year, majorCode);
    }

    @GetMapping("/school-catalog")
    public List<Map<String, Object>> schoolCatalog(@RequestParam String schoolCode) {
        return mysql.listCatalogBySchool(schoolCode);
    }

    @GetMapping("/programs")
    public List<Map<String, Object>> programs(
            @RequestParam(required = false) String schoolCode,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String majorCode,
            @RequestParam(required = false) String studyMode
    ) {
        return store.list("programs.json").stream()
                .filter(row -> matchRow(row, schoolCode, year, majorCode, studyMode))
                .collect(Collectors.toList());
    }

    @GetMapping("/admissions")
    public List<Map<String, Object>> admissions(
            @RequestParam(required = false) String schoolCode,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String majorCode,
            @RequestParam(required = false) String studyMode
    ) {
        return store.list("admissions.json").stream()
                .filter(row -> matchRow(row, schoolCode, year, majorCode, studyMode))
                .collect(Collectors.toList());
    }

    /**
     * 院校详情只读 MySQL（爬虫入库）。不再联网，也不再要求模型输出 JSON。
     */
    @GetMapping("/school-detail")
    public Map<String, Object> schoolDetail(
            @RequestParam String schoolCode,
            @RequestParam Integer year,
            @RequestParam String majorCode,
            @RequestParam String studyMode,
            @RequestParam(required = false, defaultValue = "") String province
    ) {
        List<String> codes = detailCodes(majorCode);
        boolean useMysql = mysql.available();
        Map<String, Object> pack = null;
        String usedCode = codes.get(0);
        if (useMysql) {
            for (String code : codes) {
                Map<String, Object> candidate = mysql.loadPack(schoolCode, code, studyMode);
                if (candidate != null) {
                    pack = candidate;
                    usedCode = code;
                    break;
                }
            }
        }

        List<Map<String, Object>> yearly = CohortYears.filterYearRows(yearlyOf(pack));
        int cohort = CohortYears.targetCohort();
        int lookupYear = year;
        if (!CohortYears.inWindow(lookupYear, cohort)) {
            lookupYear = cohort;
        }
        for (Map<String, Object> row : yearly) {
            if (asInt(row.get("reexam_min_score")) != null || asInt(row.get("min_score")) != null) {
                Integer ny = asInt(row.get("year"));
                if (ny != null) lookupYear = ny;
                break;
            }
        }

        Map<String, Object> program = null;
        Map<String, Object> admission = null;
        Map<String, Object> nation = null;
        if (useMysql) {
            for (String code : codes) {
                Map<String, Object> row = mysql.findProgram(schoolCode, lookupYear, code, studyMode);
                if (row != null) {
                    program = row;
                    break;
                }
            }
            for (String code : codes) {
                Map<String, Object> row = mysql.findAdmission(schoolCode, lookupYear, code, studyMode);
                if (row != null) {
                    admission = row;
                    usedCode = code;
                    break;
                }
            }
            for (String code : codes) {
                nation = mysql.findNationRow(lookupYear, code);
                if (nation != null) break;
            }
        }
        if (yearly.isEmpty() && admission != null) {
            yearly = CohortYears.filterYearRows(List.of(admissionToYear(admission)));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("program", program);
        body.put("admission", admission);
        body.put("nationLine", nation == null ? null : pickNation(nation, province));
        body.put("years", years());
        body.put("targetCohort", cohort);
        body.put("examYear", CohortYears.examYearOf(cohort));
        body.put("cohortHint", "近" + CohortYears.SPAN + "年 "
                + CohortYears.windowStart(cohort) + "–" + cohort
                + "（" + cohort + " 初试 " + CohortYears.examYearOf(cohort) + "年12月；"
                + (cohort + 1) + " 尚未开考，不展示）");
        body.put("yearlyTrend", trendOf(yearly));
        body.put("yearlyData", yearly);
        body.put("hasPack", pack != null);
        body.put("source", useMysql ? "mysql" : "none");
        body.put("needsEnrich", false);
        body.put("majorCode", usedCode);
        body.put("majorOptions", majorOptionsOf(schoolCode, studyMode, useMysql));
        if (pack != null) {
            body.put("examRules", pack.get("exam_rules"));
            body.put("dataSource", pack.get("data_source"));
            body.put("majorInfo", pack.get("major_info"));
        }
        return body;
    }

    @GetMapping("/schools")
    public Map<String, Object> schools(
            @RequestParam(defaultValue = "125300") String majorCode,
            @RequestParam(defaultValue = "fulltime") String studyMode,
            @RequestParam(required = false, defaultValue = "") String province,
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false, defaultValue = "all") String trait,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("page", page);
        body.put("pageSize", pageSize);
        body.put("source", "mysql");
        if (!mysql.available()) {
            body.put("items", List.of());
            body.put("total", 0);
            return body;
        }
        String code = detailCodes(majorCode).get(0);
        int limit = Math.max(1, Math.min(pageSize, 50));
        int offset = Math.max(0, page - 1) * limit;
        body.put("items", mysql.listSchools(code, studyMode, province, keyword, trait, limit, offset));
        body.put("total", mysql.countSchools(code, studyMode, province, keyword, trait));
        return body;
    }

    /** 该校在该学习方式下乐学喵实际有数据的专业；为空则前端不展示任何专业卡片。 */
    private List<Map<String, Object>> majorOptionsOf(String schoolCode, String studyMode, boolean useMysql) {
        List<Map<String, Object>> options = new ArrayList<>();
        if (!useMysql) return options;
        for (Map<String, Object> row : mysql.listMajorsBySchool(schoolCode, studyMode)) {
            String raw = String.valueOf(row.get("majorCode"));
            String display = displayMajorCode(raw);
            Map<String, Object> m = new HashMap<>();
            m.put("code", display);
            m.put("name", majorNameOf(display));
            options.add(m);
        }
        return options;
    }

    private static String displayMajorCode(String code) {
        if (code != null && code.startsWith("1256")) return "1256";
        return code;
    }

    private static final Map<String, String> MAJOR_NAMES = Map.of(
            "125100", "工商管理MBA",
            "125200", "公共管理MPA",
            "125300", "会计MPAcc",
            "125400", "旅游管理MTA",
            "125500", "图书情报MLis",
            "1256", "工程管理MEM",
            "125700", "审计MAud"
    );

    private static String majorNameOf(String code) {
        String name = MAJOR_NAMES.get(code);
        return name == null ? code : name;
    }

    @GetMapping("/years")
    public List<Integer> years() {
        return CohortYears.windowYears();
    }

    private static List<String> detailCodes(String majorCode) {
        // 爬虫把 MEM 四个方向（125601-125604）统一归到 125600 入库；
        // 前端可能传 1256 / 125600 / 12560x，统一先查 125600，再用具体方向码兜底。
        if (majorCode != null && majorCode.startsWith("1256")) {
            java.util.LinkedHashSet<String> ordered = new java.util.LinkedHashSet<>();
            ordered.add("125600");
            if (!"125600".equals(majorCode)) {
                ordered.add(majorCode);
            }
            for (String c : new String[]{"125601", "125602", "125603", "125604"}) {
                ordered.add(c);
            }
            return new ArrayList<>(ordered);
        }
        return List.of(majorCode);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> yearlyOf(Map<String, Object> pack) {
        if (pack == null) return new ArrayList<>();
        Object raw = pack.get("yearly_data");
        if (!(raw instanceof List<?> list)) return new ArrayList<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                out.add((Map<String, Object>) map);
            }
        }
        return out;
    }

    private static Map<String, Object> admissionToYear(Map<String, Object> admission) {
        Map<String, Object> row = new HashMap<>();
        row.put("year", admission.get("year"));
        row.put("reexam_min_score", admission.get("reexamMinScore"));
        row.put("min_score", admission.get("minScore"));
        row.put("max_score", admission.get("maxScore"));
        row.put("admit_count", admission.get("admitCount"));
        row.put("reexam_count", admission.get("reexamCount"));
        row.put("score_bands", admission.get("scoreBands"));
        row.put("admission_source_url", admission.get("sourceUrl"));
        row.put("admission_source_name", admission.get("sourceName"));
        row.put("pending_note", admission.get("pendingNote"));
        return row;
    }

    private static List<Map<String, Object>> trendOf(List<Map<String, Object>> yearly) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : yearly) {
            Map<String, Object> pt = new HashMap<>();
            pt.put("year", asInt(row.get("year")));
            pt.put("reexamMinScore", asInt(row.get("reexam_min_score")));
            pt.put("minScore", asInt(row.get("min_score")));
            pt.put("maxScore", asInt(row.get("max_score")));
            pt.put("admitCount", asInt(row.get("admit_count")));
            pt.put("reexamCount", asInt(row.get("reexam_count")));
            out.add(pt);
        }
        out.sort(Comparator.comparingInt(pt -> {
            Integer y = asInt(pt.get("year"));
            return y == null ? 0 : y;
        }));
        return out;
    }

    private static Map<String, Object> pickNation(Map<String, Object> row, String province) {
        boolean isB = isBProvince(province);
        Map<String, Object> out = new HashMap<>();
        out.put("year", row.get("year"));
        out.put("zone", isB ? "B类" : "A类");
        out.put("total", isB ? row.get("bTotal") : row.get("aTotal"));
        out.put("english", isB ? row.get("bEnglish") : row.get("aEnglish"));
        out.put("comprehensive", isB ? row.get("bComprehensive") : row.get("aComprehensive"));
        Object total = isB ? row.get("bTotal") : row.get("aTotal");
        Object en = isB ? row.get("bEnglish") : row.get("aEnglish");
        Object comp = isB ? row.get("bComprehensive") : row.get("aComprehensive");
        out.put("text", total + "（" + (isB ? "B" : "A") + "类）");
        out.put("detail", "英" + en + " / 综" + comp);
        out.put("sourceUrl", row.get("sourceUrl"));
        out.put("sourceName", row.get("sourceName"));
        out.put("syncedAt", row.get("syncedAt"));
        return out;
    }

    private static boolean isBProvince(String province) {
        if (province == null || province.isBlank()) return false;
        return "内蒙古,广西,海南,贵州,云南,西藏,甘肃,青海,宁夏,新疆".contains(province);
    }

    private static boolean matchRow(
            Map<String, Object> row,
            String schoolCode,
            Integer year,
            String majorCode,
            String studyMode
    ) {
        if (schoolCode != null && !schoolCode.equals(String.valueOf(row.get("schoolCode")))) return false;
        if (year != null && !Objects.equals(asInt(row.get("year")), year)) return false;
        if (majorCode != null && !majorCode.equals(String.valueOf(row.get("majorCode")))) return false;
        if (studyMode != null && !studyMode.equals(String.valueOf(row.get("studyMode")))) return false;
        return true;
    }

    @SuppressWarnings("unchecked")
    private static boolean containsCode(Object majorCodes, String code) {
        if (!(majorCodes instanceof List)) return false;
        return ((List<?>) majorCodes).stream().anyMatch(c -> code.equals(String.valueOf(c)));
    }

    private static Integer asInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

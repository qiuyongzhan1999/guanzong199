package com.gz199.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gz199.config.DeepSeekProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 作文批改（名师版）：扣分账单、阶梯修改、识别标志、口语/中式英语升级。
 * 类型：argument / thesis / en_letter / en_chart。
 */
@Service
public class EssayGradeService {
    private static final Logger log = LoggerFactory.getLogger(EssayGradeService.class);
    private static final Set<String> TYPES = Set.of("argument", "thesis", "en_letter", "en_chart");
    private static final int MIN_CHARS = 80;
    private static final int MAX_CHARS = 8000;
    private static final int ESSAY_MAX_TOKENS = 8192;
    private static final int MAX_BASE64_CHARS = 10_000_000;
    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    );

    private final DeepSeekClient deepSeek;
    private final DeepSeekProperties deepSeekProps;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ConcurrentHashMap<String, String> promptCache = new ConcurrentHashMap<>();

    public EssayGradeService(DeepSeekClient deepSeek, DeepSeekProperties deepSeekProps) {
        this.deepSeek = deepSeek;
        this.deepSeekProps = deepSeekProps;
    }

    public Map<String, Object> grade(Map<String, Object> body) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("disclaimer", "AI 批改仅供练习参考，不代表官方阅卷结果。");

        if (!deepSeekProps.canCall()) {
            out.put("error", "未配置 DEEPSEEK_API_KEY，暂时无法批改。");
            return out;
        }

        String type = str(body.get("type")).toLowerCase();
        if (!TYPES.contains(type)) {
            out.put("error", "请选择题型：论证有效性分析、论说文、英语小作文或英语大作文。");
            return out;
        }

        String essay = str(body.get("essay"));
        String essayImage = toDataUrl(str(body.get("essayImageBase64")), str(body.get("essayMimeType")));
        String materialImage = toDataUrl(str(body.get("materialImageBase64")), str(body.get("materialMimeType")));
        boolean hasEssayImage = !essayImage.isBlank();
        boolean hasMaterialImage = !materialImage.isBlank();

        if (!hasEssayImage && essay.length() < MIN_CHARS) {
            out.put("error", "请粘贴作文文字，或上传作文照片（文字建议不少于 " + MIN_CHARS + " 字）。");
            return out;
        }
        if (essay.length() > MAX_CHARS) {
            out.put("error", "正文过长，请控制在 " + MAX_CHARS + " 字以内。");
            return out;
        }
        if (hasEssayImage && str(body.get("essayImageBase64")).length() > MAX_BASE64_CHARS) {
            out.put("error", "作文图片太大，请压缩后再试。");
            return out;
        }
        if (hasMaterialImage && str(body.get("materialImageBase64")).length() > MAX_BASE64_CHARS) {
            out.put("error", "题目图片太大，请压缩后再试。");
            return out;
        }

        String material = str(body.get("material"));
        String topic = str(body.get("topic"));
        String requirement = str(body.get("requirement"));
        String taskType = str(body.get("taskType"));
        int wordCount = essay.isBlank() ? 0 : countWords(essay, type.startsWith("en_"));

        out.put("type", type);
        out.put("typeLabel", typeLabel(type));
        out.put("wordCount", wordCount);
        out.put("essay", essay);
        out.put("hasEssayImage", hasEssayImage);
        out.put("hasMaterialImage", hasMaterialImage);
        out.put("inputMode", hasEssayImage && essay.isBlank() ? "image"
                : hasEssayImage ? "mixed" : "text");

        try {
            String system = systemPrompt(type);
            if (hasEssayImage || hasMaterialImage) {
                system = system + "\n\n学生可能以照片提交作文或题目。请直接阅读附图中的手写/打印文字再批改；"
                        + "若同时有粘贴文字，与图片互补；引用原句时尽量准确。";
            }
            String user = userPrompt(
                    type,
                    essayForPrompt(essay, hasEssayImage),
                    materialForPrompt(type, material, topic, requirement, hasMaterialImage),
                    topicForPrompt(topic, hasMaterialImage),
                    requirementForPrompt(requirement, hasMaterialImage),
                    taskType,
                    wordCount,
                    hasEssayImage,
                    hasMaterialImage
            );

            String raw;
            if (hasEssayImage || hasMaterialImage) {
                List<String> images = new ArrayList<>();
                if (hasEssayImage) images.add(essayImage);
                if (hasMaterialImage) images.add(materialImage);
                raw = deepSeek.chatWithImages(system, user, images, ESSAY_MAX_TOKENS);
            } else {
                raw = deepSeek.chat(system, user, ESSAY_MAX_TOKENS);
            }
            Map<String, Object> report = parseReport(raw, type);
            out.put("report", report);
            out.put("summary", summaryOf(report, type));
        } catch (Exception e) {
            log.warn("essay grade failed: {}", e.getMessage());
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("end-of-input") || msg.contains("Unexpected") || msg.contains("截断")) {
                out.put("error", "批改结果不完整（输出被截断），请再点一次批改。");
            } else {
                out.put("error", "批改暂时失败：" + safeMsg(msg));
            }
        }
        return out;
    }

    /** 流式批改：onDelta 收到逐块文本（打字机），返回完整结果 Map（含 report/summary）。校验失败时 out 只含 error。 */
    public Map<String, Object> gradeStream(Map<String, Object> body, java.util.function.Consumer<String> onDelta) throws Exception {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("disclaimer", "AI 批改仅供练习参考，不代表官方阅卷结果。");

        if (!deepSeekProps.canCall()) {
            out.put("error", "未配置 DEEPSEEK_API_KEY，暂时无法批改。");
            return out;
        }

        String type = str(body.get("type")).toLowerCase();
        if (!TYPES.contains(type)) {
            out.put("error", "请选择题型：论证有效性分析、论说文、英语小作文或英语大作文。");
            return out;
        }

        String essay = str(body.get("essay"));
        String essayImage = toDataUrl(str(body.get("essayImageBase64")), str(body.get("essayMimeType")));
        String materialImage = toDataUrl(str(body.get("materialImageBase64")), str(body.get("materialMimeType")));
        boolean hasEssayImage = !essayImage.isBlank();
        boolean hasMaterialImage = !materialImage.isBlank();

        if (!hasEssayImage && essay.length() < MIN_CHARS) {
            out.put("error", "请粘贴作文文字，或上传作文照片（文字建议不少于 " + MIN_CHARS + " 字）。");
            return out;
        }
        if (essay.length() > MAX_CHARS) {
            out.put("error", "正文过长，请控制在 " + MAX_CHARS + " 字以内。");
            return out;
        }
        if (hasEssayImage && str(body.get("essayImageBase64")).length() > MAX_BASE64_CHARS) {
            out.put("error", "作文图片太大，请压缩后再试。");
            return out;
        }
        if (hasMaterialImage && str(body.get("materialImageBase64")).length() > MAX_BASE64_CHARS) {
            out.put("error", "题目图片太大，请压缩后再试。");
            return out;
        }

        String material = str(body.get("material"));
        String topic = str(body.get("topic"));
        String requirement = str(body.get("requirement"));
        String taskType = str(body.get("taskType"));
        int wordCount = essay.isBlank() ? 0 : countWords(essay, type.startsWith("en_"));

        out.put("type", type);
        out.put("typeLabel", typeLabel(type));
        out.put("wordCount", wordCount);
        out.put("essay", essay);
        out.put("hasEssayImage", hasEssayImage);
        out.put("hasMaterialImage", hasMaterialImage);
        out.put("inputMode", hasEssayImage && essay.isBlank() ? "image"
                : hasEssayImage ? "mixed" : "text");

        String system = systemPrompt(type);
        if (hasEssayImage || hasMaterialImage) {
            system = system + "\n\n学生可能以照片提交作文或题目。请直接阅读附图中的手写/打印文字再批改；"
                    + "若同时有粘贴文字，与图片互补；引用原句时尽量准确。";
        }
        String user = userPrompt(
                type,
                essayForPrompt(essay, hasEssayImage),
                materialForPrompt(type, material, topic, requirement, hasMaterialImage),
                topicForPrompt(topic, hasMaterialImage),
                requirementForPrompt(requirement, hasMaterialImage),
                taskType,
                wordCount,
                hasEssayImage,
                hasMaterialImage
        );

        String raw;
        if (hasEssayImage || hasMaterialImage) {
            List<String> images = new ArrayList<>();
            if (hasEssayImage) images.add(essayImage);
            if (hasMaterialImage) images.add(materialImage);
            raw = deepSeek.streamChatWithImages(system, user, images, ESSAY_MAX_TOKENS, onDelta);
        } else {
            raw = deepSeek.streamChat(system, user, ESSAY_MAX_TOKENS, onDelta);
        }
        Map<String, Object> report = parseReport(raw, type);
        out.put("report", report);
        out.put("summary", summaryOf(report, type));
        return out;
    }

    private String userPrompt(
            String type,
            String essay,
            String material,
            String topic,
            String requirement,
            String taskType,
            int wordCount,
            boolean hasEssayImage,
            boolean hasMaterialImage
    ) {
        String imageNote = buildImageNote(hasEssayImage, hasMaterialImage);
        if ("argument".equals(type)) {
            return """
                    %s
                    学生原文如下（文字可空；若有作文图请以图为准）：
                    <essay>
                    %s
                    </essay>

                    题目材料（文字可空；若有材料图请以图为准）：
                    <material>
                    %s
                    </material>

                    字数约：%d（仅统计粘贴文字；纯图片批改时可为 0）

                    要求：按官方 30 分制先定档再给分；内容块（缺陷识别+分析深度）合计≤16；结构+语言合计≤14且须落在一类12-14/二类8-11/三类4-7/四类0-3；四维分数之和=score；短评、少废话；strengths 可空；problems≤3。只输出 JSON：
                    {
                      "score": 0,
                      "level": "一类/二类/三类/四类",
                      "summary": "≤40字总评（点明内容/表达哪块拖分）",
                      "score_deduction": [{"item": "内容/结构/语言", "points": -1, "reason": "≤20字"}],
                      "dimensions": {
                        "缺陷识别": {"score": 0, "comment": "≤30字"},
                        "分析深度": {"score": 0, "comment": "≤30字"},
                        "结构": {"score": 0, "comment": "≤30字"},
                        "语言": {"score": 0, "comment": "≤30字"}
                      },
                      "strengths": [],
                      "problems": [{
                        "type": "缺陷识别/分析深度/结构/语言",
                        "original": "学生原句",
                        "why": "≤40字",
                        "reductio_ad_absurdum": "一句归谬或 null",
                        "tiered_fix": {"pass": "≤60字", "high_score": "≤60字", "teacher_example": "≤60字"}
                      }],
                      "missing_defects": [{
                        "defect": "≤40字",
                        "recognition_signal": "标志词",
                        "lesson": "≤30字"
                      }],
                      "vocabulary_upgrade": [{"original": "", "upgraded": "", "note": "≤15字"}],
                      "next_action": "≤40字训练动作"
                    }
                    """.formatted(imageNote, essay, material, wordCount);
        }
        if ("thesis".equals(type)) {
            return """
                    %s
                    学生原文：
                    <essay>
                    %s
                    </essay>

                    题目：
                    <topic>
                    %s
                    </topic>

                    字数：%d

                    要求：按官方 35 分制先定档再给分（一类30-35/二类24-29/三类18-23/四类11-17/五类0-10）；立意+结构+论证+语言之和=score；漏拟题目-2、错字每3个-1最多-2、标点/卷面酌情-1～-2须写入 score_deduction；偏题不得因文笔抬档；短评、少废话；strengths 可空；problems≤3。只输出 JSON：
                    {
                      "score": 0,
                      "level": "一类/二类/三类/四类/五类",
                      "summary": "≤40字总评（点明档位与主因）",
                      "score_deduction": [{"item": "漏拟题目/错别字/标点或卷面/立意或论证等", "points": -1, "reason": "≤20字"}],
                      "thesis_check": {"student_thesis": "", "is_on_topic": true, "comment": "≤30字"},
                      "dimensions": {
                        "立意": {"score": 0, "comment": "≤30字"},
                        "结构": {"score": 0, "comment": "≤30字"},
                        "论证": {"score": 0, "comment": "≤30字"},
                        "语言": {"score": 0, "comment": "≤30字"}
                      },
                      "strengths": [],
                      "problems": [{
                        "type": "立意/结构/论证/语言",
                        "original": "学生原句",
                        "why": "≤40字",
                        "tiered_fix": {"pass": "≤60字", "high_score": "≤60字", "teacher_example": "≤60字"}
                      }],
                      "outline_suggestion": "骨架短语≤80字",
                      "vocabulary_upgrade": [{"original": "", "upgraded": "", "note": "≤15字"}],
                      "next_action": "≤40字训练动作"
                    }
                    """.formatted(imageNote, essay, topic, wordCount);
        }

        String kind = "en_chart".equals(type) ? "chart" : (taskType.isBlank() ? "letter" : taskType);
        int maxScore = "en_chart".equals(type) ? 15 : 10;
        String chartExtra = "en_chart".equals(type)
                ? "Additionally check data description accuracy, analysis depth, and topic vocabulary.\n"
                : "";
        return """
                %s
                Student essay:
                <essay>
                %s
                </essay>

                Task type: %s
                Task requirement: %s
                Word count: %d
                Max score: %d
                %s
                Compact JSON only. No filler. problems≤3. strengths may be [].
                {
                  "score": 0,
                  "level": "",
                  "summary": "≤40字中文总评",
                  "score_deduction": [{"item": "", "points": -1, "reason": "≤20字"}],
                  "dimensions": {
                    "task_response": {"score": 0, "comment": "≤30字"},
                    "coherence": {"score": 0, "comment": "≤30字"},
                    "grammar": {"score": 0, "comment": "≤30字"},
                    "vocabulary": {"score": 0, "comment": "≤30字"}
                  },
                  "strengths": [],
                  "problems": [{
                    "type": "grammar/vocabulary/task/coherence/data_description",
                    "original": "",
                    "why": "≤40字中文",
                    "chinglish_alert": "一句或 null",
                    "tiered_fix": {"pass": "", "high_score": "", "teacher_example": ""}
                  }],
                  "missing_points": [{
                    "point": "≤30字",
                    "recognition_signal": "≤30字",
                    "lesson": "≤30字"
                  }],
                  "vocabulary_upgrade": [{"original": "", "upgraded": "", "note": "≤15字"}],
                  "next_action": "≤40字中文训练动作"
                }
                """.formatted(
                imageNote,
                essay,
                kind,
                requirement,
                wordCount,
                maxScore,
                chartExtra
        );
    }

    private static String buildImageNote(boolean hasEssayImage, boolean hasMaterialImage) {
        if (!hasEssayImage && !hasMaterialImage) return "";
        StringBuilder sb = new StringBuilder("附图说明（按出现顺序）：\n");
        int i = 1;
        if (hasEssayImage) {
            sb.append(i++).append(". 「作文」图：学生作文照片，请直接阅读并批改。\n");
        }
        if (hasMaterialImage) {
            sb.append(i).append(". 「题目/材料」图：题目或材料照片，请结合作文使用。\n");
        }
        return sb.toString();
    }

    private static String essayForPrompt(String essay, boolean hasEssayImage) {
        if (!essay.isBlank()) return essay;
        return hasEssayImage ? "（未粘贴文字，作文见附图「作文」）" : "";
    }

    private String materialForPrompt(
            String type,
            String material,
            String topic,
            String requirement,
            boolean hasMaterialImage
    ) {
        if (!"argument".equals(type)) {
            // argument 专用；其它类型走 topic/requirement
            return material;
        }
        if (!material.isBlank()) return material;
        if (hasMaterialImage) return "（未粘贴材料，见附图「题目/材料」）";
        return "（用户未提供材料，请仅依据作文本身评结构、语言与分析深度，缺陷识别分保守给）";
    }

    private static String topicForPrompt(String topic, boolean hasMaterialImage) {
        if (!topic.isBlank()) return topic;
        if (hasMaterialImage) return "（未粘贴题目，见附图「题目/材料」）";
        return "（用户未提供题目，请根据作文推断立意并说明推断依据）";
    }

    private static String requirementForPrompt(String requirement, boolean hasMaterialImage) {
        if (!requirement.isBlank()) return requirement;
        if (hasMaterialImage) return "(see attached requirement/material image)";
        return "(not provided)";
    }

    private static String toDataUrl(String raw, String mime) {
        if (raw == null || raw.isBlank()) return "";
        String b64 = raw.trim();
        if (b64.startsWith("data:")) return b64;
        b64 = b64.replaceAll("\\s+", "");
        if (b64.isBlank()) return "";
        String m = mime == null ? "" : mime.trim().toLowerCase();
        if (m.isBlank() || !ALLOWED_MIME.contains(m)) m = "image/jpeg";
        if ("image/jpg".equals(m)) m = "image/jpeg";
        return "data:" + m + ";base64," + b64;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseReport(String raw, String type) throws Exception {
        String json = extractJson(raw);
        JsonNode node = mapper.readTree(json);
        // 若模型包了一层 report，拆开
        if (node.has("report") && node.get("report").isObject()) {
            node = node.get("report");
        }
        Map<String, Object> report = mapper.convertValue(node, Map.class);
        normalizeProblems(report);
        normalizeMissing(report);
        if (!report.containsKey("dimensions") && isEnglish(type)) {
            Map<String, Object> dims = new LinkedHashMap<>();
            putDim(dims, "切题", report.get("task_response"));
            putDim(dims, "连贯", report.get("coherence"));
            putDim(dims, "语法", report.get("grammar"));
            putDim(dims, "词汇", report.get("vocabulary"));
            report.put("dimensions", dims);
        } else if (isEnglish(type) && report.get("dimensions") instanceof Map<?, ?> dimMap) {
            Map<String, Object> dims = new LinkedHashMap<>();
            Map<String, Object> rawDims = (Map<String, Object>) dimMap;
            putDim(dims, "切题", rawDims.getOrDefault("切题", rawDims.get("task_response")));
            putDim(dims, "连贯", rawDims.getOrDefault("连贯", rawDims.get("coherence")));
            putDim(dims, "语法", rawDims.getOrDefault("语法", rawDims.get("grammar")));
            putDim(dims, "词汇", rawDims.getOrDefault("词汇", rawDims.get("vocabulary")));
            if (!dims.isEmpty()) report.put("dimensions", dims);
        }
        if (!(report.get("score_deduction") instanceof List<?>)) {
            report.put("score_deduction", List.of());
        }
        if (!(report.get("vocabulary_upgrade") instanceof List<?>)) {
            report.put("vocabulary_upgrade", List.of());
        }
        trimReport(report);
        return report;
    }

    /** 后端再砍一刀，防止模型啰嗦。 */
    @SuppressWarnings("unchecked")
    private static void trimReport(Map<String, Object> report) {
        clip(report, "summary", 60);
        clip(report, "next_action", 60);
        clip(report, "outline_suggestion", 120);
        if (report.get("strengths") instanceof List<?> list) {
            report.put("strengths", list.stream().limit(2).map(v -> clipStr(str(v), 40)).toList());
        }
        if (report.get("score_deduction") instanceof List<?> list) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object item : list) {
                if (rows.size() >= 4) break;
                if (!(item instanceof Map<?, ?> m)) continue;
                Map<String, Object> row = new LinkedHashMap<>((Map<String, Object>) m);
                clip(row, "item", 30);
                clip(row, "reason", 40);
                rows.add(row);
            }
            report.put("score_deduction", rows);
        }
        if (report.get("problems") instanceof List<?> list) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object item : list) {
                if (rows.size() >= 3) break;
                if (!(item instanceof Map<?, ?> m)) continue;
                Map<String, Object> row = new LinkedHashMap<>((Map<String, Object>) m);
                clip(row, "why", 80);
                clip(row, "reductio_ad_absurdum", 100);
                clip(row, "chinglish_alert", 100);
                if (row.get("tiered_fix") instanceof Map<?, ?> tf) {
                    Map<String, Object> t = new LinkedHashMap<>((Map<String, Object>) tf);
                    clip(t, "pass", 120);
                    clip(t, "high_score", 120);
                    clip(t, "teacher_example", 120);
                    row.put("tiered_fix", t);
                }
                rows.add(row);
            }
            report.put("problems", rows);
        }
        if (report.get("vocabulary_upgrade") instanceof List<?> list) {
            report.put("vocabulary_upgrade", list.stream().limit(5).toList());
        }
        if (report.get("missing_list") instanceof List<?> list) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object item : list) {
                if (rows.size() >= 2) break;
                if (!(item instanceof Map<?, ?> m)) continue;
                Map<String, Object> row = new LinkedHashMap<>((Map<String, Object>) m);
                clip(row, "title", 80);
                clip(row, "signal", 60);
                clip(row, "lesson", 60);
                rows.add(row);
            }
            report.put("missing_list", rows);
        }
        if (report.get("dimensions") instanceof Map<?, ?> dims) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : dims.entrySet()) {
                if (!(e.getValue() instanceof Map<?, ?> dm)) {
                    out.put(String.valueOf(e.getKey()), e.getValue());
                    continue;
                }
                Map<String, Object> d = new LinkedHashMap<>((Map<String, Object>) dm);
                clip(d, "comment", 50);
                out.put(String.valueOf(e.getKey()), d);
            }
            report.put("dimensions", out);
        }
    }

    private static void clip(Map<String, Object> map, String key, int max) {
        if (!map.containsKey(key)) return;
        map.put(key, clipStr(str(map.get(key)), max));
    }

    private static String clipStr(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        if ("null".equalsIgnoreCase(t)) return "";
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    @SuppressWarnings("unchecked")
    private static void normalizeProblems(Map<String, Object> report) {
        Object raw = report.get("problems");
        if (!(raw instanceof List<?>) && report.get("errors") instanceof List<?>) {
            raw = report.get("errors");
            report.put("problems", raw);
        }
        if (!(raw instanceof List<?> list)) return;
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) continue;
            Map<String, Object> p = new LinkedHashMap<>((Map<String, Object>) m);
            Object tiered = p.get("tiered_fix");
            if (!(tiered instanceof Map<?, ?>) && p.get("fix") != null) {
                Map<String, Object> t = new LinkedHashMap<>();
                String fix = str(p.get("fix"));
                t.put("pass", fix);
                t.put("high_score", fix);
                t.put("teacher_example", fix);
                p.put("tiered_fix", t);
            }
            out.add(p);
        }
        report.put("problems", out);
    }

    @SuppressWarnings("unchecked")
    private static void normalizeMissing(Map<String, Object> report) {
        Object raw = report.get("missing_defects");
        if (!(raw instanceof List<?>) && report.get("missing_points") instanceof List<?>) {
            raw = report.get("missing_points");
        }
        if (!(raw instanceof List<?> list)) {
            report.put("missing_list", List.of());
            return;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof String s) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("title", s);
                m.put("signal", "");
                m.put("lesson", "");
                out.add(m);
                continue;
            }
            if (!(item instanceof Map<?, ?> map)) continue;
            Map<String, Object> m = (Map<String, Object>) map;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("title", firstNonBlank(m.get("defect"), m.get("point"), m.get("title")));
            row.put("signal", firstNonBlank(m.get("recognition_signal"), m.get("signal")));
            row.put("lesson", firstNonBlank(m.get("lesson"), m.get("tip")));
            out.add(row);
        }
        report.put("missing_list", out);
        if (!report.containsKey("missing_defects")) report.put("missing_defects", list);
        if (!report.containsKey("missing_points") && report.containsKey("missing_defects")) {
            // keep as-is
        }
    }

    private static String firstNonBlank(Object... vals) {
        for (Object v : vals) {
            String s = str(v);
            if (!s.isBlank() && !"null".equalsIgnoreCase(s)) return s;
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private static void putDim(Map<String, Object> dims, String key, Object raw) {
        if (raw instanceof Map<?, ?> m) {
            dims.put(key, m);
        }
    }

    private static String extractJson(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.startsWith("```")) {
            text = text.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        throw new IllegalStateException("模型未返回 JSON");
    }

    private String systemPrompt(String type) {
        String path = switch (type) {
            case "argument" -> "skills/essay-argument-system.md";
            case "thesis" -> "skills/essay-thesis-system.md";
            default -> "skills/essay-english-system.md";
        };
        return promptCache.computeIfAbsent(path, this::loadPrompt);
    }

    private String loadPrompt(String path) {
        try {
            ClassPathResource res = new ClassPathResource(path);
            return new String(res.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            log.warn("essay prompt missing {}: {}", path, e.getMessage());
            return "你是阅卷名师。只输出 JSON，教会学生怎么改，不编造原文没有的内容。";
        }
    }

    private static String summaryOf(Map<String, Object> report, String type) {
        Object s = report.get("summary");
        if (s != null && !str(s).isBlank()) return str(s);
        return typeLabel(type) + "约 " + report.get("score") + " 分";
    }

    private static String typeLabel(String type) {
        return switch (type) {
            case "argument" -> "论证有效性分析";
            case "thesis" -> "论说文";
            case "en_letter" -> "英语小作文";
            case "en_chart" -> "英语大作文";
            default -> type;
        };
    }

    private static boolean isEnglish(String type) {
        return type.startsWith("en_");
    }

    private static int countWords(String text, boolean english) {
        if (text == null || text.isBlank()) return 0;
        if (!english) return text.replaceAll("\\s+", "").length();
        String[] parts = text.trim().split("\\s+");
        int n = 0;
        for (String p : parts) {
            if (!p.isBlank()) n++;
        }
        return n;
    }

    private static String safeMsg(String msg) {
        if (msg == null) return "网络错误";
        return msg.length() > 120 ? msg.substring(0, 120) : msg;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}

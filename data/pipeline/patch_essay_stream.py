# -*- coding: utf-8 -*-
"""在 EssayGradeService.java 中 grade() 方法后追加 gradeStream() 流式方法"""
import io

p = r"D:\123\gz199\server\src\main\java\com\gz199\ai\EssayGradeService.java"
s = io.open(p, encoding="utf-8").read()

if "gradeStream" in s:
    print("ALREADY PATCHED")
else:
    anchor = '''        return out;
    }

    private String userPrompt('''
    assert anchor in s, "anchor not found"
    stream_method = '''        return out;
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
            system = system + "\\n\\n学生可能以照片提交作文或题目。请直接阅读附图中的手写/打印文字再批改；"
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

    private String userPrompt('''
    s = s.replace(anchor, stream_method, 1)
    io.open(p, "w", encoding="utf-8", newline="").write(s)
    print("PATCHED EssayGradeService")

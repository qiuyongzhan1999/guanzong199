package com.gz199.ai;

import com.gz199.config.DeepSeekProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 作文/题目拍照识字：DeepSeek 视觉模型 OCR。
 */
@Service
public class EssayOcrService {
    private static final Logger log = LoggerFactory.getLogger(EssayOcrService.class);
    private static final int MAX_BASE64_CHARS = 10_000_000;
    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    );

    private final DeepSeekClient deepSeek;
    private final DeepSeekProperties deepSeekProps;

    public EssayOcrService(DeepSeekClient deepSeek, DeepSeekProperties deepSeekProps) {
        this.deepSeek = deepSeek;
        this.deepSeekProps = deepSeekProps;
    }

    public Map<String, Object> recognize(Map<String, Object> body) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!deepSeekProps.canCall()) {
            out.put("error", "DeepSeek 未配置，无法识别图片");
            return out;
        }

        String raw = str(body.get("imageBase64"));
        if (raw.isBlank()) {
            out.put("error", "请先拍照或选择图片");
            return out;
        }
        if (raw.length() > MAX_BASE64_CHARS) {
            out.put("error", "图片太大，请压缩后再试");
            return out;
        }

        String mime = normalizeMime(str(body.get("mimeType")));
        String dataUrl = toDataUrl(raw, mime);
        String target = str(body.get("target"));
        String userHint = "material".equals(target)
                ? "这是考试题目/材料照片，请完整识别其中的文字。"
                : "这是学生作文照片，请完整识别其中的文字。";

        try {
            String text = deepSeek.chatWithImage(
                    loadSystem(),
                    userHint,
                    dataUrl,
                    4096
            );
            text = cleanOcr(text);
            out.put("text", text);
            out.put("ok", true);
            if (text.isBlank()) {
                out.put("warning", "几乎没识别到文字，请换更清晰的照片");
            }
            return out;
        } catch (Exception e) {
            log.warn("essay ocr failed: {}", e.getMessage());
            out.put("error", "识别失败：" + shortMsg(e.getMessage()));
            return out;
        }
    }

    private static String cleanOcr(String text) {
        if (text == null) return "";
        String t = text.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0) t = t.substring(nl + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
            t = t.trim();
        }
        return t;
    }

    private static String toDataUrl(String raw, String mime) {
        String b64 = raw.trim();
        if (b64.startsWith("data:")) {
            return b64;
        }
        // 去掉可能的空白/换行
        b64 = b64.replaceAll("\\s+", "");
        return "data:" + mime + ";base64," + b64;
    }

    private static String normalizeMime(String mime) {
        String m = mime == null ? "" : mime.trim().toLowerCase(Locale.ROOT);
        if (m.isBlank() || !ALLOWED_MIME.contains(m)) {
            return "image/jpeg";
        }
        if ("image/jpg".equals(m)) return "image/jpeg";
        return m;
    }

    private String loadSystem() {
        try {
            ClassPathResource res = new ClassPathResource("skills/essay-ocr-system.md");
            return StreamUtils.copyToString(res.getInputStream(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            log.warn("essay ocr prompt missing: {}", e.getMessage());
            return "只输出图中文字，保留换行，不要解释。";
        }
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static String shortMsg(String msg) {
        if (msg == null || msg.isBlank()) return "未知错误";
        return msg.length() > 120 ? msg.substring(0, 120) : msg;
    }
}

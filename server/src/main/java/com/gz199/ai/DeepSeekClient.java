package com.gz199.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gz199.config.DeepSeekProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * DeepSeek 对话。择校建议只要正文：chat/completions，关闭思考模式，不带 tools / 网页搜索。
 */
@Component
public class DeepSeekClient {
    private static final Logger log = LoggerFactory.getLogger(DeepSeekClient.class);

    private final DeepSeekProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;

    public DeepSeekClient(DeepSeekProperties props) {
        this.props = props;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public String chat(String systemPrompt, String userPrompt) throws Exception {
        return chat(systemPrompt, userPrompt, props.getMaxOutputTokens());
    }

    public String chat(String systemPrompt, String userPrompt, int maxTokens) throws Exception {
        if (!props.canCall()) {
            throw new IllegalStateException("DeepSeek 未配置：请设置环境变量 DEEPSEEK_API_KEY");
        }

        ObjectNode body = mapper.createObjectNode();
        body.put("model", props.getModel());
        body.put("stream", false);
        body.put("max_tokens", Math.max(512, maxTokens));
        // V4 默认开思考；择校/批改只要正文，关掉思考（不走联网搜索）
        body.putObject("thinking").put("type", "disabled");
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);

        return sendChat(body, "批改内容过长被截断，请重试；若仍失败请缩短作文后再交。");
    }

    /**
     * 视觉多模态：user 为说明文字 + 若干图片（data URL）。
     */
    public String chatWithImages(String systemPrompt, String userText, java.util.List<String> imageDataUrls, int maxTokens) throws Exception {
        if (!props.canCall()) {
            throw new IllegalStateException("DeepSeek 未配置：请设置环境变量 DEEPSEEK_API_KEY");
        }
        java.util.List<String> images = imageDataUrls == null ? java.util.List.of()
                : imageDataUrls.stream().filter(s -> s != null && !s.isBlank()).toList();
        if (images.isEmpty()) {
            throw new IllegalArgumentException("缺少图片");
        }

        String visionModel = props.getVisionModel();
        if (visionModel == null || visionModel.isBlank()) {
            visionModel = "deepseek-flash";
        }

        ObjectNode body = mapper.createObjectNode();
        body.put("model", visionModel);
        body.put("stream", false);
        body.put("max_tokens", Math.max(256, maxTokens));
        body.putObject("thinking").put("type", "disabled");
        ArrayNode messages = body.putArray("messages");
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.addObject().put("role", "system").put("content", systemPrompt);
        }
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        ArrayNode content = user.putArray("content");
        content.addObject()
                .put("type", "text")
                .put("text", userText == null || userText.isBlank() ? "请根据附图完成任务。" : userText);
        for (String dataUrl : images) {
            ObjectNode imageUrl = content.addObject().put("type", "image_url").putObject("image_url");
            imageUrl.put("url", dataUrl.trim());
            imageUrl.put("detail", "high");
        }

        return sendChat(body, "批改内容过长被截断，请重试；若仍失败请换更清晰的照片或缩短文字后再交。");
    }

    /** 单图便捷方法（OCR 等） */
    public String chatWithImage(String systemPrompt, String userText, String imageDataUrl, int maxTokens) throws Exception {
        return chatWithImages(systemPrompt, userText, java.util.List.of(imageDataUrl), maxTokens);
    }

    private String sendChat(ObjectNode body, String truncateMessage) throws Exception {
        String url = props.getBaseUrl().replaceAll("/$", "") + "/chat/completions";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(Math.max(30, props.getTimeoutSeconds())))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + props.getApiKey().trim())
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        long t0 = System.currentTimeMillis();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            String detail = resp.body() == null ? "" : resp.body();
            if (detail.length() > 500) detail = detail.substring(0, 500);
            throw new IllegalStateException("DeepSeek HTTP " + resp.statusCode() + ": " + detail);
        }

        JsonNode root = mapper.readTree(resp.body());
        String finish = root.path("choices").path(0).path("finish_reason").asText("");
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        if (content.isBlank()) {
            throw new IllegalStateException("DeepSeek 返回空正文");
        }
        if ("length".equalsIgnoreCase(finish)) {
            log.warn("DeepSeek output truncated by max_tokens model={} len={}",
                    body.path("model").asText(""), content.length());
            throw new IllegalStateException(truncateMessage);
        }
        log.info("DeepSeek chat done model={} thinking=off finish={} len={} costMs={}",
                body.path("model").asText(""), finish, content.length(), System.currentTimeMillis() - t0);
        return content.trim();
    }

    public String ping() throws Exception {
        return chat("你是探活助手。用一句话回复即可。", "回复：ok");
    }

    // ==================== 流式（SSE） ====================

    /** 流式对话：逐块回调 onDelta（纯文本增量），返回完整正文。 */
    public String streamChat(String systemPrompt, String userPrompt, int maxTokens, Consumer<String> onDelta) throws Exception {
        if (!props.canCall()) {
            throw new IllegalStateException("DeepSeek 未配置：请设置环境变量 DEEPSEEK_API_KEY");
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("model", props.getModel());
        body.put("stream", true);
        body.put("max_tokens", Math.max(512, maxTokens));
        body.putObject("thinking").put("type", "disabled");
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);
        return streamChatBody(body, onDelta, "批改内容过长被截断，请重试；若仍失败请缩短作文后再交。");
    }

    /** 流式视觉多模态：user 为说明文字 + 若干图片（data URL）。 */
    public String streamChatWithImages(String systemPrompt, String userText, java.util.List<String> imageDataUrls,
                                       int maxTokens, Consumer<String> onDelta) throws Exception {
        if (!props.canCall()) {
            throw new IllegalStateException("DeepSeek 未配置：请设置环境变量 DEEPSEEK_API_KEY");
        }
        java.util.List<String> images = imageDataUrls == null ? java.util.List.of()
                : imageDataUrls.stream().filter(s -> s != null && !s.isBlank()).toList();
        if (images.isEmpty()) {
            throw new IllegalArgumentException("缺少图片");
        }
        String visionModel = props.getVisionModel();
        if (visionModel == null || visionModel.isBlank()) {
            visionModel = "deepseek-flash";
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("model", visionModel);
        body.put("stream", true);
        body.put("max_tokens", Math.max(256, maxTokens));
        body.putObject("thinking").put("type", "disabled");
        ArrayNode messages = body.putArray("messages");
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.addObject().put("role", "system").put("content", systemPrompt);
        }
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        ArrayNode content = user.putArray("content");
        content.addObject()
                .put("type", "text")
                .put("text", userText == null || userText.isBlank() ? "请根据附图完成任务。" : userText);
        for (String dataUrl : images) {
            ObjectNode imageUrl = content.addObject().put("type", "image_url").putObject("image_url");
            imageUrl.put("url", dataUrl.trim());
            imageUrl.put("detail", "high");
        }
        return streamChatBody(body, onDelta, "批改内容过长被截断，请重试；若仍失败请换更清晰的照片或缩短文字后再交。");
    }

    /** 流式请求核心：stream=true，逐行解析 SSE，回调增量，返回完整正文。 */
    private String streamChatBody(ObjectNode body, Consumer<String> onDelta, String truncateMessage) throws Exception {
        String url = props.getBaseUrl().replaceAll("/$", "") + "/chat/completions";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(Math.max(30, props.getTimeoutSeconds() + 30)))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + props.getApiKey().trim())
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        long t0 = System.currentTimeMillis();
        HttpResponse<java.util.stream.Stream<String>> resp = http.send(req, HttpResponse.BodyHandlers.ofLines());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            String detail = resp.body() == null ? "" : resp.body().findFirst().orElse("");
            if (detail.length() > 500) detail = detail.substring(0, 500);
            throw new IllegalStateException("DeepSeek HTTP " + resp.statusCode() + ": " + detail);
        }

        StringBuilder full = new StringBuilder();
        boolean truncated = false;
        try (java.util.stream.Stream<String> lines = resp.body()) {
            java.util.Iterator<String> it = lines.iterator();
            while (it.hasNext()) {
                String line = it.next();
                String s = line.trim();
                if (!s.startsWith("data:")) continue;
                String data = s.substring(5).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) continue;
                JsonNode node;
                try {
                    node = mapper.readTree(data);
                } catch (Exception e) {
                    continue; // 忽略无法解析的心跳/注释行
                }
                String finish = node.path("choices").path(0).path("finish_reason").asText("");
                if ("length".equalsIgnoreCase(finish)) {
                    truncated = true;
                    break;
                }
                String delta = node.path("choices").path(0).path("delta").path("content").asText("");
                if (delta != null && !delta.isEmpty()) {
                    full.append(delta);
                    if (onDelta != null) onDelta.accept(delta);
                }
            }
        }
        String content = full.toString().trim();
        if (content.isBlank()) {
            throw new IllegalStateException("DeepSeek 返回空正文");
        }
        if (truncated) {
            log.warn("DeepSeek stream truncated model={} len={}", body.path("model").asText(""), content.length());
            throw new IllegalStateException(truncateMessage);
        }
        log.info("DeepSeek stream done model={} len={} costMs={}",
                body.path("model").asText(""), content.length(), System.currentTimeMillis() - t0);
        return content;
    }
}

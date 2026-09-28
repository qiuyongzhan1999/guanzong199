# -*- coding: utf-8 -*-
"""在 DeepSeekClient.java 追加流式方法"""
import io

p = r"D:\123\gz199\server\src\main\java\com\gz199\ai\DeepSeekClient.java"
s = io.open(p, encoding="utf-8").read()

if "streamChat" in s:
    print("ALREADY PATCHED")
else:
    # 加 import
    old_import = "import java.time.Duration;"
    new_import = "import java.time.Duration;\nimport java.util.function.Consumer;"
    assert old_import in s
    s = s.replace(old_import, new_import, 1)

    # 在 ping 方法后追加流式方法（替换文件尾）
    old_tail = '''    public String ping() throws Exception {
        return chat("你是探活助手。用一句话回复即可。", "回复：ok");
    }
}'''
    new_tail = '''    public String ping() throws Exception {
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
            for (String line : lines) {
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
}'''
    assert old_tail in s
    s = s.replace(old_tail, new_tail, 1)
    io.open(p, "w", encoding="utf-8", newline="").write(s)
    print("PATCHED DeepSeekClient")

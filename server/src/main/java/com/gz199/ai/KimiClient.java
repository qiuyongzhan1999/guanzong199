package com.gz199.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gz199.config.KimiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Kimi 对话。择校只要一段可排版的正文，不锁 JSON，也不联网补招录数字。
 */
@Component
public class KimiClient {
    private static final Logger log = LoggerFactory.getLogger(KimiClient.class);

    private final KimiProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;

    public KimiClient(KimiProperties props) {
        this.props = props;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public String chat(String systemPrompt, String userPrompt) throws Exception {
        if (!props.canCall()) {
            throw new IllegalStateException("Kimi 未配置：请设置环境变量 MOONSHOT_API_KEY");
        }

        ObjectNode body = mapper.createObjectNode();
        body.put("model", props.getModel());
        body.put("stream", false);
        body.put("max_tokens", Math.max(512, props.getMaxOutputTokens()));
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);

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
            throw new IllegalStateException("Kimi HTTP " + resp.statusCode() + ": " + detail);
        }

        JsonNode root = mapper.readTree(resp.body());
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        if (content.isBlank()) {
            throw new IllegalStateException("Kimi 返回空正文");
        }
        log.info("Kimi chat done model={} len={} costMs={}", props.getModel(), content.length(), System.currentTimeMillis() - t0);
        return content.trim();
    }

    public String ping() throws Exception {
        return chat("你是探活助手。用一句话回复即可。", "回复：ok");
    }
}

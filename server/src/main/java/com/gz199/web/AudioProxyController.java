package com.gz199.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 单词发音代理：统一转发有道 dictvoice 接口（type=0 美音 / type=1 英音），
 * 本地磁盘做永久缓存，避免高频直连与跨域问题。
 */
@RestController
@RequestMapping("/api/audio")
public class AudioProxyController {

    private final JdbcTemplate jdbc;
    private final Path cacheDir;

    public AudioProxyController(DataSource dataSource,
                                org.springframework.core.env.Environment env) {
        this.jdbc = new JdbcTemplate(dataSource);
        String dataDir = env.getProperty("gz199.data-dir", "D:/123/gz199/data");
        this.cacheDir = Paths.get(dataDir, "audio-cache");
        try {
            Files.createDirectories(cacheDir);
        } catch (Exception ignored) {
        }
    }

    @GetMapping("/word")
    public ResponseEntity<byte[]> word(@RequestParam String word,
                                       @RequestParam(defaultValue = "1") int type) {
        String w = word.trim();
        if (w.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        // 缓存文件：{word}_{type}.mp3，非法字符替换
        String safe = w.replaceAll("[^a-zA-Z0-9_-]", "_");
        File cache = cacheDir.resolve(safe + "_" + type + ".mp3").toFile();

        byte[] audio;
        if (cache.exists() && cache.length() > 0) {
            try {
                audio = Files.readAllBytes(cache.toPath());
            } catch (Exception e) {
                audio = fetch(w, type);
            }
        } else {
            audio = fetch(w, type);
            if (audio != null) {
                try {
                    Files.write(cache.toPath(), audio);
                } catch (Exception ignored) {
                }
            }
        }
        if (audio == null) {
            return ResponseEntity.status(502).build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("audio/mpeg"));
        headers.setCacheControl("public, max-age=604800");
        return ResponseEntity.ok().headers(headers).body(audio);
    }

    private byte[] fetch(String word, int type) {
        try {
            String url = "http://dict.youdao.com/dictvoice?type=" + type
                    + "&audio=" + URLEncoder.encode(word, StandardCharsets.UTF_8.name());
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            int code = conn.getResponseCode();
            if (code != 200) {
                return null;
            }
            try (InputStream in = conn.getInputStream()) {
                return in.readAllBytes();
            }
        } catch (Exception e) {
            return null;
        }
    }
}

package com.gz199.data;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.gz199.config.AppProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@Service
public class JsonDataStore {
    private final Path dataDir;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();
    private final Object writeLock = new Object();

    public JsonDataStore(AppProperties props) {
        this.dataDir = Paths.get(props.getDataDir()).toAbsolutePath().normalize();
    }

    public Path dataDir() {
        return dataDir;
    }

    public List<Map<String, Object>> list(String fileName) {
        try {
            Path file = dataDir.resolve(fileName);
            if (!Files.exists(file)) {
                return Collections.emptyList();
            }
            long mtime = Files.getLastModifiedTime(file).toMillis();
            Cached hit = cache.get(fileName);
            if (hit != null && hit.mtime == mtime) {
                return hit.rows;
            }
            List<Map<String, Object>> rows = mapper.readValue(
                    Files.readString(file, StandardCharsets.UTF_8),
                    new TypeReference<List<Map<String, Object>>>() {}
            );
            cache.put(fileName, new Cached(mtime, rows));
            return rows;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    /** 按匹配条件替换或追加一行，并写回文件 */
    public void upsert(String fileName, Map<String, Object> row, Predicate<Map<String, Object>> match) {
        synchronized (writeLock) {
            List<Map<String, Object>> rows = new ArrayList<>(list(fileName));
            boolean replaced = false;
            for (int i = 0; i < rows.size(); i++) {
                if (match.test(rows.get(i))) {
                    rows.set(i, row);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                rows.add(row);
            }
            Path file = dataDir.resolve(fileName);
            try {
                Files.createDirectories(file.getParent());
                mapper.writeValue(file.toFile(), rows);
                cache.remove(fileName);
            } catch (IOException e) {
                throw new IllegalStateException("写入失败: " + file, e);
            }
        }
    }

    public Map<String, Object> findSchool(String schoolCode) {
        return list("schools.json").stream()
                .filter(row -> schoolCode.equals(String.valueOf(row.get("code"))))
                .findFirst()
                .orElse(null);
    }

    private static class Cached {
        final long mtime;
        final List<Map<String, Object>> rows;

        Cached(long mtime, List<Map<String, Object>> rows) {
            this.mtime = mtime;
            this.rows = rows;
        }
    }
}

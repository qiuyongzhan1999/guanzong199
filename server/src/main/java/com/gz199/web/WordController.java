package com.gz199.web;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 背单词词库接口：按考试分类（词书）取词。
 * 数据来自 ECDICT 开源词典库（77万词中带考试标签的 1.5 万词），
 * tag 字段做考试分类：ky(考研)/cet4/cet6/gk/ielts/toefl/gre。
 */
@RestController
@RequestMapping("/api/word")
public class WordController {

    private final JdbcTemplate jdbc;

    public WordController(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    private static final String[][] BOOKS = {
            {"ky", "考研词汇"},
            {"cet4", "四级词汇"},
            {"cet6", "六级词汇"},
            {"gk", "高考词汇"},
            {"ielts", "雅思词汇"},
            {"toefl", "托福词汇"},
            {"gre", "GRE词汇"},
    };

    /** 词书列表（含各分类词数），供前端词书选择器 */
    @GetMapping("/books")
    public List<Map<String, Object>> books() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String[] b : BOOKS) {
            Map<String, Object> m = new HashMap<>();
            m.put("book", b[0]);
            m.put("name", b[1]);
            m.put("total", countByTag(b[0]));
            list.add(m);
        }
        return list;
    }

    private int countByTag(String tag) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM word WHERE tag LIKE ?", Integer.class, "%" + tag + "%");
        return n == null ? 0 : n;
    }

    @GetMapping("/count")
    public Map<String, Object> count(@RequestParam(defaultValue = "ky") String book) {
        Map<String, Object> m = new HashMap<>();
        m.put("total", countByTag(book));
        m.put("book", book);
        for (String[] b : BOOKS) {
            if (b[0].equals(book)) {
                m.put("name", b[1]);
                break;
            }
        }
        if (!m.containsKey("name")) {
            m.put("name", "全部词汇");
        }
        return m;
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "ky") String book
    ) {
        page = Math.max(page, 1);
        pageSize = Math.min(Math.max(pageSize, 1), 50);
        String like = "%" + book + "%";
        int total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM word WHERE tag LIKE ?", Integer.class, like);
        int offset = (page - 1) * pageSize;
        List<Map<String, Object>> items = jdbc.query(
                "SELECT id, word, phonetic, phonetic_us AS phoneticUs, meaning_cn AS meaningCn, " +
                        "pos, sentence_en AS sentenceEn, sentence_cn AS sentenceCn, word_rank AS wordRank " +
                        "FROM word WHERE tag LIKE ? ORDER BY word_rank, id LIMIT ? OFFSET ?",
                (rs, i) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("word", rs.getString("word"));
                    row.put("phonetic", nz(rs.getString("phonetic")));
                    row.put("phoneticUs", nz(rs.getString("phoneticUs")));
                    row.put("meaningCn", nz(rs.getString("meaningCn")));
                    row.put("pos", nz(rs.getString("pos")));
                    row.put("sentenceEn", nz(rs.getString("sentenceEn")));
                    row.put("sentenceCn", nz(rs.getString("sentenceCn")));
                    row.put("wordRank", rs.getInt("wordRank"));
                    return row;
                },
                like, pageSize, offset);
        Map<String, Object> m = new HashMap<>();
        m.put("total", total);
        m.put("page", page);
        m.put("pageSize", pageSize);
        m.put("items", items);
        return m;
    }

    /** 按单词精确查询（发音/详情用） */
    @GetMapping("/find")
    public Map<String, Object> find(@RequestParam String word) {
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT id, word, phonetic, phonetic_us AS phoneticUs, meaning_cn AS meaningCn, pos, " +
                        "sentence_en AS sentenceEn, sentence_cn AS sentenceCn FROM word WHERE word=? LIMIT 1",
                (rs, i) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("word", rs.getString("word"));
                    row.put("phonetic", nz(rs.getString("phonetic")));
                    row.put("phoneticUs", nz(rs.getString("phoneticUs")));
                    row.put("meaningCn", nz(rs.getString("meaningCn")));
                    row.put("pos", nz(rs.getString("pos")));
                    row.put("sentenceEn", nz(rs.getString("sentenceEn")));
                    row.put("sentenceCn", nz(rs.getString("sentenceCn")));
                    return row;
                },
                word);
        if (rows.isEmpty()) {
            Map<String, Object> m = new HashMap<>();
            m.put("found", false);
            return m;
        }
        Map<String, Object> m = rows.get(0);
        m.put("found", true);
        return m;
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }
}

# -*- coding: utf-8 -*-
"""在 MatchAdviceService.java 中 advise() 方法后追加 streamAdvice() 流式方法"""
import io

p = r"D:\123\gz199\server\src\main\java\com\gz199\ai\MatchAdviceService.java"
s = io.open(p, encoding="utf-8").read()

if "streamAdvice" in s:
    print("ALREADY PATCHED")
else:
    anchor = '''    @SuppressWarnings("unchecked")
    private Map<String, Object> fillAdvice(Map<String, Object> out) {'''
    assert anchor in s, "anchor not found"
    stream_method = '''    /** 流式择校建议：onDelta 收到逐块文本，返回完整建议文本。无法生成时抛异常（消息可直接展示）。 */
    @SuppressWarnings("unchecked")
    public String streamAdvice(Map<String, Object> body, java.util.function.Consumer<String> onDelta) throws Exception {
        Map<String, Object> out = buildRank(body);
        if (out.get("error") != null) {
            throw new IllegalStateException(str(out.get("error")));
        }
        List<Map<String, Object>> ranked = (List<Map<String, Object>>) out.getOrDefault("ranked", List.of());
        List<Map<String, Object>> unmatched = (List<Map<String, Object>>) out.getOrDefault("unmatched", List.of());

        if (ranked.isEmpty() && unmatched.isEmpty()) {
            return "所选省份里，库中没有这个专业、这个学习方式的学校。";
        }
        if (ranked.isEmpty()) {
            return "这些学校还没有复试线或拟录取最低分，无法分成稳 / 冲 / 难。有分数后再排。";
        }
        if (!deepSeekProps.canCall()) {
            throw new IllegalStateException("未配置 DEEPSEEK_API_KEY，暂时无法生成文字建议。");
        }
        return deepSeek.streamChat(systemPrompt(), userPrompt(
                (Integer) out.get("scoreMin"),
                (Integer) out.get("scoreMax"),
                str(out.get("majorName")),
                str(out.get("studyModeLabel")),
                stringList(out.get("provinces")),
                str(out.get("nationNote")),
                ranked,
                unmatched,
                asInt(out.get("steadyCount")) == null ? 0 : asInt(out.get("steadyCount")),
                asInt(out.get("reachCount")) == null ? 0 : asInt(out.get("reachCount")),
                asInt(out.get("hardCount")) == null ? 0 : asInt(out.get("hardCount"))
        ), 4096, onDelta);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fillAdvice(Map<String, Object> out) {'''
    s = s.replace(anchor, stream_method, 1)
    io.open(p, "w", encoding="utf-8", newline="").write(s)
    print("PATCHED MatchAdviceService")

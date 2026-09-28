# -*- coding: utf-8 -*-
"""修复 DeepSeekClient streamChatBody 的 for-each Stream 编译错误"""
import io

p = r"D:\123\gz199\server\src\main\java\com\gz199\ai\DeepSeekClient.java"
s = io.open(p, encoding="utf-8").read()

old = '''        try (java.util.stream.Stream<String> lines = resp.body()) {
            for (String line : lines) {
                String s = line.trim();'''
new = '''        try (java.util.stream.Stream<String> lines = resp.body()) {
            java.util.Iterator<String> it = lines.iterator();
            while (it.hasNext()) {
                String line = it.next();
                String s = line.trim();'''
assert old in s, "pattern not found"
s = s.replace(old, new, 1)
io.open(p, "w", encoding="utf-8", newline="").write(s)
print("FIXED iterator")

# -*- coding: utf-8 -*-
"""intent/result.vue：对照表模块右上角加「右滑可查看录取率及建议」提示"""
import io

p = r"D:\123\gz199\pages\intent\result.vue"
raw = io.open(p, "rb").read()
crlf = raw.count(b"\r\n") > raw.count(b"\n") / 2
s = raw.decode("utf-8").replace("\r\n", "\n")

if "edu-swipe-tip" in s:
    print("ALREADY PATCHED")
    raise SystemExit

# 1) 模板：在 kicker 与 scroll-view 之间插入提示行（与 scroll-view 同级缩进）
old = '''						<text class="edu-kicker">{{ blockTitle(block.title) }}<text class="edu-match-toggle">{{ isAdviceOpen(block.title) ? '收起' : '展开' }}</text></text>
						<scroll-view
							v-if="block.title === '对照表' && tableOf(block.body)"'''
new = '''						<text class="edu-kicker">{{ blockTitle(block.title) }}<text class="edu-match-toggle">{{ isAdviceOpen(block.title) ? '收起' : '展开' }}</text></text>
						<view class="edu-swipe-tip-row" v-if="block.title === '对照表'">
							<text class="edu-swipe-tip">右滑可查看录取率及建议 →</text>
						</view>
						<scroll-view
							v-if="block.title === '对照表' && tableOf(block.body)"'''
assert old in s, "kicker block not found"
s = s.replace(old, new, 1)

# 2) 追加样式
stream_css = '''
.edu-swipe-tip-row {
	display: flex;
	justify-content: flex-end;
	margin: 4rpx 0 12rpx;
}
.edu-swipe-tip {
	font-size: 22rpx;
	color: #94a3b8;
	background: #f1f5f9;
	padding: 6rpx 16rpx;
	border-radius: 999rpx;
}
</style>'''
assert s.endswith("</style>\n") or s.endswith("</style>"), "style tail not found"
s = s.rstrip()
if s.endswith("</style>"):
    s = s[: s.rfind("</style>")] + stream_css
else:
    s = s + "\n" + stream_css

if crlf:
    s = s.replace("\n", "\r\n")
io.open(p, "w", encoding="utf-8", newline="").write(s)
print("PATCHED intent/result.vue swipe tip")

# -*- coding: utf-8 -*-
"""intent/result.vue 择校建议改流式打字机"""
import io
import re

p = r"D:\123\gz199\pages\intent\result.vue"
raw = io.open(p, "rb").read()
crlf = raw.count(b"\r\n") > raw.count(b"\n") / 2
s = raw.decode("utf-8").replace("\r\n", "\n")

if "matchAdviceStream" in s:
    print("ALREADY PATCHED")
    raise SystemExit

# 1) 模板：adviceLoading 等待块内加流式输出区
old_wait = '''			<view class="edu-card edu-advice-wait" v-if="adviceLoading">
				<view class="edu-advice-wait-bar" />
				<text class="edu-advice-wait-brand">智能择校</text>
				<text class="edu-advice-wait-title edu-advice-pulse">根据学校历年分数线提供建议中{{ waitDots }}</text>
			</view>'''
new_wait = '''			<view class="edu-card edu-advice-wait" v-if="adviceLoading">
				<view class="edu-advice-wait-bar" />
				<text class="edu-advice-wait-brand">智能择校</text>
				<text class="edu-advice-wait-title edu-advice-pulse">正在根据历年分数线写建议{{ waitDots }}</text>
				<view class="edu-advice-stream" v-if="adviceStreamText">
					<scroll-view scroll-y class="edu-advice-stream-body">
						<text class="edu-advice-stream-text">{{ adviceStreamText }}</text>
					</scroll-view>
				</view>
			</view>'''
assert old_wait in s, "wait block not found"
s = s.replace(old_wait, new_wait, 1)

# 2) import
old_imp = "import { matchSchools, matchAdvice } from '../../utils/api.js'"
new_imp = "import { matchSchools, matchAdviceStream } from '../../utils/api.js'"
assert old_imp in s
s = s.replace(old_imp, new_imp, 1)

# 3) data 加 adviceStreamText
old_data = "adviceLoading: false,"
new_data = "adviceLoading: false,\n\t\t\t\tadviceStreamText: '',"
assert old_data in s
s = s.replace(old_data, new_data, 1)

# 4) loadAdvice 方法替换为流式版（tab 缩进：方法 3 tab，方法内 4 tab）
pat = re.compile(r'\t\t\tasync loadAdvice\(\) \{.*?\n\t\t\t\},\n', re.S)
m = pat.search(s)
assert m, "loadAdvice method not found"
old_advice = m.group(0)
new_advice = '''			async loadAdvice() {
				if (!this.ranked.length) return
				this.adviceLoading = true
				this.adviceStreamText = ''
				this.startDots()
				try {
					await matchAdviceStream(this.matchBody, {
						onDelta: (text) => {
							this.adviceStreamText += text
						},
						onDone: (ev) => {
							const advice = (ev && ev.advice) || ''
							this.advice = advice
							this.adviceBlocks = this.layoutBlocks(advice)
							this.adviceOpenMap = {}
							this.adviceOpen = true
							if (!this.adviceBlocks.length && !this.advice) {
								this.adviceNote = '没有生成文字建议。列表仍按稳 → 冲 → 难排列。'
							}
						},
						onError: (msg) => {
							this.adviceNote = msg || '建议暂时没生成，可先看下方稳 / 冲 / 难列表。'
						}
					})
				} catch (e) {
					this.adviceNote = '建议暂时没生成，可先看下方稳 / 冲 / 难列表。'
				} finally {
					this.stopDots()
					this.adviceLoading = false
				}
			},
			layoutBlocks(raw) {
				let text = String(raw || '').replace(/\\r\\n/g, '\\n').trim()
				text = text.replace(/^```[a-zA-Z]*\\s*/gm, '').replace(/```/g, '')
				text = text.replace(/\\*\\*/g, '').replace(/^#{1,6}\\s*/gm, '').trim()
				const rows = []
				text.split('\\n').forEach((line) => {
					const t = line.trim()
					if (t.startsWith('|')) rows.push(t)
				})
				const body = rows.join('\\n').trim()
				if (!body) {
					return text ? [{ title: '对照表', body: text }] : []
				}
				return [{ title: '对照表', body }]
			},
'''
s = s.replace(old_advice, new_advice, 1)

# 5) 追加样式
stream_css = '''
<style scoped>
.edu-advice-stream {
	margin-top: 20rpx;
	border-radius: 16rpx;
	background: #fff;
	padding: 20rpx;
	border: 1rpx solid #e5e9f2;
}
.edu-advice-stream-body {
	max-height: 400rpx;
}
.edu-advice-stream-text {
	font-size: 25rpx;
	line-height: 1.7;
	color: #334155;
	white-space: pre-wrap;
	word-break: break-all;
	font-family: monospace;
}
</style>'''
assert s.endswith("</script>\n") or s.endswith("</script>"), "script tail not found"
s = s.rstrip() + "\n" + stream_css + "\n"

if crlf:
    s = s.replace("\n", "\r\n")
io.open(p, "w", encoding="utf-8", newline="").write(s)
print("PATCHED intent/result.vue")

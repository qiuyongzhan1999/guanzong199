# -*- coding: utf-8 -*-
"""ai-essay/index.vue 改流式：模板加流式面板 + import + data + submit 流式版（正则匹配 tab 缩进）"""
import io
import re

p = r"D:\123\gz199\pages\ai-essay\index.vue"
raw = io.open(p, "rb").read()
crlf = raw.count(b"\r\n") > raw.count(b"\n") / 2
s = raw.decode("utf-8").replace("\r\n", "\n")

if "gradeEssayStream" in s:
    print("ALREADY PATCHED")
    raise SystemExit

# 1) 模板：按钮后加流式面板
old_btn = '''		<view class="edu-btn" :class="{ off: !canSubmit || loading }" @tap="submit">
			{{ loading ? '批改中…' : '开始 AI 批改' }}
		</view>
		<view class="edu-essay-disclaimer">'''
new_btn = '''		<view class="edu-btn" :class="{ off: !canSubmit || loading }" @tap="submit">
			{{ loading ? '批改中…' : '开始 AI 批改' }}
		</view>

		<view class="edu-stream-panel" v-if="loading">
			<view class="edu-stream-head">
				<text class="edu-card-title">AI 正在批改…</text>
				<text class="edu-hint">内容边生成边显示，请稍候</text>
			</view>
			<scroll-view scroll-y class="edu-stream-body">
				<text class="edu-stream-text">{{ streamText || '正在连接 AI…' }}</text>
			</scroll-view>
		</view>

		<view class="edu-essay-disclaimer">'''
assert old_btn in s, "btn pattern not found"
s = s.replace(old_btn, new_btn, 1)

# 2) import
old_imp = "import { gradeEssay } from '../../utils/api.js'"
new_imp = "import { gradeEssayStream } from '../../utils/api.js'"
assert old_imp in s
s = s.replace(old_imp, new_imp, 1)

# 3) data 加 streamText
old_data = "essayImageFile: null,\n\t\t\t\tloading: false\n\t\t\t}"
new_data = "essayImageFile: null,\n\t\t\t\tloading: false,\n\t\t\t\tstreamText: ''\n\t\t\t}"
assert old_data in s, "data pattern not found"
s = s.replace(old_data, new_data, 1)

# 4) submit() 整方法正则替换（tab 缩进：方法 3 tab，方法内 4 tab，块内 5 tab）
pat = re.compile(r'\t\t\tasync submit\(\) \{.*?\n\t\t\t\}\n', re.S)
m = pat.search(s)
assert m, "submit method not found"
old_submit = m.group(0)
new_submit = '''			async submit() {
				if (!this.canSubmit || this.loading) return
				this.loading = true
				this.streamText = ''
				try {
					const body = {
						type: this.type,
						essay: this.essay
					}
					if (this.type === 'argument') body.material = this.material
					else if (this.type === 'thesis') body.topic = this.material
					else {
						body.requirement = this.material
						body.taskType = this.type === 'en_chart' ? 'chart' : 'letter'
					}

					if (this.essayImagePath || this.essayImageFile) {
						const b64 = await this.readBase64(this.essayImagePath, this.essayImageFile)
						if (!b64) {
							uni.showToast({ title: '读作文图失败', icon: 'none' })
							return
						}
						if (b64.length > 8_000_000) {
							uni.showToast({ title: '作文图太大，请换一张', icon: 'none' })
							return
						}
						body.essayImageBase64 = b64
						body.essayMimeType = this.mimeFromPath(this.essayImagePath, this.essayImageFile)
					}
					if (this.materialImagePath || this.materialImageFile) {
						const b64 = await this.readBase64(this.materialImagePath, this.materialImageFile)
						if (!b64) {
							uni.showToast({ title: '读题目图失败', icon: 'none' })
							return
						}
						if (b64.length > 8_000_000) {
							uni.showToast({ title: '题目图太大，请换一张', icon: 'none' })
							return
						}
						body.materialImageBase64 = b64
						body.materialMimeType = this.mimeFromPath(this.materialImagePath, this.materialImageFile)
					}

					await gradeEssayStream(body, {
						onDelta: (text) => {
							this.streamText += text
						},
						onDone: (ev) => {
							if (ev && ev.error) {
								uni.showToast({ title: ev.error, icon: 'none', duration: 2800 })
								return
							}
							uni.setStorageSync('essayGradeLatest', ev)
							uni.navigateTo({ url: '/pages/ai-essay/result' })
						},
						onError: (msg) => {
							uni.showToast({ title: msg || '批改失败', icon: 'none', duration: 2800 })
						}
					})
				} catch (e) {
					const msg = (e && (e.errMsg || e.message)) || '网络错误'
					uni.showToast({ title: '失败：' + msg, icon: 'none' })
				} finally {
					this.loading = false
				}
			}
'''
s = s.replace(old_submit, new_submit, 1)

# 5) 页面无 <style> 块：在 </script> 后追加样式块
if ".edu-stream-panel" not in s:
    stream_css = '''
<style scoped>
.edu-stream-panel {
	margin-top: 24rpx;
	border-radius: 20rpx;
	background: #f5f7fb;
	padding: 24rpx;
	border: 1rpx solid #e5e9f2;
}
.edu-stream-head {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 16rpx;
}
.edu-stream-body {
	max-height: 420rpx;
}
.edu-stream-text {
	font-size: 24rpx;
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
print("PATCHED ai-essay/index.vue")

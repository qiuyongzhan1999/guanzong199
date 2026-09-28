<template>
	<view class="edu-page">
		<view class="edu-card">
			<text class="edu-card-title">选择题型</text>
			<view class="edu-chips">
				<view
					class="edu-chip"
					v-for="item in types"
					:key="item.id"
					:class="{ on: type === item.id }"
					@tap="type = item.id"
				>{{ item.label }}</view>
			</view>
			<text class="edu-hint">{{ typeHint }}</text>
		</view>

		<view class="edu-card edu-essay-card" v-if="needMaterial">
			<text class="edu-card-title">{{ materialTitle }}</text>
			<view class="edu-area-shell">
				<textarea
					class="edu-area"
					:value="material"
					:placeholder="materialPh"
					placeholder-class="edu-search-ph"
					:maxlength="4000"
					:show-confirm-bar="false"
					@input="onMaterial"
				/>
			</view>
			<view class="edu-essay-tools">
				<view class="edu-chip on edu-essay-upload" @tap="pickImage('material')">拍照上传</view>
				<text class="edu-hint" v-if="materialImagePath">已附图，将与文字一并提交批改</text>
				<text class="edu-hint" v-else>可粘贴文字，或拍照直接交给 AI</text>
			</view>
			<image v-if="materialImagePath" class="edu-essay-preview" :src="materialImagePath" mode="widthFix" />
		</view>

		<view class="edu-card edu-essay-card">
			<view class="edu-card-head">
				<text class="edu-card-title">作文正文</text>
				<text class="edu-hint">{{ wordTip }}</text>
			</view>
			<view class="edu-area-shell">
				<textarea
					class="edu-area edu-area-lg"
					:value="essay"
					placeholder="粘贴作文，或拍照上传，文字和图片都能批改。"
					placeholder-class="edu-search-ph"
					:maxlength="8000"
					:show-confirm-bar="false"
					@input="onEssay"
				/>
			</view>
			<view class="edu-essay-tools">
				<view class="edu-chip on edu-essay-upload" @tap="pickImage('essay')">拍照上传</view>
				<text class="edu-hint" v-if="essayImagePath">已附图，将与文字一并提交批改</text>
				<text class="edu-hint" v-else>可粘贴文字，或拍照直接交给 AI</text>
			</view>
			<image v-if="essayImagePath" class="edu-essay-preview" :src="essayImagePath" mode="widthFix" />
		</view>

		<view class="edu-btn" :class="{ off: !canSubmit || loading }" @tap="submit">
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

		<view class="edu-essay-disclaimer">
			<text class="edu-hint">名师式批改：先算清扣分，再给及格→高分→示范三层改法。</text>
			<text class="edu-hint edu-essay-train-tip">AI 仅供参考：它是经过专业训练的哦</text>
		</view>
	</view>
</template>

<script>
	import { gradeEssayStream } from '../../utils/api.js'

	const TYPES = [
		{ id: 'argument', label: '论证有效性', hint: '管综写作 · 30 分 · 找逻辑缺陷', need: 'material', title: '题目材料', ph: '粘贴材料原文，或拍照上传（可选，建议提供）' },
		{ id: 'thesis', label: '论说文', hint: '管综写作 · 35 分 · 立意结构论证', need: 'topic', title: '作文题目', ph: '粘贴题目，或拍照上传（可选）' },
		{ id: 'en_letter', label: '英语小作文', hint: '英语二 · 10 分 · 书信/通知', need: 'requirement', title: '题目要求', ph: '粘贴题目要求，或拍照上传' },
		{ id: 'en_chart', label: '英语大作文', hint: '英语二 · 15 分 · 图表作文', need: 'requirement', title: '题目要求', ph: '描述图表要点，或拍照上传' }
	]

	export default {
		data() {
			return {
				types: TYPES,
				type: 'argument',
				essay: '',
				material: '',
				materialImagePath: '',
				materialImageFile: null,
				essayImagePath: '',
				essayImageFile: null,
				loading: false,
				streamText: ''
			}
		},
		computed: {
			current() {
				return TYPES.find((t) => t.id === this.type) || TYPES[0]
			},
			typeHint() {
				return this.current.hint
			},
			needMaterial() {
				return true
			},
			materialTitle() {
				return this.current.title
			},
			materialPh() {
				return this.current.ph
			},
			wordTip() {
				if (this.essayImagePath && !this.essay.replace(/\s+/g, '').length) {
					return '已附图'
				}
				const n = this.essay.replace(/\s+/g, '').length
				const en = this.type.indexOf('en_') === 0
				if (en) {
					const w = this.essay.trim() ? this.essay.trim().split(/\s+/).length : 0
					const base = '约 ' + w + ' 词'
					return this.essayImagePath ? base + ' · 含图' : base
				}
				const base = '约 ' + n + ' 字'
				return this.essayImagePath ? base + ' · 含图' : base
			},
			canSubmit() {
				if (this.essayImagePath) return true
				return this.essay.replace(/\s+/g, '').length >= 80
			}
		},
		methods: {
			onEssay(e) {
				this.essay = (e.detail && e.detail.value) || ''
			},
			onMaterial(e) {
				this.material = (e.detail && e.detail.value) || ''
			},
			mimeFromPath(path, file) {
				const t = (file && (file.type || file.mimeType)) || ''
				if (t) return t
				const p = String(path || '').toLowerCase()
				if (p.indexOf('.png') >= 0 || p.indexOf('image/png') >= 0) return 'image/png'
				if (p.indexOf('.webp') >= 0) return 'image/webp'
				if (p.indexOf('.gif') >= 0) return 'image/gif'
				return 'image/jpeg'
			},
			stripDataUrl(data) {
				const s = String(data || '')
				const i = s.indexOf(',')
				return i >= 0 ? s.slice(i + 1) : s
			},
			readBase64(path, file) {
				return new Promise((resolve, reject) => {
					const done = (data) => resolve(this.stripDataUrl(data))
					const fail = (err) => reject(err || new Error('读图失败'))

					// H5：chooseImage 常带回 File/Blob，优先 FileReader
					if (
						file &&
						typeof FileReader !== 'undefined' &&
						typeof Blob !== 'undefined' &&
						file instanceof Blob
					) {
						const reader = new FileReader()
						reader.onload = () => done(reader.result)
						reader.onerror = fail
						reader.readAsDataURL(file)
						return
					}

					// 微信/小程序：uni 或 wx 的文件系统
					const getFs =
						(typeof uni !== 'undefined' && typeof uni.getFileSystemManager === 'function' && uni.getFileSystemManager) ||
						(typeof wx !== 'undefined' && typeof wx.getFileSystemManager === 'function' && wx.getFileSystemManager)
					if (getFs) {
						getFs().readFile({
							filePath: path,
							encoding: 'base64',
							success: (res) => done(res.data || ''),
							fail
						})
						return
					}

					// App：plus.io
					if (typeof plus !== 'undefined' && plus.io && path) {
						plus.io.resolveLocalFileSystemURL(
							path,
							(entry) => {
								entry.file(
									(f) => {
										const reader = new plus.io.FileReader()
										reader.onloadend = (e) => done((e && e.target && e.target.result) || '')
										reader.onerror = fail
										reader.readAsDataURL(f)
									},
									fail
								)
							},
							fail
						)
						return
					}

					// H5 兜底：blob: / http(s): 临时地址
					if (path && typeof fetch === 'function') {
						fetch(path)
							.then((r) => r.blob())
							.then((blob) => {
								const reader = new FileReader()
								reader.onload = () => done(reader.result)
								reader.onerror = fail
								reader.readAsDataURL(blob)
							})
							.catch(fail)
						return
					}

					fail(new Error('当前环境无法读取图片'))
				})
			},
			pickImage(target) {
				if (this.loading) return
				uni.chooseImage({
					count: 1,
					sizeType: ['compressed'],
					sourceType: ['camera', 'album'],
					success: (res) => {
						const path = (res.tempFilePaths && res.tempFilePaths[0]) || ''
						const file = (res.tempFiles && res.tempFiles[0]) || null
						if (!path && !file) return
						if (target === 'material') {
							this.materialImagePath = path || (file && file.path) || ''
							this.materialImageFile = file
						} else {
							this.essayImagePath = path || (file && file.path) || ''
							this.essayImageFile = file
						}
						uni.showToast({ title: '已附图，可直接批改', icon: 'none' })
					}
				})
			},
			async submit() {
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
		}
	}
</script>

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
</style>

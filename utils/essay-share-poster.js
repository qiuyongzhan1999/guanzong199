import { EDU } from '../styles/edu-theme.js'

const W = 750
const PAD = 40
const CONTENT_W = W - PAD * 2
/** 微信旧版 canvas 单边常见上限；完整模式超出会纵向压缩 */
const MAX_CANVAS_H = 4096

function cleanText(s) {
	return String(s == null ? '' : s)
		.replace(/\s+/g, ' ')
		.trim()
}

function clampText(s, n) {
	const t = cleanText(s)
	if (!t) return ''
	return t.length > n ? t.slice(0, n - 1) + '…' : t
}

/** 按中英文混排估算换行 */
function estimateLines(text, fontSize, maxWidth) {
	const t = cleanText(text)
	if (!t) return ['']
	const lines = []
	let line = ''
	let width = 0
	for (let i = 0; i < t.length; i++) {
		const ch = t[i]
		const cw = /[\u0000-\u00ff]/.test(ch) ? fontSize * 0.55 : fontSize
		if (width + cw > maxWidth && line) {
			lines.push(line)
			line = ch
			width = cw
		} else {
			line += ch
			width += cw
		}
	}
	if (line) lines.push(line)
	return lines.length ? lines : ['']
}

function formatPoints(v) {
	const n = Number(v)
	if (Number.isNaN(n)) return String(v || '')
	return n > 0 ? '+' + n : String(n)
}

function createPainter() {
	const C = EDU.color
	const blocks = []
	let y = PAD

	const pushGap = (h) => {
		y += h
	}

	const pushText = (text, opt = {}) => {
		const raw = cleanText(text)
		if (!raw && !opt.allowEmpty) return
		const fontSize = opt.fontSize || 26
		const color = opt.color || C.ink
		const bold = !!opt.bold
		const lineH = opt.lineH || Math.round(fontSize * 1.42)
		const maxWidth = opt.maxWidth || CONTENT_W
		const lines = estimateLines(raw, fontSize, maxWidth)
		blocks.push({
			type: 'text',
			x: opt.x != null ? opt.x : PAD,
			y,
			lines,
			fontSize,
			lineH,
			color,
			bold,
			align: opt.align || 'left'
		})
		y += lines.length * lineH
	}

	const pushBar = (h, color) => {
		blocks.push({ type: 'rect', x: 0, y, w: W, h, color })
		y += h
	}

	const section = (title) => {
		pushGap(8)
		pushText(title, { fontSize: 30, color: C.ink, bold: true })
		pushGap(14)
	}

	const pushScoreCard = (scoreText, summary, summaryMax) => {
		const scoreTop = y
		const summaryText = summaryMax ? clampText(summary, summaryMax) : cleanText(summary)
		const summaryLines = summaryText
			? estimateLines(summaryText, 26, CONTENT_W - 40)
			: []
		const scoreH =
			44 +
			88 +
			10 +
			26 +
			(summaryLines.length ? 14 + summaryLines.length * Math.round(26 * 1.42) : 0) +
			36
		blocks.push({
			type: 'roundRect',
			x: PAD,
			y: scoreTop,
			w: CONTENT_W,
			h: scoreH,
			r: 24,
			color: C.wash,
			stroke: 'rgba(47,111,237,0.18)'
		})
		y = scoreTop + 36
		pushText(String(scoreText), {
			fontSize: 88,
			color: C.signal,
			bold: true,
			align: 'center',
			x: W / 2,
			maxWidth: CONTENT_W
		})
		pushGap(6)
		pushText('分', {
			fontSize: 26,
			color: C.mist,
			align: 'center',
			x: W / 2
		})
		if (summaryLines.length) {
			pushGap(12)
			pushText(summaryText, {
				fontSize: 26,
				color: C.ink,
				align: 'center',
				x: W / 2,
				maxWidth: CONTENT_W - 40
			})
		}
		y = scoreTop + scoreH + 22
	}

	const pushCta = (extraHint) => {
		pushGap(12)
		blocks.push({
			type: 'line',
			x1: PAD,
			y1: y,
			x2: W - PAD,
			y2: y,
			color: 'rgba(20,32,51,0.08)'
		})
		pushGap(24)

		const ctaTop = y
		const ctaH = extraHint ? 188 : 156
		blocks.push({
			type: 'roundRect',
			x: PAD,
			y: ctaTop,
			w: CONTENT_W,
			h: ctaH,
			r: 24,
			color: C.wash,
			stroke: 'rgba(47,111,237,0.22)'
		})
		y = ctaTop + 28
		pushText('微信搜一搜 · 打开小程序', {
			fontSize: 22,
			color: C.mist,
			align: 'center',
			x: W / 2
		})
		pushGap(12)
		pushText('管综上岸通', {
			fontSize: 32,
			color: C.signal,
			bold: true,
			align: 'center',
			x: W / 2
		})
		pushGap(10)
		pushText('择校有数，上岸有路', {
			fontSize: 22,
			color: C.faint,
			align: 'center',
			x: W / 2
		})
		if (extraHint) {
			pushGap(10)
			pushText(extraHint, {
				fontSize: 22,
				color: C.mist,
				align: 'center',
				x: W / 2
			})
		}
		y = ctaTop + ctaH + 20
		pushText('AI 仅供参考', {
			fontSize: 20,
			color: C.faint,
			align: 'center',
			x: W / 2
		})
		pushGap(PAD)
	}

	const finish = (allowScale) => {
		let height = Math.ceil(y)
		let scale = 1
		if (allowScale && height > MAX_CANVAS_H) {
			scale = MAX_CANVAS_H / height
			height = MAX_CANVAS_H
			blocks.forEach((b) => {
				if (b.type === 'text') {
					b.y = Math.round(b.y * scale)
					b.fontSize = Math.max(11, Math.round(b.fontSize * scale))
					b.lineH = Math.max(13, Math.round(b.lineH * scale))
				} else if (b.type === 'rect') {
					b.y = Math.round(b.y * scale)
					b.h = Math.max(2, Math.round(b.h * scale))
				} else if (b.type === 'roundRect') {
					b.y = Math.round(b.y * scale)
					b.h = Math.max(8, Math.round(b.h * scale))
					b.r = Math.max(6, Math.round((b.r || 16) * scale))
				} else if (b.type === 'line') {
					b.y1 = Math.round(b.y1 * scale)
					b.y2 = Math.round(b.y2 * scale)
				}
			})
		}
		return { width: W, height, blocks, colors: C, scaled: scale < 1 }
	}

	return {
		C,
		get y() {
			return y
		},
		set y(v) {
			y = v
		},
		blocks,
		pushGap,
		pushText,
		pushBar,
		section,
		pushScoreCard,
		pushCta,
		finish
	}
}

/**
 * @param {object} payload
 * @param {'compact'|'full'} [payload.mode='compact'] compact=成绩单封面；full=完整长图
 */
export function buildEssayPosterPlan(payload) {
	const mode = (payload && payload.mode) === 'full' ? 'full' : 'compact'
	return mode === 'full' ? buildFullPlan(payload) : buildCompactPlan(payload)
}

/** 精简海报：分数 + 总评 + 最多 3 条扣分 + 引流 */
function buildCompactPlan(payload) {
	const {
		typeLabel = 'AI 批改',
		scoreText = '—',
		summary = '',
		metaLine = '',
		deductions = [],
		problems = []
	} = payload || {}

	const p = createPainter()
	const { C, pushBar, pushText, pushGap, pushScoreCard, section, pushCta, finish } = p

	pushBar(8, C.signal)
	pushGap(32)
	pushText('管综上岸通 · AI 批改', {
		fontSize: 22,
		color: C.mist,
		bold: true
	})
	pushGap(10)
	pushText(typeLabel, { fontSize: 38, color: C.ink, bold: true })
	pushGap(24)

	pushScoreCard(scoreText, summary, 72)

	if (metaLine) {
		pushText(clampText(metaLine, 48), { fontSize: 22, color: C.faint })
		pushGap(18)
	}

	const topDeductions = (deductions || []).slice(0, 3)
	if (topDeductions.length) {
		section('主要扣分')
		topDeductions.forEach((d) => {
			pushText((d.item || '扣分') + '  ' + formatPoints(d.points), {
				fontSize: 26,
				color: C.heat,
				bold: true
			})
			pushGap(6)
			if (d.reason) {
				pushText(clampText(d.reason, 64), { fontSize: 24, color: C.mist })
			}
			pushGap(14)
		})
	} else if ((problems || []).length) {
		section('核心问题')
		problems.slice(0, 2).forEach((item, i) => {
			pushText((item.type || '问题') + ' ' + (i + 1), {
				fontSize: 26,
				color: C.ink,
				bold: true
			})
			pushGap(6)
			if (item.why) {
				pushText(clampText(item.why, 72), { fontSize: 24, color: C.mist })
			}
			pushGap(14)
		})
	}

	pushCta('完整批改请打开小程序查看')
	return finish(false)
}

/** 完整长图：结果页主要区块全量（存档用） */
function buildFullPlan(payload) {
	const {
		typeLabel = 'AI 批改',
		scoreText = '—',
		summary = '',
		metaLine = '',
		deductions = [],
		dimList = [],
		thesisCheck = null,
		strengths = [],
		problems = [],
		missingList = [],
		missingTitle = '漏掉的致命伤',
		vocab = [],
		outline = ''
	} = payload || {}

	const p = createPainter()
	const { C, pushBar, pushText, pushGap, pushScoreCard, section, pushCta, finish } = p

	pushBar(8, C.signal)
	pushGap(32)
	pushText('管综上岸通 · AI 批改（完整版）', {
		fontSize: 22,
		color: C.mist,
		bold: true
	})
	pushGap(10)
	pushText(typeLabel, { fontSize: 38, color: C.ink, bold: true })
	pushGap(24)

	pushScoreCard(scoreText, summary, 0)

	if (metaLine) {
		pushText(metaLine, { fontSize: 22, color: C.faint })
		pushGap(20)
	}

	if (deductions.length) {
		section('扣分账单')
		deductions.forEach((d) => {
			pushText((d.item || '扣分') + '  ' + formatPoints(d.points), {
				fontSize: 26,
				color: C.heat,
				bold: true
			})
			pushGap(6)
			if (d.reason) pushText(d.reason, { fontSize: 24, color: C.mist })
			pushGap(16)
		})
	}

	if (dimList.length) {
		section('分项')
		dimList.forEach((item) => {
			pushText((item.name || '') + '  ' + (item.score == null ? '—' : item.score), {
				fontSize: 26,
				color: C.signal,
				bold: true
			})
			pushGap(4)
			if (item.comment) pushText(item.comment, { fontSize: 24, color: C.mist })
			pushGap(14)
		})
	}

	if (thesisCheck) {
		section('立意检查')
		pushText('学生立意：' + (thesisCheck.student_thesis || '—'), {
			fontSize: 24,
			color: C.ink
		})
		pushGap(8)
		pushText(
			(thesisCheck.is_on_topic ? '切题' : '可能偏题') +
				(thesisCheck.comment ? ' · ' + thesisCheck.comment : ''),
			{ fontSize: 24, color: C.mist }
		)
		pushGap(12)
	}

	if (strengths.length) {
		section('写得好的地方')
		strengths.forEach((s) => {
			pushText('· ' + s, { fontSize: 24, color: C.good })
			pushGap(10)
		})
	}

	if (problems.length) {
		section('核心问题 · 阶梯修改')
		problems.forEach((item, i) => {
			pushText((item.type || '问题') + ' ' + (i + 1), {
				fontSize: 26,
				color: C.ink,
				bold: true
			})
			pushGap(8)
			if (item.original) {
				pushText('原句：' + item.original, { fontSize: 24, color: C.mist })
				pushGap(6)
			}
			if (item.why) {
				pushText(item.why, { fontSize: 24, color: C.ink })
				pushGap(8)
			}
			if (item.chinglish_alert && item.chinglish_alert !== 'null') {
				pushText('中式英语：' + item.chinglish_alert, {
					fontSize: 24,
					color: C.heat
				})
				pushGap(8)
			}
			if (item.reductio_ad_absurdum && item.reductio_ad_absurdum !== 'null') {
				pushText('归谬示范：' + item.reductio_ad_absurdum, {
					fontSize: 24,
					color: C.signal
				})
				pushGap(8)
			}
			const tf = item.tiered_fix
			if (tf) {
				if (tf.pass) {
					pushText('及格改：' + tf.pass, { fontSize: 24, color: C.signal })
					pushGap(6)
				}
				if (tf.high_score) {
					pushText('高分改：' + tf.high_score, { fontSize: 24, color: C.good })
					pushGap(6)
				}
				if (tf.teacher_example) {
					pushText('名师示范：' + tf.teacher_example, {
						fontSize: 24,
						color: C.heat
					})
					pushGap(6)
				}
			}
			pushGap(18)
		})
	}

	if (missingList.length) {
		section(missingTitle || '漏掉的致命伤')
		missingList.forEach((m) => {
			pushText(m.title || m.name || '要点', {
				fontSize: 26,
				color: C.ink,
				bold: true
			})
			pushGap(6)
			if (m.signal) {
				pushText('识别标志：' + m.signal, { fontSize: 24, color: C.mist })
				pushGap(4)
			}
			if (m.lesson) pushText('下次：' + m.lesson, { fontSize: 24, color: C.faint })
			pushGap(14)
		})
	}

	if (vocab.length) {
		section('表达升级库')
		vocab.forEach((v) => {
			if (v.original) {
				pushText(v.original, { fontSize: 24, color: C.mist })
				pushGap(4)
			}
			if (v.upgraded) {
				pushText('→ ' + v.upgraded, { fontSize: 24, color: C.good, bold: true })
				pushGap(4)
			}
			if (v.note) pushText(v.note, { fontSize: 22, color: C.faint })
			pushGap(14)
		})
	}

	if (outline) {
		section('结构提纲建议')
		pushText(outline, { fontSize: 24, color: C.ink })
		pushGap(12)
	}

	pushCta('')
	return finish(true)
}

function roundRectPath(ctx, x, y, w, h, r) {
	const radius = Math.min(r, w / 2, h / 2)
	ctx.beginPath()
	ctx.moveTo(x + radius, y)
	ctx.arcTo(x + w, y, x + w, y + h, radius)
	ctx.arcTo(x + w, y + h, x, y + h, radius)
	ctx.arcTo(x, y + h, x, y, radius)
	ctx.arcTo(x, y, x + w, y, radius)
	ctx.closePath()
}

export function drawEssayPoster(canvasId, plan, componentInstance) {
	return new Promise((resolve, reject) => {
		if (!plan || !plan.height) {
			reject(new Error('海报数据为空'))
			return
		}
		const ctx = uni.createCanvasContext(canvasId, componentInstance)
		const C = plan.colors || EDU.color

		ctx.setFillStyle(C.canvas)
		ctx.fillRect(0, 0, plan.width, plan.height)

		plan.blocks.forEach((b) => {
			if (b.type === 'rect') {
				ctx.setFillStyle(b.color)
				ctx.fillRect(b.x, b.y, b.w, b.h)
				return
			}
			if (b.type === 'roundRect') {
				roundRectPath(ctx, b.x, b.y, b.w, b.h, b.r || 16)
				ctx.setFillStyle(b.color)
				ctx.fill()
				if (b.stroke) {
					roundRectPath(ctx, b.x, b.y, b.w, b.h, b.r || 16)
					ctx.setStrokeStyle(b.stroke)
					ctx.setLineWidth(2)
					ctx.stroke()
				}
				return
			}
			if (b.type === 'line') {
				ctx.beginPath()
				ctx.setStrokeStyle(b.color)
				ctx.setLineWidth(1)
				ctx.moveTo(b.x1, b.y1)
				ctx.lineTo(b.x2, b.y2)
				ctx.stroke()
				return
			}
			if (b.type === 'text') {
				ctx.setFillStyle(b.color)
				ctx.setFontSize(b.fontSize)
				ctx.setTextAlign(b.align || 'left')
				try {
					ctx.setTextBaseline('top')
				} catch (e) {
					/* ignore */
				}
				try {
					ctx.font = (b.bold ? 'bold ' : 'normal ') + b.fontSize + 'px sans-serif'
				} catch (e) {
					/* ignore */
				}
				;(b.lines || []).forEach((line, i) => {
					ctx.fillText(line, b.x, b.y + i * b.lineH)
				})
			}
		})

		ctx.draw(false, () => {
			setTimeout(() => {
				uni.canvasToTempFilePath(
					{
						canvasId,
						width: plan.width,
						height: plan.height,
						destWidth: plan.width,
						destHeight: plan.height,
						fileType: 'png',
						quality: 1,
						success: (res) => resolve(res.tempFilePath),
						fail: (err) => reject(err || new Error('导出图片失败'))
					},
					componentInstance
				)
			}, 160)
		})
	})
}

export function saveImageToAlbum(filePath) {
	return new Promise((resolve, reject) => {
		uni.saveImageToPhotosAlbum({
			filePath,
			success: resolve,
			fail: (err) => {
				uni.getSetting({
					success: (setRes) => {
						const ok = setRes.authSetting && setRes.authSetting['scope.writePhotosAlbum']
						if (ok === false) {
							uni.showModal({
								title: '需要相册权限',
								content: '请允许保存图片到相册，以便分享给好友。',
								confirmText: '去设置',
								success: (m) => {
									if (m.confirm) uni.openSetting({})
								}
							})
						}
						reject(err)
					},
					fail: () => reject(err)
				})
			}
		})
	})
}

export function showWeixinShareImageMenu(filePath) {
	return new Promise((resolve, reject) => {
		const api =
			(typeof wx !== 'undefined' && wx.showShareImageMenu) ||
			(typeof uni !== 'undefined' && uni.showShareImageMenu)
		if (!api) {
			reject(new Error('当前环境不支持图片分享菜单'))
			return
		}
		api({
			path: filePath,
			success: resolve,
			fail: reject
		})
	})
}

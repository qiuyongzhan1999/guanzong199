<template>
	<view class="edu-page">
		<EmptyState v-if="!ready" title="还没有批改结果" sub="请先提交一篇作文。" />
		<template v-else>
			<view class="edu-card edu-essay-score">
				<text class="edu-kicker">{{ typeLabel }}</text>
				<view class="edu-hero-line">
					<text class="edu-hero-num edu-essay-num">{{ scoreText }}</text>
					<text class="edu-hero-unit">分</text>
				</view>
				<text class="edu-prose">{{ summary }}</text>
				<text class="edu-hint">{{ metaLine }}</text>
			</view>

			<view class="edu-card" v-if="deductions.length">
				<text class="edu-card-title">扣分账单</text>
				<view class="edu-essay-bill" v-for="(d, i) in deductions" :key="'d' + i">
					<view class="edu-card-head">
						<text class="edu-name">{{ d.item }}</text>
						<text class="edu-essay-points">{{ formatPoints(d.points) }}</text>
					</view>
					<text class="edu-sub">{{ d.reason }}</text>
				</view>
			</view>

			<view class="edu-card" v-if="dimList.length">
				<text class="edu-card-title">分项</text>
				<view class="edu-essay-dim" v-for="item in dimList" :key="item.name">
					<view class="edu-card-head">
						<text class="edu-name">{{ item.name }}</text>
						<text class="edu-hint tone-signal">{{ item.score }}</text>
					</view>
					<text class="edu-sub">{{ item.comment }}</text>
				</view>
			</view>

			<view class="edu-card" v-if="thesisCheck">
				<text class="edu-card-title">立意检查</text>
				<text class="edu-prose">学生立意：{{ thesisCheck.student_thesis || '—' }}</text>
				<text class="edu-sub">{{ thesisCheck.is_on_topic ? '切题' : '可能偏题' }} · {{ thesisCheck.comment || '' }}</text>
			</view>

			<view class="edu-card" v-if="strengths.length">
				<text class="edu-card-title">写得好的地方</text>
				<text class="edu-prose" v-for="(s, i) in strengths" :key="'s' + i">· {{ s }}</text>
			</view>

			<view class="edu-card" v-if="problems.length">
				<text class="edu-card-title">核心问题 · 阶梯修改</text>
				<view
					class="edu-match-block"
					:class="{ fold: !isOpen('p' + i) }"
					v-for="(p, i) in problems"
					:key="'p' + i"
					@tap="toggle('p' + i)"
				>
					<text class="edu-kicker">{{ p.type || '问题' }} {{ i + 1 }}<text class="edu-match-toggle">{{ isOpen('p' + i) ? '收起' : '展开' }}</text></text>
					<text class="edu-sub" v-if="p.original">原句：{{ p.original }}</text>
					<text class="edu-prose">{{ p.why }}</text>
					<view class="edu-essay-alert" v-if="p.chinglish_alert && p.chinglish_alert !== 'null'">
						<text class="edu-kicker">中式英语</text>
						<text class="edu-prose">{{ p.chinglish_alert }}</text>
					</view>
					<view class="edu-essay-alert edu-essay-reductio" v-if="p.reductio_ad_absurdum && p.reductio_ad_absurdum !== 'null'">
						<text class="edu-kicker">归谬示范</text>
						<text class="edu-prose">{{ p.reductio_ad_absurdum }}</text>
					</view>
					<view class="edu-essay-ladder" v-if="p.tiered_fix">
						<text class="edu-sub"><text class="tone-signal">及格改</text> {{ p.tiered_fix.pass }}</text>
						<text class="edu-sub"><text class="tone-good">高分改</text> {{ p.tiered_fix.high_score }}</text>
						<text class="edu-sub"><text class="tone-heat">名师示范</text> {{ p.tiered_fix.teacher_example }}</text>
					</view>
				</view>
			</view>

			<view class="edu-card" v-if="missingList.length">
				<text class="edu-card-title">{{ missingTitle }}</text>
				<view class="edu-match-block" v-for="(m, i) in missingList" :key="'m' + i">
					<text class="edu-prose">{{ m.title }}</text>
					<text class="edu-sub" v-if="m.signal">识别标志：{{ m.signal }}</text>
					<text class="edu-hint" v-if="m.lesson">下次：{{ m.lesson }}</text>
				</view>
			</view>

			<view class="edu-card" v-if="vocab.length">
				<text class="edu-card-title">表达升级库</text>
				<view class="edu-essay-vocab" v-for="(v, i) in vocab" :key="'v' + i">
					<text class="edu-sub">{{ v.original }}</text>
					<text class="edu-prose tone-good">→ {{ v.upgraded }}</text>
					<text class="edu-hint" v-if="v.note">{{ v.note }}</text>
				</view>
			</view>

			<view class="edu-card" v-if="outline">
				<text class="edu-card-title">结构提纲建议</text>
				<text class="edu-prose">{{ outline }}</text>
			</view>

			<view class="edu-essay-actions">
				<view class="edu-btn" :class="{ off: sharing }" @tap="shareCompact">
					{{ sharing === 'compact' ? '生成海报中…' : '分享给朋友' }}
				</view>
				<text class="edu-hint edu-essay-share-tip">精简成绩单海报，可发给微信好友；完整批改请对方打开小程序</text>
				<view class="edu-btn edu-btn-ghost" :class="{ off: sharing }" @tap="exportFullPoster">
					{{ sharing === 'full' ? '导出中…' : '导出完整长图' }}
				</view>
				<button class="edu-btn edu-btn-ghost edu-essay-share-mp" open-type="share">分享小程序给好友</button>
				<view class="edu-btn edu-btn-ghost" @tap="again">再改一篇</view>
			</view>

			<!-- 离屏画布：用于导出长图 -->
			<canvas
				canvas-id="essaySharePoster"
				id="essaySharePoster"
				class="edu-essay-share-canvas"
				:style="{ width: posterW + 'px', height: posterH + 'px' }"
				:width="posterW"
				:height="posterH"
			/>
		</template>
	</view>
</template>

<script>
	import EmptyState from '../../components/ui/EmptyState.vue'
	import {
		buildEssayPosterPlan,
		drawEssayPoster,
		saveImageToAlbum,
		showWeixinShareImageMenu
	} from '../../utils/essay-share-poster.js'

	export default {
		components: { EmptyState },
		data() {
			return {
				ready: false,
				typeLabel: '',
				type: '',
				wordCount: 0,
				wordUnit: '字',
				disclaimer: '',
				inputMode: '',
				hasEssayImage: false,
				report: {},
				summary: '',
				openMap: {},
				sharing: '',
				shareImagePath: '',
				posterW: 750,
				posterH: 1200
			}
		},
		computed: {
			scoreText() {
				const s = this.report.score
				return s === undefined || s === null ? '—' : String(s)
			},
			metaLine() {
				const bits = []
				if (this.inputMode === 'image' || (this.hasEssayImage && !this.wordCount)) {
					bits.push('图片作文')
				} else if (this.hasEssayImage) {
					bits.push('约 ' + this.wordCount + ' ' + this.wordUnit + ' · 含图')
				} else {
					bits.push('约 ' + this.wordCount + ' ' + this.wordUnit)
				}
				if (this.disclaimer) bits.push(this.disclaimer)
				return bits.join(' · ')
			},
			deductions() {
				return Array.isArray(this.report.score_deduction) ? this.report.score_deduction : []
			},
			dimList() {
				const dims = this.report.dimensions || {}
				return Object.keys(dims).map((name) => {
					const d = dims[name] || {}
					return {
						name,
						score: d.score === undefined ? '—' : String(d.score),
						comment: d.comment || ''
					}
				})
			},
			thesisCheck() {
				return this.report.thesis_check || null
			},
			strengths() {
				return Array.isArray(this.report.strengths) ? this.report.strengths : []
			},
			problems() {
				const list = this.report.problems || []
				return Array.isArray(list) ? list.slice(0, 6) : []
			},
			missingList() {
				return Array.isArray(this.report.missing_list) ? this.report.missing_list : []
			},
			missingTitle() {
				return String(this.type || '').indexOf('en_') === 0 ? '遗漏要点' : '漏掉的致命伤'
			},
			vocab() {
				return Array.isArray(this.report.vocabulary_upgrade) ? this.report.vocabulary_upgrade : []
			},
			outline() {
				return this.report.outline_suggestion || ''
			}
		},
		onShow() {
			const data = uni.getStorageSync('essayGradeLatest')
			if (!data || !data.report) {
				this.ready = false
				return
			}
			this.ready = true
			this.type = data.type || ''
			this.typeLabel = data.typeLabel || '批改结果'
			this.wordCount = data.wordCount || 0
			this.wordUnit = String(data.type || '').indexOf('en_') === 0 ? '词' : '字'
			this.disclaimer = data.disclaimer || ''
			this.inputMode = data.inputMode || ''
			this.hasEssayImage = !!data.hasEssayImage
			this.report = data.report || {}
			this.summary = data.summary || this.report.summary || ''
			this.openMap = { p0: true }
			try {
				uni.showShareMenu({
					withShareTicket: true,
					menus: ['shareAppMessage', 'shareTimeline']
				})
			} catch (e) {
				/* ignore */
			}
		},
		onShareAppMessage() {
			return {
				title: '我刚用管综上岸通批了一篇 · ' + (this.typeLabel || 'AI 批改') + ' ' + this.scoreText + ' 分',
				path: '/pages/ai-essay/index',
				imageUrl: this.shareImagePath || ''
			}
		},
		methods: {
			formatPoints(v) {
				const n = Number(v)
				if (Number.isNaN(n)) return String(v || '')
				return n > 0 ? '+' + n : String(n)
			},
			isOpen(key) {
				return !!this.openMap[key]
			},
			toggle(key) {
				const next = Object.assign({}, this.openMap)
				next[key] = !next[key]
				this.openMap = next
			},
			again() {
				uni.navigateBack({ fail: () => uni.redirectTo({ url: '/pages/ai-essay/index' }) })
			},
			goHuman() {
				uni.navigateTo({ url: '/pages/ai-essay/human' })
			},
			posterPayload(mode) {
				return {
					mode,
					typeLabel: this.typeLabel,
					scoreText: this.scoreText,
					summary: this.summary,
					metaLine: this.metaLine,
					deductions: this.deductions,
					dimList: this.dimList,
					thesisCheck: this.thesisCheck,
					strengths: this.strengths,
					problems: this.problems,
					missingList: this.missingList,
					missingTitle: this.missingTitle,
					vocab: this.vocab,
					outline: this.outline
				}
			},
			async makePoster(mode) {
				const plan = buildEssayPosterPlan(this.posterPayload(mode))
				this.posterW = plan.width
				this.posterH = plan.height
				await this.$nextTick()
				await new Promise((r) => setTimeout(r, 80))
				const path = await drawEssayPoster('essaySharePoster', plan, this)
				if (mode === 'compact') this.shareImagePath = path
				return path
			},
			async afterImageReady(path, preferShare) {
				if (preferShare) {
					try {
						await showWeixinShareImageMenu(path)
						return
					} catch (e) {
						/* fallback below */
					}
				}
				uni.showActionSheet({
					itemList: preferShare
						? ['保存到相册', '预览海报']
						: ['保存到相册', '预览长图', '发给微信好友'],
					success: async (res) => {
						if (res.tapIndex === 0) {
							try {
								await saveImageToAlbum(path)
								uni.showToast({ title: '已保存到相册', icon: 'success' })
							} catch (err) {
								uni.showToast({ title: '保存失败，请检查相册权限', icon: 'none' })
							}
						} else if (res.tapIndex === 1) {
							uni.previewImage({ urls: [path] })
						} else if (res.tapIndex === 2) {
							try {
								await showWeixinShareImageMenu(path)
							} catch (err) {
								uni.showToast({ title: '请先保存后，从相册发给好友', icon: 'none' })
							}
						}
					}
				})
			},
			async shareCompact() {
				if (!this.ready || this.sharing) return
				this.sharing = 'compact'
				uni.showLoading({ title: '生成海报中', mask: true })
				try {
					const path = await this.makePoster('compact')
					uni.hideLoading()
					await this.afterImageReady(path, true)
				} catch (e) {
					uni.hideLoading()
					const msg = (e && (e.errMsg || e.message)) || '生成失败'
					uni.showToast({ title: msg, icon: 'none' })
				} finally {
					this.sharing = ''
				}
			},
			async exportFullPoster() {
				if (!this.ready || this.sharing) return
				this.sharing = 'full'
				uni.showLoading({ title: '导出完整长图', mask: true })
				try {
					const path = await this.makePoster('full')
					uni.hideLoading()
					await this.afterImageReady(path, false)
				} catch (e) {
					uni.hideLoading()
					const msg = (e && (e.errMsg || e.message)) || '导出失败'
					uni.showToast({ title: msg, icon: 'none' })
				} finally {
					this.sharing = ''
				}
			}
		}
	}
</script>

<template>
	<view class="edu-page">
		<EmptyState v-if="!ready" title="没有练习会话" sub="请从刷题首页开始。" />
		<template v-else>
			<view class="edu-card edu-practice-quiz-top">
				<view class="edu-card-head">
					<text class="edu-kicker">{{ metaLabel }}</text>
					<text class="edu-hint">{{ index + 1 }} / {{ ids.length }}</text>
				</view>
				<view class="edu-practice-progress">
					<view class="edu-practice-progress-bar" :style="{ width: progressPct + '%' }" />
				</view>
			</view>

			<view class="edu-card" v-if="question">
				<view class="edu-card-head">
					<text class="edu-card-title">题目</text>
					<text class="edu-practice-star" :class="{ on: favorited }" @tap="toggleFav">{{ favorited ? '★' : '☆' }}</text>
				</view>
				<text class="edu-prose">{{ question.content }}</text>
				<view
					class="edu-practice-option"
					v-for="opt in question.options || []"
					:key="opt.key"
					:class="optionClass(opt.key)"
					@tap="pick(opt.key)"
				>
					<text class="edu-name">{{ opt.key }}. {{ opt.text }}</text>
				</view>
			</view>

			<view class="edu-card" v-if="submitted">
				<text class="edu-card-title">{{ result.correct ? '回答正确' : '回答错误' }}</text>
				<text class="edu-hint">正确答案：{{ result.answer }}</text>
				<view class="edu-match-block" v-if="result.analysisIdea">
					<text class="edu-kicker">破题思路</text>
					<text class="edu-prose">{{ result.analysisIdea }}</text>
				</view>
				<view class="edu-match-block" v-if="result.analysis">
					<text class="edu-kicker">详解</text>
					<text class="edu-prose">{{ result.analysis }}</text>
				</view>
				<view class="edu-match-block" v-if="result.analysisKp">
					<text class="edu-kicker">知识点</text>
					<text class="edu-prose">{{ result.analysisKp }}</text>
				</view>
			</view>

			<view class="edu-practice-nav">
				<view class="edu-btn edu-btn-ghost" @tap="prev" v-if="index > 0">上一题</view>
				<view class="edu-btn" v-if="!submitted" :class="{ off: !picked || submitting }" @tap="submit">
					{{ submitting ? '提交中…' : '提交答案' }}
				</view>
				<view class="edu-btn" v-else @tap="next">
					{{ index + 1 >= ids.length ? '完成本轮' : '下一题' }}
				</view>
			</view>
		</template>
	</view>
</template>

<script>
	import EmptyState from '../../components/ui/EmptyState.vue'
	import { practiceFavToggle, practiceQuestion, practiceSubmit } from '../../utils/api.js'
	import { getPracticeUserKey } from '../../utils/practice-user.js'

	export default {
		components: { EmptyState },
		data() {
			return {
				ready: false,
				ids: [],
				index: 0,
				mode: 'order',
				subject: 'guanzong',
				question: null,
				picked: '',
				submitted: false,
				submitting: false,
				favorited: false,
				result: {},
				startedAt: 0
			}
		},
		computed: {
			progressPct() {
				if (!this.ids.length) return 0
				return Math.round(((this.index + 1) / this.ids.length) * 100)
			},
			metaLabel() {
				const t = (this.question && this.question.type) || '管综'
				const kp = (this.question && this.question.knowledgePoint) || ''
				return kp ? t + ' · ' + kp : t
			}
		},
		onShow() {
			const s = uni.getStorageSync('practiceSession')
			if (!s || !s.ids || !s.ids.length) {
				this.ready = false
				return
			}
			this.ready = true
			this.ids = s.ids
			this.index = s.index || 0
			this.mode = s.mode || 'order'
			this.subject = s.subject || 'guanzong'
			this.loadCurrent()
		},
		methods: {
			persistIndex() {
				const s = uni.getStorageSync('practiceSession') || {}
				s.index = this.index
				uni.setStorageSync('practiceSession', s)
			},
			async loadCurrent() {
				const id = this.ids[this.index]
				if (!id) return
				this.picked = ''
				this.submitted = false
				this.result = {}
				this.startedAt = Date.now()
				try {
					const data = await practiceQuestion(id, getPracticeUserKey())
					if (!data || !data.ok) {
						uni.showToast({ title: (data && data.error) || '加载失败', icon: 'none' })
						return
					}
					this.question = data.item
					this.favorited = !!(data.item && data.item.favorited)
				} catch (e) {
					uni.showToast({ title: '网络错误', icon: 'none' })
				}
			},
			pick(key) {
				if (this.submitted) return
				this.picked = key
			},
			optionClass(key) {
				if (!this.submitted) return this.picked === key ? 'on' : ''
				if (key === this.result.answer) return 'ok'
				if (key === this.picked && !this.result.correct) return 'bad'
				return ''
			},
			async submit() {
				if (!this.picked || this.submitting || this.submitted) return
				this.submitting = true
				try {
					const data = await practiceSubmit({
						userKey: getPracticeUserKey(),
						questionId: this.question.id,
						answer: this.picked,
						mode: this.mode,
						timeSpent: Math.max(0, Date.now() - this.startedAt)
					})
					if (!data || data.ok === false) {
						uni.showToast({ title: (data && data.error) || '提交失败', icon: 'none' })
						return
					}
					this.result = data
					this.submitted = true
					this.favorited = !!data.favorited
					if (!data.correct) {
						try {
							uni.vibrateShort({})
						} catch (e) {
							/* ignore */
						}
					}
				} catch (e) {
					uni.showToast({ title: '网络错误', icon: 'none' })
				} finally {
					this.submitting = false
				}
			},
			async toggleFav() {
				if (!this.question) return
				try {
					const data = await practiceFavToggle({
						userKey: getPracticeUserKey(),
						questionId: this.question.id
					})
					if (data && data.ok) {
						this.favorited = !!data.favorited
						uni.showToast({ title: this.favorited ? '已收藏' : '已取消', icon: 'none' })
					}
				} catch (e) {
					uni.showToast({ title: '操作失败', icon: 'none' })
				}
			},
			prev() {
				if (this.index <= 0) return
				this.index -= 1
				this.persistIndex()
				this.loadCurrent()
			},
			next() {
				if (this.index + 1 >= this.ids.length) {
					uni.showToast({ title: '本轮完成', icon: 'success' })
					setTimeout(() => {
						uni.navigateBack({ fail: () => uni.redirectTo({ url: '/pages/practice/index' }) })
					}, 500)
					return
				}
				this.index += 1
				this.persistIndex()
				this.loadCurrent()
			}
		}
	}
</script>

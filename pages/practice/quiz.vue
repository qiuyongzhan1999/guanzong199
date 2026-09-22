<template>
	<view class="edu-page edu-quiz-page">
		<EmptyState v-if="!ready" title="没有练习会话" sub="请从章节列表开始刷题。" />
		<template v-else>
			<view class="edu-card edu-practice-quiz-top">
				<view class="edu-card-head">
					<text class="edu-kicker">{{ metaLabel }}</text>
					<view class="edu-quiz-top-right">
						<text class="edu-hint">{{ index + 1 }} / {{ ids.length }}</text>
						<text class="edu-practice-star" :class="{ on: favorited }" @tap="toggleFav">{{ favorited ? '★' : '☆' }}</text>
					</view>
				</view>
				<view class="edu-hp-bar edu-hp-bar-lg">
					<view class="edu-hp-fill" :class="hpTone" :style="{ width: progressPct + '%' }" />
				</view>
			</view>

			<view class="edu-card edu-quiz-card" v-if="question" :class="{ pop: optionPop }">
				<text class="edu-prose">{{ question.stem || question.content }}</text>
				<view
					class="edu-practice-option"
					v-for="opt in question.options || []"
					:key="opt.key"
					:class="[optionClass(opt.key), { bounce: bounceKey === opt.key }]"
					@tap="pick(opt.key)"
				>
					<text class="edu-name">{{ opt.key }}. {{ opt.text }}</text>
				</view>
			</view>

			<view class="edu-card" v-if="submitted">
				<text class="edu-card-title" :class="result.correct ? 'tone-good-text' : 'tone-heat-text'">
					{{ result.correct ? '回答正确' : '回答错误' }}
				</text>
				<text class="edu-hint">正确答案：{{ result.answer }}</text>
				<text class="edu-muted" v-if="result.autoRemoved">已连续答对 2 次，本题已移出错题本</text>
				<view class="edu-match-block" v-if="showAnalysis">
					<text class="edu-kicker" @tap="analysisOpen = !analysisOpen">
						解析 {{ analysisOpen ? '▾' : '▸' }}
					</text>
					<template v-if="analysisOpen">
						<text class="edu-prose" v-if="result.analysisIdea">{{ result.analysisIdea }}</text>
						<text class="edu-prose" v-if="result.analysis">{{ result.analysis }}</text>
					</template>
				</view>
			</view>

			<view class="edu-confetti" v-if="showConfetti">
				<view class="edu-confetti-piece" v-for="n in 12" :key="n" :class="'c' + (n % 3)" />
			</view>

			<view class="edu-practice-nav">
				<view class="edu-btn edu-btn-ghost" :class="{ off: index <= 0 }" @tap="prev">上一题</view>
				<view class="edu-btn" @tap="next">
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
				/** 本轮各题作答缓存：{ [id]: { picked, submitted, result } } */
				answers: {},
				question: null,
				picked: '',
				submitted: false,
				submitting: false,
				favorited: false,
				result: {},
				startedAt: 0,
				bounceKey: '',
				optionPop: false,
				showConfetti: false,
				analysisOpen: false,
				lastCorrect: null
			}
		},
		computed: {
			progressPct() {
				if (!this.ids.length) return 0
				return Math.round(((this.index + 1) / this.ids.length) * 100)
			},
			currentId() {
				return this.ids[this.index]
			},
			metaLabel() {
				if (!this.question) return '答题'
				const parts = [this.question.subjectName, this.question.knowledgePointName || this.question.knowledgePoint]
				return parts.filter(Boolean).join(' · ')
			},
			showAnalysis() {
				return !!(this.result.analysis || this.result.analysisIdea)
			},
			hpTone() {
				if (this.lastCorrect === true) return 'tone-good'
				if (this.lastCorrect === false) return 'tone-heat'
				return ''
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
			this.answers = s.answers && typeof s.answers === 'object' ? s.answers : {}
			this.loadCurrent()
		},
		methods: {
			persistSession() {
				const s = uni.getStorageSync('practiceSession') || {}
				s.index = this.index
				s.answers = this.answers
				uni.setStorageSync('practiceSession', s)
			},
			saveCurrentAnswer() {
				const id = this.currentId
				if (!id) return
				if (!this.submitted && !this.picked) return
				this.answers = {
					...this.answers,
					[String(id)]: {
						picked: this.picked,
						submitted: this.submitted,
						result: this.submitted ? this.result : {}
					}
				}
				this.persistSession()
			},
			restoreAnswer(id) {
				const saved = this.answers[String(id)]
				if (!saved) {
					this.picked = ''
					this.submitted = false
					this.result = {}
					this.lastCorrect = null
					return
				}
				this.picked = saved.picked || ''
				this.submitted = !!saved.submitted
				this.result = saved.result || {}
				this.lastCorrect = this.submitted ? !!this.result.correct : null
			},
			async loadCurrent() {
				const id = this.currentId
				if (!id) return
				this.analysisOpen = false
				this.showConfetti = false
				this.startedAt = Date.now()
				this.restoreAnswer(id)
				try {
					const data = await practiceQuestion(id, getPracticeUserKey())
					this.question = (data && data.item) || null
					this.favorited = !!(this.question && this.question.favorited)
				} catch (e) {
					this.question = null
					uni.showToast({ title: '题目加载失败', icon: 'none' })
				}
			},
			pick(key) {
				if (this.submitted || this.submitting) return
				this.picked = key
				this.bounceKey = key
				this.optionPop = true
				setTimeout(() => {
					this.bounceKey = ''
					this.optionPop = false
				}, 280)
				this.submit(key)
			},
			optionClass(key) {
				if (!this.submitted) {
					if (this.submitting && this.picked === key) return 'on'
					return this.picked === key ? 'on' : ''
				}
				if (key === this.result.answer) return 'ok'
				if (key === this.picked && !this.result.correct) return 'bad'
				return ''
			},
			async submit(answerKey) {
				const answer = answerKey || this.picked
				if (!answer || this.submitting || this.submitted) return
				this.submitting = true
				try {
					const data = await practiceSubmit({
						userKey: getPracticeUserKey(),
						questionId: this.currentId,
						answer,
						timeSpent: Date.now() - this.startedAt
					})
					if (!data || data.ok === false) {
						this.picked = ''
						uni.showToast({ title: (data && data.error) || '提交失败', icon: 'none' })
						return
					}
					this.picked = answer
					this.result = data
					this.submitted = true
					this.lastCorrect = !!data.correct
					this.saveCurrentAnswer()
					if (data.correct) {
						this.showConfetti = true
						setTimeout(() => { this.showConfetti = false }, 900)
					} else {
						try { uni.vibrateShort({}) } catch (e) { /* ignore */ }
					}
				} catch (e) {
					this.picked = ''
					uni.showToast({ title: '网络错误', icon: 'none' })
				} finally {
					this.submitting = false
				}
			},
			async toggleFav() {
				try {
					const data = await practiceFavToggle({
						userKey: getPracticeUserKey(),
						questionId: this.currentId
					})
					this.favorited = !!(data && data.favorited)
				} catch (e) {
					uni.showToast({ title: '收藏失败', icon: 'none' })
				}
			},
			prev() {
				if (this.index <= 0) return
				this.saveCurrentAnswer()
				this.index -= 1
				this.persistSession()
				this.loadCurrent()
			},
			next() {
				this.saveCurrentAnswer()
				if (this.index + 1 >= this.ids.length) {
					uni.showToast({ title: '本轮完成', icon: 'success' })
					setTimeout(() => uni.navigateBack(), 500)
					return
				}
				this.index += 1
				this.persistSession()
				this.loadCurrent()
			}
		}
	}
</script>

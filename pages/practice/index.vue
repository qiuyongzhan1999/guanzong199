<template>
	<view class="edu-page">
		<view class="edu-card edu-practice-hero">
			<text class="edu-kicker">{{ subject === 'english' ? '英语二刷题' : '管综刷题' }}</text>
			<text class="edu-card-title">{{ subject === 'english' ? '词汇 · 完形 · 阅读' : '数学 · 逻辑' }}</text>
			<text class="edu-hint">按题型与知识点筛选后开练，错题会进错题本。</text>
			<StatRow :columns="3" :items="statItems" />
		</view>

		<view class="edu-card">
			<text class="edu-card-title">题型</text>
			<view class="edu-chips">
				<view
					class="edu-chip"
					v-for="t in types"
					:key="t.id"
					:class="{ on: type === t.id }"
					@tap="onType(t.id)"
				>{{ t.label }}</view>
			</view>
		</view>

		<view class="edu-card">
			<text class="edu-card-title">知识点</text>
			<view class="edu-chips" v-if="moduleChips.length">
				<view
					class="edu-chip"
					v-for="m in moduleChips"
					:key="m.id"
					:class="{ on: knowledgePoint === m.id }"
					@tap="knowledgePoint = m.id"
				>{{ m.label }}</view>
			</view>
			<text class="edu-hint" v-else>加载知识点中…</text>
		</view>

		<view class="edu-btn" :class="{ off: loading }" @tap="start">
			{{ loading ? '准备中…' : '开始练习' }}
		</view>

		<view class="edu-practice-links">
			<view class="edu-chip on" @tap="go('/pages/practice/wrong?subject=' + subject)">错题本</view>
			<view class="edu-chip on" @tap="go('/pages/practice/favorites?subject=' + subject)">收藏</view>
			<view class="edu-chip on" @tap="go('/pages/practice/stats?subject=' + subject)">统计</view>
		</view>
	</view>
</template>

<script>
	import StatRow from '../../components/ui/StatRow.vue'
	import { practiceHealth, practiceModules, practiceOverview, practiceQuestions } from '../../utils/api.js'
	import { getPracticeUserKey } from '../../utils/practice-user.js'

	const TYPES = {
		guanzong: [
			{ id: '', label: '全部' },
			{ id: '数学', label: '数学' },
			{ id: '逻辑', label: '逻辑' }
		],
		english: [
			{ id: '', label: '全部' },
			{ id: '词汇', label: '词汇' },
			{ id: '完形', label: '完形' },
			{ id: '阅读', label: '阅读' }
		]
	}

	export default {
		components: { StatRow },
		data() {
			return {
				subject: 'guanzong',
				type: '',
				knowledgePoint: '',
				modules: [],
				stats: { today: 0, wrongOpen: 0, accuracy: 0 },
				loading: false
			}
		},
		computed: {
			types() {
				return TYPES[this.subject] || TYPES.guanzong
			},
			moduleChips() {
				const list = [{ id: '', label: '全部' }]
				;(this.modules || []).forEach((name) => {
					if (name) list.push({ id: name, label: name })
				})
				return list
			},
			statItems() {
				return [
					{ label: '今日', value: String(this.stats.today || 0) },
					{ label: '错题', value: String(this.stats.wrongOpen || 0) },
					{ label: '正确率', value: (this.stats.accuracy || 0) + '%' }
				]
			}
		},
		onLoad(q) {
			if (q && q.subject) this.subject = q.subject === 'english' ? 'english' : 'guanzong'
			this.type = ''
			this.knowledgePoint = ''
		},
		onShow() {
			this.loadStats()
			this.loadModules()
		},
		methods: {
			onType(id) {
				this.type = id
				this.knowledgePoint = ''
				this.loadModules()
			},
			async loadModules() {
				try {
					const data = await practiceModules({
						subject: this.subject,
						type: this.type || undefined
					})
					this.modules = (data && data.items) || []
					if (this.knowledgePoint && this.modules.indexOf(this.knowledgePoint) < 0) {
						this.knowledgePoint = ''
					}
				} catch (e) {
					this.modules = []
				}
			},
			async loadStats() {
				try {
					await practiceHealth()
					const data = await practiceOverview({
						userKey: getPracticeUserKey(),
						subject: this.subject
					})
					if (data && data.ok) {
						this.stats = Object.assign({}, data.stats || {})
					}
				} catch (e) {
					/* ignore */
				}
			},
			go(url) {
				uni.navigateTo({ url })
			},
			async start() {
				if (this.loading) return
				this.loading = true
				try {
					const data = await practiceQuestions({
						subject: this.subject,
						type: this.type || undefined,
						knowledgePoint: this.knowledgePoint || undefined,
						mode: 'random',
						limit: 20
					})
					if (!data || data.ok === false) {
						uni.showToast({ title: (data && data.error) || '拉题失败', icon: 'none' })
						return
					}
					const items = data.items || []
					if (!items.length) {
						uni.showToast({ title: '该模块暂无题目', icon: 'none' })
						return
					}
					uni.setStorageSync('practiceSession', {
						subject: this.subject,
						type: this.type,
						knowledgePoint: this.knowledgePoint,
						mode: 'random',
						ids: items.map((q) => q.id),
						index: 0
					})
					uni.navigateTo({ url: '/pages/practice/quiz' })
				} catch (e) {
					uni.showToast({ title: '网络错误，请确认后端已启动', icon: 'none' })
				} finally {
					this.loading = false
				}
			}
		}
	}
</script>

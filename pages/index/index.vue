<template>
	<view class="edu-page edu-home">
		<view class="edu-hero edu-hero-home">
			<view class="edu-hero-orb edu-hero-orb-a" />
			<view class="edu-hero-orb edu-hero-orb-b" />
			<view class="edu-hero-orb edu-hero-orb-c" />
			<view class="edu-hero-glow" />
			<view class="edu-hero-top">
				<text class="edu-kicker">管综上岸通</text>
				<text class="edu-hero-badge">满分目标</text>
			</view>
			<view class="edu-hero-brand">
				<text class="edu-hero-brand-name">管综</text>
				<view class="edu-hero-score-wrap">
					<text class="edu-hero-num">199</text>
					<text class="edu-hero-score-unit">分</text>
				</view>
			</view>
			<text class="edu-hero-tag">择校有数，上岸有路</text>
			<text class="edu-hero-sub">每天进步一点点，我们陪你上岸。</text>
			<view class="edu-hero-parts">
				<view
					class="edu-hero-part"
					v-for="item in parts"
					:key="item.label"
					:class="'tone-' + item.tone"
				>
					<text class="edu-hero-part-v">{{ item.value }}</text>
					<text class="edu-hero-part-k">{{ item.label }}</text>
				</view>
			</view>
		</view>

		<text class="edu-section-label">刷题开练</text>
		<view class="edu-home-grid">
			<view
				class="edu-tile"
				v-for="item in drills"
				:key="item.title"
				@tap="open(item)"
			>
				<text class="edu-mark edu-tile-mark" :class="'tone-' + item.tone">{{ item.mark }}</text>
				<text class="edu-tile-title">{{ item.title }}</text>
				<text class="edu-tile-sub">{{ item.sub }}</text>
			</view>
		</view>

		<text class="edu-section-label">择校找校</text>
		<view class="edu-home-grid">
			<view
				class="edu-tile"
				v-for="item in schools"
				:key="item.title"
				@tap="open(item)"
			>
				<text class="edu-mark edu-tile-mark" :class="'tone-' + item.tone">{{ item.mark }}</text>
				<text class="edu-tile-title">{{ item.title }}</text>
				<text class="edu-tile-sub">{{ item.sub }}</text>
			</view>
		</view>

		<text class="edu-section-label">作文批改</text>
		<view class="edu-home-row">
			<view
				class="edu-tile edu-tile-wide"
				v-for="item in essays"
				:key="item.title"
				@tap="open(item)"
			>
				<text class="edu-mark edu-tile-mark" :class="'tone-' + item.tone">{{ item.mark }}</text>
				<view class="edu-tile-copy">
					<text class="edu-tile-title">{{ item.title }}</text>
					<text class="edu-tile-sub">{{ item.sub }}</text>
				</view>
				<text class="edu-go">›</text>
			</view>
		</view>
	</view>
</template>

<script>
	import { practiceOverview } from '../../utils/api.js'
	import { getPracticeUserKey } from '../../utils/practice-user.js'

	export default {
		data() {
			return {
				parts: [
					{ label: '数学', value: '75', tone: 'signal' },
					{ label: '逻辑', value: '60', tone: 'good' },
					{ label: '写作', value: '65', tone: 'heat' }
				],
				drills: [
					{ mark: '数', title: '管综刷题', sub: '数学 · 逻辑 · 3000题', url: '/pages/practice/index?subject=guanzong', tone: 'signal', live: true },
					{ mark: '英', title: '英语二刷题', sub: '词汇 · 完形 · 阅读 · 2000题', url: '/pages/practice/index?subject=english', tone: 'good', live: true }
				],
				schools: [
					{ mark: '择', title: '智能择校', sub: '稳冲难一眼看清', url: '/pages/intent/index', tone: 'signal' },
					{ mark: '校', title: '院校库', sub: '搜校名 · 查分数', tab: '/pages/schools/index', tone: 'signal' }
				],
				essays: [
					{ mark: '批', title: 'AI 批改', sub: '写作 / 英语作文先改一版', url: '/pages/ai-essay/index', tone: 'heat' },
					{ mark: '人', title: '人工批改', sub: '加微信，老师一对一盯卷', url: '/pages/ai-essay/human', tone: 'good' }
				]
			}
		},
		onShow() {
			this.loadPracticeHint()
		},
		methods: {
			open(item) {
				if (item.tab) {
					uni.switchTab({ url: item.tab })
					return
				}
				if (item.url) {
					uni.navigateTo({ url: item.url })
					return
				}
				uni.showToast({ title: '还没开放', icon: 'none' })
			},
			async loadPracticeHint() {
				try {
					const data = await practiceOverview({
						userKey: getPracticeUserKey(),
						subject: 'guanzong'
					})
					if (!data || !data.ok) return
					const s = data.stats || {}
					const bits = []
					if (data.questionCount) bits.push('题库 ' + data.questionCount)
					if (s.wrongOpen) bits.push('错题 ' + s.wrongOpen)
					if (s.accuracy != null && s.answered) bits.push('正确率 ' + s.accuracy + '%')
					if (!bits.length) return
					const d = this.drills.find((x) => x.live)
					if (d) d.sub = bits.join(' · ')
				} catch (e) {
					/* ignore */
				}
			}
		}
	}
</script>

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

		<view class="edu-section-head">
			<view class="edu-section-bar" />
			<text class="edu-section-title">择校找校</text>
			<text class="edu-section-hint">先看稳冲难</text>
		</view>
		<view
			class="edu-tile edu-tile-feature tone-signal"
			@tap="open(schools[0])"
		>
			<view class="edu-tile-feature-main">
				<text class="edu-mark edu-tile-mark tone-signal">{{ schools[0].mark }}</text>
				<view class="edu-tile-copy">
					<text class="edu-tile-title">{{ schools[0].title }}</text>
					<text class="edu-tile-sub">{{ schools[0].sub }}</text>
				</view>
			</view>
		</view>
		<view
			class="edu-tile edu-tile-wide tone-soft"
			@tap="open(schools[1])"
		>
			<text class="edu-mark edu-tile-mark tone-signal">{{ schools[1].mark }}</text>
			<view class="edu-tile-copy">
				<text class="edu-tile-title">{{ schools[1].title }}</text>
				<text class="edu-tile-sub">{{ schools[1].sub }}</text>
			</view>
			<text class="edu-go">›</text>
		</view>

		<view class="edu-section-head">
			<view class="edu-section-bar tone-heat" />
			<text class="edu-section-title">作文批改</text>
		</view>
		<view class="edu-home-row">
			<view
				class="edu-tile edu-tile-wide"
				v-for="item in essays"
				:key="item.title"
				:class="'tone-' + item.tone"
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

		<view class="edu-section-head">
			<view class="edu-section-bar tone-good" />
			<text class="edu-section-title">背单词</text>
		</view>
		<view class="edu-home-row">
			<view
				class="edu-tile edu-tile-wide"
				v-for="item in words"
				:key="item.title"
				:class="'tone-' + item.tone"
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
	export default {
		data() {
			return {
				parts: [
					{ label: '数学', value: '75', tone: 'signal' },
					{ label: '逻辑', value: '60', tone: 'good' },
					{ label: '写作', value: '65', tone: 'heat' }
				],
				schools: [
					{ mark: '择', title: '智能择校', sub: '稳冲难一眼看清', url: '/pages/intent/index', tone: 'signal' },
					{ mark: '校', title: '院校库', sub: '搜校名 · 查分数', tab: '/pages/schools/index', tone: 'signal' }
				],
				essays: [
					{ mark: '批', title: 'AI 批改', sub: '写作 / 英语作文先改一版', url: '/pages/ai-essay/index', tone: 'heat' },
					{ mark: '人', title: '人工批改', sub: '加微信，老师一对一盯卷', url: '/pages/ai-essay/human', tone: 'good' }
				],
				words: [
					{ mark: '词', title: '背单词', sub: '考研核心词汇 · 真人发音', url: '/pages/words/learn', tone: 'good' },
					{ mark: '队', title: '组队打卡', sub: '和小伙伴一起坚持', url: '/pages/words/team', tone: 'signal' }
				]
			}
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
			}
		}
	}
</script>

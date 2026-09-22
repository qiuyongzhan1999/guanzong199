<template>
	<view class="edu-page edu-practice-home">
		<view class="edu-section-head">
			<view class="edu-section-bar" />
			<text class="edu-section-title">刷题开练</text>
			<text class="edu-section-hint">管综 · 英语二</text>
		</view>

		<view
			class="edu-subject-card"
			v-for="item in subjects"
			:key="item.id"
			:class="'tone-' + item.tone"
			@tap="openSubject(item)"
		>
			<view class="edu-subject-card-glow" />
			<view class="edu-subject-card-top">
				<text class="edu-subject-card-name">{{ item.name }}</text>
				<text class="edu-go tone-on">›</text>
			</view>
			<text class="edu-subject-card-sub">今日 {{ item.today }} · 错题 {{ item.wrong }} · 正确率 {{ item.accuracy }}%</text>
			<view class="edu-hp-bar">
				<view class="edu-hp-fill" :style="{ width: item.accuracy + '%' }" />
			</view>
		</view>

		<view class="edu-card edu-practice-tools">
			<view class="edu-menu-row" @tap="go('/pages/practice/favorites')">
				<view class="edu-practice-tool-copy">
					<text class="edu-menu-title">收藏夹</text>
					<text class="edu-muted">收藏过的题随时重做</text>
				</view>
				<text class="edu-go">›</text>
			</view>
			<view class="edu-menu-row" @tap="go('/pages/practice/wrong')">
				<view class="edu-practice-tool-copy">
					<text class="edu-menu-title">错题本</text>
					<text class="edu-muted">连续答对 2 次自动移出</text>
				</view>
				<text class="edu-go">›</text>
			</view>
			<view class="edu-menu-row" @tap="go('/pages/practice/history')">
				<view class="edu-practice-tool-copy">
					<text class="edu-menu-title">历史记录</text>
					<text class="edu-muted">最近作答轨迹</text>
				</view>
				<text class="edu-go">›</text>
			</view>
			<view class="edu-menu-row" @tap="go('/pages/practice/years')">
				<view class="edu-practice-tool-copy">
					<text class="edu-menu-title">历年真题</text>
					<text class="edu-muted">按年份刷管综真题</text>
				</view>
				<text class="edu-go">›</text>
			</view>
			<view class="edu-menu-row" @tap="go('/pages/practice/stats')">
				<view class="edu-practice-tool-copy">
					<text class="edu-menu-title">数据统计</text>
					<text class="edu-muted">雷达图与正确率趋势</text>
				</view>
				<text class="edu-go">›</text>
			</view>
		</view>
	</view>
</template>

<script>
	import { practiceHealth, practiceSubjects } from '../../utils/api.js'
	import { getPracticeUserKey } from '../../utils/practice-user.js'

	const TONE = { math: 'signal', logic: 'heat', english: 'good' }

	export default {
		data() {
			return {
				subjects: []
			}
		},
		onShow() {
			this.load()
		},
		methods: {
			async load() {
				try {
					await practiceHealth()
					const data = await practiceSubjects(getPracticeUserKey())
					const items = (data && data.items) || []
					this.subjects = items.map((s) => {
						const p = s.progress || {}
						return {
							id: s.id,
							code: s.code,
							name: s.name,
							tone: TONE[s.code] || 'signal',
							today: p.todayCount || 0,
							wrong: p.wrongCount || 0,
							accuracy: p.accuracy || 0
						}
					})
				} catch (e) {
					uni.showToast({ title: '刷题服务未就绪', icon: 'none' })
				}
			},
			openSubject(item) {
				uni.navigateTo({
					url: '/pages/practice/chapters?subject_id=' + item.id + '&name=' + encodeURIComponent(item.name)
				})
			},
			go(url) {
				uni.navigateTo({ url })
			}
		}
	}
</script>

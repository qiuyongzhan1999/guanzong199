<template>
	<view class="edu-page">
		<view class="edu-section-head">
			<view class="edu-section-bar" />
			<text class="edu-section-title">历年真题</text>
			<text class="edu-section-hint">管综199</text>
		</view>

		<EmptyState v-if="!loading && !years.length" title="暂无真题" sub="题库还没有带年份的题目，导入后再来。" />

		<view class="edu-card" v-else-if="years.length">
			<view
				class="edu-menu-row"
				v-for="item in years"
				:key="item.year"
				@tap="goYear(item)"
			>
				<view class="edu-practice-tool-copy">
					<text class="edu-menu-title">{{ item.year }}年管综真题</text>
					<text class="edu-muted">共 {{ item.count }} 道题</text>
				</view>
				<text class="edu-go">›</text>
			</view>
		</view>
	</view>
</template>

<script>
	import EmptyState from '../../components/ui/EmptyState.vue'
	import { practiceQuestionsByYear, practiceYears } from '../../utils/api.js'

	export default {
		components: { EmptyState },
		data() {
			return {
				loading: true,
				years: []
			}
		},
		onShow() {
			this.load()
		},
		methods: {
			async load() {
				this.loading = true
				try {
					const data = await practiceYears()
					this.years = (data && data.items) || []
				} catch (e) {
					this.years = []
					uni.showToast({ title: '加载年份失败', icon: 'none' })
				} finally {
					this.loading = false
				}
			},
			async goYear(item) {
				uni.showLoading({ title: '加载题目...' })
				try {
					const res = await practiceQuestionsByYear(item.year)
					const ids = (res && res.ids) || []
					if (!ids.length) {
						uni.showToast({ title: '该年份暂无题目', icon: 'none' })
						return
					}
					uni.setStorageSync('practiceSession', {
						ids,
						index: 0,
						mode: 'order',
						answers: {}
					})
					uni.navigateTo({
						url: '/pages/practice/quiz?name=' + encodeURIComponent(item.year + '年管综真题')
					})
				} catch (e) {
					uni.showToast({ title: '加载失败', icon: 'none' })
				} finally {
					uni.hideLoading()
				}
			}
		}
	}
</script>

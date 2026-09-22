<template>
	<view class="edu-page">
		<view class="edu-card">
			<text class="edu-card-title">练习统计</text>
			<StatRow :columns="3" :items="overviewItems" />
		</view>
		<view class="edu-card">
			<text class="edu-card-title">薄弱知识点</text>
			<EmptyState v-if="!knowledge.length" title="还没有数据" sub="多刷几题后这里会按正确率排序。" />
			<view class="edu-essay-dim" v-for="k in knowledge" :key="k.knowledgePoint">
				<view class="edu-card-head">
					<text class="edu-name">{{ k.knowledgePoint }}</text>
					<text class="edu-hint tone-signal">{{ k.masteryRate }}%</text>
				</view>
				<text class="edu-sub">做 {{ k.total }} 题 · 对 {{ k.correct }} 题</text>
			</view>
		</view>
	</view>
</template>

<script>
	import EmptyState from '../../components/ui/EmptyState.vue'
	import StatRow from '../../components/ui/StatRow.vue'
	import { practiceKnowledge, practiceOverview } from '../../utils/api.js'
	import { getPracticeUserKey } from '../../utils/practice-user.js'

	export default {
		components: { EmptyState, StatRow },
		data() {
			return {
				stats: {},
				knowledge: []
			}
		},
		computed: {
			overviewItems() {
				return [
					{ label: '已做', value: String(this.stats.answered || 0) },
					{ label: '正确率', value: (this.stats.accuracy || 0) + '%' },
					{ label: '未掌握错题', value: String(this.stats.wrongOpen || 0) }
				]
			}
		},
		onShow() {
			this.load()
		},
		methods: {
			async load() {
				const key = getPracticeUserKey()
				try {
					const [ov, kn] = await Promise.all([
						practiceOverview({ userKey: key, subject: 'guanzong' }),
						practiceKnowledge({ userKey: key, subject: 'guanzong' })
					])
					this.stats = (ov && ov.stats) || {}
					this.knowledge = (kn && kn.items) || []
				} catch (e) {
					uni.showToast({ title: '加载失败', icon: 'none' })
				}
			}
		}
	}
</script>

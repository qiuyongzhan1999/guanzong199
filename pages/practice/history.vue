<template>
	<view class="edu-page">
		<view class="edu-card">
			<view class="edu-section-head edu-section-head-in">
				<view class="edu-section-bar" />
				<text class="edu-section-title">历史记录</text>
			</view>
			<text class="edu-hint">最近作答，最新在上</text>
		</view>

		<EmptyState v-if="!items.length && !loading" title="还没有记录" sub="去做几道题，这里会出现轨迹。" />

		<view class="edu-card" v-for="row in items" :key="row.id">
			<view class="edu-card-head">
				<text class="edu-name">{{ row.subjectName }} · {{ row.knowledgePointName }}</text>
				<text :class="row.isCorrect ? 'tone-good-text' : 'tone-heat-text'">
					{{ row.isCorrect ? '正确' : '错误' }}
				</text>
			</view>
			<text class="edu-sub">{{ row.stem }}</text>
			<text class="edu-muted">答 {{ row.userAnswer }} · {{ fmt(row.createdAt) }}</text>
		</view>
	</view>
</template>

<script>
	import EmptyState from '../../components/ui/EmptyState.vue'
	import { practiceHistory } from '../../utils/api.js'
	import { getPracticeUserKey } from '../../utils/practice-user.js'

	export default {
		components: { EmptyState },
		data() {
			return { items: [], loading: false }
		},
		onShow() {
			this.load()
		},
		methods: {
			fmt(v) {
				if (!v) return ''
				return String(v).replace('T', ' ').slice(0, 19)
			},
			async load() {
				this.loading = true
				try {
					const data = await practiceHistory({
						userKey: getPracticeUserKey(),
						limit: 50
					})
					this.items = ((data && data.items) || []).map((r) => ({
						...r,
						isCorrect: r.isCorrect === 1 || r.isCorrect === true
					}))
				} catch (e) {
					this.items = []
				} finally {
					this.loading = false
				}
			}
		}
	}
</script>

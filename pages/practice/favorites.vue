<template>
	<view class="edu-page">
		<view class="edu-card">
			<text class="edu-card-title">收藏夹</text>
			<text class="edu-hint">管综收藏题，点进去可重做。</text>
		</view>
		<EmptyState v-if="!items.length && !loading" title="还没有收藏" sub="答题页点星标即可收藏。" />
		<view class="edu-card" v-for="q in items" :key="q.id" @tap="redo(q.id)">
			<view class="edu-card-head">
				<text class="edu-name">{{ q.type }} · {{ q.knowledgePoint || '综合' }}</text>
				<text class="edu-hint">难度 {{ q.difficulty || '—' }}</text>
			</view>
			<text class="edu-sub">{{ q.content }}</text>
		</view>
	</view>
</template>

<script>
	import EmptyState from '../../components/ui/EmptyState.vue'
	import { practiceFavorites } from '../../utils/api.js'
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
			async load() {
				this.loading = true
				try {
					const data = await practiceFavorites({
						userKey: getPracticeUserKey(),
						subject: 'guanzong'
					})
					this.items = (data && data.items) || []
				} catch (e) {
					uni.showToast({ title: '加载失败', icon: 'none' })
				} finally {
					this.loading = false
				}
			},
			redo(id) {
				uni.setStorageSync('practiceSession', {
					subject: 'guanzong',
					mode: 'order',
					ids: [id],
					index: 0
				})
				uni.navigateTo({ url: '/pages/practice/quiz' })
			}
		}
	}
</script>

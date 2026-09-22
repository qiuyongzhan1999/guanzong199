<template>
	<view class="edu-page">
		<view class="edu-card">
			<text class="edu-card-title">错题本</text>
			<view class="edu-chips">
				<view class="edu-chip" :class="{ on: status === 0 }" @tap="status = 0; load()">未掌握</view>
				<view class="edu-chip" :class="{ on: status === 1 }" @tap="status = 1; load()">已掌握</view>
			</view>
		</view>
		<EmptyState v-if="!items.length && !loading" title="暂无错题" sub="答错的题会自动出现在这里。" />
		<view class="edu-card" v-for="q in items" :key="q.id">
			<view class="edu-card-head">
				<text class="edu-name">{{ q.type }} · {{ q.knowledgePoint || '综合' }}</text>
				<text class="edu-essay-points">错 {{ q.wrongCount || 1 }} 次</text>
			</view>
			<text class="edu-sub">{{ q.content }}</text>
			<view class="edu-practice-links">
				<view class="edu-chip on" @tap="redo(q.id)">重做</view>
				<view class="edu-chip" v-if="status === 0" @tap="master(q.id)">已掌握</view>
			</view>
		</view>
	</view>
</template>

<script>
	import EmptyState from '../../components/ui/EmptyState.vue'
	import { practiceMaster, practiceWrong } from '../../utils/api.js'
	import { getPracticeUserKey } from '../../utils/practice-user.js'

	export default {
		components: { EmptyState },
		data() {
			return { items: [], loading: false, status: 0 }
		},
		onShow() {
			this.load()
		},
		methods: {
			async load() {
				this.loading = true
				try {
					const data = await practiceWrong({
						userKey: getPracticeUserKey(),
						subject: 'guanzong',
						status: this.status
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
					mode: 'wrong',
					ids: [id],
					index: 0
				})
				uni.navigateTo({ url: '/pages/practice/quiz' })
			},
			async master(id) {
				try {
					await practiceMaster({ userKey: getPracticeUserKey(), questionId: id })
					uni.showToast({ title: '已标记掌握', icon: 'none' })
					this.load()
				} catch (e) {
					uni.showToast({ title: '操作失败', icon: 'none' })
				}
			}
		}
	}
</script>

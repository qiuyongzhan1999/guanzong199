<template>
	<view class="edu-page">
		<view class="edu-card">
			<view class="edu-section-head edu-section-head-in">
				<view class="edu-section-bar tone-heat" />
				<text class="edu-section-title">错题本</text>
			</view>
			<text class="edu-hint">右滑重做 · 左滑跳过 · 连续答对 2 次自动移出</text>
			<view class="edu-chips" style="margin-top:16rpx">
				<view class="edu-chip" :class="{ on: sort === 'time' }" @tap="setSort('time')">最近错误</view>
				<view class="edu-chip" :class="{ on: sort === 'wrong_count' }" @tap="setSort('wrong_count')">错误次数</view>
				<view class="edu-chip" :class="{ on: sort === 'knowledge' }" @tap="setSort('knowledge')">知识点</view>
			</view>
		</view>

		<EmptyState v-if="!items.length && !loading" title="暂无错题" sub="答错的题会自动出现在这里。" />

		<view class="edu-wrong-stack" v-if="items.length" @touchstart="onStart" @touchend="onEnd">
			<view
				class="edu-card edu-wrong-card"
				:style="cardStyle"
			>
				<view class="edu-tile-tag tone-heat"><text>{{ current.knowledgePointName || '错题' }}</text></view>
				<text class="edu-muted" style="margin-top:12rpx;display:block">
					错 {{ current.wrongCount || 1 }} 次 · 连对 {{ current.consecutiveCorrect || 0 }}/2
				</text>
				<text class="edu-prose" style="margin-top:16rpx">{{ current.stem || current.content }}</text>
				<view class="edu-practice-nav" style="margin-top:24rpx">
					<view class="edu-btn edu-btn-ghost" @tap="skip">跳过</view>
					<view class="edu-btn" @tap="redo">重做</view>
				</view>
			</view>
			<text class="edu-hint" style="text-align:center;margin-top:16rpx">
				剩余 {{ items.length }} 题
			</text>
		</view>
	</view>
</template>

<script>
	import EmptyState from '../../components/ui/EmptyState.vue'
	import { practiceWrong } from '../../utils/api.js'
	import { getPracticeUserKey } from '../../utils/practice-user.js'

	export default {
		components: { EmptyState },
		data() {
			return {
				items: [],
				loading: false,
				sort: 'time',
				dx: 0,
				startX: 0
			}
		},
		computed: {
			current() {
				return this.items[0] || {}
			},
			cardStyle() {
				const rot = this.dx / 20
				return {
					transform: 'translateX(' + this.dx + 'px) rotate(' + rot + 'deg)',
					transition: this.dx === 0 ? 'transform 0.25s ease' : 'none'
				}
			}
		},
		onShow() {
			this.load()
		},
		methods: {
			setSort(s) {
				this.sort = s
				this.load()
			},
			async load() {
				this.loading = true
				try {
					const data = await practiceWrong({
						userKey: getPracticeUserKey(),
						sort: this.sort
					})
					this.items = (data && data.items) || []
				} catch (e) {
					this.items = []
				} finally {
					this.loading = false
				}
			},
			onStart(e) {
				this.startX = e.changedTouches[0].clientX
			},
			onEnd(e) {
				const x = e.changedTouches[0].clientX
				const d = x - this.startX
				if (d > 80) {
					this.dx = 320
					setTimeout(() => { this.dx = 0; this.redo() }, 200)
				} else if (d < -80) {
					this.dx = -320
					setTimeout(() => { this.dx = 0; this.skip() }, 200)
				} else {
					this.dx = 0
				}
			},
			skip() {
				if (!this.items.length) return
				const first = this.items.shift()
				this.items.push(first)
				this.items = this.items.slice()
			},
			redo() {
				if (!this.items.length) return
				const q = this.items[0]
				uni.setStorageSync('practiceSession', {
					mode: 'wrong',
					ids: [q.id],
					index: 0,
					answers: {}
				})
				uni.navigateTo({ url: '/pages/practice/quiz' })
			}
		}
	}
</script>

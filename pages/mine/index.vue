<template>
	<view class="edu-page">
		<view class="edu-card edu-profile center" @tap="later">
			<view class="edu-mark">未</view>
			<view class="edu-profile-copy">
				<text class="edu-name">未登录</text>
				<text class="edu-sub">登录后可看练习和批改记录</text>
			</view>
		</view>
		<view class="edu-card">
			<view class="edu-menu-row" v-for="item in rows" :key="item.title" @tap="open(item)">
				<text class="edu-menu-title">{{ item.title }}</text>
				<text class="edu-hint">{{ item.url ? '进入' : '未开放' }}</text>
			</view>
		</view>
	</view>
</template>

<script>
	export default {
		data() {
			return {
				rows: [
					{ title: '智能择校', url: '/pages/intent/index' },
					{ title: 'AI 批改', url: '/pages/ai-essay/index' },
					{ title: '练习记录' },
					{ title: '批改记录' },
					{ title: '加老师微信', url: '/pages/ai-essay/human' },
					{ title: '反馈' },
					{ title: '设置' }
				]
			}
		},
		methods: {
			open(item) {
				if (item.url) {
					uni.navigateTo({ url: item.url })
					return
				}
				uni.showToast({ title: '还没开放', icon: 'none' })
			},
			later() {
				uni.showToast({ title: '还没开放', icon: 'none' })
			}
		}
	}
</script>

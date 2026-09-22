<template>
	<view class="edu-page">
		<view class="edu-card">
			<text class="edu-card-title">人工批改</text>
			<text class="edu-prose">想让老师再看一遍？添加老师微信，把作文和 AI 批改结果一起发过去，一对一复核。</text>
			<text class="edu-sub">人工批改按篇沟通，适合冲刺期或对分数没底的时候。</text>
		</view>
		<view class="edu-btn" @tap="copyWx">复制微信号</view>
		<text class="edu-hint edu-match-disclaimer">微信号可在后台配置；当前为占位，请先改成你的真实微信。</text>
	</view>
</template>

<script>
	const TEACHER_WX = 'gz199teacher'

	export default {
		methods: {
			copyWx() {
				uni.setClipboardData({
					data: TEACHER_WX,
					success: () => uni.showToast({ title: '已复制', icon: 'none' })
				})
			}
		}
	}
</script>

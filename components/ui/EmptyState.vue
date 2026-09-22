<template>
	<view class="edu-empty">
		<text class="edu-empty-title">{{ title }}</text>
		<text class="edu-empty-sub" v-if="sub">{{ sub }}</text>
	</view>
</template>

<script>
	export default {
		name: 'EmptyState',
		props: {
			title: { type: String, default: '' },
			sub: { type: String, default: '' }
		}
	}
</script>

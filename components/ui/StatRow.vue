<template>
	<view class="edu-stats" v-if="items.length">
		<view
			class="edu-stat"
			v-for="item in items"
			:key="item.label"
			:class="{ pending: item.pending }"
			:style="{ width: colWidth, flexBasis: colWidth }"
		>
			<text class="edu-stat-value">{{ item.value }}</text>
			<text class="edu-stat-label">{{ item.label }}</text>
		</view>
	</view>
</template>

<script>
	export default {
		name: 'StatRow',
		props: {
			items: { type: Array, default: () => [] },
			columns: { type: Number, default: 2 }
		},
		computed: {
			colWidth() {
				const n = this.columns > 0 ? this.columns : 2
				return (100 / n) + '%'
			}
		}
	}
</script>

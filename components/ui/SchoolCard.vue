<template>
	<view class="edu-school" @tap="$emit('open')">
		<image
			v-if="item.logo && !failed"
			class="edu-logo"
			:src="item.logo"
			mode="aspectFill"
			@error="failed = true"
		/>
		<view v-else class="edu-logo edu-logo-ph">{{ initial }}</view>
		<view class="edu-profile-copy">
			<view class="edu-name-row">
				<text class="edu-name">{{ item.name }}</text>
			</view>
			<text class="edu-sub" v-if="meta">{{ meta }}</text>
			<view class="edu-tags" v-if="tags.length">
				<text
					class="edu-tag"
					v-for="tag in tags"
					:key="tag.text"
					:class="tagClass(tag)"
				>{{ tag.text }}</text>
			</view>
		</view>
		<text class="edu-go">›</text>
	</view>
</template>

<script>
	export default {
		name: 'SchoolCard',
		props: {
			item: { type: Object, required: true },
			meta: { type: String, default: '' },
			tags: { type: Array, default: () => [] }
		},
		data() {
			return { failed: false }
		},
		computed: {
			initial() {
				return String(this.item && this.item.name ? this.item.name : '').slice(0, 1)
			}
		},
		methods: {
			tagClass(tag) {
				if (tag.tone === 'signal') return 'signal'
				if (tag.tone === 'heat' || tag.hot) return 'hot'
				return ''
			}
		},
		watch: {
			'item.logo'() {
				this.failed = false
			}
		}
	}
</script>

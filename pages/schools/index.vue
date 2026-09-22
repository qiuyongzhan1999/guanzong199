<template>
	<view class="edu-page">
		<view class="edu-toolbar">
			<view class="edu-search-row">
				<input
					class="edu-search"
					type="text"
					confirm-type="search"
					placeholder="搜招生单位名称"
					placeholder-class="edu-search-ph"
					:value="keyword"
					@input="onInput"
					@confirm="reload"
				/>
				<view class="edu-search-btn" @tap="reload">搜索</view>
			</view>
			<view class="edu-filters">
				<picker mode="selector" :range="regions" range-key="name" :value="regionIndex" @change="onRegion">
					<view class="edu-chip" :class="{ on: regionIndex > 0 }">{{ regions[regionIndex].name }}</view>
				</picker>
				<picker mode="selector" :range="modes" range-key="label" :value="modeIndex" @change="onMode">
					<view class="edu-chip edu-chip-dropdown">
						{{ modes[modeIndex].label }}
						<text class="edu-arrow">▾</text>
					</view>
				</picker>
				<view
					class="edu-chip"
					v-for="item in traits"
					:key="item.id"
					:class="{ on: trait === item.id }"
					@tap="setTrait(item.id)"
				>{{ item.label }}</view>
			</view>
		</view>

		<EmptyState
			v-if="error && !list.length"
			title="名单没有读到"
			:sub="errorText()"
		/>
		<EmptyState
			v-else-if="!loading && !list.length"
			title="没有这个招生单位"
			sub="换个校名，或把地区和特性改回不限。"
		/>

		<SchoolCard
			v-for="item in list"
			:key="item.schId || item.name"
			:item="item"
			:meta="meta(item)"
			:tags="labels(item)"
			@open="openDetail(item)"
		/>

		<view class="edu-foot" v-if="loading">正在整理名单…</view>
		<view class="edu-foot link" v-else-if="error && list.length" @tap="load(false)">这一页没读到，点此重试</view>
		<view class="edu-foot" v-else-if="list.length">名单来自中国研究生招生信息网</view>
	</view>
</template>

<script>
	import { REGIONS, fetchSchoolPage, schoolLabels } from '../../utils/chsi.js'
	import SchoolCard from '../../components/ui/SchoolCard.vue'
	import EmptyState from '../../components/ui/EmptyState.vue'

	export default {
		components: { SchoolCard, EmptyState },
		data() {
			return {
				keyword: '',
				appliedKeyword: '',
				regions: REGIONS,
				regionIndex: 0,
				studyMode: 'fulltime',
				modeIndex: 0,
				modes: [
					{ id: 'fulltime', label: '全日制' },
					{ id: 'parttime', label: '非全日制' }
				],
				trait: 'all',
				traits: [
					{ id: 'all', label: '不限' },
					{ id: 'ylgx', label: '双一流' },
					{ id: 'yjsy', label: '研究生院' },
					{ id: 'zhx', label: '自划线' }
				],
				list: [],
				start: 0,
				hasMore: true,
				loading: false,
				error: '',
				seq: 0
			}
		},
		onLoad() {
			this.reload()
		},
		onPullDownRefresh() {
			this.reload()
		},
		onReachBottom() {
			this.load(false)
		},
		methods: {
			labels: schoolLabels,
			errorText() {
				const msg = typeof this.error === 'string' ? this.error : ''
				if (msg.indexOf('domain list') >= 0 || msg.indexOf('合法域名') >= 0) {
					return '微信还在拦这次请求。域名刚保存的话，关掉开发者工具再打开，然后重新编译。'
				}
				if (msg === 'bad response') {
					return '研招网有响应，但没有院校名单。下拉重试。'
				}
				if (msg.indexOf('http ') === 0) {
					return '研招网返回了 ' + msg.slice(5) + '。下拉重试。'
				}
				if (!msg || msg === 'fail') {
					return '名单没有读到。下拉重试。'
				}
				return msg
			},
			meta(item) {
				const parts = []
				if (item.province) parts.push(item.province)
				if (item.authority) parts.push(item.authority)
				return parts.join(' · ')
			},
			openDetail(item) {
				if (!item.schId) return
				uni.navigateTo({ url: '/pages/schools/detail?id=' + item.schId })
			},
			onInput(e) {
				this.keyword = e.detail.value
			},
			onRegion(e) {
				this.regionIndex = Number(e.detail.value)
				this.reload()
			},
			onMode(e) {
				this.modeIndex = Number(e.detail.value)
				this.studyMode = this.modes[this.modeIndex].id
				this.reload()
			},
			setTrait(id) {
				this.trait = id
				this.reload()
			},
			reload() {
				this.appliedKeyword = (this.keyword || '').trim()
				return this.load(true)
			},
			load(reset) {
				if (this.loading && !reset) return Promise.resolve()
				if (!reset && !this.hasMore) return Promise.resolve()
				const seq = this.seq + 1
				this.seq = seq
				const start = reset ? 0 : this.start
				if (reset) {
					this.list = []
					this.start = 0
					this.hasMore = true
					this.error = ''
				}
				this.loading = true
				const region = this.regions[this.regionIndex]
				const traitOn = this.trait !== 'all'
				return fetchSchoolPage({
					yxmc: this.appliedKeyword,
					ssdm: region.code,
					ylgx: traitOn && this.trait === 'ylgx' ? '1' : '',
					yjsy: traitOn && this.trait === 'yjsy' ? '1' : '',
					zhx: traitOn && this.trait === 'zhx' ? '1' : '',
					start
				}).then((batch) => {
					if (seq !== this.seq) return
					const base = reset ? [] : this.list.slice()
					const seen = {}
					base.forEach((item) => {
						seen[item.schId || item.name] = true
					})
					let added = 0
					batch.forEach((item) => {
						const key = item.schId || item.name
						if (!seen[key]) {
							seen[key] = true
							base.push(item)
							added += 1
						}
					})
					this.list = base
					this.start = start + 20
					this.hasMore = batch.length >= 20 && added > 0
					this.loading = false
					this.error = ''
					uni.stopPullDownRefresh()
				}).catch((err) => {
					if (seq !== this.seq) return
					this.loading = false
					const message = err && typeof err.message === 'string' ? err.message : ''
					this.error = message && message.indexOf('native code') < 0 ? message : 'fail'
					uni.stopPullDownRefresh()
				})
			}
		}
	}
</script>

<style>
.edu-chip-dropdown {
	display: inline-flex;
	align-items: center;
	gap: 4px;
}
.edu-arrow {
	font-size: 12px;
	opacity: 0.6;
}
</style>

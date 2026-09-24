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

			<view class="edu-filter-grid">
				<view class="edu-filter-item">
					<picker mode="selector" :range="regions" range-key="name" :value="regionIndex" @change="onRegion">
						<view class="edu-filter-cell" :class="{ on: regionIndex > 0 }">
							<text class="edu-filter-k">地区</text>
							<text class="edu-filter-v">{{ regionLabel }}</text>
							<text class="edu-filter-arrow">▾</text>
						</view>
					</picker>
				</view>
				<view class="edu-filter-item">
					<picker mode="selector" :range="modes" range-key="label" :value="modeIndex" @change="onMode">
						<view class="edu-filter-cell">
							<text class="edu-filter-k">学制</text>
							<text class="edu-filter-v">{{ modes[modeIndex].label }}</text>
							<text class="edu-filter-arrow">▾</text>
						</view>
					</picker>
				</view>
				<view class="edu-filter-item">
					<picker mode="selector" :range="traits" range-key="label" :value="traitIndex" @change="onTrait">
						<view class="edu-filter-cell" :class="{ on: trait !== 'all' }">
							<text class="edu-filter-k">特质</text>
							<text class="edu-filter-v">{{ traits[traitIndex].label }}</text>
							<text class="edu-filter-arrow">▾</text>
						</view>
					</picker>
				</view>
				<view class="edu-filter-item">
					<picker mode="selector" :range="majors" range-key="name" :value="majorIndex" @change="onMajor">
						<view class="edu-filter-cell on">
							<text class="edu-filter-k">专业</text>
							<text class="edu-filter-v">{{ majors[majorIndex].name }}</text>
							<text class="edu-filter-arrow">▾</text>
						</view>
					</picker>
				</view>
			</view>
		</view>

		<EmptyState
			v-if="error && !list.length"
			title="名单没有读到"
			:sub="errorText()"
		/>
		<EmptyState
			v-else-if="!loading && !list.length"
			title="暂时没有院校数据"
			sub="换个校名，或把专业、地区和学习形式改回不限。"
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
		<view class="edu-foot" v-else-if="list.length">名单来自乐学猫院校库</view>
	</view>
</template>

<script>
	import { REGIONS, schoolLabels } from '../../utils/chsi.js'
	import { fetchSchools } from '../../utils/api.js'
	import { MAJORS } from '../../utils/programs.js'
	import SchoolCard from '../../components/ui/SchoolCard.vue'
	import EmptyState from '../../components/ui/EmptyState.vue'

	export default {
		components: { SchoolCard, EmptyState },
		data() {
			return {
				keyword: '',
				appliedKeyword: '',
				majors: MAJORS,
				majorIndex: 0,
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
		computed: {
			traitIndex() {
				const i = this.traits.findIndex((item) => item.id === this.trait)
				return i >= 0 ? i : 0
			},
			regionLabel() {
				const name = this.regions[this.regionIndex] && this.regions[this.regionIndex].name
				return name === '全部地区' ? '不限' : name
			}
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
				const majorCode = this.majors[this.majorIndex] ? this.majors[this.majorIndex].code : '125300'
				uni.navigateTo({ url: '/pages/schools/detail?id=' + item.schId + '&mode=' + this.studyMode + '&major=' + majorCode })
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
			onTrait(e) {
				const i = Number(e.detail.value)
				this.trait = this.traits[i] ? this.traits[i].id : 'all'
				this.reload()
			},
			onMajor(e) {
				this.majorIndex = Number(e.detail.value)
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
				const majorCode = this.majors[this.majorIndex] ? this.majors[this.majorIndex].code : '125300'
				const page = Math.floor(start / 20) + 1
				return fetchSchools({
					keyword: this.appliedKeyword,
					province: region.name === '全部地区' ? '' : region.name,
					studyMode: this.studyMode,
					trait: this.trait,
					majorCode,
					page
				}).then((res) => {
					const batch = (res && res.items) || []
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

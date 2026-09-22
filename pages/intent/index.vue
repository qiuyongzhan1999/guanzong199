<template>
	<view class="edu-page">
		<view class="edu-card">
			<text class="edu-card-title">专业</text>
			<view class="edu-chips">
				<view
					class="edu-chip"
					v-for="item in majors"
					:key="item.code"
					:class="{ on: major === item.code }"
					@tap="major = item.code"
				>{{ item.name }}</view>
			</view>
		</view>
		<view class="edu-card">
			<text class="edu-card-title">学习方式</text>
			<view class="edu-chips">
				<view
					class="edu-chip"
					v-for="item in modes"
					:key="item.id"
					:class="{ on: studyMode === item.id }"
					@tap="studyMode = item.id"
				>{{ item.label }}</view>
			</view>
		</view>
		<view class="edu-card">
			<text class="edu-card-title">请填写你的预估分数区间</text>
			<text class="edu-hint">管综 200 + 英语 100，满分 300。</text>
			<view class="edu-search-row edu-range">
				<input
					class="edu-search"
					type="number"
					:value="scoreMin"
					placeholder="下限 200"
					placeholder-class="edu-search-ph"
					@input="onMin"
				/>
				<text class="edu-hint edu-range-dash">–</text>
				<input
					class="edu-search"
					type="number"
					:value="scoreMax"
					placeholder="上限 230"
					placeholder-class="edu-search-ph"
					@input="onMax"
				/>
			</view>
		</view>
		<view class="edu-card">
			<view class="edu-card-head">
				<text class="edu-card-title">想去的省份</text>
				<text class="edu-hint">{{ regionText }}</text>
			</view>
			<view class="edu-chips">
				<view class="edu-chip" :class="{ on: allRegions }" @tap="toggleAll">全选</view>
				<view
					class="edu-chip"
					v-for="item in regionOptions"
					:key="item.code"
					:class="{ on: picked[item.code] }"
					@tap="toggleRegion(item.code)"
				>{{ item.name }}</view>
			</view>
		</view>
		<view class="edu-btn" :class="{ off: !ready }" @tap="viewSchools">查看稳冲难建议</view>
	</view>
</template>

<script>
	import { REGIONS } from '../../utils/chsi.js'
	import { MAJORS } from '../../utils/programs.js'

	export default {
		data() {
			return {
				majors: MAJORS,
				major: '',
				studyMode: 'fulltime',
				modes: [
					{ id: 'fulltime', label: '全日制' },
					{ id: 'parttime', label: '非全日制' }
				],
				scoreMin: '',
				scoreMax: '',
				regionOptions: REGIONS.filter((item) => item.code && item.code < '71'),
				picked: {}
			}
		},
		computed: {
			pickedItems() {
				return this.regionOptions.filter((item) => this.picked[item.code])
			},
			allRegions() {
				return this.pickedItems.length === this.regionOptions.length
			},
			regionText() {
				const n = this.pickedItems.length
				return n ? ('已选 ' + n + ' 个') : '不限'
			},
			ready() {
				return !!this.major && this.rangeOk()
			}
		},
		methods: {
			onMin(e) {
				this.scoreMin = (e.detail && e.detail.value) || ''
			},
			onMax(e) {
				this.scoreMax = (e.detail && e.detail.value) || ''
			},
			rangeOk() {
				const lo = parseInt(this.scoreMin, 10)
				const hi = parseInt(this.scoreMax, 10)
				return lo >= 100 && hi <= 280 && lo <= hi
			},
			toggleRegion(code) {
				this.picked = Object.assign({}, this.picked, { [code]: !this.picked[code] })
			},
			toggleAll() {
				const next = {}
				if (!this.allRegions) {
					this.regionOptions.forEach((item) => {
						next[item.code] = true
					})
				}
				this.picked = next
			},
			viewSchools() {
				if (!this.major) {
					uni.showToast({ title: '先选专业', icon: 'none' })
					return
				}
				const lo = parseInt(this.scoreMin, 10)
				const hi = parseInt(this.scoreMax, 10)
				if (!(lo >= 100 && hi <= 280 && lo <= hi)) {
					uni.showToast({ title: '分数填 100–280，下限不要高于上限', icon: 'none' })
					return
				}
				const provinces = this.pickedItems.map((item) => item.name).join(',')
				uni.navigateTo({
					url: '/pages/intent/result?major=' + this.major
						+ '&mode=' + this.studyMode
						+ '&scoreMin=' + lo
						+ '&scoreMax=' + hi
						+ '&provinces=' + encodeURIComponent(provinces)
				})
			}
		}
	}
</script>

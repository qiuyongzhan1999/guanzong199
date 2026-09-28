<template>
	<view class="edu-page">
		<view v-if="loading" class="edu-card edu-advice-wait">
			<view class="edu-advice-wait-bar" />
			<text class="edu-advice-wait-brand">智能择校</text>
			<text class="edu-advice-wait-title edu-advice-pulse">正在排序院校{{ waitDots }}</text>
		</view>
		<EmptyState
			v-else-if="error"
			title="暂时排不了"
			:sub="error"
		/>
		<template v-else>
			<StatRow :columns="3" :items="bandStats" />

			<view class="edu-card edu-advice-wait" v-if="adviceLoading">
				<view class="edu-advice-wait-bar" />
				<text class="edu-advice-wait-brand">智能择校</text>
				<text class="edu-advice-wait-title edu-advice-pulse">正在根据历年分数线写建议{{ waitDots }}</text>
				<view class="edu-advice-stream" v-if="adviceStreamText">
					<scroll-view scroll-y class="edu-advice-stream-body">
						<text class="edu-advice-stream-text">{{ adviceStreamText }}</text>
					</scroll-view>
				</view>
			</view>

			<view class="edu-card" v-else-if="adviceBlocks.length || advice || adviceNote">
				<view class="edu-card-head edu-card-head-center">
					<text class="edu-card-title">智能择校</text>
					<text class="edu-hint" @tap="toggleAdviceAll">{{ adviceOpen ? '收起' : '展开' }}</text>
				</view>
				<block v-if="adviceBlocks.length">
					<view
						class="edu-match-block"
						:class="{ fold: !isAdviceOpen(block.title) }"
						v-for="block in adviceBlocks"
						:key="block.title"
						@tap="toggleAdvice(block.title)"
					>
						<text class="edu-kicker">{{ blockTitle(block.title) }}<text class="edu-match-toggle">{{ isAdviceOpen(block.title) ? '收起' : '展开' }}</text></text>
						<scroll-view
							v-if="block.title === '对照表' && tableOf(block.body)"
							class="edu-match-table-scroll"
							scroll-x
							:show-scrollbar="false"
						>
							<view class="edu-match-table">
								<view class="edu-match-tr head">
									<text
										class="edu-match-td"
										v-for="(h, hi) in tableOf(block.body).headers"
										:key="'h' + hi"
									>{{ h }}</text>
								</view>
								<view
									class="edu-match-tr"
									v-for="(row, ri) in tableOf(block.body).rows"
									:key="'r' + ri"
								>
									<text
										class="edu-match-td"
										v-for="(cell, ci) in row"
										:key="'c' + ri + '-' + ci"
									>{{ cell }}</text>
								</view>
							</view>
						</scroll-view>
						<text class="edu-prose" v-else>{{ block.body }}</text>
					</view>
				</block>
				<text class="edu-prose" v-else-if="advice">{{ advice }}</text>
				<text class="edu-sub" v-else>{{ adviceNote }}</text>
				<text class="edu-hint edu-match-disclaimer">{{ disclaimer }}</text>
			</view>

			<view class="edu-card" v-if="ranked.length || unmatched.length">
				<view class="edu-card-head">
					<text class="edu-card-title">院校列表</text>
					<text class="edu-hint">稳 → 冲 → 难</text>
				</view>

				<view class="edu-chips">
					<view class="edu-chip" :class="{ on: !band }" @tap="setBand('')">全部</view>
					<view
						class="edu-chip"
						v-for="item in bandOptions"
						:key="item"
						:class="{ on: band === item }"
						@tap="setBand(item)"
					>{{ item }}</view>
				</view>

				<text class="edu-filter-label">省份</text>
				<view class="edu-chips edu-chips-wrap">
					<view class="edu-chip" :class="{ on: !province }" @tap="setProvince('')">全部</view>
					<view
						class="edu-chip"
						v-for="item in provinceOptions"
						:key="item"
						:class="{ on: province === item }"
						@tap="setProvince(item)"
					>{{ item }}</view>
				</view>

				<input
					class="edu-search"
					type="text"
					confirm-type="search"
					placeholder="搜校名"
					placeholder-class="edu-search-ph"
					:value="keyword"
					@input="onKeyword"
				/>
			</view>

			<SchoolCard
				v-for="item in filteredRanked"
				:key="item.schoolCode + item.majorCode"
				:item="item"
				:meta="meta(item)"
				:tags="tags(item)"
				@open="open(item)"
			/>

			<EmptyState
				v-if="ranked.length && !filteredRanked.length && !(showUnmatched && filteredUnmatched.length)"
				title="这个筛选下没有学校"
				sub="换个档位或省份，或清空搜校名。"
			/>

			<template v-if="showUnmatched && filteredUnmatched.length">
				<view class="edu-card">
					<text class="edu-card-title">暂无对照分</text>
					<text class="edu-hint">没有复试线或拟录取最低分，未列入稳 / 冲 / 难。</text>
				</view>
				<SchoolCard
					v-for="item in filteredUnmatched"
					:key="'u-' + item.schoolCode + item.majorCode"
					:item="item"
					:meta="plainMeta(item)"
					:tags="[{ text: '暂无分', hot: false }]"
					@open="open(item)"
				/>
			</template>
		</template>
	</view>
</template>

<script>
	import { matchSchools, matchAdviceStream } from '../../utils/api.js'
	import SchoolCard from '../../components/ui/SchoolCard.vue'
	import EmptyState from '../../components/ui/EmptyState.vue'
	import StatRow from '../../components/ui/StatRow.vue'

	const BANDS = ['稳', '冲', '难']

	export default {
		components: { SchoolCard, EmptyState, StatRow },
		data() {
			return {
				loading: true,
				adviceLoading: false,
				adviceStreamText: '',
				error: '',
				advice: '',
				adviceBlocks: [],
				adviceOpenMap: {},
				adviceOpen: true,
				adviceNote: '',
				disclaimer: '建议仅供参考，招生计划和分数线以院校官方公告为准。',
				ranked: [],
				unmatched: [],
				scoreMin: '',
				scoreMax: '',
				major: '',
				mode: 'fulltime',
				province: '',
				band: '',
				keyword: '',
				waitDots: '。',
				dotTimer: null,
				queryProvinces: []
			}
		},
		computed: {
			bandStats() {
				const counts = { 稳: 0, 冲: 0, 难: 0 }
				this.ranked.forEach((item) => {
					if (counts[item.band] !== undefined) counts[item.band]++
				})
				return [
					{ label: '稳', value: String(counts['稳']) },
					{ label: '冲', value: String(counts['冲']) },
					{ label: '难', value: String(counts['难']) }
				]
			},
			provinceOptions() {
				const seen = {}
				const list = []
				this.ranked.concat(this.unmatched).forEach((item) => {
					const key = ((item && item.province) || '').trim()
					if (key && !seen[key]) {
						seen[key] = true
						list.push(key)
					}
				})
				return list.sort((a, b) => a.localeCompare(b, 'zh-CN'))
			},
			bandOptions() {
				const present = {}
				this.ranked.forEach((item) => {
					if (item.band) present[item.band] = true
				})
				return BANDS.filter((b) => present[b])
			},
			showUnmatched() {
				return !this.band
			},
			filteredRanked() {
				return this.ranked.filter((item) => this.passFilters(item, true))
			},
			filteredUnmatched() {
				return this.unmatched.filter((item) => this.passFilters(item, false))
			},
			matchBody() {
				return {
					majorCode: this.major,
					studyMode: this.mode,
					scoreMin: Number(this.scoreMin),
					scoreMax: Number(this.scoreMax),
					provinces: this.queryProvinces
				}
			}
		},
		onLoad(query) {
			this.major = query.major || ''
			this.mode = query.mode || 'fulltime'
			this.scoreMin = query.scoreMin || query.score || ''
			this.scoreMax = query.scoreMax || query.score || ''
			uni.setNavigationBarTitle({ title: '智能择校' })
			this.queryProvinces = query.provinces
				? decodeURIComponent(query.provinces).split(',').filter(Boolean)
				: []
			this.load()
		},
		onUnload() {
			this.stopDots()
		},
		methods: {
			startDots() {
				this.stopDots()
				const frames = ['。', '。。', '。。。']
				let i = 0
				this.waitDots = frames[0]
				this.dotTimer = setInterval(() => {
					i = (i + 1) % frames.length
					this.waitDots = frames[i]
				}, 800)
			},
			stopDots() {
				if (this.dotTimer) {
					clearInterval(this.dotTimer)
					this.dotTimer = null
				}
			},
			passFilters(item, checkBand) {
				if (this.province && ((item && item.province) || '') !== this.province) return false
				if (checkBand && this.band && item.band !== this.band) return false
				const kw = (this.keyword || '').trim()
				if (kw && String(item.name || '').indexOf(kw) < 0) return false
				return true
			},
			setProvince(value) {
				this.province = value === '' ? '' : (this.province === value ? '' : value)
			},
			setBand(value) {
				this.band = value === '' ? '' : (this.band === value ? '' : value)
			},
			onKeyword(e) {
				this.keyword = (e.detail && e.detail.value) || ''
			},
			isAdviceOpen(title) {
				if (Object.prototype.hasOwnProperty.call(this.adviceOpenMap, title)) {
					return !!this.adviceOpenMap[title]
				}
				return title === '对照表'
			},
			blockTitle(title) {
				return title === '对照表' ? '对照表' : title
			},
			tableOf(body) {
				const lines = String(body || '').split('\n').map((s) => s.trim()).filter(Boolean)
				const rows = []
				for (let i = 0; i < lines.length; i++) {
					const line = lines[i]
					if (!line.startsWith('|')) continue
					const cells = line.split('|').slice(1, -1).map((c) => c.trim())
					if (!cells.length) continue
					if (/^[-:]+$/.test(cells.join('').replace(/-/g, '-'))) {
						const onlySep = cells.every((c) => /^[-:\s]+$/.test(c))
						if (onlySep) continue
					}
					if (cells.every((c) => /^[-:\s]+$/.test(c))) continue
					rows.push(cells)
				}
				if (rows.length < 2) return null
				return { headers: rows[0], rows: rows.slice(1) }
			},
			toggleAdvice(title) {
				const next = Object.assign({}, this.adviceOpenMap)
				next[title] = !this.isAdviceOpen(title)
				this.adviceOpenMap = next
			},
			toggleAdviceAll() {
				const open = !this.adviceOpen
				this.adviceOpen = open
				const next = {}
				this.adviceBlocks.forEach((block) => {
					next[block.title] = open
				})
				this.adviceOpenMap = next
			},
			async load() {
				this.loading = true
				this.error = ''
				this.adviceLoading = false
				this.advice = ''
				this.adviceBlocks = []
				this.adviceNote = ''
				this.startDots()
				try {
					const data = await matchSchools(this.matchBody)
					if (data && data.error && !(data.ranked && data.ranked.length)) {
						this.error = data.error
						this.stopDots()
						return
					}
					this.disclaimer = data.disclaimer || this.disclaimer
					this.ranked = data.ranked || []
					this.unmatched = data.unmatched || []
					if (!this.ranked.length && !this.unmatched.length && !this.error) {
						this.error = '没有符合条件的学校。'
						this.stopDots()
						return
					}
					this.loading = false
					this.loadAdvice()
				} catch (e) {
					const msg = (e && (e.errMsg || e.message)) || '网络错误'
					this.error = '请求失败：' + msg
					this.loading = false
					this.stopDots()
				}
			},
			async loadAdvice() {
				if (!this.ranked.length) return
				this.adviceLoading = true
				this.adviceStreamText = ''
				this.startDots()
				try {
					await matchAdviceStream(this.matchBody, {
						onDelta: (text) => {
							this.adviceStreamText += text
						},
						onDone: (ev) => {
							const advice = (ev && ev.advice) || ''
							this.advice = advice
							this.adviceBlocks = this.layoutBlocks(advice)
							this.adviceOpenMap = {}
							this.adviceOpen = true
							if (!this.adviceBlocks.length && !this.advice) {
								this.adviceNote = '没有生成文字建议。列表仍按稳 → 冲 → 难排列。'
							}
						},
						onError: (msg) => {
							this.adviceNote = msg || '建议暂时没生成，可先看下方稳 / 冲 / 难列表。'
						}
					})
				} catch (e) {
					this.adviceNote = '建议暂时没生成，可先看下方稳 / 冲 / 难列表。'
				} finally {
					this.stopDots()
					this.adviceLoading = false
				}
			},
			layoutBlocks(raw) {
				let text = String(raw || '').replace(/\r\n/g, '\n').trim()
				text = text.replace(/^```[a-zA-Z]*\s*/gm, '').replace(/```/g, '')
				text = text.replace(/\*\*/g, '').replace(/^#{1,6}\s*/gm, '').trim()
				const rows = []
				text.split('\n').forEach((line) => {
					const t = line.trim()
					if (t.startsWith('|')) rows.push(t)
				})
				const body = rows.join('\n').trim()
				if (!body) {
					return text ? [{ title: '对照表', body: text }] : []
				}
				return [{ title: '对照表', body }]
			},
			meta(item) {
				const low = item.recentMinScores
				const avg = item.recentMinAvg
				const bits = []
				if (low && low !== '—') bits.push('近三年最低 ' + low)
				if (avg && avg !== '—') bits.push('平均 ' + avg)
				if (item.gapNote) bits.push(item.gapNote)
				return bits.join(' · ')
			},
			plainMeta(item) {
				return item.majorName || ''
			},
			tags(item) {
				const tone = item.band === '难' || item.lineOnly
					? 'heat'
					: (item.band === '冲' ? 'signal' : 'good')
				const list = [{ text: item.band || '对照', tone }]
				if (item.lineOnly) list.push({ text: '仅复试线', tone: 'heat' })
				return list
			},
			open(item) {
				if (!item.schId) {
					uni.showToast({ title: '缺少院校编号', icon: 'none' })
					return
				}
				uni.navigateTo({
					url: '/pages/schools/detail?id=' + item.schId
						+ '&major=' + item.majorCode
						+ '&mode=' + (item.studyMode || this.mode)
				})
			}
		}
	}
</script>

<style scoped>
.edu-advice-stream {
	margin-top: 20rpx;
	border-radius: 16rpx;
	background: #fff;
	padding: 20rpx;
	border: 1rpx solid #e5e9f2;
}
.edu-advice-stream-body {
	max-height: 400rpx;
}
.edu-advice-stream-text {
	font-size: 25rpx;
	line-height: 1.7;
	color: #334155;
	white-space: pre-wrap;
	word-break: break-all;
	font-family: monospace;
}
</style>

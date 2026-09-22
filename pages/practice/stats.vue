<template>
	<view class="edu-page">
		<view class="edu-card edu-practice-hero">
			<text class="edu-kicker">练习数据</text>
			<text class="edu-card-title">刷题总览</text>
			<StatRow :columns="3" :items="statItems" />
			<view class="edu-kv" style="margin-top:16rpx">
				<text class="edu-kv-k">连续天数</text>
				<text class="edu-kv-v">{{ streak }} 天</text>
			</view>
		</view>

		<view class="edu-card">
			<view class="edu-section-head edu-section-head-in">
				<view class="edu-section-bar" />
				<text class="edu-section-title">知识点雷达</text>
			</view>
			<view class="edu-radar-wrap" v-if="radar.length">
				<canvas
					canvas-id="kpRadar"
					id="kpRadar"
					class="edu-radar-canvas"
					:style="{ width: canvasSize + 'px', height: canvasSize + 'px' }"
				/>
			</view>
			<EmptyState v-else title="暂无雷达数据" sub="多做几题后这里会展开知识点掌握情况。" />
			<view class="edu-kv" v-for="r in radar" :key="r.knowledgePointId">
				<text class="edu-kv-k">{{ r.knowledgePointName }}</text>
				<text class="edu-kv-v">{{ r.masteryRate }}%</text>
			</view>
		</view>

		<view class="edu-card">
			<view class="edu-section-head edu-section-head-in">
				<view class="edu-section-bar tone-heat" />
				<text class="edu-section-title">正确率趋势</text>
			</view>
			<EmptyState v-if="!trend.length" title="暂无趋势" sub="连续几天刷题后会出现折线趋势。" />
			<view class="edu-trend-row" v-for="t in trend" :key="t.day">
				<text class="edu-muted">{{ t.day }}</text>
				<view class="edu-hp-bar" style="flex:1;margin:0 16rpx">
					<view class="edu-hp-fill tone-good" :style="{ width: t.accuracy + '%' }" />
				</view>
				<text class="edu-hint">{{ t.accuracy }}%</text>
			</view>
		</view>
	</view>
</template>

<script>
	import EmptyState from '../../components/ui/EmptyState.vue'
	import StatRow from '../../components/ui/StatRow.vue'
	import { EDU } from '../../styles/edu-theme.js'
	import { practiceKnowledge, practiceOverview } from '../../utils/api.js'
	import { getPracticeUserKey } from '../../utils/practice-user.js'

	export default {
		components: { EmptyState, StatRow },
		data() {
			return {
				stats: {},
				radar: [],
				trend: [],
				canvasSize: 280
			}
		},
		computed: {
			streak() {
				return (this.stats && this.stats.streakDays) || 0
			},
			statItems() {
				const s = this.stats || {}
				return [
					{ label: '总题数', value: String(s.totalAnswered || 0) },
					{ label: '正确率', value: (s.accuracy || 0) + '%' },
					{ label: '错题', value: String(s.wrongOpen || 0) }
				]
			}
		},
		onShow() {
			this.load()
		},
		methods: {
			async load() {
				const key = getPracticeUserKey()
				try {
					const ov = await practiceOverview({ userKey: key })
					this.stats = (ov && ov.stats) || {}
					this.trend = (ov && ov.trend) || []
					const kn = await practiceKnowledge({ userKey: key })
					this.radar = (kn && kn.radar) || []
					this.$nextTick(() => this.drawRadar())
				} catch (e) {
					uni.showToast({ title: '统计加载失败', icon: 'none' })
				}
			},
			drawRadar() {
				if (!this.radar.length) return
				const ctx = uni.createCanvasContext('kpRadar', this)
				const n = this.radar.length
				const cx = this.canvasSize / 2
				const cy = this.canvasSize / 2
				const R = this.canvasSize * 0.36
				const levels = 4

				ctx.clearRect(0, 0, this.canvasSize, this.canvasSize)
				ctx.setStrokeStyle(EDU.chart.grid)
				ctx.setLineWidth(1)
				for (let lv = 1; lv <= levels; lv++) {
					const r = (R * lv) / levels
					ctx.beginPath()
					for (let i = 0; i < n; i++) {
						const ang = -Math.PI / 2 + (Math.PI * 2 * i) / n
						const x = cx + r * Math.cos(ang)
						const y = cy + r * Math.sin(ang)
						if (i === 0) ctx.moveTo(x, y)
						else ctx.lineTo(x, y)
					}
					ctx.closePath()
					ctx.stroke()
				}

				ctx.setFillStyle('rgba(47, 111, 237, 0.22)')
				ctx.setStrokeStyle(EDU.color.signal)
				ctx.setLineWidth(2)
				ctx.beginPath()
				for (let i = 0; i < n; i++) {
					const rate = Number(this.radar[i].masteryRate) || 0
					const r = (R * Math.min(100, Math.max(0, rate))) / 100
					const ang = -Math.PI / 2 + (Math.PI * 2 * i) / n
					const x = cx + r * Math.cos(ang)
					const y = cy + r * Math.sin(ang)
					if (i === 0) ctx.moveTo(x, y)
					else ctx.lineTo(x, y)
				}
				ctx.closePath()
				ctx.fill()
				ctx.stroke()

				ctx.setFillStyle(EDU.color.mist)
				ctx.setFontSize(10)
				for (let i = 0; i < n; i++) {
					const ang = -Math.PI / 2 + (Math.PI * 2 * i) / n
					const x = cx + (R + 14) * Math.cos(ang)
					const y = cy + (R + 14) * Math.sin(ang)
					const name = String(this.radar[i].knowledgePointName || '').slice(0, 4)
					ctx.fillText(name, x - 12, y + 4)
				}
				ctx.draw()
			}
		}
	}
</script>

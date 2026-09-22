<template>
	<view class="edu-chart" v-if="points.length">
		<view class="edu-chart-block" v-if="mode !== 'bar'">
			<view class="edu-chart-box edu-chart-box-sm">
				<l-echart ref="scoreChart" custom-style="width:100%;height:360rpx" @finished="initScore"></l-echart>
			</view>
		</view>
		<view class="edu-chart-block" v-if="mode !== 'line'">
			<view class="edu-chart-box edu-chart-box-sm">
				<l-echart ref="admitChart" custom-style="width:100%;height:360rpx" @finished="initAdmit"></l-echart>
			</view>
		</view>
	</view>
</template>

<script>
	// #ifdef MP
	const echarts = require('../uni_modules/lime-echart/static/echarts.min.js')
	// #endif
	// #ifndef MP
	import * as echarts from 'echarts/dist/echarts.esm'
	// #endif
	import LEchart from '../uni_modules/lime-echart/components/l-echart/l-echart.vue'
	import { EDU } from '../styles/edu-theme.js'

	const AXIS = EDU.chart.axis
	const GRID = EDU.chart.grid

	export default {
		name: 'TrendCharts',
		components: { LEchart },
		props: {
			trend: {
				type: Array,
				default: () => []
			},
			mode: {
				type: String,
				default: 'both'
			}
		},
		data() {
			return {
				scoreIns: null,
				admitIns: null
			}
		},
		computed: {
			points() {
				return (this.trend || []).slice().sort((a, b) => (a.year || 0) - (b.year || 0))
			},
			years() {
				return this.points.map((p) => String(p.year))
			},
			scoreData() {
				return this.points.map((p) => (typeof p.reexamMinScore === 'number' ? p.reexamMinScore : null))
			},
			minScoreData() {
				return this.points.map((p) => (typeof p.minScore === 'number' ? p.minScore : null))
			},
			admitData() {
				return this.points.map((p) => (typeof p.admitCount === 'number' ? p.admitCount : null))
			},
			reexamData() {
				return this.points.map((p) => (typeof p.reexamCount === 'number' ? p.reexamCount : null))
			},
			rateData() {
				return this.points.map((p) => {
					const raw = p.retestRate
					if (raw == null || raw === '') {
						if (typeof p.reexamCount === 'number' && p.reexamCount > 0 && typeof p.admitCount === 'number') {
							return Math.round((p.admitCount * 1000) / p.reexamCount) / 10
						}
						return null
					}
					const n = parseFloat(String(raw).replace('%', ''))
					return isNaN(n) ? null : n
				})
			},
			scoreOption() {
				return {
					color: [EDU.chart.reexam, EDU.chart.minScore],
					legend: {
						data: ['复试线', '录取最低分'],
						bottom: 0,
						itemWidth: 8,
						itemHeight: 8,
						textStyle: { color: EDU.color.mist, fontSize: 10 }
					},
					tooltip: {
						trigger: 'axis',
						confine: true,
						textStyle: { fontSize: 11, textShadowBlur: 0 }
					},
					grid: { left: '2%', right: '2%', top: '12%', bottom: '18%', containLabel: true },
					xAxis: {
						type: 'category',
						boundaryGap: false,
						data: this.years,
						axisLine: { lineStyle: { color: GRID } },
						axisLabel: { color: AXIS, fontSize: 10 }
					},
					yAxis: {
						type: 'value',
						scale: true,
						axisLabel: { color: EDU.color.faint, fontSize: 10 },
						splitLine: { lineStyle: { color: GRID } }
					},
					series: [
						{
							name: '复试线',
							type: 'line',
							smooth: true,
							symbol: 'circle',
							symbolSize: 5,
							data: this.scoreData,
							lineStyle: { width: 2, color: EDU.chart.reexam },
							itemStyle: { color: EDU.chart.reexam },
							areaStyle: {
								color: {
									type: 'linear',
									x: 0, y: 0, x2: 0, y2: 1,
									colorStops: [
										{ offset: 0, color: EDU.chart.reexamFill },
										{ offset: 1, color: EDU.chart.reexamFillEnd }
									]
								}
							}
						},
						{
							name: '录取最低分',
							type: 'line',
							smooth: true,
							symbol: 'circle',
							symbolSize: 5,
							data: this.minScoreData,
							lineStyle: { width: 2, color: EDU.chart.minScore },
							itemStyle: { color: EDU.chart.minScore }
						}
					]
				}
			},
			admitOption() {
				return {
					color: [EDU.chart.reexamCount, EDU.chart.reexam, EDU.chart.minScore],
					legend: {
						data: ['进复试', '实际录取', '录取率'],
						bottom: 0,
						itemWidth: 8,
						itemHeight: 8,
						textStyle: { color: EDU.color.mist, fontSize: 10 }
					},
					tooltip: {
						trigger: 'axis',
						confine: true,
						textStyle: { fontSize: 11, textShadowBlur: 0 }
					},
					grid: { left: '2%', right: '2%', top: '12%', bottom: '18%', containLabel: true },
					xAxis: {
						type: 'category',
						data: this.years,
						axisLine: { lineStyle: { color: GRID } },
						axisLabel: { color: AXIS, fontSize: 10 }
					},
					yAxis: [
						{
							type: 'value',
							minInterval: 1,
							axisLabel: { color: EDU.color.faint, fontSize: 10 },
							splitLine: { lineStyle: { color: GRID } }
						},
						{
							type: 'value',
							min: 0,
							max: 100,
							axisLabel: { color: EDU.color.faint, fontSize: 10, formatter: '{value}%' },
							splitLine: { show: false }
						}
					],
					series: [
						{
							name: '进复试',
							type: 'bar',
							barWidth: '18%',
							data: this.reexamData,
							itemStyle: { color: EDU.chart.reexamCount, borderRadius: [6, 6, 0, 0] }
						},
						{
							name: '实际录取',
							type: 'bar',
							barWidth: '18%',
							data: this.admitData,
							itemStyle: { color: EDU.chart.reexam, borderRadius: [6, 6, 0, 0] }
						},
						{
							name: '录取率',
							type: 'line',
							yAxisIndex: 1,
							smooth: true,
							symbolSize: 5,
							data: this.rateData,
							lineStyle: { width: 2, color: EDU.chart.minScore },
							itemStyle: { color: EDU.chart.minScore }
						}
					]
				}
			}
		},
		watch: {
			trend: {
				deep: true,
				handler() {
					this.$nextTick(() => this.refresh())
				}
			},
			mode() {
				this.$nextTick(() => this.refresh())
			}
		},
		methods: {
			async initScore() {
				if (!this.$refs.scoreChart) return
				this.scoreIns = await this.$refs.scoreChart.init(echarts)
				this.scoreIns.setOption(this.scoreOption)
			},
			async initAdmit() {
				if (!this.$refs.admitChart) return
				this.admitIns = await this.$refs.admitChart.init(echarts)
				this.admitIns.setOption(this.admitOption)
			},
			refresh() {
				if (this.scoreIns) this.scoreIns.setOption(this.scoreOption, true)
				if (this.admitIns) this.admitIns.setOption(this.admitOption, true)
			}
		}
	}
</script>

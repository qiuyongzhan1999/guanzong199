<template>
	<view class="edu-page" v-if="school">
		<!-- 头部：保持现有蓝卡 -->
		<view class="edu-scoreboard">
			<view class="edu-scoreboard-top">
				<image
					v-if="school.logo && !logoFailed"
					class="edu-logo"
					:src="school.logo"
					mode="aspectFill"
					@error="failLogo"
				/>
				<view v-else class="edu-logo edu-logo-ph">{{ school.name.slice(0, 1) }}</view>
				<view class="edu-profile-copy">
					<text class="edu-name">{{ school.name }}</text>
					<text class="edu-sub" v-if="meta">{{ meta }}</text>
					<view class="edu-tags" v-if="labels(school).length">
						<text class="edu-tag" v-for="tag in labels(school)" :key="tag.text" :class="{ hot: tag.hot }">{{ tag.text }}</text>
					</view>
				</view>
			</view>
			<view class="edu-spotlight">
				<view class="edu-spotlight-main">
					<text class="edu-spotlight-label">{{ spotlight.label }}</text>
					<text class="edu-spotlight-num">{{ spotlight.value }}</text>
				</view>
				<view class="edu-spotlight-side">
					<text class="edu-spotlight-side-k">{{ spotlightSide }}</text>
					<text class="edu-spotlight-side-v">{{ majorLabel }}</text>
				</view>
			</view>
		</view>

		<view
			v-if="syncText"
			class="edu-sync"
			:class="{ busy: syncing, err: !!syncError }"
		>
			<view class="edu-sync-dot"></view>
			<text class="edu-sync-text">{{ syncText }}</text>
		</view>

		<view class="edu-card" v-if="!majorOptions.length">
			<text class="edu-card-title">管综专业</text>
			<text class="edu-muted">2026年硕士专业目录里，这所学校没有管综专业。</text>
		</view>

		<block v-else>
			<!-- 专业方向分段 -->
			<view class="edu-card edu-card-tight">
				<text class="edu-filter-label">专业方向</text>
				<view class="edu-chips edu-chips-wrap">
					<view
						class="edu-chip"
						v-for="item in majorOptions"
						:key="'m-' + item.code"
						:class="{ on: majorCode === item.code }"
						@tap="pickMajor(item.code)"
					>{{ item.name }}</view>
				</view>
			</view>

			<!-- 核心数据看板 2 列 -->
			<view class="edu-dash" v-if="dashReady">
				<view class="edu-dash-cell">
					<text class="edu-dash-k">录取最低分</text>
					<text class="edu-dash-v">{{ dash.lowest }}</text>
					<text class="edu-dash-note tone-heat" v-if="dash.diffNote">{{ dash.diffNote }}</text>
				</view>
				<view class="edu-dash-cell">
					<text class="edu-dash-k">录取最高分</text>
					<text class="edu-dash-v">{{ dash.highest }}</text>
				</view>
				<view class="edu-dash-cell" :class="dash.rateTone">
					<text class="edu-dash-k">复试录取率</text>
					<text class="edu-dash-v">{{ dash.rate }}</text>
					<text class="edu-dash-note">{{ dash.rateNote }}</text>
				</view>
				<view class="edu-dash-cell">
					<text class="edu-dash-k">进复试 / 录取</text>
					<text class="edu-dash-v edu-dash-v-sm">{{ dash.retest }} / {{ dash.enroll }}</text>
				</view>
				<view class="edu-dash-cell edu-dash-span">
					<text class="edu-dash-k">学制 / 学费</text>
					<text class="edu-dash-v edu-dash-v-sm">{{ dash.duration }} · {{ dash.tuition }}</text>
				</view>
			</view>
			<view class="edu-dash-empty" v-else>
				<text>{{ syncing ? '正在读取招录数据…' : ('暂无 ' + targetCohort + ' 招录数据。') }}</text>
			</view>

			<!-- 折线：复试线 vs 录取最低 -->
			<view class="edu-card">
				<view class="edu-card-head">
					<text class="edu-card-title">近5年分数线</text>
				</view>
				<TrendCharts v-if="hasTrend" :trend="yearlyTrend" mode="line" />
				<view class="edu-chart-empty" v-else>
					<text>{{ syncing ? '读取中…' : '暂无趋势数据' }}</text>
				</view>
			</view>

			<!-- 近5年明细表 -->
			<view class="edu-card">
				<view class="edu-card-head">
					<text class="edu-card-title">近5年详细数据</text>
				</view>
				<scroll-view scroll-x class="edu-table-scroll" :show-scrollbar="false">
					<view class="edu-table-wide">
						<view class="edu-table-head edu-table-head-wide">
							<text>年份</text>
							<text>复试线</text>
							<text>进复试</text>
							<text>录取</text>
							<text>录取率</text>
							<text>最低分</text>
							<text>最高分</text>
						</view>
						<view
							class="edu-table-band edu-table-band-wide"
							v-for="row in tableRows"
							:key="'y-' + row.year"
						>
							<text>{{ row.yearLabel }}</text>
							<template v-if="row.empty">
								<text class="edu-table-span">暂无官方数据</text>
							</template>
							<template v-else>
								<text>{{ row.scoreLine }}</text>
								<text>{{ row.retest }}</text>
								<text>{{ row.enroll }}</text>
								<text :class="row.rateClass">{{ row.rate }}</text>
								<text :class="row.lowestClass">{{ row.lowest }}</text>
								<text>{{ row.highest }}</text>
							</template>
						</view>
					</view>
				</scroll-view>
			</view>

			<!-- AI 报考建议 -->
			<view class="edu-advice-banner" v-if="aiAdvice">
				<text class="edu-advice-title">报考建议</text>
				<text class="edu-advice-body">{{ aiAdvice }}</text>
			</view>
		</block>
	</view>
	<view class="edu-page" v-else>
		<EmptyState title="没有这所学校" sub="回到院校库，换一所再看。" />
	</view>
</template>

<script>
	import { findSchool, schoolLabels } from '../../utils/chsi.js'
	import {
		YEARS,
		majorsOf,
		modesOf,
		defaultStudyMode,
		findProgram,
		findAdmission,
		findNationLine,
		formatScore,
		admitRate,
		majorByCode
	} from '../../utils/programs.js'
	import { apiEnabled, fetchSchoolDetail } from '../../utils/api.js'
	import { cohortLabel, cohortWindow, filterByCohortWindow, targetCohort } from '../../utils/cohort.js'
	import TrendCharts from '../../components/TrendCharts.vue'
	import EmptyState from '../../components/ui/EmptyState.vue'

	const B_PROVINCES = '内蒙古,广西,海南,贵州,云南,西藏,甘肃,青海,宁夏,新疆'

	export default {
		components: { TrendCharts, EmptyState },
		data() {
			return {
				school: null,
				logoFailed: false,
				majorCode: '',
				studyMode: '',
				year: YEARS[0] || targetCohort(),
				years: YEARS,
				targetCohort: targetCohort(),
				cohortHint: '',
				remoteProgram: null,
				remoteAdmission: null,
				remoteNation: null,
				yearlyTrend: [],
				yearlyData: [],
				majorInfo: null,
				packLoadedKey: '',
				useRemote: false,
				loadSeq: 0,
				syncing: false,
				syncError: '',
				apiMajorCode: ''
			}
		},
		computed: {
			meta() {
				if (!this.school) return ''
				const parts = []
				if (this.school.province) parts.push(this.school.province)
				if (this.school.authority) parts.push(this.school.authority)
				return parts.join(' · ')
			},
			majorOptions() {
				if (!this.school) return []
				return majorsOf(this.school.code)
			},
			modeOptions() {
				if (!this.school || !this.majorCode) return []
				// 全日制永远在非全日制前面
				const modes = modesOf(this.school.code, this.majorCode).slice()
				modes.sort((a, b) => {
					const rank = (id) => (id === 'fulltime' || id === 'full_time' ? 0
						: id === 'parttime' || id === 'part_time' ? 1 : 9)
					return rank(a.id) - rank(b.id)
				})
				return modes
			},
			majorLabel() {
				const m = majorByCode(this.majorCode)
				return m ? m.name : (this.majorCode || '—')
			},
			modeLabel() {
				if (this.studyMode === 'parttime') return '非全日制'
				if (this.studyMode === 'fulltime') return '全日制'
				return '—'
			},
			program() {
				if (this.useRemote && this.remoteProgram) return this.remoteProgram
				if (!this.school || !this.majorCode || !this.studyMode) return null
				return findProgram(this.school.code, this.year, this.majorCode, this.studyMode)
			},
			admission() {
				if (this.useRemote && this.remoteAdmission) return this.remoteAdmission
				if (!this.school || !this.majorCode || !this.studyMode) return null
				return findAdmission(this.school.code, this.year, this.majorCode, this.studyMode)
			},
			nationLine() {
				if (this.useRemote && this.remoteNation) return this.remoteNation
				if (!this.school || !this.majorCode) return null
				return findNationLine(this.majorCode, this.year, this.school.province)
			},
			spotlight() {
				const end = this.targetCohort || targetCohort()
				if (this.nationLine && this.nationLine.total != null) {
					const y = Number(end) || 0
					return { label: y + ' 国家线', value: String(this.nationLine.total) }
				}
				const row = this.scoreRow
				if (row) {
					const v = row.reexam_min_score != null ? row.reexam_min_score
						: (row.reexamMinScore != null ? row.reexamMinScore
							: (row.min_score != null ? row.min_score : row.minScore))
					if (v != null && v !== '') {
						const y = Number(row.year) || 0
						const label = y ? (y + ' 院校分数线') : '院校分数线'
						return { label, value: String(v) }
					}
				}
				const adm = this.admission
				if (adm && adm.reexamMinScore != null && adm.reexamMinScore !== '') {
					return { label: '院校分数线', value: String(adm.reexamMinScore) }
				}
				return { label: end + ' 国家线', value: '—' }
			},
			spotlightSide() {
				const end = this.targetCohort || targetCohort()
				const scoreY = this.scoreRow && this.scoreRow.year ? Number(this.scoreRow.year) : 0
				const yearBit = scoreY && scoreY !== end
					? (end + ' · 数据' + scoreY)
					: String(end)
				return yearBit + ' · ' + this.modeLabel
			},
			latestRow() {
				const rows = this.yearlyData || []
				if (rows.length) {
					const sorted = rows.slice().sort((a, b) => Number(b.year) - Number(a.year))
					return sorted[0]
				}
				return null
			},
			/** 最近一个真正有复试分/录取人数的年份。最新一年经常还没收齐。 */
			scoreRow() {
				const rows = (this.yearlyData || []).slice().sort((a, b) => Number(b.year) - Number(a.year))
				const hit = rows.find((row) => this.rowHasScores(row))
				if (hit) return hit
				const trend = (this.yearlyTrend || []).slice().sort((a, b) => Number(b.year) - Number(a.year))
				const t = trend.find((row) => this.rowHasScores(row))
				if (!t) return null
				return {
					year: t.year,
					reexam_min_score: t.reexamMinScore,
					min_score: t.minScore,
					max_score: t.maxScore,
					admit_count: t.admitCount,
					reexam_count: t.reexamCount,
					retest_rate: t.retestRate,
					score_line_text: t.scoreLineText
				}
			},
			dashReady() {
				return !!this.scoreRow
			},
			dash() {
				const row = this.scoreRow || {}
				const feeRow = this.latestRow || row
				const adm = this.admission || {}
				const prog = this.program || {}
				const info = this.majorInfo || {}
				const scoreLine = this.reexamScoreOnly(row, adm)
				const lowest = this.fmt(row.min_score != null ? row.min_score : adm.minScore)
				const highest = this.fmt(row.max_score != null ? row.max_score : adm.maxScore)
				const retest = this.fmt(row.reexam_count != null ? row.reexam_count : adm.reexamCount)
				const enroll = this.fmt(row.admit_count != null ? row.admit_count : adm.admitCount)
				const rateRaw = row.retest_rate
				const rateNum = this.parseRate(rateRaw, row.reexam_count || adm.reexamCount, row.admit_count || adm.admitCount)
				const rate = rateNum == null ? (rateRaw || '—') : (String(rateRaw).indexOf('%') >= 0 ? String(rateRaw) : rateNum.toFixed(1) + '%')
				let rateTone = ''
				let rateNote = ''
				if (rateNum != null) {
					if (rateNum >= 90) {
						rateTone = 'tone-good'
						rateNote = '过线即录取概率大'
					} else if (rateNum < 75) {
						rateTone = 'tone-heat'
						rateNote = '复试刷人率高'
					} else {
						rateNote = '竞争适中'
					}
				}
				const lineTotal = this.parseScoreTotal(scoreLine)
				const lowestNum = Number(lowest)
				let diffNote = ''
				if (lineTotal != null && !isNaN(lowestNum) && lowestNum > lineTotal) {
					diffNote = '高复试线 ' + (lowestNum - lineTotal) + ' 分'
				}
				const duration = this.displayText(feeRow.duration_text || row.duration_text || prog.durationText || info.duration)
				const tuition = this.displayText(feeRow.tuition_text || row.tuition_text || prog.tuitionText || this.tuitionFromInfo(info))
				return {
					scoreYear: row.year || null,
					scoreLine,
					lowest,
					highest,
					retest,
					enroll,
					rate,
					rateTone,
					rateNote,
					diffNote,
					duration,
					tuition
				}
			},
			cohortRangeHint() {
				const end = this.targetCohort || targetCohort()
				return (end - 4) + '–' + end + ' · 初试为上一年12月 · ' + (end + 1) + '尚未开考'
			},
			tableRows() {
				const end = this.targetCohort || targetCohort()
				const byYear = {}
				const src = (this.yearlyData && this.yearlyData.length)
					? this.yearlyData
					: (this.yearlyTrend || [])
				src.forEach((row) => {
					const y = Number(row.year)
					if (y) byYear[y] = row
				})
				const years = cohortWindow(end)
				return years.map((year) => {
					const row = byYear[year] || { year }
					const retest = row.reexam_count != null ? row.reexam_count : row.reexamCount
					const enroll = row.admit_count != null ? row.admit_count : row.admitCount
					const lowest = row.min_score != null ? row.min_score : row.minScore
					const highest = row.max_score != null ? row.max_score : row.maxScore
					const scoreLine = this.reexamScoreOnly(row, {
						reexamMinScore: row.reexam_min_score != null ? row.reexam_min_score : row.reexamMinScore
					})
					const empty = !byYear[year]
						|| ((retest == null || Number(retest) === 0)
							&& (enroll == null || Number(enroll) === 0)
							&& lowest == null
							&& highest == null
							&& (scoreLine === '—' || scoreLine === '暂无' || scoreLine === '暂无官方数据'
								|| String(scoreLine).indexOf('暂无') >= 0))
					const rateNum = this.parseRate(row.retest_rate || row.retestRate, retest, enroll)
					const rate = rateNum == null
						? (row.retest_rate || row.retestRate || '—')
						: (String(row.retest_rate || '').indexOf('%') >= 0 ? String(row.retest_rate) : rateNum.toFixed(1) + '%')
					const lineTotal = this.parseScoreTotal(scoreLine)
					return {
						year,
						yearLabel: cohortLabel(year),
						empty,
						scoreLine,
						retest: this.fmt(retest),
						enroll: this.fmt(enroll),
						rate: empty ? '—' : rate,
						lowest: this.fmt(lowest),
						highest: this.fmt(highest),
						rateClass: rateNum != null && rateNum < 75 ? 'tone-heat' : 'tone-good',
						lowestClass: (lineTotal != null && lowest != null && Number(lowest) > lineTotal) ? 'tone-heat' : ''
					}
				})
			},
			aiAdvice() {
				if (!this.dashReady) return ''
				const d = this.dash
				const rateNum = this.parseRate(d.rate)
				const lineTotal = this.parseScoreTotal(d.scoreLine)
				const lowest = Number(d.lowest)
				const highest = Number(d.highest)
				const mode = this.modeLabel
				const major = this.majorLabel
				if (rateNum == null && (d.scoreLine === '—' || !d.scoreLine)) return ''
				if (rateNum != null && rateNum < 75) {
					const diff = (!isNaN(lowest) && lineTotal != null) ? (lowest - lineTotal) : null
					const target = !isNaN(highest) ? (highest - 10) : null
					return major + '（' + mode + '）竞争激烈，复试线 ' + d.scoreLine
						+ (diff != null ? '，录取最低分高出 ' + diff + ' 分' : '')
						+ '。复试淘汰率约 ' + (100 - rateNum).toFixed(1) + '%'
						+ (target != null ? '，建议初试目标定在 ' + target + ' 分以上。' : '。')
				}
				if (rateNum != null && rateNum >= 90) {
					const target = lineTotal != null ? (lineTotal + 15) : null
					return major + '（' + mode + '）复试录取率达 ' + d.rate + '，过线录取概率高。复试线 '
						+ d.scoreLine
						+ (target != null ? '，建议初试目标定在 ' + target + ' 分以上。' : '。')
				}
				const target = !isNaN(lowest) ? (lowest + 10) : (lineTotal != null ? lineTotal + 15 : null)
				return major + '（' + mode + '）竞争适中。复试线 ' + d.scoreLine
					+ '，录取最低 ' + d.lowest + '。录取率 ' + d.rate
					+ (target != null ? '，建议初试目标定在 ' + target + ' 分以上。' : '。')
			},
			hasTrend() {
				return (this.yearlyTrend || []).some((p) =>
					p && (p.reexamMinScore != null || p.minScore != null || p.admitCount != null || p.reexamCount != null)
				)
			},
			dataSparse() {
				if (this.scoreRow) return false
				return !this.hasTrend
			},
			canManualEnrich() {
				return false
			},
			syncText() {
				if (this.syncing) return '正在读取招录数据…'
				if (this.syncError) return this.syncError
				if (this.dataSparse) return '库里还没有这一年的招录数字。'
				return ''
			}
		},
		onLoad(query) {
			this.school = findSchool(query.id)
			if (!this.school) return
			const majors = majorsOf(this.school.code)
			const raw = query.major || ''
			const display = raw.indexOf('1256') === 0 ? '1256' : raw
			const wanted = display && majors.some((item) => item.code === display)
				? display
				: (majors[0] && majors[0].code)
			this.majorCode = wanted || ''
			this.apiMajorCode = raw && raw !== this.majorCode ? raw : ''
			const modes = modesOf(this.school.code, this.majorCode)
			if (query.mode === 'fulltime' || query.mode === 'parttime') {
				this.studyMode = query.mode
			} else {
				this.studyMode = defaultStudyMode(modes)
			}
			this.loadRemote()
		},
		methods: {
			labels: schoolLabels,
			formatScore,
			admitRate,
			rowHasScores(row) {
				if (!row) return false
				const has = (v) => v != null && v !== '' && v !== '暂无'
				return has(row.reexam_min_score) || has(row.reexamMinScore)
					|| has(row.score_line_text) || has(row.scoreLineText)
					|| has(row.admit_count) || has(row.admitCount)
					|| has(row.min_score) || has(row.minScore)
					|| has(row.reexam_count) || has(row.reexamCount)
			},
			fmt(v) {
				if (v == null || v === '' || v === 'null') return '—'
				return String(v)
			},
			displayText(v) {
				if (v == null || v === '' || v === 'null') return '待同步'
				return String(v)
			},
			tuitionFromInfo(info) {
				if (!info) return ''
				const t = info.tuition || {}
				const per = t.per_year || info.tuition_per_year
				const total = t.total || info.tuition_total
				if (per && total) return per + '/年，全程 ' + total
				return per || total || info.tuition_text || ''
			},
			composeScoreLine(row, adm) {
				return this.reexamScoreOnly(row, adm)
			},
			/** 复试线只展示总分，不带英/综 */
			reexamScoreOnly(row, adm) {
				row = row || {}
				adm = adm || {}
				const total = row.reexam_min_score != null ? row.reexam_min_score
					: (adm.reexamMinScore != null ? adm.reexamMinScore : null)
				if (total != null && total !== '') return String(total)
				const text = row.score_line_text || row.scoreLineText
				if (text != null && text !== '') {
					const n = this.parseScoreTotal(text)
					return n != null ? String(n) : '—'
				}
				return '—'
			},
			parseScoreTotal(scoreLine) {
				if (scoreLine == null || scoreLine === '—') return null
				const s = String(scoreLine).split(/[/／]/)[0]
				const n = parseInt(s.replace(/[^\d]/g, ''), 10)
				return isNaN(n) ? null : n
			},
			parseRate(raw, retest, enroll) {
				if (raw != null && raw !== '') {
					const n = parseFloat(String(raw).replace('%', ''))
					if (!isNaN(n)) return n
				}
				const r = Number(retest)
				const e = Number(enroll)
				if (r > 0 && !isNaN(e)) return Math.round((e * 1000) / r) / 10
				return null
			},
			failLogo() {
				this.logoFailed = true
			},
			resetPack() {
				this.packLoadedKey = ''
				this.yearlyData = []
				this.yearlyTrend = []
				this.remoteProgram = null
				this.remoteAdmission = null
				this.remoteNation = null
				this.useRemote = false
				this.syncError = ''
				this.apiMajorCode = ''
			},
			pickMajor(code) {
				if (this.majorCode === code) return
				this.majorCode = code
				const modes = modesOf(this.school.code, code)
				const keep = modes.some((item) => item.id === this.studyMode)
				this.studyMode = keep ? this.studyMode : defaultStudyMode(modes)
				this.resetPack()
				this.loadRemote()
			},
			setMode(id) {
				if (this.studyMode === id) return
				this.studyMode = id
				this.resetPack()
				this.loadRemote()
			},
			packKey() {
				if (!this.school || !this.majorCode || !this.studyMode) return ''
				return this.school.code + '_' + this.majorCode + '_' + this.studyMode
			},
			textOrNull(v) {
				if (v == null || v === '' || v === 'null') return null
				return String(v)
			},
			applyYearLocal() {
				const rows = this.yearlyData || []
				if (!rows.length) return false
				const scoreHit = rows.slice().sort((a, b) => Number(b.year) - Number(a.year))
					.find((item) => this.rowHasScores(item))
				const newest = rows.slice().sort((a, b) => Number(b.year) - Number(a.year))[0]
				const row = scoreHit || newest
				if (!row || !row.year) return false
				this.year = Number(row.year)
				const fee = newest || row
				const modeLabel = this.studyMode === 'parttime' ? '非全日制' : '全日制'
				const info = this.majorInfo || {}
				const tuitionObj = info.tuition || {}
				let tuitionText = this.textOrNull(fee.tuition_text || fee.tuitionText || row.tuition_text || row.tuitionText)
				if (!tuitionText) {
					const per = this.textOrNull(tuitionObj.per_year || info.tuition_per_year)
					const total = this.textOrNull(tuitionObj.total || info.tuition_total)
					if (per && total) tuitionText = per + '/年，全程 ' + total
					else tuitionText = per || total || this.textOrNull(info.tuition_text)
				}
				this.remoteProgram = {
					year: fee.year || row.year,
					studyModeLabel: modeLabel,
					tuitionText,
					planText: this.textOrNull(fee.plan_text || fee.planText || row.plan_text || row.planText || info.plan_text),
					durationText: this.textOrNull(fee.duration_text || fee.durationText || row.duration_text || row.durationText || info.duration),
					sourceUrl: row.program_source_url || '',
					sourceName: row.program_source_name || ''
				}
				this.remoteAdmission = {
					year: row.year,
					reexamMinScore: row.reexam_min_score != null ? row.reexam_min_score : row.reexamMinScore,
					minScore: row.min_score != null ? row.min_score : row.minScore,
					maxScore: row.max_score != null ? row.max_score : row.maxScore,
					admitCount: row.admit_count != null ? row.admit_count : row.admitCount,
					reexamCount: row.reexam_count != null ? row.reexam_count : row.reexamCount,
					scoreBands: row.score_bands || row.scoreBands || [],
					sourceUrl: row.admission_source_url || '',
					sourceName: row.admission_source_name || '',
					pendingNote: row.pending_note || row.note || null
				}
				const isB = B_PROVINCES.indexOf(this.school.province || '') >= 0
				const nationSrc = newest || row
				const total = isB ? nationSrc.nation_b_total : nationSrc.nation_a_total
				const english = isB ? nationSrc.nation_b_english : nationSrc.nation_a_english
				const comprehensive = isB ? nationSrc.nation_b_comprehensive : nationSrc.nation_a_comprehensive
				if (total != null) {
					this.remoteNation = {
						text: total + '（' + (isB ? 'B' : 'A') + '类）',
						detail: '英' + english + ' / 综' + comprehensive,
						total,
						sourceUrl: nationSrc.nation_source_url || '',
						sourceName: nationSrc.nation_source_name || '国家线'
					}
				} else {
					this.remoteNation = null
				}
				this.useRemote = true
				return true
			},
			applyPayload(data) {
				const end = data.targetCohort || targetCohort()
				this.targetCohort = end
				this.cohortHint = data.cohortHint || ''
				this.yearlyTrend = filterByCohortWindow(
					Array.isArray(data.yearlyTrend) ? data.yearlyTrend : [],
					end
				)
				this.yearlyData = filterByCohortWindow(
					Array.isArray(data.yearlyData) ? data.yearlyData : [],
					end
				)
				this.majorInfo = data.majorInfo || data.major_info || null
				if (Array.isArray(data.years) && data.years.length) this.years = data.years
				else this.years = YEARS
				const fromPack = this.applyYearLocal()
				if (data.program) this.remoteProgram = data.program
				else if (!fromPack) this.remoteProgram = null
				if (data.admission && (data.admission.reexamMinScore != null || data.admission.minScore != null || data.admission.admitCount != null)) {
					this.remoteAdmission = data.admission
				}
				if (data.nationLine) this.remoteNation = data.nationLine
				this.useRemote = true
			},
			/** 只读数据库里的招录，不再联网补数 */
			async loadRemote() {
				const reqMajor = this.apiMajorCode || this.majorCode
				if (!apiEnabled() || !this.school || !reqMajor || !this.studyMode) return
				const key = this.packKey()
				if (this.syncing) return
				const reqMode = this.studyMode
				const seq = this.loadSeq + 1
				this.loadSeq = seq
				this.syncing = true
				this.syncError = ''
				try {
					const data = await fetchSchoolDetail({
						schoolCode: this.school.code,
						year: this.year,
						majorCode: reqMajor,
						studyMode: reqMode,
						province: this.school.province || ''
					})
					if (seq !== this.loadSeq) return
					if ((this.apiMajorCode || this.majorCode) !== reqMajor || this.studyMode !== reqMode) return

					this.applyPayload(data)
					this.packLoadedKey = key
					this.syncError = ''
				} catch (e) {
					if (seq === this.loadSeq) {
						const msg = (e && (e.errMsg || e.message)) || '网络错误'
						this.syncError = '读取失败：' + msg
						this.useRemote = false
					}
				} finally {
					if (seq === this.loadSeq) {
						this.syncing = false
					}
				}
			}
		}
	}
</script>

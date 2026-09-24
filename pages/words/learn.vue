<template>
	<view class="edu-page edu-wl">
		<!-- 选词书：学习仪表盘（无奖励计划） -->
		<block v-if="phase === 'pick'">
			<view class="edu-wl-dash" :class="{ cold: streak <= 0 }">
				<view class="edu-wl-dash-orb" />
				<view class="edu-wl-dash-top">
					<view class="edu-wl-streak" @tap="openCal">
						<text class="edu-wl-streak-t">{{ streakLabel }}</text>
					</view>
					<text class="edu-wl-dash-goal" @tap="showGoal = true">今日目标 {{ dailyTarget }} 词 ›</text>
				</view>
				<view class="edu-wl-dash-main3">
					<view class="edu-wl-dash-cell left">
						<text class="edu-wl-dash-cell-n">{{ fmtMin(todaySec) }}</text>
						<text class="edu-wl-dash-cell-l">今日专注</text>
					</view>
					<view class="edu-wl-dash-cell">
						<text class="edu-wl-dash-cell-n">{{ todayWords }}</text>
						<text class="edu-wl-dash-cell-l">今日单词</text>
					</view>
					<view class="edu-wl-dash-cell right">
						<text class="edu-wl-dash-cell-n">{{ goalPct }}%</text>
						<text class="edu-wl-dash-cell-l">目标进度</text>
					</view>
				</view>
				<view class="edu-wl-dash-motto">{{ motto }}</view>
				<view class="edu-wl-dash-cta" @tap="continueOrHint">
					{{ ctaText }}
				</view>
			</view>

			<view class="edu-wl-soft" v-if="missedYesterday">
				<text class="edu-wl-soft-t">昨天没打卡也没关系，今日复习会自动少推 5 个新词，先把节奏捡回来。</text>
			</view>

			<!-- 我的词书（当前） -->
			<view class="edu-section-head" v-if="lastBookMeta">
				<view class="edu-section-bar tone-signal" />
				<text class="edu-section-title">我的词书</text>
				<text class="edu-section-hint">一键继续</text>
			</view>
			<view class="edu-wl-books" v-if="lastBookMeta">
				<view
					class="edu-wl-book-card current"
					:class="'tone-' + bookTone(lastBookMeta.book)"
					@tap="pickBook(lastBookMeta)"
				>
					<text class="edu-wl-book-mark" :class="'tone-' + bookTone(lastBookMeta.book)">{{ bookMark(lastBookMeta.book) }}</text>
					<view class="edu-wl-book-body">
						<view class="edu-wl-book-title-row">
							<text class="edu-wl-book-title">{{ lastBookMeta.name }}</text>
							<text class="edu-wl-book-badge">当前</text>
						</view>
						<text class="edu-wl-book-sub">已掌握 {{ bookKnow(lastBookMeta.book) }} / {{ lastBookMeta.total }}</text>
						<view class="edu-wl-book-track">
							<view class="edu-wl-book-fill" :style="{ width: bookPct(lastBookMeta) + '%' }" />
						</view>
					</view>
					<text class="edu-wl-book-arrow">›</text>
				</view>
			</view>

			<view class="edu-section-head">
				<view class="edu-section-bar tone-good" />
				<text class="edu-section-title">国内考试</text>
				<text class="edu-section-hint">点卡片切换</text>
			</view>
			<view class="edu-wl-books">
				<view
					class="edu-wl-book-card"
					v-for="b in domesticBooks"
					:key="b.book"
					:class="['tone-' + bookTone(b.book), { current: lastBook === b.book, done: bookPct(b) >= 100 }]"
					@tap="pickBook(b)"
					@longpress="previewBook(b)"
				>
					<text class="edu-wl-book-mark" :class="'tone-' + bookTone(b.book)">{{ bookMark(b.book) }}</text>
					<view class="edu-wl-book-body">
						<view class="edu-wl-book-title-row">
							<text class="edu-wl-book-title">{{ b.name }}</text>
							<text class="edu-wl-book-badge" v-if="lastBook === b.book">当前</text>
							<text class="edu-wl-book-done" v-else-if="bookPct(b) >= 100">Done</text>
						</view>
						<text class="edu-wl-book-sub">已掌握 {{ bookKnow(b.book) }} / {{ b.total }}</text>
						<view class="edu-wl-book-track">
							<view class="edu-wl-book-fill" :style="{ width: bookPct(b) + '%' }" />
						</view>
					</view>
					<text class="edu-wl-book-arrow">›</text>
				</view>
			</view>

			<view class="edu-section-head" v-if="overseasBooks.length" @tap="overseasOpen = !overseasOpen">
				<view class="edu-section-bar tone-signal" />
				<text class="edu-section-title">出国考试</text>
				<text class="edu-section-hint">{{ overseasOpen ? '收起' : '展开 ' + overseasBooks.length + ' 本' }}</text>
			</view>
			<view class="edu-wl-books" v-if="overseasBooks.length && overseasOpen">
				<view
					class="edu-wl-book-card"
					v-for="b in overseasBooks"
					:key="b.book"
					:class="['tone-' + bookTone(b.book), { current: lastBook === b.book, done: bookPct(b) >= 100 }]"
					@tap="pickBook(b)"
					@longpress="previewBook(b)"
				>
					<text class="edu-wl-book-mark" :class="'tone-' + bookTone(b.book)">{{ bookMark(b.book) }}</text>
					<view class="edu-wl-book-body">
						<view class="edu-wl-book-title-row">
							<text class="edu-wl-book-title">{{ b.name }}</text>
							<text class="edu-wl-book-badge" v-if="lastBook === b.book">当前</text>
							<text class="edu-wl-book-done" v-else-if="bookPct(b) >= 100">Done</text>
						</view>
						<text class="edu-wl-book-sub">已掌握 {{ bookKnow(b.book) }} / {{ b.total }}</text>
						<view class="edu-wl-book-track">
							<view class="edu-wl-book-fill" :style="{ width: bookPct(b) + '%' }" />
						</view>
					</view>
					<text class="edu-wl-book-arrow">›</text>
				</view>
			</view>

			<view class="edu-card" v-if="myTeams.length">
				<text class="edu-card-title">组队打卡</text>
				<view class="edu-wl-team" v-for="t in myTeams" :key="t.id">
					<view class="edu-wl-team-copy">
						<text class="edu-wl-team-name">{{ t.name }}</text>
						<text class="edu-muted">连续 {{ t.streak }} 天 · 目标 {{ t.dailyTarget }} 词/日</text>
					</view>
					<view
						class="edu-chip"
						:class="{ on: checkinDone[t.id] }"
						@tap="doCheckin(t)"
					>{{ checkinDone[t.id] ? '已打卡' : '打卡' }}</view>
				</view>
			</view>

			<!-- 目标设定 -->
			<view class="edu-wl-mask" v-if="showGoal" @tap="showGoal = false">
				<view class="edu-wl-dialog" @tap.stop>
					<text class="edu-wl-dialog-t">每日单词目标</text>
					<text class="edu-muted">推荐 30 / 50 / 100，也可自定义</text>
					<view class="edu-wl-targets">
						<view class="edu-chip" :class="{ on: !goalCustom && draftTarget === 30 }" @tap="pickTarget(30)">30</view>
						<view class="edu-chip" :class="{ on: !goalCustom && draftTarget === 50 }" @tap="pickTarget(50)">50</view>
						<view class="edu-chip" :class="{ on: !goalCustom && draftTarget === 100 }" @tap="pickTarget(100)">100</view>
						<view class="edu-chip" :class="{ on: goalCustom }" @tap="pickCustom">自定义</view>
					</view>
					<view class="edu-wl-custom" v-if="goalCustom">
						<input
							class="edu-wl-custom-input"
							type="number"
							v-model="customInput"
							placeholder="输入 5～500"
							maxlength="3"
						/>
						<text class="edu-muted">词 / 日</text>
					</view>
					<view class="edu-wl-dialog-ok" @tap="saveTarget">确定</view>
				</view>
			</view>

			<!-- 打卡热力月历 -->
			<view class="edu-wl-mask" v-if="showCal" @tap="showCal = false">
				<view class="edu-wl-dialog edu-wl-cal-dialog" @tap.stop>
					<view class="edu-wl-cal-h">
						<text class="edu-wl-dialog-t">学习热力</text>
						<view class="edu-wl-cal-navs">
							<text class="edu-wl-cal-nav" @tap="shiftCal(-1)">‹</text>
							<text class="edu-wl-cal-m">{{ calMonth }}</text>
							<text class="edu-wl-cal-nav" @tap="shiftCal(1)">›</text>
						</view>
					</view>
					<view class="edu-wl-cal-grid">
						<text class="edu-wl-cal-w" v-for="w in ['一','二','三','四','五','六','日']" :key="w">{{ w }}</text>
						<view class="edu-wl-cal-blank" v-for="n in calBlank" :key="'b'+n" />
						<view
							class="edu-wl-cal-day"
							:class="'lv' + (calLevels[day] || 0)"
							v-for="day in calDays"
							:key="'d'+day"
						>
							<text class="edu-wl-cal-n">{{ day }}</text>
						</view>
					</view>
					<text class="edu-muted edu-wl-cal-tip">颜色越深，当天背得越多 · 连续 {{ streak }} 天</text>
				</view>
			</view>

			<!-- 词书预览 -->
			<view class="edu-wl-mask" v-if="preview" @tap="preview = null">
				<view class="edu-wl-dialog" @tap.stop>
					<text class="edu-wl-dialog-t">{{ preview.name }} · 词例</text>
					<view class="edu-kv" v-for="(w, i) in preview.words" :key="i">
						<text class="edu-kv-k">{{ w.word }}</text>
						<text class="edu-kv-v">{{ w.meaningCn || '—' }}</text>
					</view>
					<view class="edu-wl-dialog-ok" @tap="pickBook(preview.bookMeta); preview = null">开始背这本</view>
				</view>
			</view>
		</block>

		<!-- 背单词页 -->
		<block v-else>
			<view class="edu-wl-learn-top">
				<view class="edu-chip" @tap="backToBooks">‹ 词书</view>
				<text class="edu-wl-learn-book">{{ curBookName }}</text>
				<text class="edu-wl-learn-goal">{{ todayWords }}/{{ dailyTarget }}</text>
			</view>

			<view class="edu-wl-progress">
				<text class="edu-wl-progress-t">{{ answeredCount }} / {{ total }}</text>
				<progress
					class="edu-wl-bar"
					:percent="total ? Math.round(answeredCount / total * 100) : 0"
					stroke-width="4"
					activeColor="#2F6FED"
					backgroundColor="#E3EAF6"
				/>
			</view>

			<view class="edu-wl-switch-row edu-wl-switch-compact" @tap="toggleShowCn">
				<view class="edu-wl-switch-copy">
					<text class="edu-wl-switch-title">中文释义</text>
					<text class="edu-wl-switch-sub">{{ showCn ? '已打开：释义与造句中文' : '已关闭：点卡片可偷看' }}</text>
				</view>
				<view class="edu-wl-switch" :class="{ on: showCn }">
					<view class="edu-wl-switch-knob" />
				</view>
			</view>

			<view class="edu-card edu-wl-card" v-if="cur">
				<text class="edu-wl-word">{{ cur.word }}</text>
				<text class="edu-wl-phonetic">/{{ cur.phonetic || cur.phoneticUs || '—' }}/</text>

				<view class="edu-wl-audio-row">
					<view class="edu-chip" :class="{ on: playing === 1 }" @tap="playAudio(1)">🔊 英音</view>
					<view class="edu-chip" :class="{ on: playing === 0 }" @tap="playAudio(0)">🔊 美音</view>
				</view>

				<view class="edu-wl-reveal" v-if="showMeaning" @tap="onCardTap">
					<view class="edu-wl-block">
						<text class="edu-wl-block-k">释义</text>
						<text class="edu-wl-pos" v-if="cur.pos">{{ cur.pos }}</text>
						<text class="edu-wl-cn">{{ cur.meaningCn || '暂无释义' }}</text>
					</view>
					<view class="edu-wl-block">
						<text class="edu-wl-block-k">造句</text>
						<template v-if="sentenceEn">
							<text class="edu-wl-sentence-en">{{ sentenceEn }}</text>
							<text class="edu-wl-sentence-cn" v-if="sentenceCn">{{ sentenceCn }}</text>
						</template>
						<text class="edu-muted" v-else>暂无例句</text>
					</view>
				</view>
				<view class="edu-wl-hint" v-else @tap="onCardTap">先想词义，再选下方掌握程度；点这里可偷看释义与造句</view>
			</view>

			<view class="edu-card edu-wl-empty" v-else>
				<text class="edu-card-title">本组已刷完</text>
				<text class="edu-muted">不认识的词会优先再出现。也可换一本词书继续。</text>
				<view class="edu-wl-empty-actions">
					<view class="edu-chip on" @tap="backToBooks">换词书</view>
				</view>
			</view>

			<view class="edu-wl-dock" v-if="cur">
				<view class="edu-wl-dock-item" @tap="choose('unknown')">
					<view class="edu-wl-dock-dot tone-heat" />
					<text class="edu-wl-dock-v tone-heat">不认识</text>
					<text class="edu-wl-dock-k">稍后重现</text>
				</view>
				<view class="edu-wl-dock-split" />
				<view class="edu-wl-dock-item" @tap="choose('vague')">
					<view class="edu-wl-dock-dot tone-signal" />
					<text class="edu-wl-dock-v tone-signal">模糊</text>
					<text class="edu-wl-dock-k">再巩固</text>
				</view>
				<view class="edu-wl-dock-split" />
				<view class="edu-wl-dock-item" @tap="choose('know')">
					<view class="edu-wl-dock-dot tone-good" />
					<text class="edu-wl-dock-v tone-good">认识</text>
					<text class="edu-wl-dock-k">已掌握</text>
				</view>
			</view>

			<view class="edu-foot">学习时长自动累计，切后台/离开页面自动上报</view>
		</block>
	</view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { onShow, onHide, onUnload } from '@dcloudio/uni-app'
import { wordApi, deviceId } from '../../utils/word-api.js'

const MOTTOS = [
	'Small steps every day.',
	'Consistency beats intensity.',
	'One word closer to the exam.',
	'Focus beats scrolling.'
]

const phase = ref('pick')
const total = ref(0)
const todaySec = ref(0)
const todayWords = ref(0)
const streak = ref(0)
const PRESET_GOALS = [30, 50, 100]
const dailyTarget = ref(Number(uni.getStorageSync('word_daily_target')) || 30)
const goalPct = ref(0)
const missedYesterday = ref(false)
const items = ref([])
const idx = ref(0)
const peeked = ref(false)
const showCn = ref(uni.getStorageSync('word_show_cn') === true)
const grades = ref({})
const myTeams = ref([])
const checkinDone = ref({})
const lastBook = ref(uni.getStorageSync('word_last_book') || '')
const overseasOpen = ref(false)
const showGoal = ref(false)
const draftTarget = ref(dailyTarget.value)
const goalCustom = ref(!PRESET_GOALS.includes(dailyTarget.value))
const customInput = ref(String(dailyTarget.value))
const showCal = ref(false)
const calMonth = ref('')
const calDays = ref(0)
const calBlank = ref(0)
const calLevels = ref({})
const preview = ref(null)
const booksLoading = ref(true)

const cur = computed(() => items.value[idx.value] || null)
const showMeaning = computed(() => showCn.value || peeked.value)

const books = ref([])
const curBook = ref('ky')
const curBookName = computed(() => {
	const hit = books.value.find((b) => b.book === curBook.value)
	return (hit && hit.name) || '考研词汇'
})
const lastBookMeta = computed(() => books.value.find((b) => b.book === lastBook.value) || null)

const DOMESTIC = { ky: true, cet4: true, cet6: true, gk: true }
const domesticBooks = computed(() => books.value.filter((b) => DOMESTIC[b.book]))
const overseasBooks = computed(() => books.value.filter((b) => !DOMESTIC[b.book]))

const knowCount = computed(() => {
	return Object.keys(grades.value).filter((id) => grades.value[id] && grades.value[id].grade === 'know').length
})
const answeredCount = computed(() => Object.keys(grades.value).length)

const sentenceEn = computed(() => {
	if (!cur.value) return ''
	return String(cur.value.sentenceEn || '').trim() || buildFallbackSentence(cur.value)
})
const sentenceCn = computed(() => {
	if (!cur.value) return ''
	const cn = String(cur.value.sentenceCn || '').trim()
	if (cn) return cn
	if (cur.value.sentenceEn) return ''
	return buildFallbackSentenceCn(cur.value)
})

const streakLabel = computed(() => {
	if (streak.value > 0) return '连续打卡 ' + streak.value + ' 天'
	return '今天开启打卡'
})

const motto = computed(() => MOTTOS[Math.min(streak.value, MOTTOS.length - 1)] || MOTTOS[0])

const ctaText = computed(() => {
	if (!lastBookMeta.value) return '选一本词书开始'
	const left = Math.max(0, dailyTarget.value - todayWords.value)
	if (left <= 0) return '目标已完成 · 再练一会'
	return '继续背词 · 还差 ' + left + ' 词'
})

const BOOK_MARK = {
	ky: '研', cet4: '四', cet6: '六', gk: '高', ielts: '雅', toefl: '托', gre: 'G'
}
const BOOK_TONE = {
	ky: 'good', cet4: 'signal', cet6: 'heat', gk: 'good',
	ielts: 'signal', toefl: 'heat', gre: 'signal'
}

function bookMark(code) {
	return BOOK_MARK[code] || '词'
}
function bookTone(code) {
	return BOOK_TONE[code] || 'signal'
}
function bookKnow(book) {
	const g = uni.getStorageSync('word_grades_' + book) || {}
	return Object.keys(g).filter((id) => g[id] && g[id].grade === 'know').length
}
function bookPct(b) {
	const n = Number(b.total) || 0
	if (!n) return 0
	return Math.min(100, Math.round(bookKnow(b.book) / n * 100))
}

function buildFallbackSentence(w) {
	const word = w && w.word ? w.word : ''
	if (!word) return ''
	return 'I often use the word "' + word + '" when I talk about this topic.'
}
function buildFallbackSentenceCn(w) {
	const word = w && w.word ? w.word : ''
	const gloss = String((w && w.meaningCn) || '').split(/[；;\n]/)[0].trim()
	if (!word) return ''
	if (gloss) return '谈到这个话题时，我常用单词「' + word + '」（' + gloss + '）。'
	return '谈到这个话题时，我常用单词「' + word + '」。'
}

function gradeKey() {
	return 'word_grades_' + curBook.value
}
function loadGrades() {
	grades.value = uni.getStorageSync(gradeKey()) || {}
}
function saveGrades() {
	uni.setStorageSync(gradeKey(), grades.value)
}

function toggleShowCn() {
	showCn.value = !showCn.value
	uni.setStorageSync('word_show_cn', showCn.value)
	if (showCn.value) peeked.value = false
}
function onCardTap() {
	if (showCn.value) return
	peeked.value = !peeked.value
}

async function loadBooks() {
	booksLoading.value = true
	try {
		books.value = await wordApi.books()
	} catch (e) {
		uni.showToast({ title: '词书加载失败', icon: 'none' })
	} finally {
		booksLoading.value = false
	}
}

function continueOrHint() {
	if (lastBookMeta.value) {
		pickBook(lastBookMeta.value)
		return
	}
	uni.showToast({ title: '先点下方词书卡片', icon: 'none' })
}

function pickBook(b) {
	if (!b || !b.book) return
	curBook.value = b.book
	lastBook.value = b.book
	uni.setStorageSync('word_last_book', b.book)
	phase.value = 'learn'
	uni.setNavigationBarTitle({ title: b.name || '背单词' })
	peeked.value = false
	loadBatch()
	startTimer()
}

function backToBooks() {
	stopTimer()
	phase.value = 'pick'
	uni.setNavigationBarTitle({ title: '背单词' })
	items.value = []
	idx.value = 0
	peeked.value = false
	refreshStats()
}

function pickTarget(n) {
	goalCustom.value = false
	draftTarget.value = n
	customInput.value = String(n)
}
function pickCustom() {
	goalCustom.value = true
	if (PRESET_GOALS.includes(draftTarget.value)) {
		customInput.value = String(draftTarget.value)
	} else {
		customInput.value = String(draftTarget.value || 80)
	}
}
async function saveTarget() {
	let n = draftTarget.value
	if (goalCustom.value) {
		n = parseInt(String(customInput.value).trim(), 10)
		if (!Number.isFinite(n) || n < 5 || n > 500) {
			uni.showToast({ title: '请输入 5～500 的整数', icon: 'none' })
			return
		}
	}
	draftTarget.value = n
	dailyTarget.value = n
	goalCustom.value = !PRESET_GOALS.includes(n)
	uni.setStorageSync('word_daily_target', n)
	showGoal.value = false
	try {
		const res = await wordApi.setPref(n)
		if (res && res.ok === false) {
			uni.showToast({ title: res.error || '保存失败', icon: 'none' })
			return
		}
	} catch (e) {
		uni.showToast({ title: '保存失败', icon: 'none' })
	}
	refreshStats()
}

async function openCal() {
	showCal.value = true
	const now = new Date()
	calMonth.value = now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0')
	await loadCal()
}
function shiftCal(delta) {
	const [y, m] = calMonth.value.split('-').map(Number)
	const d = new Date(y, m - 1 + delta, 1)
	calMonth.value = d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0')
	loadCal()
}
async function loadCal() {
	try {
		const res = await wordApi.studyCalendar(calMonth.value)
		calDays.value = res.daysInMonth || 30
		calBlank.value = Math.max(0, (res.firstWeekday || 1) - 1)
		const levels = {}
		;(res.days || []).forEach((d) => {
			levels[d.day] = d.level || 0
		})
		calLevels.value = levels
		if (typeof res.streak === 'number') streak.value = res.streak
	} catch (e) {
		uni.showToast({ title: '日历加载失败', icon: 'none' })
	}
}

async function previewBook(b) {
	try {
		const res = await wordApi.list(1, 3, b.book)
		preview.value = {
			name: b.name,
			bookMeta: b,
			words: (res.items || []).slice(0, 3)
		}
	} catch (e) {
		uni.showToast({ title: '预览失败', icon: 'none' })
	}
}

let startAt = null
let pendingSec = 0
let reportTimer = null

function flushReport() {
	if (pendingSec <= 0) return
	const sec = pendingSec
	pendingSec = 0
	wordApi.report(sec, 0).catch(() => { pendingSec += sec })
}
function startTimer() {
	if (phase.value !== 'learn') return
	startAt = Date.now()
	if (!reportTimer) {
		reportTimer = setInterval(() => {
			if (phase.value !== 'learn') return
			if (startAt) pendingSec += Math.floor((Date.now() - startAt) / 1000)
			startAt = Date.now()
			flushReport()
		}, 30000)
	}
}
function stopTimer() {
	if (startAt) {
		pendingSec += Math.floor((Date.now() - startAt) / 1000)
		startAt = null
	}
	if (reportTimer) {
		clearInterval(reportTimer)
		reportTimer = null
	}
	flushReport()
}

function gradeOf(id) {
	const g = grades.value[String(id)]
	return g ? g.grade : ''
}
function pickNextIndex(from) {
	const list = items.value
	if (!list.length) return -1
	const order = ['', 'unknown', 'vague']
	for (let o = 0; o < order.length; o += 1) {
		const want = order[o]
		for (let i = 1; i <= list.length; i += 1) {
			const j = (from + i) % list.length
			if (gradeOf(list[j].id) === want) return j
		}
	}
	return -1
}

async function loadBatch() {
	loadGrades()
	const saved = uni.getStorageSync('word_learn_pos_' + curBook.value) || { idx: 0 }
	try {
		const res = await wordApi.list(1, 50, curBook.value)
		total.value = res.total || 0
		items.value = res.items || []
		const start = Math.min(saved.idx || 0, Math.max((res.items || []).length - 1, 0))
		const next = pickNextIndex(start - 1)
		idx.value = next >= 0 ? next : (res.items || []).length
		peeked.value = false
	} catch (e) {
		uni.showToast({ title: '词库加载失败', icon: 'none' })
	}
}

function savePos() {
	uni.setStorageSync('word_learn_pos_' + curBook.value, { idx: idx.value })
}

function choose(grade) {
	if (!cur.value) return
	const id = String(cur.value.id)
	grades.value = {
		...grades.value,
		[id]: { grade, at: Date.now(), word: cur.value.word }
	}
	saveGrades()
	todayWords.value += 1
	goalPct.value = dailyTarget.value
		? Math.min(100, Math.round(todayWords.value * 100 / dailyTarget.value))
		: 0
	wordApi.report(0, 1).then((res) => {
		if (res && typeof res.streak === 'number') streak.value = res.streak
	}).catch(() => {})
	const next = pickNextIndex(idx.value)
	idx.value = next >= 0 ? next : items.value.length
	peeked.value = false
	savePos()
}

let audioCtx = null
const playing = ref(-1)

function playAudio(type) {
	const word = cur.value && cur.value.word
	if (!word) {
		uni.showToast({ title: '暂无单词', icon: 'none' })
		return
	}
	const url = wordApi.audioUrl(word, type)
	playing.value = type

	if (audioCtx) {
		try { audioCtx.stop() } catch (e) {}
		try { audioCtx.destroy() } catch (e) {}
		audioCtx = null
	}

	audioCtx = uni.createInnerAudioContext()
	audioCtx.obeyMuteSwitch = false
	audioCtx.onPlay(() => { playing.value = type })
	audioCtx.onEnded(() => { playing.value = -1 })
	audioCtx.onStop(() => { playing.value = -1 })
	audioCtx.onError((err) => {
		playing.value = -1
		const msg = (err && (err.errMsg || err.message)) || '发音加载失败'
		uni.showToast({ title: String(msg).slice(0, 40) || '发音加载失败', icon: 'none' })
	})
	audioCtx.src = url
	audioCtx.play()
}

async function refreshStats() {
	try {
		const [st, teams] = await Promise.all([wordApi.statistics(), wordApi.myTeams()])
		todaySec.value = st.todaySec || 0
		todayWords.value = st.todayWords || 0
		streak.value = st.streak || 0
		dailyTarget.value = st.dailyTarget || dailyTarget.value || 30
		goalPct.value = st.goalPct != null
			? st.goalPct
			: (dailyTarget.value ? Math.min(100, Math.round(todayWords.value * 100 / dailyTarget.value)) : 0)
		draftTarget.value = dailyTarget.value
		goalCustom.value = !PRESET_GOALS.includes(dailyTarget.value)
		customInput.value = String(dailyTarget.value)
		// 断签安抚：有历史但 streak=0，且今日尚未达标
		missedYesterday.value = (st.studyDays || 0) > 0 && streak.value === 0 && todayWords.value === 0
		myTeams.value = teams.items || []
		const dones = {}
		await Promise.all((teams.items || []).map(async (t) => {
			try {
				const mem = await wordApi.teamMembers(t.id)
				const me = (mem.items || []).find((x) => x.userId === deviceId())
				dones[t.id] = !!(me && me.totalCheckinDays > 0 && t.streak >= 0)
			} catch (e) {}
		}))
		checkinDone.value = dones

		if (!uni.getStorageSync('word_goal_inited')) {
			showGoal.value = true
			uni.setStorageSync('word_goal_inited', true)
		}
	} catch (e) {}
}

async function doCheckin(t) {
	try {
		const res = await wordApi.checkin(t.id, knowCount.value >= t.dailyTarget ? knowCount.value : answeredCount.value)
		if (res.ok) {
			uni.showToast({ title: res.isCompleted ? '打卡成功' : '已记录，未达目标', icon: 'none' })
			checkinDone.value = { ...checkinDone.value, [t.id]: true }
			refreshStats()
		} else {
			uni.showToast({ title: res.error || '打卡失败', icon: 'none' })
		}
	} catch (e) {
		uni.showToast({ title: '打卡失败', icon: 'none' })
	}
}

function fmtMin(sec) {
	if (!sec) return '0分'
	if (sec < 60) return sec + '秒'
	return Math.floor(sec / 60) + '分'
}

onMounted(() => {
	loadBooks()
	refreshStats()
})
onShow(() => {
	if (phase.value === 'learn') startTimer()
	else refreshStats()
})
onHide(() => {
	stopTimer()
})
onUnload(() => {
	stopTimer()
	playing.value = -1
	if (audioCtx) {
		try { audioCtx.destroy() } catch (e) {}
		audioCtx = null
	}
})
</script>

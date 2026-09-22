import catalog from '../data/catalog.json'
import schoolData from '../data/schools.json'
import nationLines from '../data/nation_lines.json'
import { REGIONS } from './chsi.js'
import { cohortWindow, targetCohort } from './cohort.js'

export const CATALOG_URL = 'https://yz.chsi.com.cn/zsml/'

const MODE_LABEL = {
	fulltime: '全日制',
	parttime: '非全日制'
}

const B_PROVINCES = {
	内蒙古: true,
	广西: true,
	海南: true,
	贵州: true,
	云南: true,
	西藏: true,
	甘肃: true,
	青海: true,
	宁夏: true,
	新疆: true
}

/** 199 管理类联考七个专业。工程管理在研招网拆成 125601–125604，界面上合成一个。会计默认靠前。 */
export const MAJORS = [
	{ code: '125300', name: '会计MPAcc' },
	{ code: '125100', name: '工商管理MBA' },
	{ code: '125200', name: '公共管理MPA' },
	{ code: '125400', name: '旅游管理MTA' },
	{ code: '125500', name: '图书情报MLis' },
	{ code: '1256', name: '工程管理MEM' },
	{ code: '125700', name: '审计MAud' }
]

const MEM_CODES = {
	'125601': true,
	'125602': true,
	'125603': true,
	'125604': true
}

/** 界面专业码 → 目录里的代码。工程管理覆盖四个方向。 */
export function catalogCodesOf(majorCode) {
	if (majorCode === '1256') return ['125601', '125602', '125603', '125604']
	return [majorCode]
}

function displayCode(code) {
	if (code === '1256' || MEM_CODES[code] || (code && String(code).indexOf('1256') === 0)) return '1256'
	return code
}

function collectYears() {
	return cohortWindow(targetCohort())
}

/** 近 5 年 Tab：入学年；初试为上一自然年 12 月；不加「届」 */
export const YEARS = collectYears()
export { targetCohort, cohortWindow } from './cohort.js'

/** 按 schoolCode 建索引，避免每次扫全表 */
const catalogBySchool = {}
catalog.forEach((item) => {
	const key = item.schoolCode
	if (!catalogBySchool[key]) catalogBySchool[key] = []
	catalogBySchool[key].push(item)
})

const nationByYear = {}
nationLines.forEach((item) => {
	const y = item.year
	if (!nationByYear[y]) nationByYear[y] = []
	nationByYear[y].push(item)
})

const provinceNameToCode = {}
REGIONS.forEach((item) => {
	if (item.code) provinceNameToCode[item.name] = item.code
})

export function majorByCode(code) {
	return MAJORS.find((item) => item.code === code) || null
}

function majorIndex(code) {
	const index = MAJORS.findIndex((item) => item.code === code)
	return index < 0 ? 99 : index
}

export function majorsOf(schoolCode) {
	const seen = {}
	const list = []
	const rows = catalogBySchool[schoolCode] || []
	rows.forEach((item) => {
		const code = displayCode(item.majorCode)
		if (seen[code]) return
		seen[code] = true
		const major = majorByCode(code)
		list.push({ code, name: major ? major.name : item.majorName })
	})
	list.sort((a, b) => majorIndex(a.code) - majorIndex(b.code))
	return list
}

export function modesOf(schoolCode, majorCode) {
	const order = { fulltime: 0, full_time: 0, parttime: 1, part_time: 1 }
	const codes = {}
	catalogCodesOf(majorCode).forEach((code) => { codes[code] = true })
	const seen = {}
	const list = []
	const rows = catalogBySchool[schoolCode] || []
	rows.forEach((item) => {
		if (!codes[item.majorCode] || seen[item.studyMode]) return
		seen[item.studyMode] = true
		list.push({ id: item.studyMode, label: MODE_LABEL[item.studyMode] || item.studyMode })
	})
	list.sort((a, b) => (order[a.id] ?? 9) - (order[b.id] ?? 9))
	return list
}

/** 有全日制就选全日制，否则取列表第一项 */
export function defaultStudyMode(modes) {
	if (!modes || !modes.length) return ''
	const full = modes.find((m) => m.id === 'fulltime' || m.id === 'full_time')
	return full ? full.id : modes[0].id
}

/** 本地不再存 programs/admissions；招录一律走后端 MySQL */
export function findProgram() {
	return null
}

export function findAdmission() {
	return null
}

export function findNationLine(majorCode, year, province) {
	const rows = nationByYear[year] || []
	const row = rows.find((item) => {
		const codes = item.majorCodes || []
		if (codes.indexOf(majorCode) >= 0) return true
		if (majorCode !== '1256') return false
		return codes.some((code) => MEM_CODES[code] || String(code).indexOf('1256') === 0)
	})
	if (!row) return null
	const isB = !!B_PROVINCES[province]
	return {
		year: row.year,
		zone: isB ? 'B类' : 'A类',
		total: isB ? row.bTotal : row.aTotal,
		english: isB ? row.bEnglish : row.aEnglish,
		comprehensive: isB ? row.bComprehensive : row.aComprehensive,
		text: `${isB ? row.bTotal : row.aTotal}（${isB ? 'B' : 'A'}类）`,
		detail: `英${isB ? row.bEnglish : row.aEnglish} / 综${isB ? row.bComprehensive : row.aComprehensive}`,
		sourceUrl: row.sourceUrl,
		sourceName: row.sourceName,
		syncedAt: row.syncedAt || ''
	}
}

export function schoolsForIntent(majorCode, level, regionCodes) {
	const allowed = {}
	const codes = {}
	catalogCodesOf(majorCode).forEach((code) => { codes[code] = true })
	catalog.forEach((item) => {
		if (codes[item.majorCode] || item.majorCode === majorCode) allowed[item.schoolCode] = true
	})
	return schoolData.filter((school) => {
		if (!allowed[school.code]) return false
		if (level === 'ylgx' && !school.tags.some((tag) => tag.indexOf('双一流') >= 0)) return false
		if (level === 'zhx' && school.flags.indexOf('自划线') < 0) return false
		if (regionCodes.length && regionCodes.indexOf(provinceNameToCode[school.province] || '') < 0) return false
		return true
	}).map((school) => Object.assign({ logoFailed: false }, school))
}

export function formatScore(value) {
	return value == null || value === '' ? '待同步' : String(value)
}

export function admitRate(reexamCount, admitCount) {
	if (!reexamCount || admitCount == null) return '—'
	return `${Math.round((admitCount / reexamCount) * 1000) / 10}%`
}

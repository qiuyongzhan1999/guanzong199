/**
 * 考研入学年与初试年。
 *
 * 入学年：9 月入学的那一年（页面只写数字，不加「届」）。
 * 初试：上一自然年的 12 月。
 * 例：2026 ← 2025 年 12 月考试；2027 ← 2026 年 12 月考试。
 *
 * 展示截止到已经考过初试的最新入学年，等于当前日历年。
 * 乐学喵数据只覆盖近 3 年，窗口取 2024–2026。
 */
export const COHORT_SPAN = 3

export function targetCohort(date) {
	const d = date || new Date()
	return d.getFullYear()
}

/** 某入学年初试所在自然年（12 月考试） */
export function examYearOf(cohort) {
	return Number(cohort) - 1
}

export function cohortWindow(endCohort) {
	const end = endCohort == null ? targetCohort() : Number(endCohort)
	const start = end - (COHORT_SPAN - 1)
	const list = []
	for (let y = end; y >= start; y--) list.push(y)
	return list
}

export function inCohortWindow(year, endCohort) {
	const y = Number(year)
	if (!y) return false
	const end = endCohort == null ? targetCohort() : Number(endCohort)
	const start = end - (COHORT_SPAN - 1)
	return y >= start && y <= end
}

export function filterByCohortWindow(rows, endCohort) {
	if (!Array.isArray(rows)) return []
	return rows.filter((row) => inCohortWindow(row && row.year, endCohort))
}

/** 展示：只写年份数字，不加「届」 */
export function cohortLabel(year) {
	const y = Number(year)
	if (!y) return '—'
	return String(y)
}

export function cohortExamHint(year) {
	const y = Number(year)
	if (!y) return ''
	return '初试 ' + examYearOf(y) + '.12'
}

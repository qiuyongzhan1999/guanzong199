/**
 * 服务端 API。详情只读数据库；择校排序走 /api/match。
 */
// 真机/局域网预览必须用电脑局域网 IP，不能写 127.0.0.1（那是手机自己）。
// 电脑浏览器本地调试也可改回 http://127.0.0.1:8080
export const API_BASE = 'http://localhost:8080'

export function apiEnabled() {
	return !!(API_BASE && String(API_BASE).trim())
}

const inflight = {}
const loadingKeys = {}

function packKey(params) {
	return [params.schoolCode, params.majorCode, params.studyMode].join('|')
}

function request(path, query, timeout) {
	const qs = []
	if (query) {
		Object.keys(query).forEach((key) => {
			const val = query[key]
			if (val === undefined || val === null || val === '') return
			qs.push(encodeURIComponent(key) + '=' + encodeURIComponent(val))
		})
	}
	const url = API_BASE.replace(/\/$/, '') + path + (qs.length ? '?' + qs.join('&') : '')
	if (inflight[url]) return inflight[url]
	inflight[url] = new Promise((resolve, reject) => {
		uni.request({
			url,
			method: 'GET',
			timeout: timeout || 12000,
			success(res) {
				if (res.statusCode >= 200 && res.statusCode < 300) resolve(res.data)
				else reject(new Error('HTTP ' + res.statusCode))
			},
			fail: reject
		})
	}).finally(() => {
		delete inflight[url]
	})
	return inflight[url]
}

function post(path, data, timeout) {
	const url = API_BASE.replace(/\/$/, '') + path
	return new Promise((resolve, reject) => {
		uni.request({
			url,
			method: 'POST',
			data: data || {},
			header: { 'Content-Type': 'application/json' },
			timeout: timeout || 90000,
			success(res) {
				if (res.statusCode >= 200 && res.statusCode < 300) resolve(res.data)
				else reject(new Error('HTTP ' + res.statusCode))
			},
			fail: reject
		})
	})
}

/** 详情只读库，不再等联网补数 */
export function fetchSchoolDetail(params) {
	const pkey = packKey(params || {})
	if (loadingKeys[pkey]) {
		return loadingKeys[pkey]
	}

	const query = {
		schoolCode: params.schoolCode,
		year: params.year,
		majorCode: params.majorCode,
		studyMode: params.studyMode,
		province: params.province || ''
	}

	const req = request('/api/school-detail', query, 20000)
	loadingKeys[pkey] = req.finally(() => {
		if (loadingKeys[pkey] === req) delete loadingKeys[pkey]
	})
	return loadingKeys[pkey]
}

/** 智能择校：省份 + 估分 → 稳/冲/难排序（先出列表） */
export function matchSchools(body) {
	return post('/api/match', body, 30000)
}

/** 智能择校：异步拉取文字建议 */
export function matchAdvice(body) {
	return post('/api/match/advice', body, 90000)
}

/** AI 作文批改 */
export function gradeEssay(body) {
	return post('/api/essay/grade', body, 120000)
}

/** 作文/题目拍照识字 */
export function ocrEssayImage(body) {
	return post('/api/essay/ocr', body, 90000)
}

export function clearSchoolDetailCache() {
	// 已取消前端详情缓存；保留空实现以免旧调用报错
}

/** ---------- 刷题 V2 ---------- */

export function practiceHealth() {
	return request('/api/practice/health', null, 15000)
}

/** 科目列表（含用户进度） */
export function practiceSubjects(userKey) {
	return request('/api/subjects', { userKey }, 15000)
}

/** 章节列表 */
export function practiceChapters(query) {
	return request('/api/chapters', query, 12000)
}

/** 知识点 */
export function practiceKnowledgePoints(query) {
	return request('/api/knowledge-points', query, 12000)
}

/** 题目列表（分页筛选） */
export function practiceQuestions(query) {
	return request('/api/questions', query, 15000)
}

/** 历年真题年份列表 */
export function practiceYears() {
	return request('/api/questions/years', null, 12000)
}

/** 某年真题 id 列表 */
export function practiceQuestionsByYear(year) {
	return request('/api/questions/by-year', { year }, 15000)
}

/** 单题 */
export function practiceQuestion(id, userKey) {
	return request('/api/questions/' + id, { userKey }, 12000)
}

/** 提交答案 */
export function practiceSubmit(body) {
	return post('/api/questions/submit', body, 20000)
}

/** 收藏切换 */
export function practiceFavToggle(body) {
	return post('/api/favorites/toggle', body, 12000)
}

/** 收藏列表 */
export function practiceFavorites(query) {
	return request('/api/favorites', query, 12000)
}

/** 错题列表（仅未自动移出） */
export function practiceWrong(query) {
	return request('/api/wrong-questions', query, 12000)
}

/** 总览统计 */
export function practiceOverview(query) {
	return request('/api/stats/overview', query, 12000)
}

/** 知识点雷达 */
export function practiceKnowledge(query) {
	return request('/api/stats/knowledge', query, 12000)
}

/** 答题历史 */
export function practiceHistory(query) {
	return request('/api/stats/history', query, 12000)
}

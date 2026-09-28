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

/** 乐学喵院校列表（MySQL 数据源） */
export function fetchSchools(query) {
	const params = {
		majorCode: (query && query.majorCode) || '125300',
		studyMode: (query && query.studyMode) || 'fulltime',
		province: (query && query.province) || '',
		keyword: (query && query.keyword) || '',
		trait: (query && query.trait) || 'all',
		page: (query && query.page) || 1,
		pageSize: (query && query.pageSize) || 20
	}
	return request('/api/schools', params, 15000)
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

/** 是否 H5（浏览器）环境：只有 H5 支持 fetch 流式读取 SSE */
function isH5() {
	try {
		return uni.getSystemInfoSync().uniPlatform === 'web'
	} catch (e) {
		return typeof window !== 'undefined' && typeof window.fetch === 'function'
	}
}

/**
 * 流式请求（SSE）：H5 用 fetch + ReadableStream 逐块解析；小程序/App 自动降级为原接口一次性返回。
 * handlers: { onDelta(text, full), onDone(event), onError(message) }
 * resolve(doneEvent) / reject(Error)
 */
export function streamPost(path, body, handlers, timeoutMs) {
	const h = handlers || {}
	const url = API_BASE.replace(/\/$/, '') + path

	if (!isH5()) {
		// 非 H5 降级：走原一次性接口（后端 stream 接口对普通请求也可返回完整 JSON 即可，这里直接调非流式接口）
		const legacy = path.includes('/grade/stream')
			? '/api/essay/grade'
			: path.includes('/advice/stream') ? '/api/match/advice' : path.replace(/\/stream$/, '')
		return post(legacy, body, timeoutMs || 120000).then((data) => {
			if (h.onDelta) h.onDelta(JSON.stringify(data), JSON.stringify(data))
			if (h.onDone) h.onDone(data)
			return data
		}).catch((e) => {
			if (h.onError) h.onError((e && e.message) || '请求失败')
			throw e
		})
	}

	return new Promise((resolve, reject) => {
		let settled = false
		const finish = (fn, arg) => {
			if (settled) return
			settled = true
			fn(arg)
		}
		let full = ''
		fetch(url, {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify(body || {})
		}).then((resp) => {
			if (!resp.ok) {
				return resp.text().then((t) => {
					throw new Error('HTTP ' + resp.status + (t ? ' ' + t.slice(0, 200) : ''))
				})
			}
			if (!resp.body) throw new Error('浏览器不支持流式读取')
			const reader = resp.body.getReader()
			const decoder = new TextDecoder('utf-8')
			let buf = ''

			function handleChunk(chunk) {
				let dataLine = ''
				const lines = String(chunk).split('\n')
				for (const line of lines) {
					const t = line.trim()
					if (t.startsWith('data:')) dataLine = t.slice(5).trim()
				}
				if (!dataLine || dataLine === '[DONE]') return
				let ev
				try {
					ev = JSON.parse(dataLine)
				} catch (e) {
					return
				}
				if (ev.type === 'delta') {
					const text = ev.text || ''
					full += text
					if (h.onDelta) h.onDelta(text, full)
				} else if (ev.type === 'done') {
					if (h.onDone) h.onDone(ev)
					finish(resolve, ev)
				} else if (ev.type === 'error') {
					const msg = ev.message || '生成失败'
					if (h.onError) h.onError(msg)
					finish(reject, new Error(msg))
				}
			}

			function pump() {
				return reader.read().then(({ done, value }) => {
					if (done) {
						if (!settled) finish(reject, new Error('连接中断，未收到完整结果'))
						return
					}
					buf += decoder.decode(value, { stream: true })
					let idx
					while ((idx = buf.indexOf('\n\n')) >= 0) {
						const chunk = buf.slice(0, idx)
						buf = buf.slice(idx + 2)
						handleChunk(chunk)
					}
					return pump()
				})
			}
			return pump()
		}).catch((e) => {
			const msg = (e && e.message) || '网络错误'
			if (h.onError) h.onError(msg)
			finish(reject, e)
		})
	})
}

/** AI 作文批改（流式） */
export function gradeEssayStream(body, handlers) {
	return streamPost('/api/essay/grade/stream', body, handlers, 180000)
}

/** 智能择校文字建议（流式） */
export function matchAdviceStream(body, handlers) {
	return streamPost('/api/match/advice/stream', body, handlers, 180000)
}

export function clearSchoolDetailCache() {
	// 已取消前端详情缓存；保留空实现以免旧调用报错
}


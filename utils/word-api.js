/**
 * 背单词模块 API 封装。
 * 当前无登录体系：userId 用设备维度（首次生成 UUID 存本地，头字段 X-UserId）。
 */
import { API_BASE } from './api.js'

export function deviceId() {
	let id = uni.getStorageSync('word_device_id')
	if (!id) {
		id = 'u' + Date.now().toString(36) + Math.random().toString(36).slice(2, 10)
		uni.setStorageSync('word_device_id', id)
	}
	return id
}

function get(path, query, timeout) {
	const qs = []
	if (query) {
		Object.keys(query).forEach((key) => {
			const val = query[key]
			if (val === undefined || val === null || val === '') return
			qs.push(encodeURIComponent(key) + '=' + encodeURIComponent(val))
		})
	}
	const url = API_BASE.replace(/\/$/, '') + path + (qs.length ? '?' + qs.join('&') : '')
	return new Promise((resolve, reject) => {
		uni.request({
			url,
			method: 'GET',
			header: { 'X-UserId': deviceId() },
			timeout: timeout || 15000,
			success(res) {
				if (res.statusCode >= 200 && res.statusCode < 300) resolve(res.data)
				else reject(new Error('HTTP ' + res.statusCode))
			},
			fail: reject
		})
	})
}

function post(path, data, timeout) {
	const url = API_BASE.replace(/\/$/, '') + path
	return new Promise((resolve, reject) => {
		uni.request({
			url,
			method: 'POST',
			data: data || {},
			header: { 'Content-Type': 'application/json', 'X-UserId': deviceId() },
			timeout: timeout || 15000,
			success(res) {
				if (res.statusCode >= 200 && res.statusCode < 300) resolve(res.data)
				else reject(new Error('HTTP ' + res.statusCode))
			},
			fail: reject
		})
	})
}

/** 发音代理地址（供 audio 标签/InnerAudioContext 使用，无需 header） */
export function audioUrl(word, type) {
	return API_BASE.replace(/\/$/, '') + '/api/audio/word?word=' + encodeURIComponent(word) + '&type=' + (type || 1)
}

export const wordApi = {
	count: (book) => get('/api/word/count', { book }),
	books: () => get('/api/word/books'),
	list: (page, pageSize, book) => get('/api/word/list', { page, pageSize, book }),
	find: (word) => get('/api/word/find', { word }),
	audioUrl: (word, type) => audioUrl(word, type),
	report: (durationSec, wordsSeen) => post('/api/study/report', { durationSec, wordsSeen }),
	today: () => get('/api/study/today'),
	statistics: () => get('/api/study/statistics', { range: 'total' }),
	pref: () => get('/api/study/pref'),
	setPref: (dailyTarget) => post('/api/study/pref', { dailyTarget }),
	studyCalendar: (month) => get('/api/study/calendar', { month }),
	// 组队
	createTeam: (name, dailyTargetWords) => post('/api/team/create', { name, dailyTargetWords }),
	joinTeam: (inviteCode) => post('/api/team/join', { inviteCode }),
	myTeams: () => get('/api/team/my-teams'),
	byInvite: (inviteCode) => get('/api/team/by-invite', { code: inviteCode }),
	checkin: (teamId, wordsLearned) => post('/api/team/checkin', { teamId, wordsLearned }),
	calendar: (teamId, month) => get('/api/team/' + teamId + '/calendar', { month }),
	teamMembers: (teamId) => get('/api/team/' + teamId + '/members')
}

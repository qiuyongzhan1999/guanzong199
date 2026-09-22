/**
 * 刷题本地用户标识（微信登录就绪前用本地 key）
 */
const KEY = 'practiceUserKey'

export function getPracticeUserKey() {
	let k = uni.getStorageSync(KEY)
	if (k) return String(k)
	k = 'u_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2, 8)
	uni.setStorageSync(KEY, k)
	return k
}

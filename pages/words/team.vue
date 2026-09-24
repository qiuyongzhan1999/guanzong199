<template>
	<view class="wteam">
		<!-- 顶部操作 -->
		<view class="wt-ops">
			<view class="wt-op wt-op-create" @tap="showCreate = true"><text class="wt-op-t">创建队伍</text></view>
			<view class="wt-op wt-op-join" @tap="showJoin = true"><text class="wt-op-t">邀请码加入</text></view>
		</view>

		<!-- 我的队伍 -->
		<view class="wt-section" v-for="t in myTeams" :key="t.id">
			<view class="wt-card">
				<view class="wt-card-h">
					<text class="wt-card-name">{{ t.name }}</text>
					<view class="wt-card-role" v-if="t.role === 'leader'">队长</view>
				</view>
				<view class="wt-card-meta">
					<text>成员 {{ t.currentMembers }}/{{ t.maxMembers }}</text>
					<text>目标 {{ t.dailyTarget }} 词/日</text>
					<text>连续 {{ t.streak }} 天</text>
				</view>
				<view class="wt-invite">
					<text class="wt-invite-l">邀请码</text>
					<text class="wt-invite-code" @tap="copyCode(t.inviteCode)">{{ t.inviteCode }}</text>
					<text class="wt-invite-h">点击复制分享</text>
				</view>
			</view>

			<!-- 打卡日历 -->
			<view class="wt-cal">
				<view class="wt-cal-h">
					<text class="wt-cal-m">{{ calMonth }}</text>
					<view class="wt-cal-nav" @tap="shiftMonth(-1)">‹</view>
					<view class="wt-cal-nav" @tap="shiftMonth(1)">›</view>
				</view>
				<view class="wt-cal-grid">
					<view class="wt-cal-week" v-for="w in ['一','二','三','四','五','六','日']" :key="w">{{ w }}</view>
					<view class="wt-cal-day wt-cal-blank" v-for="n in calBlank" :key="'b'+n"></view>
					<view class="wt-cal-day" :class="calClass(day)" v-for="day in calDays" :key="'d'+day">
						<text class="wt-cal-n">{{ day }}</text>
						<text class="wt-cal-c" v-if="calCount[day]">{{ calCount[day] }}</text>
					</view>
				</view>
			</view>
		</view>

		<view class="wt-empty" v-if="!myTeams.length">
			<view class="wt-empty-t">还没有队伍</view>
			<view class="wt-empty-s">创建队伍或输入邀请码加入，和小伙伴一起打卡</view>
		</view>

		<!-- 创建弹层 -->
		<view class="wt-mask" v-if="showCreate" @tap="showCreate = false">
			<view class="wt-dialog" @tap.stop>
				<view class="wt-dialog-t">创建队伍</view>
				<input class="wt-input" v-model="createName" placeholder="队伍名称（如：上岸冲刺营）" maxlength="20" />
				<view class="wt-dialog-label">每日共同目标单词数</view>
				<view class="wt-target-row">
					<view class="wt-target" :class="{ on: target === 10 }" @tap="target = 10">10</view>
					<view class="wt-target" :class="{ on: target === 20 }" @tap="target = 20">20</view>
					<view class="wt-target" :class="{ on: target === 30 }" @tap="target = 30">30</view>
					<view class="wt-target" :class="{ on: target === 50 }" @tap="target = 50">50</view>
				</view>
				<view class="wt-dialog-btns">
					<view class="wt-btn wt-btn-cancel" @tap="showCreate = false">取消</view>
					<view class="wt-btn wt-btn-ok" @tap="doCreate">创建</view>
				</view>
			</view>
		</view>

		<!-- 加入弹层 -->
		<view class="wt-mask" v-if="showJoin" @tap="showJoin = false">
			<view class="wt-dialog" @tap.stop>
				<view class="wt-dialog-t">加入队伍</view>
				<input class="wt-input" v-model="joinCode" placeholder="输入 6 位邀请码" maxlength="6" @input="joinInput" />
				<view class="wt-preview" v-if="preview">
					<text class="wt-preview-n">{{ preview.name }}</text>
					<text class="wt-preview-m">成员 {{ preview.currentMembers }}/{{ preview.maxMembers }} · 目标 {{ preview.dailyTarget }} 词/日</text>
				</view>
				<view class="wt-dialog-btns">
					<view class="wt-btn wt-btn-cancel" @tap="showJoin = false">取消</view>
					<view class="wt-btn wt-btn-ok" @tap="doJoin">加入</view>
				</view>
			</view>
		</view>
	</view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { wordApi } from '../../utils/word-api.js'

const myTeams = ref([])
const showCreate = ref(false)
const showJoin = ref(false)
const createName = ref('')
const target = ref(20)
const joinCode = ref('')
const preview = ref(null)
const calMonth = ref('')
const calCount = ref({})
const calMap = ref({})

onMounted(refresh)
onShow(refresh)

async function refresh() {
	try {
		const res = await wordApi.myTeams()
		myTeams.value = res.items || []
		if (myTeams.value.length) {
			const now = new Date()
			calMonth.value = now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0')
			await loadCalendar(myTeams.value[0].id)
		}
	} catch (e) {}
}

async function loadCalendar(teamId) {
	try {
		const res = await wordApi.calendar(teamId, calMonth.value)
		calCount.value = res.days || {}
		calMap.value = { teamId }
	} catch (e) {}
}

function shiftMonth(delta) {
	const [y, m] = calMonth.value.split('-').map(Number)
	const d = new Date(y, m - 1 + delta, 1)
	calMonth.value = d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0')
	if (calMap.value.teamId) loadCalendar(calMap.value.teamId)
}

const calDays = computed(() => {
	if (!calMonth.value) return []
	const [y, m] = calMonth.value.split('-').map(Number)
	return new Date(y, m, 0).getDate()
})
const calBlank = computed(() => {
	if (!calMonth.value) return 0
	const [y, m] = calMonth.value.split('-').map(Number)
	const first = new Date(y, m - 1, 1).getDay()
	return (first + 6) % 7
})
function calClass(day) {
	const v = calCount.value[String(day)]
	if (v > 0) return 'wt-cal-hit'
	return ''
}

async function doCreate() {
	const name = createName.value.trim()
	if (!name) {
		uni.showToast({ title: '请填写队伍名称', icon: 'none' })
		return
	}
	try {
		const res = await wordApi.createTeam(name, target.value)
		if (res.ok) {
			uni.showToast({ title: '创建成功，邀请码 ' + res.team.inviteCode, icon: 'none' })
			showCreate.value = false
			createName.value = ''
			refresh()
		} else {
			uni.showToast({ title: res.error || '创建失败', icon: 'none' })
		}
	} catch (e) {
		uni.showToast({ title: '创建失败', icon: 'none' })
	}
}

async function joinInput(e) {
	const code = (e.detail.value || '').trim().toUpperCase()
	if (code.length === 6) {
		try {
			const res = await wordApi.byInvite(code)
			preview.value = res.ok ? res.team : null
			if (!res.ok) uni.showToast({ title: res.error || '邀请码无效', icon: 'none' })
		} catch (err) {
			preview.value = null
		}
	} else {
		preview.value = null
	}
}

async function doJoin() {
	const code = joinCode.value.trim().toUpperCase()
	if (!code) {
		uni.showToast({ title: '请输入邀请码', icon: 'none' })
		return
	}
	try {
		const res = await wordApi.joinTeam(code)
		if (res.ok) {
			uni.showToast({ title: '加入成功 🎉', icon: 'none' })
			showJoin.value = false
			joinCode.value = ''
			preview.value = null
			refresh()
		} else {
			uni.showToast({ title: res.error || '加入失败', icon: 'none' })
		}
	} catch (e) {
		uni.showToast({ title: '加入失败', icon: 'none' })
	}
}

function copyCode(code) {
	uni.setClipboardData({
		data: code,
		success() {
			uni.showToast({ title: '邀请码已复制', icon: 'none' })
		}
	})
}
</script>

<style scoped>
.wteam { padding: 24rpx 30rpx 60rpx; background: #F4F7FB; min-height: 100vh; }
.wt-ops { display: flex; gap: 24rpx; margin-bottom: 28rpx; }
.wt-op { flex: 1; text-align: center; padding: 26rpx 0; border-radius: 16rpx; font-weight: 600; }
.wt-op-create { background: #2F6FED; color: #fff; }
.wt-op-join { background: #fff; color: #2F6FED; border: 1rpx solid #2F6FED; }
.wt-op-t { font-size: 30rpx; }
.wt-section { margin-bottom: 28rpx; }
.wt-card { background: #fff; border-radius: 20rpx; padding: 24rpx; }
.wt-card-h { display: flex; align-items: center; gap: 12rpx; }
.wt-card-name { font-size: 32rpx; font-weight: 700; color: #1F2D3D; }
.wt-card-role { font-size: 20rpx; color: #fff; background: #FF9F2E; padding: 4rpx 12rpx; border-radius: 8rpx; }
.wt-card-meta { display: flex; gap: 24rpx; margin-top: 12rpx; font-size: 24rpx; color: #8B97A8; }
.wt-invite { display: flex; align-items: center; gap: 16rpx; margin-top: 16rpx; background: #F7F9FC; padding: 14rpx 20rpx; border-radius: 12rpx; }
.wt-invite-l { font-size: 24rpx; color: #8B97A8; }
.wt-invite-code { font-size: 32rpx; font-weight: 700; color: #2F6FED; letter-spacing: 4rpx; }
.wt-invite-h { font-size: 20rpx; color: #B6C0CF; margin-left: auto; }
.wt-cal { background: #fff; border-radius: 20rpx; padding: 24rpx; margin-top: 20rpx; }
.wt-cal-h { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16rpx; }
.wt-cal-m { font-size: 28rpx; font-weight: 600; color: #1F2D3D; }
.wt-cal-nav { width: 56rpx; height: 56rpx; display: flex; align-items: center; justify-content: center; background: #EEF3FB; border-radius: 12rpx; color: #2F6FED; font-size: 36rpx; }
.wt-cal-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 8rpx; }
.wt-cal-week { text-align: center; font-size: 22rpx; color: #B6C0CF; padding: 8rpx 0; }
.wt-cal-day { text-align: center; position: relative; height: 76rpx; display: flex; flex-direction: column; align-items: center; justify-content: center; border-radius: 12rpx; }
.wt-cal-blank { background: transparent; }
.wt-cal-n { font-size: 26rpx; color: #44536B; }
.wt-cal-c { font-size: 20rpx; color: #fff; background: #2F6FED; border-radius: 999rpx; padding: 2rpx 10rpx; margin-top: 2rpx; }
.wt-cal-hit { background: #EAF1FF; }
.wt-empty { text-align: center; padding: 120rpx 0; }
.wt-empty-t { font-size: 32rpx; color: #8B97A8; }
.wt-empty-s { margin-top: 12rpx; font-size: 24rpx; color: #B6C0CF; }
.wt-mask { position: fixed; inset: 0; background: rgba(20,30,50,.45); display: flex; align-items: center; justify-content: center; z-index: 99; }
.wt-dialog { width: 620rpx; background: #fff; border-radius: 24rpx; padding: 40rpx; }
.wt-dialog-t { font-size: 34rpx; font-weight: 700; color: #1F2D3D; margin-bottom: 24rpx; }
.wt-input { border: 1rpx solid #DCE3EF; border-radius: 12rpx; padding: 20rpx 24rpx; font-size: 28rpx; background: #F7F9FC; }
.wt-dialog-label { margin-top: 24rpx; font-size: 24rpx; color: #8B97A8; }
.wt-target-row { display: flex; gap: 16rpx; margin-top: 12rpx; }
.wt-target { flex: 1; text-align: center; padding: 16rpx 0; border-radius: 12rpx; background: #F7F9FC; color: #44536B; font-size: 26rpx; }
.wt-target.on { background: #2F6FED; color: #fff; font-weight: 600; }
.wt-dialog-btns { display: flex; gap: 20rpx; margin-top: 32rpx; }
.wt-btn { flex: 1; text-align: center; padding: 22rpx 0; border-radius: 14rpx; font-size: 28rpx; font-weight: 600; }
.wt-btn-cancel { background: #EEF1F6; color: #44536B; }
.wt-btn-ok { background: #2F6FED; color: #fff; }
.wt-preview { margin-top: 20rpx; background: #EEF3FB; border-radius: 12rpx; padding: 16rpx 20rpx; }
.wt-preview-n { display: block; font-size: 28rpx; color: #1F2D3D; font-weight: 600; }
.wt-preview-m { display: block; font-size: 22rpx; color: #8B97A8; margin-top: 4rpx; }
</style>

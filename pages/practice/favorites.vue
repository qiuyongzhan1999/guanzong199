<template>
	<view class="edu-page">
		<view class="edu-card">
			<view class="edu-section-head edu-section-head-in">
				<view class="edu-section-bar" />
				<text class="edu-section-title">收藏夹</text>
			</view>
			<view class="edu-chips">
				<view class="edu-chip" :class="{ on: !subjectId }" @tap="filterSubject(null)">全部</view>
				<view
					class="edu-chip"
					v-for="s in subjects"
					:key="s.id"
					:class="{ on: subjectId === s.id }"
					@tap="filterSubject(s.id)"
				>{{ s.shortName || s.name }}</view>
			</view>
		</view>

		<EmptyState v-if="!items.length && !loading" title="还没有收藏" sub="答题页点右上角星标即可收藏。" />

		<view class="edu-card" v-for="q in items" :key="q.id" @tap="redo(q)">
			<view class="edu-card-head">
				<text class="edu-name">{{ q.subjectName }} · {{ q.knowledgePointName || '综合' }}</text>
				<text class="edu-go">›</text>
			</view>
			<text class="edu-sub">{{ q.stem || q.content }}</text>
		</view>
	</view>
</template>

<script>
	import EmptyState from '../../components/ui/EmptyState.vue'
	import { practiceFavorites, practiceSubjects } from '../../utils/api.js'
	import { getPracticeUserKey } from '../../utils/practice-user.js'

	export default {
		components: { EmptyState },
		data() {
			return {
				items: [],
				subjects: [],
				subjectId: null,
				loading: false
			}
		},
		onShow() {
			this.loadSubjects()
			this.load()
		},
		methods: {
			async loadSubjects() {
				try {
					const data = await practiceSubjects(getPracticeUserKey())
					this.subjects = (data && data.items) || []
				} catch (e) {
					this.subjects = []
				}
			},
			filterSubject(id) {
				this.subjectId = id
				this.load()
			},
			async load() {
				this.loading = true
				try {
					const query = { userKey: getPracticeUserKey() }
					if (this.subjectId) query.subject_id = this.subjectId
					const data = await practiceFavorites(query)
					this.items = (data && data.items) || []
				} catch (e) {
					this.items = []
				} finally {
					this.loading = false
				}
			},
			redo(q) {
				uni.setStorageSync('practiceSession', {
					mode: 'favorite',
					ids: [q.id],
					index: 0,
					answers: {}
				})
				uni.navigateTo({ url: '/pages/practice/quiz' })
			}
		}
	}
</script>

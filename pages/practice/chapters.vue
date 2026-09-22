<template>
	<view class="edu-page">
		<view class="edu-card edu-practice-hero">
			<text class="edu-kicker">章节练习</text>
			<text class="edu-card-title">{{ subjectName }}</text>
			<text class="edu-hint">选章节开练，进度条为本章正确率。</text>
		</view>

		<EmptyState v-if="!chapters.length && !loading" title="暂无章节" sub="请确认已执行建表 SQL。" />

		<view
			class="edu-card edu-chapter-row"
			v-for="c in chapters"
			:key="c.id"
			@tap="startChapter(c)"
		>
			<view class="edu-chapter-head">
				<text class="edu-menu-title">{{ c.name }}</text>
				<text class="edu-hint">{{ c.questionCount || 0 }} 题</text>
			</view>
			<view class="edu-hp-bar">
				<view
					class="edu-hp-fill"
					:class="c.masteryRate == null ? 'is-empty' : ''"
					:style="{ width: (c.masteryRate == null ? 0 : c.masteryRate) + '%' }"
				/>
			</view>
			<text class="edu-muted">
				{{ c.masteryRate == null ? '尚未练习' : ('掌握度 ' + c.masteryRate + '%') }}
			</text>
		</view>
	</view>
</template>

<script>
	import EmptyState from '../../components/ui/EmptyState.vue'
	import { practiceChapters, practiceQuestions } from '../../utils/api.js'
	import { getPracticeUserKey } from '../../utils/practice-user.js'

	export default {
		components: { EmptyState },
		data() {
			return {
				subjectId: 0,
				subjectName: '刷题',
				chapters: [],
				loading: false
			}
		},
		onLoad(q) {
			this.subjectId = Number((q && q.subject_id) || 0)
			this.subjectName = (q && q.name) ? decodeURIComponent(q.name) : '刷题'
			uni.setNavigationBarTitle({ title: this.subjectName })
		},
		onShow() {
			this.load()
		},
		methods: {
			async load() {
				if (!this.subjectId) return
				this.loading = true
				try {
					const data = await practiceChapters({
						subject_id: this.subjectId,
						userKey: getPracticeUserKey()
					})
					this.chapters = (data && data.items) || []
				} catch (e) {
					this.chapters = []
				} finally {
					this.loading = false
				}
			},
			async startChapter(c) {
				try {
					const data = await practiceQuestions({
						subject_id: this.subjectId,
						chapter_id: c.id,
						mode: 'order',
						page: 1,
						page_size: 20
					})
					const items = (data && data.items) || []
					if (!items.length) {
						uni.showToast({ title: '该章暂无题目', icon: 'none' })
						return
					}
					uni.setStorageSync('practiceSession', {
						subjectId: this.subjectId,
						chapterId: c.id,
						mode: 'order',
						ids: items.map((q) => q.id),
						index: 0,
						answers: {}
					})
					uni.navigateTo({ url: '/pages/practice/quiz' })
				} catch (e) {
					uni.showToast({ title: '拉题失败', icon: 'none' })
				}
			}
		}
	}
</script>

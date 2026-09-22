/**
 * 管综上岸通 · 现代教育科技风
 * Slogan：择校有数，上岸有路。
 * 干净底、微渐变、大数字、圆角、留白。页面只许用这里的色，不要另写一套。
 * 与 styles/edu-ui.scss、pages.json 的导航色保持一致。
 */
export const EDU = {
	color: {
		canvas: '#F4F7FB',
		surface: '#FFFFFF',
		ink: '#142033',
		mist: '#6A7A90',
		faint: '#8B97A8',
		signal: '#2F6FED',
		wash: '#E7F0FF',
		good: '#1F8A70',
		heat: '#E25B4A'
	},
	/** 刷题三科卡片色（映射自 signal / heat / good，禁止另起紫系） */
	subject: {
		math: { tone: 'signal', from: '#2F6FED', to: '#5B8DEF', soft: '#E7F0FF' },
		logic: { tone: 'heat', from: '#E25B4A', to: '#F08A5A', soft: '#FDECEA' },
		english: { tone: 'good', from: '#1F8A70', to: '#2AA8A0', soft: '#E7F6F2' }
	},
	chart: {
		axis: '#6A7A90',
		grid: 'rgba(20, 32, 51, 0.06)',
		reexam: '#2F6FED',
		minScore: '#E25B4A',
		admit: '#1F8A70',
		reexamCount: '#A8B3C4',
		reexamFill: 'rgba(47, 111, 237, 0.16)',
		reexamFillEnd: 'rgba(47, 111, 237, 0)'
	}
}

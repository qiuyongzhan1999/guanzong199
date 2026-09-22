import schoolData from '../data/schools.json'

export const REGIONS = [
	{ code: '', name: '全部地区' },
	{ code: '11', name: '北京' },
	{ code: '12', name: '天津' },
	{ code: '13', name: '河北' },
	{ code: '14', name: '山西' },
	{ code: '15', name: '内蒙古' },
	{ code: '21', name: '辽宁' },
	{ code: '22', name: '吉林' },
	{ code: '23', name: '黑龙江' },
	{ code: '31', name: '上海' },
	{ code: '32', name: '江苏' },
	{ code: '33', name: '浙江' },
	{ code: '34', name: '安徽' },
	{ code: '35', name: '福建' },
	{ code: '36', name: '江西' },
	{ code: '37', name: '山东' },
	{ code: '41', name: '河南' },
	{ code: '42', name: '湖北' },
	{ code: '43', name: '湖南' },
	{ code: '44', name: '广东' },
	{ code: '45', name: '广西' },
	{ code: '46', name: '海南' },
	{ code: '50', name: '重庆' },
	{ code: '51', name: '四川' },
	{ code: '52', name: '贵州' },
	{ code: '53', name: '云南' },
	{ code: '54', name: '西藏' },
	{ code: '61', name: '陕西' },
	{ code: '62', name: '甘肃' },
	{ code: '63', name: '青海' },
	{ code: '64', name: '宁夏' },
	{ code: '65', name: '新疆' },
	{ code: '71', name: '台湾' },
	{ code: '81', name: '香港' },
	{ code: '91', name: '澳门' }
]

function strip(html) {
	return html
	.replace(/<[^>]+>/g, ' ')
	.replace(/&#x?[0-9a-fA-F]+;/g, ' ')
	.replace(/&nbsp;/g, ' ')
		.replace(/&amp;/g, '&')
		.replace(/\s+/g, ' ')
		.trim()
}

function parseOne(chunk) {
	const nameMatch = chunk.match(/<a class="name[^"]*"[\s\S]*?>([\s\S]*?)<\/a>/)
	if (!nameMatch) return null
	const name = strip(nameMatch[1])
	if (!name) return null
	const hrefMatch = chunk.match(/<a class="name[^"]*"[\s\S]*?href="([^"]+)"/)
	const href = hrefMatch ? hrefMatch[1] : ''
	const idMatch = href.match(/schId-(\d+)/)
	const logoMatch = chunk.match(/<img\s+src=['"]([^'"]+)['"]/)
	const logo = logoMatch ? logoMatch[1] : ''
	const codeMatch = logo.match(/\/xh\/(\d+)\.jpg/)
	const tags = []
	const tagRe = /<span class="sch-tag">([\s\S]*?)<\/span>/g
	let tag
	while ((tag = tagRe.exec(chunk))) {
		const text = strip(tag[1])
		if (text) tags.push(text)
	}
	const depMatch = chunk.match(/<div class="sch-department">([\s\S]*?)<\/div>/)
	const dep = depMatch ? strip(depMatch[1]) : ''
	const authMatch = dep.match(/主管部门：\s*(\S+)/)
	const provMatch = dep.match(/^(\S+)/)
	const flags = []
	if (dep.indexOf('研究生院') >= 0) flags.push('研究生院')
	if (dep.indexOf('自划线') >= 0) flags.push('自划线')
	return {
		name,
		logo,
		logoFailed: false,
		schId: idMatch ? idMatch[1] : '',
		code: codeMatch ? codeMatch[1] : '',
		tags,
		province: provMatch && provMatch[1] !== '主管部门：' ? provMatch[1] : '',
		authority: authMatch ? authMatch[1] : '',
		flags
	}
}

export function parseSchoolList(html) {
	if (!html || html.indexOf('sch-item') < 0) return []
	return html
		.split('<div class="sch-item">')
		.slice(1)
		.map(parseOne)
		.filter(Boolean)
}

export function schoolLabels(item) {
	const labels = []
	const i = item.tags.length
	let n = 0
	while (n < i) {
		if (item.tags[n].indexOf('双一流') >= 0) {
			labels.push({ text: '双一流', hot: true })
			break
		}
		n += 1
	}
	item.flags.forEach((text) => {
		labels.push({ text, hot: false })
	})
	return labels
}

export function findSchool(schId) {
	const id = String(schId || '')
	const found = schoolData.find((item) => item.schId === id)
	return found || null
}

export function fetchSchoolPage(query) {
	const keyword = (query.yxmc || '').trim()
	const region = REGIONS.find((item) => item.code === (query.ssdm || ''))
	const list = schoolData.filter((item) => {
		if (keyword && item.name.indexOf(keyword) < 0) return false
		if (region && region.name !== '全部地区' && item.province !== region.name) return false
		if (query.ylgx && !item.tags.some((tag) => tag.indexOf('双一流') >= 0)) return false
		if (query.yjsy && item.flags.indexOf('研究生院') < 0) return false
		if (query.zhx && item.flags.indexOf('自划线') < 0) return false
		return true
	})
	const start = query.start || 0
	return Promise.resolve(list.slice(start, start + 20).map((item) => ({
		logoFailed: false,
		name: item.name,
		logo: item.logo,
		schId: item.schId,
		code: item.code,
		tags: item.tags,
		province: item.province,
		authority: item.authority,
		flags: item.flags
	})))
}

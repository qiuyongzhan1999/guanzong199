/**
 * 自编题库生成器：管综约 3000 + 英语二约 2000
 * 用法：node tools/gen-practice-bank.mjs
 * 输出：data/practice/guanzong-bulk-*.json 与 english-bulk-*.json
 *
 * 说明：模板参数化自编题，用于刷题量；非历年真题照搬。
 */
const fs = require('fs')
const path = require('path')

const ROOT = path.resolve(__dirname, '..')
const OUT = path.join(ROOT, 'data', 'practice')
const CHUNK = 500

function ensureDir(d) {
	fs.mkdirSync(d, { recursive: true })
}

function shuffle(arr, rnd) {
	const a = arr.slice()
	for (let i = a.length - 1; i > 0; i--) {
		const j = Math.floor(rnd() * (i + 1))
		;[a[i], a[j]] = [a[j], a[i]]
	}
	return a
}

function mulberry32(seed) {
	let t = seed >>> 0
	return function () {
		t += 0x6d2b79f5
		let r = Math.imul(t ^ (t >>> 15), 1 | t)
		r ^= r + Math.imul(r ^ (r >>> 7), 61 | r)
		return ((r ^ (r >>> 14)) >>> 0) / 4294967296
	}
}

function pick(rnd, arr) {
	return arr[Math.floor(rnd() * arr.length)]
}

function randInt(rnd, lo, hi) {
	return lo + Math.floor(rnd() * (hi - lo + 1))
}

function optionsABCD(rnd, correctKey, correctText, wrongs) {
	const map = { A: null, B: null, C: null, D: null }
	map[correctKey] = String(correctText)
	const keys = shuffle(['A', 'B', 'C', 'D'].filter((k) => k !== correctKey), rnd)
	wrongs.slice(0, 3).forEach((w, i) => {
		map[keys[i]] = String(w)
	})
	return ['A', 'B', 'C', 'D'].map((k) => ({ key: k, text: map[k] }))
}

function placeCorrect(rnd, correct, distractors) {
	const key = pick(rnd, ['A', 'B', 'C', 'D'])
	const wrongs = shuffle(distractors.map(String), rnd).slice(0, 3)
	while (wrongs.length < 3) wrongs.push(String(Number(correct) + wrongs.length + 1))
	return { key, options: optionsABCD(rnd, key, correct, wrongs) }
}

function qBase(subject, type, kp, content, answerKey, options, idea, analysis, diff) {
	return {
		subject,
		type,
		knowledge_point: kp,
		content,
		options,
		answer: answerKey,
		analysisIdea: idea,
		analysis,
		analysisKp: kp,
		difficulty: diff
	}
}

/** ---------- 管综：数学模板 ---------- */
function genMath(rnd, i) {
	const kind = i % 12
	if (kind === 0) {
		const girls = randInt(rnd, 10, 40)
		const more = randInt(rnd, 2, 12)
		const boys = girls + more
		const total = boys + girls
		const { key, options } = placeCorrect(rnd, boys, [girls, total - more, boys + 2, more])
		return qBase(
			'guanzong',
			'数学',
			'方程',
			`某班有学生 ${total} 人。男生人数比女生多 ${more} 人。问男生有多少人？`,
			key,
			options,
			'设女生 x，男生 x+差额，总和已知。',
			`女生 ${girls}，男生 ${boys}。选 ${key}。`,
			1 + (i % 3)
		)
	}
	if (kind === 1) {
		const va = randInt(rnd, 30, 80)
		const vb = randInt(rnd, 20, 70)
		const t = randInt(rnd, 2, 6)
		const s = (va + vb) * t
		const { key, options } = placeCorrect(rnd, t, [t + 1, t - 1 || 1, t + 2, Math.max(1, t - 2)])
		return qBase(
			'guanzong',
			'数学',
			'行程',
			`甲乙两地相距 ${s} 公里。A 车时速 ${va}，B 车时速 ${vb}，同时从两端相向开出，几小时后相遇？`,
			key,
			options,
			'相遇时间 = 路程 ÷ 速度和。',
			`速度和=${va + vb}，时间=${s}/${va + vb}=${t}。选 ${key}。`,
			2
		)
	}
	if (kind === 2) {
		const a = randInt(rnd, 6, 20)
		const b = randInt(rnd, 8, 24)
		// 合作天数 = 1/(1/a+1/b) = ab/(a+b)
		const num = a * b
		const den = a + b
		const days = Math.round((num / den) * 10) / 10
		const { key, options } = placeCorrect(rnd, days, [
			Math.round((num / den + 0.5) * 10) / 10,
			Math.round((num / den - 0.5) * 10) / 10,
			a,
			b
		])
		return qBase(
			'guanzong',
			'数学',
			'工程',
			`一项工程，甲独做 ${a} 天完成，乙独做 ${b} 天完成。两人合作约几天完成？（保留一位小数若需要）`,
			key,
			options,
			'效率和相加。',
			`天数=${a}×${b}/(${a}+${b})=${days}。选 ${key}。`,
			2
		)
	}
	if (kind === 3) {
		const price = randInt(rnd, 100, 500)
		const up = pick(rnd, [10, 20, 25])
		const down = pick(rnd, [10, 20, 25])
		const now = Math.round(price * (1 + up / 100) * (1 - down / 100) * 100) / 100
		const { key, options } = placeCorrect(rnd, now, [price, Math.round(price * 1.1), Math.round(price * 0.9), now + 10])
		return qBase(
			'guanzong',
			'数学',
			'百分数',
			`商品原价 ${price} 元，先涨价 ${up}%，再降价 ${down}%。现价是多少元？`,
			key,
			options,
			'连续涨跌用连乘。',
			`现价=${price}×${1 + up / 100}×${1 - down / 100}=${now}。选 ${key}。`,
			2
		)
	}
	if (kind === 4) {
		const a1 = randInt(rnd, 1, 20)
		const d = randInt(rnd, 1, 8)
		const n = randInt(rnd, 5, 12)
		const an = a1 + (n - 1) * d
		const { key, options } = placeCorrect(rnd, an, [an + d, an - d, a1 + n * d, a1])
		return qBase(
			'guanzong',
			'数学',
			'数列',
			`等差数列中，a₁=${a1}，公差 d=${d}，则 a${n === 1 ? '₁' : 'ₙ'.replace('ₙ', String(n))}（第 ${n} 项）等于？`,
			key,
			options,
			'aₙ=a₁+(n-1)d。',
			`a${n}=${a1}+(${n}-1)×${d}=${an}。选 ${key}。`,
			1
		)
	}
	if (kind === 5) {
		const n = randInt(rnd, 4, 8)
		const k = randInt(rnd, 2, Math.min(4, n - 1))
		const comb = factorial(n) / (factorial(k) * factorial(n - k))
		const { key, options } = placeCorrect(rnd, comb, [comb + 1, Math.max(1, comb - 1), n * k, factorial(n) / factorial(n - k)])
		return qBase(
			'guanzong',
			'数学',
			'排列组合',
			`从 ${n} 本不同的书中选 ${k} 本，有多少种选法？`,
			key,
			options,
			'组合 C(n,k)。',
			`C(${n},${k})=${comb}。选 ${key}。`,
			2
		)
	}
	if (kind === 6) {
		const red = randInt(rnd, 2, 6)
		const white = randInt(rnd, 1, 5)
		const total = red + white
		const p = `${red}/${total}`
		const { key, options } = placeCorrect(rnd, p, [`${white}/${total}`, `${red}/${red + white + 1}`, '1/2', `${red - 1}/${total}`])
		return qBase(
			'guanzong',
			'数学',
			'概率',
			`袋中有 ${red} 红球、${white} 白球，随机摸 1 个，摸到红球的概率是？`,
			key,
			options,
			'古典概型。',
			`概率=${red}/${total}。选 ${key}。`,
			1
		)
	}
	if (kind === 7) {
		const a = randInt(rnd, 3, 12)
		const b = randInt(rnd, 4, 16)
		const c = Math.round(Math.sqrt(a * a + b * b) * 1000) / 1000
		// prefer Pythagorean triples sometimes
		const triples = [
			[3, 4, 5],
			[5, 12, 13],
			[6, 8, 10],
			[9, 12, 15],
			[8, 15, 17]
		]
		const t = pick(rnd, triples)
		const { key, options } = placeCorrect(rnd, t[2], [t[0] + t[1], t[2] + 1, t[2] - 1, t[0] * t[1]])
		return qBase(
			'guanzong',
			'数学',
			'几何',
			`直角三角形两直角边为 ${t[0]} 和 ${t[1]}，斜边长是？`,
			key,
			options,
			'勾股定理。',
			`斜边=√(${t[0]}²+${t[1]}²)=${t[2]}。选 ${key}。`,
			1
		)
	}
	if (kind === 8) {
		const pen = randInt(rnd, 2, 9)
		const book = randInt(rnd, 3, 12)
		const n1 = 3
		const n2 = 5
		const c1 = n1 * pen + 2 * book
		const c2 = n2 * pen + 2 * book
		const { key, options } = placeCorrect(rnd, pen, [book, c1, c2, pen + book])
		return qBase(
			'guanzong',
			'数学',
			'应用题',
			`买 ${n1} 支笔和 2 本本子共 ${c1} 元；买 ${n2} 支笔和 2 本本子共 ${c2} 元。一支笔多少元？`,
			key,
			options,
			'两式相减。',
			`2 支笔差价 ${c2 - c1} 元，每支 ${pen} 元。选 ${key}。`,
			2
		)
	}
	if (kind === 9) {
		// cleaner inequality with text options
		const m = randInt(rnd, 2, 8)
		const c = m * randInt(rnd, 2, 9)
		const bound = c / m
		const correct = `x≤${bound}`
		const key = pick(rnd, ['A', 'B', 'C', 'D'])
		const wrongs = [`x≥${bound}`, `x≤${-bound}`, `x≥${-bound}`]
		return qBase(
			'guanzong',
			'数学',
			'不等式',
			`不等式 ${m}x−${c}≤0 的解集是？`,
			key,
			optionsABCD(rnd, key, correct, wrongs),
			'移项。',
			`${m}x≤${c} → x≤${bound}。选 ${key}。`,
			1
		)
	}
	if (kind === 10) {
		const scores = [randInt(rnd, 60, 90), randInt(rnd, 60, 90), randInt(rnd, 60, 90), randInt(rnd, 60, 90)]
		const avg = randInt(rnd, 70, 85)
		const sum4 = scores.reduce((a, b) => a + b, 0)
		const x = avg * 5 - sum4
		const { key, options } = placeCorrect(rnd, x, [avg, sum4, x + 5, Math.max(0, x - 5)])
		return qBase(
			'guanzong',
			'数学',
			'平均值',
			`五次成绩为 ${scores.join('、')}、x。若平均分是 ${avg}，则 x 是？`,
			key,
			options,
			'总和=平均×次数。',
			`x=${avg}×5−${sum4}=${x}。选 ${key}。`,
			1
		)
	}
	// kind 11 function
	const k = randInt(rnd, 1, 6)
	const b = randInt(rnd, 1, 12)
	const x0 = b // y=kx-kb zero at x=b if y=kx - k*b
	const { key, options } = placeCorrect(rnd, b, [0, k, b + k, -b])
	return qBase(
		'guanzong',
		'数学',
		'函数',
		`一次函数 y=${k}x−${k * b} 与 x 轴交点的横坐标是？`,
		key,
		options,
		'令 y=0。',
		`${k}x=${k * b} → x=${b}。选 ${key}。`,
		1
	)
}

function factorial(n) {
	let r = 1
	for (let i = 2; i <= n; i++) r *= i
	return r
}

/** ---------- 管综：逻辑模板 ---------- */
function genLogic(rnd, i) {
	const kind = i % 8
	const names = ['小周', '小吴', '小郑', '小王', '小李', '小张', '小赵', '小钱']
	const a = pick(rnd, names)
	const b = pick(rnd, names.filter((x) => x !== a))
	if (kind === 0) {
		const key = 'B'
		const options = optionsABCD(rnd, key, `${a}去了，说明通过了审核`, [
			`通过审核就能去`,
			`${a}没去，说明没通过审核`,
			`通过审核是去的充分条件`
		])
		// fix: placeCorrect style - rebuild
		const k2 = pick(rnd, ['A', 'B', 'C', 'D'])
		const correct = `${a}报名了，一定通过了审核`
		const opts = optionsABCD(rnd, k2, correct, [
			`通过审核就能报名`,
			`${a}没报名，一定没通过审核`,
			`通过审核是报名的充分条件`
		])
		return qBase(
			'guanzong',
			'逻辑',
			'充分必要',
			`「只有通过审核，${a}才能报名活动」为真。由此可推出？`,
			k2,
			opts,
			'只有 p 才 q ⇔ q→p。',
			`报名→通过审核，故「报名了一定通过审核」。选 ${k2}。`,
			2
		)
	}
	if (kind === 1) {
		const k2 = pick(rnd, ['A', 'B', 'C', 'D'])
		const correct = '灯没灭，一定没停电'
		const opts = optionsABCD(rnd, k2, correct, [
			'灯灭了，一定停电了',
			'没停电，灯一定不灭',
			'停电是灯灭的必要条件'
		])
		return qBase(
			'guanzong',
			'逻辑',
			'充分必要',
			'「如果停电，则灯会灭」为真。以下哪项一定为真？',
			k2,
			opts,
			'逆否：¬q→¬p。',
			`灯不灭→没停电。选 ${k2}。`,
			2
		)
	}
	if (kind === 2) {
		const k2 = pick(rnd, ['A', 'B', 'C', 'D'])
		const animal = pick(rnd, ['海豚', '企鹅', '鸵鸟', '蝙蝠'])
		const claim = pick(rnd, ['会飞', '会游泳', '恒温', '有羽毛'])
		const correct = `有的${animal}不会${claim.replace('会', '')}`.replace('不会飞', '不会飞')
		// simpler:
		const correct2 = `有的天鹅不是白色的`
		const opts = optionsABCD(rnd, k2, correct2, [
			'所有天鹅都不是白色的',
			'有的天鹅是白色的',
			'没有天鹅是白色的'
		])
		return qBase(
			'guanzong',
			'逻辑',
			'直言命题',
			'「所有的天鹅都是白色的」为假。以下哪项一定为真？',
			k2,
			opts,
			'全称肯定的否定是特称否定。',
			`有的天鹅不是白色的。选 ${k2}。`,
			2
		)
	}
	if (kind === 3) {
		const k2 = pick(rnd, ['A', 'B', 'C', 'D'])
		const topic = pick(rnd, ['常跑步的人睡眠更好', '常听音乐的人更放松', '常阅读的人词汇量更大'])
		const correct = '相关不等于因果，也可能存在因果倒置或其他共同原因'
		const opts = optionsABCD(rnd, k2, correct, [
			'有人不喜欢该活动',
			'样本量可能不够大',
			'该活动需要花钱'
		])
		return qBase(
			'guanzong',
			'逻辑',
			'论证逻辑',
			`研究发现：${topic}。因此想获得相应好处就应该这样做。以下哪项最能指出推理漏洞？`,
			k2,
			opts,
			'相关推因果的经典漏洞。',
			`指出相关≠因果。选 ${k2}。`,
			3
		)
	}
	if (kind === 4) {
		const k2 = pick(rnd, ['A', 'B', 'C', 'D'])
		const correct = '未采取措施的对照群体同期指标明显更差'
		const opts = optionsABCD(rnd, k2, correct, [
			'措施成本很低',
			'有人支持该措施',
			'指标很难测量'
		])
		return qBase(
			'guanzong',
			'逻辑',
			'论证逻辑',
			`结论：采取某培训措施能提升团队效率。下列哪项最能加强？`,
			k2,
			opts,
			'对照实验加强因果。',
			`对照更差可加强。选 ${k2}。`,
			2
		)
	}
	if (kind === 5) {
		const k2 = pick(rnd, ['A', 'B', 'C', 'D'])
		const correct = a
		const opts = optionsABCD(rnd, k2, correct, [b, pick(rnd, names.filter((x) => x !== a && x !== b)), '无法确定'])
		return qBase(
			'guanzong',
			'逻辑',
			'真假话',
			`甲乙丙三人只有一人说真话。甲：「是${b}干的。」乙：「不是我干的。」丙：「不是${a}干的。」若只有一人作案且答案为 ${a} 的情形成立（甲假乙真丙假），则作案者是？`,
			k2,
			opts,
			'假设法验证唯一真话。',
			`当作案者为${a}时仅乙真，与条件相符。选 ${k2}。`,
			3
		)
	}
	if (kind === 6) {
		const k2 = pick(rnd, ['A', 'B', 'C', 'D'])
		const correct = `${a}没去`
		const opts = optionsABCD(rnd, k2, correct, [`${a}去了`, `${b}去了`, '无法确定'])
		return qBase(
			'guanzong',
			'逻辑',
			'形式逻辑',
			`已知：①若${a}去开会，则${b}也去；②${b}没去。由此可推出？`,
			k2,
			opts,
			'否定后件否定前件。',
			`${a}→${b}，非${b}⇒非${a}。选 ${k2}。`,
			2
		)
	}
	const k2 = pick(rnd, ['A', 'B', 'C', 'D'])
	const correct = `${a}没有被视为放弃`
	const opts = optionsABCD(rnd, k2, correct, [
		`${a}一定是第一个到的`,
		'当时名额一定很多',
		`${a}一定提前准备过`
	])
	return qBase(
		'guanzong',
		'逻辑',
		'结论题',
		`规定：迟到超过 10 分钟视为放弃选课。${a}第 ${randInt(rnd, 3, 9)} 分钟到场并成功选到课。由此可推出？`,
		k2,
		opts,
		'能选到课 ⇒ 未被放弃。',
		`选 ${k2}。`,
		2
	)
}

/** ---------- 英语二模板 ---------- */
const vocab = [
	['abandon', '放弃', '获得', '坚持', '赞美'],
	['benefit', '益处', '伤害', '成本', '风险'],
	['crucial', '关键的', '次要的', '偶然的', '虚假的'],
	['decline', '下降', '上升', '稳定', '爆炸'],
	['efficient', '高效的', '低效的', '昂贵的', '危险的'],
	['flexible', '灵活的', '僵硬的', '脆弱的', '粗糙的'],
	['generate', '产生', '销毁', '隐藏', '忽略'],
	['highlight', '强调', '削弱', '删除', '推迟'],
	['indicate', '表明', '否认', '禁止', '庆祝'],
	['justify', '证明…正当', '指责', '嘲笑', '遗忘'],
	['maintain', '维持', '破坏', '出售', '逃避'],
	['objective', '目标', '障碍', '借口', '谣言'],
	['persist', '坚持', '放弃', '犹豫', '妥协'],
	['relevant', '相关的', '无关的', '过时的', '秘密的'],
	['significant', '显著的', '微小的', '模糊的', '临时的'],
	['tend', '倾向于', '拒绝', '命令', '禁止'],
	['undergo', '经历', '避免', '发明', '翻译'],
	['valid', '有效的', '无效的', '危险的', '冗长的'],
	['widespread', '广泛的', '罕见的', '短暂的', '局部的'],
	['yield', '产生；屈服', '购买', '关闭', '打印']
]

function genEnglish(rnd, i) {
	const kind = i % 5
	if (kind === 0) {
		const [word, right, w1, w2, w3] = pick(rnd, vocab)
		const k2 = pick(rnd, ['A', 'B', 'C', 'D'])
		const opts = optionsABCD(rnd, k2, right, [w1, w2, w3])
		return qBase(
			'english',
			'词汇',
			'词义',
			`The word “${word}” is closest in meaning to which of the following Chinese options?`,
			k2,
			opts,
			'根据常用考研词汇义项选择。',
			`“${word}” ≈ ${right}。选 ${k2}。`,
			1 + (i % 3)
		)
	}
	if (kind === 1) {
		const k2 = pick(rnd, ['A', 'B', 'C', 'D'])
		const correct = 'has been rising'
		const opts = optionsABCD(rnd, k2, correct, ['have been rising', 'is risen', 'were rising'])
		const year = 2010 + (i % 15)
		return qBase(
			'english',
			'完形',
			'时态语态',
			`The number of online learners ______ steadily since ${year}.`,
			k2,
			opts,
			'主语 the number 单数；since 常配现在完成进行/完成。',
			`选 has been rising。选 ${k2}。`,
			2
		)
	}
	if (kind === 2) {
		const k2 = pick(rnd, ['A', 'B', 'C', 'D'])
		const correct = 'which'
		const opts = optionsABCD(rnd, k2, correct, ['who', 'what', 'where'])
		return qBase(
			'english',
			'完形',
			'从句',
			`They discussed a plan ______ could reduce costs without cutting jobs.`,
			k2,
			opts,
			'先行词 plan 指物，用 which/that；选项给 which。',
			`选 ${k2}。`,
			2
		)
	}
	if (kind === 3) {
		const k2 = pick(rnd, ['A', 'B', 'C', 'D'])
		const correct = 'However'
		const opts = optionsABCD(rnd, k2, correct, ['Therefore', 'Moreover', 'Likewise'])
		return qBase(
			'english',
			'阅读',
			'逻辑衔接',
			`Sales grew in the first quarter. ______, customer complaints also increased.`,
			k2,
			opts,
			'前后转折。',
			`However 表转折。选 ${k2}。`,
			2
		)
	}
	const k2 = pick(rnd, ['A', 'B', 'C', 'D'])
	const city = pick(rnd, ['city libraries', 'community centers', 'public parks', 'museums'])
	const correct = `They can help people access learning resources`
	const opts = optionsABCD(rnd, k2, correct, [
		'They mainly serve as tourist attractions',
		'They have replaced all online courses',
		'They are no longer funded by the government'
	])
	return qBase(
		'english',
		'阅读',
		'细节理解',
		`According to a short notice: “Local ${city} offer free workshops and quiet study spaces for residents.” What can be inferred?`,
		k2,
		opts,
		'抓住 free workshops / study spaces。',
		`推断有助于获取学习资源。选 ${k2}。`,
		2
	)
}

function writeChunks(prefix, list) {
	ensureDir(OUT)
	// remove old bulk files for this prefix
	for (const f of fs.readdirSync(OUT)) {
		if (f.startsWith(prefix + '-bulk-') && f.endsWith('.json')) {
			fs.unlinkSync(path.join(OUT, f))
		}
	}
	let part = 1
	for (let i = 0; i < list.length; i += CHUNK) {
		const chunk = list.slice(i, i + CHUNK)
		const file = path.join(OUT, `${prefix}-bulk-${String(part).padStart(2, '0')}.json`)
		fs.writeFileSync(file, JSON.stringify(chunk, null, 0), 'utf8')
		console.log('wrote', file, chunk.length)
		part++
	}
}

function main() {
	const rndG = mulberry32(1990315)
	const rndE = mulberry32(20240922)
	const guanzong = []
	const targetG = 3000
	for (let i = 0; i < targetG; i++) {
		if (i % 2 === 0) guanzong.push(genMath(rndG, i))
		else guanzong.push(genLogic(rndG, i))
	}
	const english = []
	const targetE = 2000
	for (let i = 0; i < targetE; i++) {
		english.push(genEnglish(rndE, i))
	}
	writeChunks('guanzong', guanzong)
	writeChunks('english', english)
	// keep a small curated seed optional; bulk is source of truth for volume
	const meta = {
		guanzong: guanzong.length,
		english: english.length,
		total: guanzong.length + english.length,
		note: '模板参数化自编题，供刷题训练；非历年真题原文。'
	}
	fs.writeFileSync(path.join(OUT, 'bank-meta.json'), JSON.stringify(meta, null, 2), 'utf8')
	console.log(JSON.stringify(meta))
}

main()

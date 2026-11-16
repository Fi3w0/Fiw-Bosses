// Console-driven gameplay checks for FIW Bosses on a production server.
//
// A vanilla-protocol client cannot join this server: FIW Bosses registers its own entity types,
// so Fabric API (and NeoForge) turn away clients without the mod. Everything here therefore runs
// as console commands, and every response is read back from the server log. stdout is the JSON
// bridge to the runner: {"console": ...} lines become server console input.
//
// Installs the QA pack (examples/qa), spawns every QA boss and minion, forces phases and rages,
// checks reload, room, limits and schedules, damages a boss through a phase threshold and to
// death, checks its ground loot, rolls spawn affixes, and that a boss's phase and affixes
// survive a restart. When Fiw Custom
// Items is installed next to it, the boss must also drop a Fiw Custom Items item.
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const [, version, phase, directory] = process.argv.slice(2)
const qa = path.join(__dirname, '..', '..', 'examples', 'qa')
const log = path.join(directory, `${phase}.log`)
const emit = value => process.stdout.write(JSON.stringify(value) + '\n')
const delay = ms => new Promise(resolve => setTimeout(resolve, ms))
const deadline = setTimeout(() => { emit({ error: 'Console verification timed out' }); process.exit(1) }, 240000)
const legacy = version === '1.20.1'
const tools = fs.readdirSync(path.join(directory, 'mods')).some(jar => jar.startsWith('fiw-tools-'))

const text = () => fs.readFileSync(log, 'utf8')
const mark = () => text().length
function since(start) { return text().slice(start) }
async function until(start, condition, description, timeout = 15000) {
  const end = Date.now() + timeout
  for (;;) {
    const output = since(start)
    if (condition(output)) return output
    if (Date.now() > end) throw new Error(`Timed out: ${description}; last output:\n${output.split('\n').slice(-12).join('\n')}`)
    await delay(200)
  }
}
async function run(command, wait = 1000) { emit({ console: command }); await delay(wait) }
// Repeats an async check until it holds, so a slow server only makes the test slower, not flaky.
async function eventually(check, description, timeout = 15000) {
  const end = Date.now() + timeout
  for (;;) {
    if (await check()) return
    if (Date.now() > end) throw new Error(`Timed out: ${description}`)
    await delay(1000)
  }
}
// Sends a command and waits until its response appears in the log.
async function expect(command, response, timeout) {
  const start = mark()
  emit({ console: command })
  return until(start, output => output.includes(response), `${command} -> ${response}`, timeout)
}
const boss = id => `@e[nbt={BossId:"${id}"},limit=1]`
async function phaseOf(id) {
  const output = await expect(`data get entity ${boss(id)} BossPhase`, 'has the following entity data:')
  return Number(output.match(/has the following entity data: (-?\d+)/)[1])
}
// The attribute was renamed from generic.x to x in 1.21.2; only one of the two names exists.
async function attribute(selector, name, base = false) {
  const start = mark()
  for (const id of [`minecraft:${name}`, `minecraft:generic.${name}`]) {
    emit({ console: `attribute ${selector} ${id} ${base ? 'base ' : ''}get` })
  }
  const output = await until(start, o => / is -?\d/.test(o), `attribute ${name}`)
  return Number(output.match(/ is (-?[\d.]+)/)[1])
}
// The affixes qa_affix must have: both pool entries (count 2 of 2), one from the settings library.
async function checkAffixes() {
  const saved = await expect(`data get entity ${boss('qa_affix')} BossAffixes`, 'has the following entity data:')
  assert.match(saved, /qa_swift/, 'the library affix rolled and was saved')
  assert.match(saved, /qa_armored/, 'the inline affix rolled and was saved')
  const name = await expect(`data get entity ${boss('qa_affix')} CustomName`, 'has the following entity data:')
  for (const part of ['Swift', 'Armored', 'QA Affix']) assert.match(name, new RegExp(part), `name shows ${part}`)
  assert.equal(await attribute(boss('qa_affix'), 'armor', true), 10, 'Armored adds 10 armor')
  assert(Math.abs(await attribute(boss('qa_affix'), 'max_health') - 200) < 0.01, 'Armored doubles health')
  assert(Math.abs(await attribute(boss('qa_affix'), 'movement_speed', true) - 0.4) < 0.001, 'Swift doubles speed')
}
async function count(selector) {
  const start = mark()
  emit({ console: `execute if entity ${selector}` })
  const output = await until(start, o => /Test (passed|failed)/.test(o), `count ${selector}`)
  // "Test passed, count: 2" on older versions, "Test passed. Count: 2" on newer ones.
  const passed = output.match(/Test passed[.,] count: (\d+)/i)
  return passed ? Number(passed[1]) : 0
}
// Fills are only trusted once the server confirms them: an unloaded chunk fails silently.
async function fill(command) {
  for (let attempt = 0; attempt < 20; attempt++) {
    const start = mark()
    emit({ console: command })
    const output = await until(start, o => /Successfully filled|No blocks were filled|not loaded|Unloaded|Too many/.test(o), command)
    if (/Successfully filled|No blocks were filled/.test(output)) return
    await delay(500)
  }
  throw new Error(`Test terrain was not built: ${command}`)
}
function installPack() {
  const config = path.join(directory, 'config', 'fiw_bosses')
  for (const folder of ['bosses', 'minions', 'skins']) {
    fs.mkdirSync(path.join(config, folder), { recursive: true })
    for (const file of fs.readdirSync(path.join(qa, folder))) {
      fs.copyFileSync(path.join(qa, folder, file), path.join(config, folder, file))
    }
  }
  fs.copyFileSync(path.join(qa, 'settings.json'), path.join(config, 'settings.json'))
  if (tools) {
    const items = path.join(directory, 'config', 'fiw_tools', 'items')
    fs.mkdirSync(items, { recursive: true })
    fs.writeFileSync(path.join(items, 'qa_boss_drop.json'), JSON.stringify({
      id: 'qa_boss_drop', base: 'minecraft:emerald', displayName: '&aQA Boss drop'
    }, null, 2))
  }
}

async function first() {
  installPack()
  if (tools) await expect('fiwtools reload', 'Reloaded:')
  const reload = await expect('boss reload', 'minion definitions.')
  assert.match(reload, /boss definitions \(0 failed\)/, 'every QA boss loads')
  assert.match(reload, /minion definitions \(0 failed\)/, 'every QA minion loads')
  await expect('boss validate', 'No config problems found.')
  await expect('boss list', 'qa_domain')
  emit({ assertion: 'QA pack loads with no failed or invalid configs' })

  await run('forceload add -32 -32 31 31')
  await fill('fill -24 79 -24 24 79 24 minecraft:stone')
  await fill('fill -24 80 -24 24 92 24 minecraft:air')
  const spawns = [
    ['qa_domain', '0 80 0'], ['qa_melee', '6 80 0'], ['qa_skin', '-6 80 0'], ['qa_shield', '0 80 6'],
    ['qa_golem_disguise', '10 80 10'], ['qa_golem_giant', '-12 80 -12'], ['qa_golem_small', '12 80 -12'],
    ['qa_player_giant', '-12 80 12'], ['qa_player_tiny', '4 80 -8'], ['qa_player_reach', '-4 80 -8'],
    ['qa_scale_abilities', '16 80 0'], ['qa_loot', '-20 80 0'], ['qa_affix', '20 80 0']
  ]
  for (const [id, pos] of spawns) await expect(`boss spawn ${id} ${pos}`, `Spawned boss ${id}`)
  await expect('boss spawn qa_summon variant hard 0 80 -16', 'Spawned boss qa_summon')
  for (const id of ['qa_minion_skin', 'qa_scaled_minion', 'qa_vanilla_minion']) await run(`boss minion spawn ${id}`)
  await delay(4000)
  assert.equal(await count('@e[type=fiw_bosses:boss]'), spawns.length + 1, 'every spawned boss is alive in the world')
  emit({ assertion: 'all QA bosses and minions spawn' })
  await checkAffixes()
  emit({ assertion: 'spawn affixes roll from the pool and the settings library and change name, armor, health and speed' })

  if (!legacy) {
    // 1.21.1 still names the attribute generic.scale; only one of the two names exists per version.
    const start = mark()
    await run(`attribute ${boss('qa_golem_giant')} minecraft:scale get`)
    await run(`attribute ${boss('qa_golem_giant')} minecraft:generic.scale get`)
    assert.match(since(start), /attribute .*[Ss]cale.* is 2(\.0)?\s*$/m, 'qa_golem_giant is scaled x2')
    emit({ assertion: 'boss scale attribute applied' })
  }

  for (const n of [2, 3, 4, 1]) {
    await run(`boss phase ${boss('qa_domain')} set ${n}`)
    assert.equal(await phaseOf('qa_domain'), n - 1, `qa_domain forced to phase ${n}`)
  }
  await run(`boss rage ${boss('qa_summon')} start qa_rage`, 5000)
  await run(`boss rage ${boss('qa_summon')} end`)
  emit({ assertion: 'forced phases are applied and stored; a rage starts and ends' })

  await expect('boss reload', 'minion definitions.')
  await delay(3000)
  await checkAffixes()
  emit({ assertion: 'a boss keeps its affixes through /boss reload' })
  // A 7x3x7 pocket under a stone roof is too small for the x6 boss.
  await fill('fill 20 80 20 26 82 26 minecraft:air')
  await fill('fill 20 83 20 26 83 26 minecraft:stone')
  await expect('boss spawn qa_huge_room 23 80 23', "Not enough room for boss 'qa_huge_room'")
  await run('boss limits qa_summon')
  await run('boss schedule trigger qa_schedule', 3000)
  await run('boss schedule trigger qa_schedule', 3000)
  assert.equal(await count(boss('qa_schedule')), 1, 'a schedule never keeps two live instances')
  emit({ assertion: 'reload keeps bosses alive; room check refuses a cramped spawn; schedules stay single' })

  // Real damage, not /boss kill: the fight, phase and payout paths must run.
  await run(`damage ${boss('qa_loot')} 40`)
  await eventually(async () => await phaseOf('qa_loot') === 1, 'qa_loot entered phase 2 below half health')
  await run(`damage ${boss('qa_loot')} 200`)
  await eventually(async () => await count(boss('qa_loot')) === 0, 'qa_loot died from damage')
  await eventually(async () => await count('@e[type=minecraft:item,nbt={Item:{id:"minecraft:diamond"}}]') >= 1,
    'qa_loot dropped its diamonds')
  if (tools) {
    await eventually(async () => await count(
      '@e[type=minecraft:item,nbt={Item:{components:{"minecraft:custom_data":{fiw_tools_id:"qa_boss_drop"}}}}]') >= 1,
    'qa_loot dropped the Fiw Custom Items item')
    emit({ assertion: 'boss loot includes a Fiw Custom Items item' })
  }
  await run('kill @e[type=minecraft:item]')
  emit({ assertion: 'damage moves a boss to its next phase and kills it; its loot drops' })

  await run('boss stats')
  for (const id of ['qa_melee', 'qa_skin', 'qa_shield', 'qa_golem_disguise', 'qa_golem_giant', 'qa_golem_small',
    'qa_player_giant', 'qa_player_tiny', 'qa_player_reach', 'qa_scale_abilities', 'qa_summon', 'qa_schedule']) {
    await run(`boss kill ${id}`, 500)
  }
  await run('boss minion kill all')
  await run(`boss phase ${boss('qa_domain')} set 3`, 2000)
  await delay(2000)
  assert.equal(await count('@e[type=fiw_bosses:boss]'), 2, 'only qa_domain and qa_affix are left for the restart check')
  await expect('save-all flush', 'Saved the game')
}

async function restart() {
  await delay(5000)
  await expect('boss list', 'qa_domain')
  assert.equal(await phaseOf('qa_domain'), 2, 'qa_domain is still in phase 3 after a restart')
  emit({ assertion: 'a boss keeps its phase across a restart' })
  await checkAffixes()
  emit({ assertion: 'a boss keeps its affixes across a restart' })
  await run('boss kill all', 2000)
  assert.equal(await count('@e[type=fiw_bosses:boss]'), 0, 'boss kill all removes every boss')
}

async function main() {
  if (phase === 'first') await first()
  else if (phase === 'restart') await restart()
  else throw new Error(`Unknown test phase: ${phase}`)
  clearTimeout(deadline)
  emit({ passed: phase, version, tools })
}

main().catch(error => { emit({ error: error.stack }); process.exit(1) })

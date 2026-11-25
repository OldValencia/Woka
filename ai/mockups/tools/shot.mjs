// Screenshots of a mockup page through headless Edge, without a browser extension.
//
//   node shot.mjs <page.html> <outDir> [--size 1440x900] [--themes dark,light] [--styles] [--setup file.js] <name=step>...
//   node shot.mjs --compare <dirA> <dirB>
//
// A step is `#section/scene` (navigates the hash) or JavaScript evaluated in the page; a value it
// returns is printed. Steps run in order on one page, so each sees the state the last one left.
// Every step is captured once per theme as <name>-<theme>.png; --styles also writes every
// element's computed style as <name>-<theme>.json. --setup evaluates a script file once after
// load, for helpers too long for a command line. Reduced motion is emulated.
//
// --compare reports PNGs that differ by bytes and elements whose computed style differs. The
// styles are the check that something moved: two runs of the same page already disagree in bytes
// on scenes with a dialog or menu animating in, and at 1600 px on the scaled photograph (measured
// 2026-11-25), while their computed styles agree exactly. Needs Node 22+ (built-in WebSocket).
import { spawn, spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const sleep = ms => new Promise(done => setTimeout(done, ms));
const args = process.argv.slice(2);

if (args[0] === '--compare') compare(args[1], args[2]);
else await capture(args);

async function capture(argv) {
  const options = { size: '1440x900', themes: 'dark,light', styles: false, setup: null };
  const positional = [];
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === '--size') options.size = argv[++i];
    else if (argv[i] === '--themes') options.themes = argv[++i];
    else if (argv[i] === '--styles') options.styles = true;
    else if (argv[i] === '--setup') options.setup = argv[++i];
    else positional.push(argv[i]);
  }
  const [page, outDir, ...steps] = positional;
  const [width, height] = options.size.split('x').map(Number);
  mkdirSync(outDir, { recursive: true });
  const profile = mkdtempSync(join(tmpdir(), 'woka-shot-'));
  const edge = spawn(EDGE, ['--headless=new', '--remote-debugging-port=0', `--user-data-dir=${profile}`,
    '--hide-scrollbars', `--window-size=${width},${height}`, 'about:blank'], { stdio: 'ignore' });
  try {
    const send = await connect(profile);
    await send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: false });
    await send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'reduce' }] });
    await send('Page.navigate', { url: pathToFileURL(resolve(page)).href });
    await sleep(1500);
    if (options.setup) await send('Runtime.evaluate', { expression: readFileSync(options.setup, 'utf8') });
    for (const step of steps) {
      const eq = step.indexOf('=');
      const name = step.slice(0, eq), code = step.slice(eq + 1);
      const script = code.startsWith('#') ? `location.hash=${JSON.stringify(code)}` : code;
      const result = await send('Runtime.evaluate', { expression: script, awaitPromise: true, returnByValue: true });
      if (result.exceptionDetails) console.log(`${name}: ${result.exceptionDetails.exception?.description ?? 'error'}`);
      else if (result.result?.value !== undefined) console.log(`${name}: ${JSON.stringify(result.result.value)}`);
      await sleep(900);
      for (const theme of options.themes.split(',')) {
        await send('Runtime.evaluate', { expression: `document.documentElement.dataset.theme=${JSON.stringify(theme)}` });
        await sleep(700);
        const shot = await send('Page.captureScreenshot', { format: 'png' });
        writeFileSync(join(outDir, `${name}-${theme}.png`), Buffer.from(shot.data, 'base64'));
        if (options.styles) {
          // A dump runs to megabytes, and a message that size never arrives over the
          // socket; it is parked in the page and read back in slices.
          const size = await send('Runtime.evaluate', {
            expression: `(window.__dump = JSON.stringify((${dumpStyles})())).length`, returnByValue: true
          });
          let json = '';
          for (let at = 0; at < size.result.value; at += 500000) {
            const slice = await send('Runtime.evaluate', { expression: `window.__dump.slice(${at}, ${at + 500000})`, returnByValue: true });
            json += slice.result.value;
          }
          writeFileSync(join(outDir, `${name}-${theme}.json`), json);
        }
      }
      console.log(`saved ${name}`);
    }
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  } finally {
    // Edge forks helpers that keep the debugging socket alive; the tree goes, not the parent.
    spawnSync('taskkill', ['/pid', String(edge.pid), '/T', '/F'], { stdio: 'ignore' });
    process.exit();
  }
}

// Port 0 lets Edge pick a free port and write it to DevToolsActivePort; a fixed port can still
// be held by the previous run's dying instance and hand back its stale page.
async function connect(profile) {
  let target;
  for (let i = 0; i < 50 && !target; i++) {
    try {
      const port = readFileSync(join(profile, 'DevToolsActivePort'), 'utf8').split(/\r?\n/)[0];
      const list = await (await fetch(`http://127.0.0.1:${port}/json`)).json();
      target = list.find(entry => entry.type === 'page');
    } catch { /* Edge is still starting */ }
    if (!target) await sleep(200);
  }
  if (!target) throw new Error('Edge did not open a debugging target');
  const socket = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise(done => { socket.onopen = done; });
  let id = 0;
  const pending = new Map();
  socket.onmessage = event => {
    const message = JSON.parse(event.data);
    if (pending.has(message.id)) { pending.get(message.id)(message.result ?? {}); pending.delete(message.id); }
  };
  return (method, params = {}) => new Promise((done, fail) => {
    const call = ++id;
    const timer = setTimeout(() => fail(new Error(`${method} did not answer in 60 s`)), 60000);
    pending.set(call, result => { clearTimeout(timer); done(result); });
    socket.send(JSON.stringify({ id: call, method, params }));
  });
}

// Runs in the page: element path → computed values, pseudo-elements included when drawn.
function dumpStyles() {
  // Custom properties are left out: they are the tokens' names, not what is drawn.
  const names = [...getComputedStyle(document.body)].filter(name => !name.startsWith('--'));
  const read = style => names.map(name => style.getPropertyValue(name)).join('\u0001');
  // Anchored at the nearest id, so a sibling added elsewhere does not renumber every element after it.
  const path = element => {
    const parts = [];
    for (let node = element; node && node !== document.documentElement; node = node.parentElement) {
      if (node.id) { parts.unshift('#' + node.id); break; }
      parts.unshift(`${node.tagName.toLowerCase()}:${[...node.parentElement.children].indexOf(node)}`);
    }
    return parts.join('>');
  };
  const elements = {};
  for (const element of document.querySelectorAll('html, body *')) {
    if (['SCRIPT', 'LINK', 'STYLE', 'META', 'TITLE', 'HEAD'].includes(element.tagName)) continue;
    const key = element === document.documentElement ? 'html' : path(element);
    elements[key] = read(getComputedStyle(element));
    for (const pseudo of ['::before', '::after']) {
      const style = getComputedStyle(element, pseudo);
      if (style.content !== 'none' && style.content !== 'normal') elements[key + pseudo] = read(style);
    }
  }
  return { names, elements };
}

function compare(left, right) {
  let differences = 0;
  for (const file of readdirSync(left).sort()) {
    const other = join(right, file);
    if (!existsSync(other)) { console.log(`${file}: missing in ${right}`); differences++; continue; }
    if (file.endsWith('.png')) {
      if (!readFileSync(join(left, file)).equals(readFileSync(other))) { console.log(`${file}: pixels differ`); differences++; }
      continue;
    }
    if (!file.endsWith('.json')) continue;
    const a = JSON.parse(readFileSync(join(left, file), 'utf8')), b = JSON.parse(readFileSync(other, 'utf8'));
    const changed = [];
    for (const key of new Set([...Object.keys(a.elements), ...Object.keys(b.elements)])) {
      const va = a.elements[key]?.split('\u0001'), vb = b.elements[key]?.split('\u0001');
      if (!va || !vb) { changed.push(`${key}: only in ${va ? 'first' : 'second'}`); continue; }
      const props = a.names.filter((name, i) => !name.startsWith('--') && va[i] !== vb[b.names.indexOf(name)]);
      if (props.length) changed.push(`${key}: ${props.slice(0, 6).map(p => `${p} ${va[a.names.indexOf(p)]} → ${vb[b.names.indexOf(p)]}`).join('; ')}`);
    }
    if (changed.length) { differences++; console.log(`${file}: ${changed.length} elements differ`); changed.slice(0, 8).forEach(line => console.log(`  ${line}`)); }
  }
  console.log(differences ? `${differences} files differ` : 'identical');
}

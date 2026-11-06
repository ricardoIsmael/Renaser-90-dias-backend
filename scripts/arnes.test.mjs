// Reglas del arnés del backend (2026-11-06): lo que tiene que seguir cierto para que una sesión nueva arranque bien.
// Sin dependencias: `node --test scripts/arnes.test.mjs`. Lo corren scripts/verificar.sh y trazabilidad.yml.
//
// Por qué existe: el archivo de entrada era `CLAUDE.MD`, un diseño de migración de 1046 líneas y 113 KB de agosto, que
// hablaba en futuro de algo ya construido y repetía (con versiones viejas) las reglas de `.claude/rules/`. El mapa
// ahora es `AGENTS.md`, corto, y el diseño vive en `docs/ARQUITECTURA.md`. Estas pruebas frenan que vuelva a crecer.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const RAIZ = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const leer = (a) => fs.readFileSync(path.join(RAIZ, a), 'utf8');
const agents = leer('AGENTS.md');

test('AGENTS.md es un mapa corto: ≤ 150 líneas y ≤ 16 KB (lo largo va a un documento enlazado)', () => {
  assert.ok(agents.split('\n').length <= 150, `AGENTS.md tiene ${agents.split('\n').length} líneas`);
  assert.ok(Buffer.byteLength(agents) <= 16 * 1024);
});

test('CLAUDE.md solo importa AGENTS.md y suma las reglas de graphify', () => {
  const claude = leer('CLAUDE.md');
  assert.match(claude, /^@AGENTS\.md/);
  assert.ok(claude.split('\n').length <= 60, 'CLAUDE.md creció: lo nuevo va en AGENTS.md o en .claude/rules/');
});

test('no vuelve un CLAUDE.MD en mayúsculas en la raíz (choca con CLAUDE.md en Windows y macOS)', () => {
  const raiz = execFileSync('git', ['ls-files'], { cwd: RAIZ, encoding: 'utf8' }).split('\n').filter((f) => !f.includes('/'));
  assert.deepEqual(raiz.filter((f) => f.toLowerCase() === 'claude.md' && f !== 'CLAUDE.md'), []);
});

test('cada archivo que AGENTS.md cita entre comillas invertidas existe', () => {
  const citados = [...agents.matchAll(/`((?:docs|scripts|specs|\.specify|\.github)\/[^`\s*]+?\.(?:md|mjs|sh|json|yml))`/g)].map((m) => m[1]);
  assert.ok(citados.length > 3);
  assert.deepEqual(citados.filter((c) => !fs.existsSync(path.join(RAIZ, c))), []);
});

test('las reglas 00 a 06 que AGENTS.md resume existen', () => {
  const reglas = fs.readdirSync(path.join(RAIZ, '.claude', 'rules'));
  for (const n of ['00', '01', '02', '03', '04', '05', '06']) assert.ok(reglas.some((r) => r.startsWith(`${n}-`)), `falta la regla ${n}`);
});

test('PROGRESO.md es corto y tiene sus secciones', () => {
  const p = leer('PROGRESO.md');
  assert.ok(p.split('\n').length <= 150, 'PROGRESO.md pasa de 150 líneas: borra lo viejo, ya queda en git');
  for (const s of ['## Estado verificado', '## Siguiente paso', '## A medias o pendiente', '## Sesiones']) assert.ok(p.includes(s), `falta «${s}»`);
});

test('no hay archivos compilados de Python en git', () => {
  const pyc = execFileSync('git', ['ls-files'], { cwd: RAIZ, encoding: 'utf8' }).split('\n').filter((f) => /__pycache__|\.pyc$/.test(f));
  assert.deepEqual(pyc, []);
});

test('los scripts de worktree y del grafo existen, son ejecutables y su bash es válido (arnés, 2026-11-06)', () => {
  for (const s of ['worktree-nuevo.sh', 'worktree-cerrar.sh', 'worktree-viejos.sh', 'grafo-al-dia.sh']) {
    const p = path.join(RAIZ, 'scripts', s);
    assert.ok(fs.existsSync(p), `falta scripts/${s}`);
    assert.ok(fs.statSync(p).mode & 0o111, `scripts/${s} no es ejecutable`);
    execFileSync('bash', ['-n', p]);
  }
});

test('AGENTS.md manda a usar el grafo primero y a cerrar el worktree al fusionar', () => {
  assert.match(agents, /graphify query/);
  assert.match(agents, /scripts\/grafo-al-dia\.sh/);
  assert.match(agents, /scripts\/worktree-cerrar\.sh/);
});

test('un PR no está listo sin el E2E de su spec y los logs revisados (regla del dueño, 2026-11-06)', () => {
  assert.match(agents, /no está listo sin el E2E de su spec/);
  assert.match(agents, /logcat/);
});

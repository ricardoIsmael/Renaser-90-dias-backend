#!/usr/bin/env node
/**
 * Trazabilidad de specs: cada requisito (FR-NNN) y cada criterio de éxito (SC-NNN) de las specs
 * que se exigen tiene que tener, por lo menos, una prueba que lo cubra.
 *
 *   node scripts/trazabilidad-specs.mjs [ruta-de-la-app] [opciones]
 *
 *   ruta-de-la-app   checkout del frontend (donde viven los Jest de `src/ ** /__tests__` y los flujos
 *                    de `.maestro/`). Sin ella solo se buscan marcas en el backend.
 *   --backend <dir>  raíz del backend (por defecto, la carpeta que contiene `scripts/`).
 *   --lista <file>   lista de specs que se exigen (por defecto `<backend>/specs/trazabilidad.json`,
 *                    un arreglo de números: ["035","036","037"]; también vale el nombre completo de la
 *                    carpeta, útil si dos specs comparten número).
 *   --spec <ids>     revisa solo esas specs (separadas por coma) y NO lee la lista. Sirve para probar
 *                    una spec suelta contra su rama.
 *   --md <archivo>   agrega además las tablas en Markdown (el CI lo apunta a $GITHUB_STEP_SUMMARY).
 *   --detalle        en la tabla, la ruta completa de cada prueba en vez de `Archivo:línea`.
 *
 * LA CONVENCIÓN (cada requisito exigido lleva una prueba marcada):
 * la prueba que cubre un requisito lleva, en un comentario, una marca `spec NNN FR-NNN`.
 *
 *   Java / Jest   // spec 037 FR-005          (o dentro del @DisplayName / del nombre del `it`)
 *   Maestro       # spec 037 FR-005
 *   Varios        // spec 037 FR-005, FR-006 SC-002
 *
 * Qué cuenta como requisito: solo los que la spec DEFINE (una viñeta `- **FR-001**: …`, un
 * encabezado `### FR-001`, o la primera celda de una tabla), no los que apenas menciona. Si un
 * criterio no se puede probar con código (por ejemplo «el dueño aprueba el texto»), la spec lo
 * dice en su misma viñeta con `[sin prueba automática: el motivo]` y queda exento, con su motivo.
 *
 * Sale con 1 si un requisito exigido no tiene ninguna prueba, o si una marca apunta a un
 * requisito que la spec no define (casi siempre un error de tipeo). Sale con 2 si la configuración
 * está mal (una spec de la lista sin carpeta, sin requisitos, JSON roto, ruta que no existe).
 *
 * Sin dependencias: solo Node 18 o más.
 */
import { appendFileSync, existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { basename, dirname, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

// ───────────────────────────── lectura de las specs ─────────────────────────────

const ID = '((?:FR|SC)-\\d{3})';
/** Las formas en que una línea de spec.md DEFINE un requisito. Las menciones al paso no cuentan. */
const DEFINICIONES = [
  new RegExp(`^\\s*(?:(?:[-*+]|\\d+[.)])\\s+)?\\*\\*${ID}\\*\\*`), //   - **FR-001**: …   (con o sin viñeta)
  new RegExp(`^\\s*(?:[-*+]|\\d+[.)])\\s+${ID}\\s*[:.)—–]`), //          - FR-001: …
  new RegExp(`^\\s*#{1,6}\\s+\\**${ID}\\b`), //                            ### FR-001 …
  new RegExp(`^\\s*\\|\\s*\\**${ID}\\**\\s*\\|`), //                       | FR-001 | … |
];
const EXENTO = /\[sin prueba autom[aá]tica\s*:\s*([^\]]*?)\s*\]/i;

function idDefinido(linea) {
  for (const patron of DEFINICIONES) {
    const m = patron.exec(linea);
    if (m) return m[1];
  }
  return null;
}

const sangria = (linea) => linea.length - linea.trimStart().length;
const arrancaEstructura = (linea) => /^\s*(?:[-*+]|\d+[.)])\s/.test(linea) || /^\s*[#|]/.test(linea);

/** Cuántas líneas, después de la que define, siguen siendo de la misma viñeta. */
function continuaciones(lineas, desde, sangriaBase) {
  let fin = desde;
  while (fin < lineas.length) {
    const l = lineas[fin];
    if (l.trim() === '' || idDefinido(l)) break;
    if (arrancaEstructura(l) && sangria(l) <= sangriaBase) break;
    fin += 1;
  }
  return fin;
}

function resumir(cuerpo) {
  const sinMarca = cuerpo.replace(EXENTO, '');
  const limpio = sinMarca
    .replace(/^\s*(?:[-*+]|\d+[.)]|#{1,6}|\|)?\s*\**(?:FR|SC)-\d{3}\**\s*[:.)—–|]?\s*/, '')
    .replace(/[*`_]/g, '')
    .replace(/\s+/g, ' ')
    .trim();
  return limpio.length > 64 ? `${limpio.slice(0, 63).trimEnd()}…` : limpio;
}

/**
 * @returns {{requisitos: Array<{id:string,tipo:'FR'|'SC',resumen:string,exento:string|null}>, repetidos: string[]}}
 */
export function leerRequisitos(textoDeLaSpec) {
  const lineas = textoDeLaSpec.split(/\r?\n/);
  const requisitos = [];
  const vistos = new Set();
  const repetidos = [];
  for (let i = 0; i < lineas.length; i += 1) {
    const id = idDefinido(lineas[i]);
    if (!id) continue;
    const fin = continuaciones(lineas, i + 1, sangria(lineas[i]));
    const cuerpo = lineas.slice(i, fin).join(' ');
    if (vistos.has(id)) {
      repetidos.push(id);
      continue;
    }
    vistos.add(id);
    const exento = EXENTO.exec(cuerpo);
    requisitos.push({
      id,
      tipo: id.slice(0, 2),
      resumen: resumir(cuerpo),
      exento: exento ? exento[1] || '(sin motivo)' : null,
    });
  }
  return { requisitos, repetidos };
}

// ───────────────────────────── lectura de las marcas ─────────────────────────────

const MARCA = new RegExp(
  '\\b[Ss]pec[ \\t]+(\\d{3}[a-z]?)\\b[ \\t]+' +
    '((?:FR|SC)-\\d{3}(?:[ \\t]*(?:[,;/+]|\\by\\b|\\be\\b)?[ \\t]*(?:FR|SC)-\\d{3})*)',
  'g',
);

/** @returns {Array<{spec:string, req:string, linea:number}>} */
export function leerMarcas(texto) {
  const marcas = [];
  texto.split(/\r?\n/).forEach((linea, i) => {
    MARCA.lastIndex = 0;
    let m;
    while ((m = MARCA.exec(linea)) !== null) {
      for (const req of m[2].match(/(?:FR|SC)-\d{3}/g)) {
        marcas.push({ spec: m[1], req, linea: i + 1 });
      }
    }
  });
  return marcas;
}

// ───────────────────────────── dónde buscar pruebas ─────────────────────────────

const CARPETAS_IGNORADAS = new Set(['node_modules', '.git', 'target', 'build', 'dist', 'graphify-out', '.expo']);
const TEXTO_DEL_BACKEND = new Set(['.java', '.kt', '.groovy', '.sql', '.yaml', '.yml', '.json', '.xml', '.properties', '.md', '.txt', '.sh']);
const CODIGO_DE_LA_APP = new Set(['.ts', '.tsx', '.js', '.jsx', '.mjs', '.cjs']);
const MAX_BYTES = 2 * 1024 * 1024;

const extension = (nombre) => (nombre.includes('.') ? nombre.slice(nombre.lastIndexOf('.')).toLowerCase() : '');

/** Pruebas del backend: todo texto bajo `src/test/`. */
const esPruebaDelBackend = (rel) => rel.startsWith(`src${sep}test${sep}`) && TEXTO_DEL_BACKEND.has(extension(rel));

/** Pruebas de la app: Jest (`__tests__` o `*.test.*` / `*.spec.*` bajo `src/`) y Maestro (`.maestro/**.yaml`). */
function esPruebaDeLaApp(rel) {
  const ext = extension(rel);
  const partes = rel.split(sep);
  if (partes[0] === '.maestro') return ext === '.yaml' || ext === '.yml';
  if (partes[0] !== 'src' || !CODIGO_DE_LA_APP.has(ext)) return false;
  return partes.includes('__tests__') || /\.(test|spec)\.[a-z]+$/i.test(rel);
}

function* recorrer(raiz, esPrueba) {
  const pila = [raiz];
  while (pila.length > 0) {
    const dir = pila.pop();
    for (const e of readdirSync(dir, { withFileTypes: true })) {
      const ruta = join(dir, e.name);
      if (e.isDirectory()) {
        if (!CARPETAS_IGNORADAS.has(e.name)) pila.push(ruta);
      } else if (e.isFile() && esPrueba(relative(raiz, ruta))) {
        yield ruta;
      }
    }
  }
}

function esTexto(buffer) {
  return !buffer.subarray(0, 8192).includes(0);
}

/** @returns {Map<string, Array<{repo:string, ruta:string, linea:number}>>} clave `NNN|FR-001` */
export function buscarPruebas({ backend, app }) {
  const porRequisito = new Map();
  const agregar = (repo, raiz, esPrueba) => {
    for (const archivo of recorrer(raiz, esPrueba)) {
      if (statSync(archivo).size > MAX_BYTES) continue;
      const bytes = readFileSync(archivo);
      if (!esTexto(bytes)) continue;
      for (const { spec, req, linea } of leerMarcas(bytes.toString('utf8'))) {
        const clave = `${spec}|${req}`;
        if (!porRequisito.has(clave)) porRequisito.set(clave, []);
        const ya = porRequisito.get(clave);
        const ruta = relative(raiz, archivo).split(sep).join('/');
        if (!ya.some((p) => p.repo === repo && p.ruta === ruta && p.linea === linea)) ya.push({ repo, ruta, linea });
      }
    }
  };
  if (existsSync(join(backend, 'src', 'test'))) agregar('backend', backend, esPruebaDelBackend);
  if (app) agregar('app', app, esPruebaDeLaApp);
  return porRequisito;
}

// ───────────────────────────── qué specs se exigen ─────────────────────────────

/** `035` → la carpeta `specs/035-…`; también acepta el nombre completo. */
function carpetaDeLaSpec(carpetaSpecs, entrada) {
  const nombres = existsSync(carpetaSpecs) ? readdirSync(carpetaSpecs, { withFileTypes: true }).filter((e) => e.isDirectory()).map((e) => e.name) : [];
  const exacta = nombres.filter((n) => n === entrada);
  const candidatas = exacta.length > 0 ? exacta : nombres.filter((n) => n.startsWith(`${entrada}-`));
  if (candidatas.length === 0) {
    return { error: `la spec ${entrada} está en la lista pero no existe specs/${entrada}-*/ (¿falta fusionar su rama?)` };
  }
  if (candidatas.length > 1) {
    return { error: `la spec ${entrada} es ambigua (${candidatas.join(', ')}): pon el nombre completo de la carpeta en la lista` };
  }
  return { nombre: candidatas[0] };
}

// ───────────────────────────── evaluación y reporte ─────────────────────────────

/**
 * @returns {{specs: Array, configuracion: string[]}}
 */
export function evaluar({ backend, entradas, pruebas }) {
  const carpetaSpecs = join(backend, 'specs');
  const configuracion = [];
  const specs = [];
  for (const entrada of entradas) {
    const hallada = carpetaDeLaSpec(carpetaSpecs, entrada);
    if (hallada.error) {
      configuracion.push(hallada.error);
      continue;
    }
    const archivo = join(carpetaSpecs, hallada.nombre, 'spec.md');
    if (!existsSync(archivo)) {
      configuracion.push(`specs/${hallada.nombre}/ no tiene spec.md`);
      continue;
    }
    const { requisitos, repetidos } = leerRequisitos(readFileSync(archivo, 'utf8'));
    if (requisitos.length === 0) {
      configuracion.push(`specs/${hallada.nombre}/spec.md no define ningún FR-NNN ni SC-NNN (se leen de viñetas como «- **FR-001**: …»)`);
      continue;
    }
    for (const id of repetidos) configuracion.push(`specs/${hallada.nombre}/spec.md define ${id} más de una vez`);
    const numero = /^\d{3}[a-z]?/.exec(hallada.nombre)[0];
    const filas = requisitos.map((r) => {
      const lista = pruebas.get(`${numero}|${r.id}`) ?? [];
      const estado = lista.length > 0 ? 'cubierto' : r.exento !== null ? 'exento' : 'FALTA';
      return { ...r, pruebas: lista, estado };
    });
    const definidos = new Set(requisitos.map((r) => r.id));
    const huerfanas = [...pruebas.entries()]
      .filter(([clave]) => clave.startsWith(`${numero}|`) && !definidos.has(clave.split('|')[1]))
      .map(([clave, lista]) => ({ req: clave.split('|')[1], pruebas: lista }));
    specs.push({ numero, nombre: hallada.nombre, archivo: `specs/${hallada.nombre}/spec.md`, filas, huerfanas });
  }
  return { specs, configuracion };
}

const etiquetaDe = (p, detalle) => `${detalle ? `${p.repo}:${p.ruta}` : basename(p.ruta)}:${p.linea}`;

function pruebasEnTexto(fila, detalle) {
  if (fila.estado === 'exento') return `exento: ${fila.exento}`;
  if (fila.pruebas.length === 0) return '—';
  const mostrar = detalle ? fila.pruebas : fila.pruebas.slice(0, 2);
  const resto = fila.pruebas.length - mostrar.length;
  return `${mostrar.map((p) => etiquetaDe(p, detalle)).join(', ')}${resto > 0 ? ` (+${resto} más)` : ''}`;
}

const cuenta = (filas, tipo) => filas.filter((f) => f.tipo === tipo).length;
const resumenDeLaSpec = (s) =>
  `${cuenta(s.filas, 'FR')} FR, ${cuenta(s.filas, 'SC')} SC · ${s.filas.filter((f) => f.estado === 'cubierto').length} cubiertos, ` +
  `${s.filas.filter((f) => f.estado === 'exento').length} exentos, ${s.filas.filter((f) => f.estado === 'FALTA').length} sin prueba`;

export function tablaEnTexto(spec, detalle = false) {
  const filas = spec.filas.map((f) => [f.id, f.resumen, pruebasEnTexto(f, detalle), f.estado]);
  const cabecera = ['Requisito', 'Qué pide', 'Pruebas', 'Estado'];
  const ancho = cabecera.map((c, i) => Math.max(c.length, ...filas.map((f) => [...f[i]].length)));
  const linea = (celdas) => celdas.map((c, i) => c + ' '.repeat(ancho[i] - [...c].length)).join('  ').trimEnd();
  const salida = [`Spec ${spec.numero} · ${spec.nombre}  (${resumenDeLaSpec(spec)})`, `  ${linea(cabecera)}`, `  ${ancho.map((a) => '-'.repeat(a)).join('  ')}`];
  for (const f of filas) salida.push(`  ${linea(f)}`);
  for (const h of spec.huerfanas) {
    salida.push(`  ! marca huérfana: ${h.req} no está definido en ${spec.archivo} (${h.pruebas.map((p) => etiquetaDe(p, detalle)).join(', ')})`);
  }
  return salida.join('\n');
}

export function tablaEnMarkdown(spec, detalle = false) {
  const celda = (t) => t.replace(/\|/g, '\\|');
  const salida = [`### Spec ${spec.numero} · ${spec.nombre}`, '', `${resumenDeLaSpec(spec)}`, '', '| Requisito | Qué pide | Pruebas | Estado |', '| --- | --- | --- | --- |'];
  for (const f of spec.filas) {
    salida.push(`| ${f.id} | ${celda(f.resumen)} | ${celda(pruebasEnTexto(f, detalle))} | ${f.estado === 'FALTA' ? '**FALTA**' : f.estado} |`);
  }
  for (const h of spec.huerfanas) {
    salida.push('', `> Marca huérfana: \`${h.req}\` no está definido en \`${spec.archivo}\` (${h.pruebas.map((p) => etiquetaDe(p, detalle)).join(', ')})`);
  }
  return `${salida.join('\n')}\n`;
}

// ───────────────────────────── línea de comandos ─────────────────────────────

function leerArgumentos(argv) {
  const a = { app: null, backend: null, lista: null, specs: null, md: null, detalle: false, ayuda: false, errores: [] };
  const valorDe = (i, nombre) => {
    if (i + 1 >= argv.length) a.errores.push(`${nombre} necesita un valor`);
    return argv[i + 1];
  };
  for (let i = 0; i < argv.length; i += 1) {
    const x = argv[i];
    if (x === '--help' || x === '-h') a.ayuda = true;
    else if (x === '--detalle') a.detalle = true;
    else if (x === '--backend') a.backend = valorDe(i++, x);
    else if (x === '--lista') a.lista = valorDe(i++, x);
    else if (x === '--md') a.md = valorDe(i++, x);
    else if (x === '--spec') a.specs = (valorDe(i++, x) ?? '').split(',').map((s) => s.trim()).filter(Boolean);
    else if (x.startsWith('--')) a.errores.push(`opción desconocida: ${x}`);
    else if (a.app === null) a.app = x;
    else a.errores.push(`sobra el argumento «${x}»`);
  }
  return a;
}

export function main(argv, io = { out: (t) => console.log(t), err: (t) => console.error(t) }) {
  const a = leerArgumentos(argv);
  if (a.ayuda) {
    io.out('Uso: node scripts/trazabilidad-specs.mjs [ruta-de-la-app] [--backend dir] [--lista archivo] [--spec 035,036] [--md archivo] [--detalle]');
    return 0;
  }
  const configuracion = [...a.errores];
  const backend = resolve(a.backend ?? join(dirname(fileURLToPath(import.meta.url)), '..'));
  const app = a.app ? resolve(a.app) : null;
  if (!existsSync(backend)) configuracion.push(`no existe el backend: ${backend}`);
  if (app && !existsSync(app)) configuracion.push(`no existe la carpeta de la app: ${app}`);

  let entradas = a.specs;
  if (entradas === null && configuracion.length === 0) {
    const lista = resolve(a.lista ?? join(backend, 'specs', 'trazabilidad.json'));
    try {
      const json = JSON.parse(readFileSync(lista, 'utf8'));
      if (!Array.isArray(json) || json.some((e) => typeof e !== 'string')) throw new Error('tiene que ser un arreglo de textos, por ejemplo ["035","036"]');
      entradas = json;
    } catch (e) {
      configuracion.push(`no se pudo leer la lista de specs (${lista}): ${e.message}`);
    }
  }
  if (entradas !== null && entradas.length === 0 && configuracion.length === 0) configuracion.push('la lista de specs está vacía');

  let specs = [];
  if (configuracion.length === 0) {
    const pruebas = buscarPruebas({ backend, app });
    const resultado = evaluar({ backend, entradas, pruebas });
    specs = resultado.specs;
    configuracion.push(...resultado.configuracion);
    if (!app) io.out('Sin ruta de la app: solo se buscan marcas en las pruebas del backend.\n');
  }

  for (const s of specs) {
    io.out(`${tablaEnTexto(s, a.detalle)}\n`);
    if (a.md) appendFileSync(a.md, `${tablaEnMarkdown(s, a.detalle)}\n`);
  }

  const sinPrueba = specs.flatMap((s) => s.filas.filter((f) => f.estado === 'FALTA').map((f) => `${s.numero} ${f.id}`));
  const huerfanas = specs.flatMap((s) => s.huerfanas.map((h) => `${s.numero} ${h.req}`));
  const exentos = specs.reduce((n, s) => n + s.filas.filter((f) => f.estado === 'exento').length, 0);
  const total = specs.reduce((n, s) => n + s.filas.length, 0);

  if (configuracion.length > 0) {
    io.err(`Configuración con problemas:\n${configuracion.map((c) => `  - ${c}`).join('\n')}`);
    if (a.md) appendFileSync(a.md, `> **Configuración con problemas:** ${configuracion.join('; ')}\n`);
  }
  if (sinPrueba.length > 0) io.err(`Requisitos sin ninguna prueba (${sinPrueba.length}): ${sinPrueba.join(', ')}`);
  if (huerfanas.length > 0) io.err(`Marcas que apuntan a un requisito que la spec no define (${huerfanas.length}): ${huerfanas.join(', ')}`);

  const cubiertos = total - sinPrueba.length - exentos;
  const linea = `${specs.length} spec(s), ${total} requisitos: ${cubiertos} cubiertos, ${exentos} exentos, ${sinPrueba.length} sin prueba.`;
  if (configuracion.length > 0) {
    io.out(linea);
    return 2;
  }
  io.out(sinPrueba.length + huerfanas.length === 0 ? `OK · ${linea}` : `FALLA · ${linea}`);
  return sinPrueba.length + huerfanas.length === 0 ? 0 : 1;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  process.exitCode = main(process.argv.slice(2));
}

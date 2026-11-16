// Familias de errores que se repiten, convertidas en chequeos (2026-11-06).
//
// Cada prueba nace de una familia de `docs/BITACORA_ERRORES.md`: lo que la bitácora repetía como «cómo evitarlo» en
// prosa, acá se ejecuta. Sin dependencias y en segundos: `node --test scripts/reglas-de-codigo.test.mjs` (lo corren
// `scripts/verificar.sh` y trazabilidad.yml).
//
// Las violaciones que ya existían el día que nació el chequeo están en `scripts/reglas-de-codigo-excepciones.json`, con fecha y
// su issue. Esa lista solo puede achicarse: si arreglas una, borra su línea (la prueba te avisa si sobra).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const RAIZ = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const EXC = JSON.parse(fs.readFileSync(path.join(RAIZ, 'scripts', 'reglas-de-codigo-excepciones.json'), 'utf8'));
const rel = (p) => path.relative(RAIZ, p).split(path.sep).join('/');

function archivos(dir, filtro) {
  const out = [];
  const pila = [path.join(RAIZ, dir)];
  while (pila.length) {
    const d = pila.pop();
    if (!fs.existsSync(d)) continue;
    for (const e of fs.readdirSync(d, { withFileTypes: true })) {
      const p = path.join(d, e.name);
      if (e.isDirectory()) pila.push(p);
      else if (filtro(e.name)) out.push(p);
    }
  }
  return out.sort();
}

/** Quita comentarios de Java (// y /* *\/) conservando los saltos de línea, para que los números de línea sigan. */
function sinComentarios(java) {
  return java.replace(/\/\*[\s\S]*?\*\//g, (m) => m.replace(/[^\n]/g, ' ')).replace(/(^|[^:"])\/\/[^\n]*/g, '$1');
}

const JAVA_MAIN = archivos('src/main/java', (n) => n.endsWith('.java'));
const JAVA_TEST = archivos('src/test/java', (n) => n.endsWith('.java'));

/** La base para comparar con master: el merge-base con origin/master, o null si no hay historia (checkout superficial). */
function base() {
  try {
    return execFileSync('git', ['merge-base', 'HEAD', 'origin/master'], { cwd: RAIZ, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
  } catch {
    return null;
  }
}

// ── Familia «tiempo» (E-91, E-105, E-534, E-550, E-556, E-615…) ────────────────────────────────────────────────────
test('tiempo: ningún LocalDate/LocalDateTime/LocalTime/ZonedDateTime/OffsetDateTime.now() en src/main (el día sale de Clock y la zona de la persona)', () => {
  const malos = [];
  for (const f of JAVA_MAIN) {
    if (EXC.tiempoServidor.archivos.includes(rel(f))) continue;
    sinComentarios(fs.readFileSync(f, 'utf8')).split('\n').forEach((l, i) => {
      if (/\b(LocalDate|LocalDateTime|LocalTime|ZonedDateTime|OffsetDateTime)\.now\(/.test(l)) malos.push(`${rel(f)}:${i + 1}`);
    });
  }
  assert.deepEqual(malos, [], 'Usa clock.now().atZone(zona de la persona).toLocalDate() (.claude/rules/02, E-91): ' + malos.join(', '));
});

// ── Familia «migraciones» (E-86, E-157, E-170, E-211, E-262, issue #61) ────────────────────────────────────────────
const MIGR = archivos('src/main/resources/db/migration', (n) => /^V\d+__.*\.sql$/.test(n));
const version = (f) => Number(path.basename(f).match(/^V(\d+)__/)[1]);

test('scripts: la limpieza al salir fija la ruta al crearla (trap con comillas dobles), nunca una variable que puede no existir en el EXIT', () => {
  // Con comillas simples, `trap 'rm -f "$x"' EXIT` expande $x AL SALIR: si era local de una función ya no existe y, con set -u,
  // el script termina en error después de hacer su trabajo (pasó dos veces el 11/11/2026, E-679). Se escribe trap "rm -f '$x'" EXIT.
  const malos = [];
  for (const raiz of ['scripts', 'infra']) {
    if (!fs.existsSync(path.join(RAIZ, raiz))) continue;
    for (const f of execFileSync('git', ['ls-files', raiz], { cwd: RAIZ, encoding: 'utf8' }).split('\n').filter(x => x.endsWith('.sh'))) {
      fs.readFileSync(path.join(RAIZ, f), 'utf8').split('\n').forEach((linea, n) => {
        if (/trap '[^']*\$[^']*' EXIT/.test(linea)) malos.push(`${f}:${n + 1}`);
      });
    }
  }
  assert.deepEqual(malos, [], `Usa trap "rm -f '$ruta'" EXIT (comillas dobles afuera) en: ${malos.join(', ')}`);
});

test('migraciones: cada versión de Flyway es única', () => {
  const vistas = new Map();
  const repetidas = [];
  for (const f of MIGR) {
    const v = version(f);
    if (vistas.has(v)) repetidas.push(`V${v}: ${vistas.get(v)} y ${path.basename(f)}`);
    vistas.set(v, path.basename(f));
  }
  assert.deepEqual(repetidas, [], 'Dos migraciones con el mismo número (E-262): renumera la tuya por encima de la última de master.');
});

test('migraciones: cada una fija el search_path o califica con renaser. (issue #61: la V160 migraba sobre public)', () => {
  const malas = MIGR.filter((f) => !EXC.migracionSinEsquema.archivos.includes(path.basename(f)))
    .filter((f) => { const t = fs.readFileSync(f, 'utf8'); return !/search_path/i.test(t) && !/\brenaser\./.test(t); })
    .map((f) => path.basename(f));
  assert.deepEqual(malas, [], 'Agrega al principio: SET search_path TO renaser, public;');
});

test('migraciones: no se edita ni se borra una que ya está en master, y una nueva va por encima de la última de master (E-86, E-262)', (t) => {
  const b = base();
  if (!b) return t.skip('sin origin/master en este checkout (CI superficial): no hay contra qué comparar');
  const dir = 'src/main/resources/db/migration';
  const cambios = execFileSync('git', ['diff', '--name-status', b, '--', dir], { cwd: RAIZ, encoding: 'utf8' })
    .split('\n').filter(Boolean).map((l) => l.split('\t'));
  const tocadas = cambios.filter(([estado]) => estado !== 'A').map(([e, ...p]) => `${e} ${p.join(' → ')}`);
  assert.deepEqual(tocadas, [], 'Nunca se edita una migración aplicada: agrega otra (.claude/rules/04).');
  const enMaster = execFileSync('git', ['ls-tree', '--name-only', `${b}:${dir}`], { cwd: RAIZ, encoding: 'utf8' })
    .split('\n').filter((n) => /^V\d+__/.test(n)).map((n) => Number(n.match(/^V(\d+)/)[1]));
  const maxima = Math.max(0, ...enMaster);
  const bajas = cambios.filter(([e]) => e === 'A').map(([, p]) => path.basename(p))
    .filter((n) => /^V\d+__/.test(n) && Number(n.match(/^V(\d+)/)[1]) <= maxima);
  assert.deepEqual(bajas, [], `Una migración nueva por debajo de V${maxima} (la última de master) no corre en una base ya migrada.`);
});

// ── Familia «listas sin tope» (spec 050 tramo F, FR-034) ───────────────────────────────────────────────────────────
// Un `size=100000` no puede llegar a una consulta. Todo parámetro de tamaño de página de un controller pasa por
// `shared.domain.PaginaAcotada` (el único lugar donde se decide cuánto es demasiado).
const NOMBRES_DE_TAMANO = 'size|limit|limite|tamano|tamanio|pageSize';
const ARGS_DE_ANOTACION = String.raw`(?:[^()]|\([^()]*\))*`;
const TAMANO_POR_NOMBRE_DE_PARAMETRO = new RegExp(String.raw`@RequestParam(?:\(${ARGS_DE_ANOTACION}\))?\s*(?:final\s+)?(?:int|Integer|long|Long)\s+(?:${NOMBRES_DE_TAMANO})\b`);
const TAMANO_POR_NOMBRE_EN_LA_ANOTACION = new RegExp(String.raw`@RequestParam\(${ARGS_DE_ANOTACION}["'](?:${NOMBRES_DE_TAMANO})["']${ARGS_DE_ANOTACION}\)\s*(?:final\s+)?(?:int|Integer|long|Long)\s+\w+`);

/** ¿Este controller (su fuente) recibe un tamaño de página y no lo pasa por PaginaAcotada? */
function recibeUnTamanoSinAcotar(fuente) {
  const java = sinComentarios(fuente);
  return (TAMANO_POR_NOMBRE_DE_PARAMETRO.test(java) || TAMANO_POR_NOMBRE_EN_LA_ANOTACION.test(java)) && !/\bPaginaAcotada\b/.test(java);
}

test('listas: un controller que recibe size, limit o tamano lo pasa por PaginaAcotada (el tope no se escribe en cada lista)', () => {
  const sueltos = JAVA_MAIN.filter((f) => f.endsWith('Controller.java'))
    .filter((f) => !EXC.tamanoSinPaginaAcotada.archivos.includes(rel(f)))
    .filter((f) => recibeUnTamanoSinAcotar(fs.readFileSync(f, 'utf8'))).map(rel);
  assert.deepEqual(sueltos, [], 'Un parámetro de tamaño de página sin PaginaAcotada deja pasar un size=100000. Úsala en el controller: ' +
    'PaginaAcotada.estricta(page, size, MAXIMO) responde 400 con el motivo (como GET /admin/pagos/cuotas) y PaginaAcotada.recortada(...) lo ' +
    'lleva al máximo en silencio; declara el MAXIMO en el caso de uso (ver ConsultarAuditoriaUseCase.TAMANO_MAXIMO).');
});

test('listas: la lista de controllers sin PaginaAcotada solo se achica (si uno ya la usa o ya no recibe tamaño, bórralo de las excepciones)', () => {
  const sobran = EXC.tamanoSinPaginaAcotada.archivos.filter((r) => !fs.existsSync(path.join(RAIZ, r))
    || !recibeUnTamanoSinAcotar(fs.readFileSync(path.join(RAIZ, r), 'utf8')));
  assert.deepEqual(sobran, [], 'Ya cumplen: quita estas líneas de scripts/reglas-de-codigo-excepciones.json (tamanoSinPaginaAcotada).');
});

test('listas: el chequeo reconoce el caso malo (un size suelto) y deja pasar el bueno (con PaginaAcotada)', () => {
  const malo = '@RestController class ListaController { @GetMapping List<X> lista(@RequestParam(defaultValue = "50") int size) { return null; } }';
  const maloPorLimit = '@RestController class L { @GetMapping List<X> l(@RequestParam(required = false, defaultValue = "30") int limit) { return null; } }';
  const maloPorNombre = '@RestController class L { @GetMapping List<X> l(@RequestParam(value = "size", defaultValue = "30") int porPagina) { return null; } }';
  const maloConInteger = '@RestController class L { @GetMapping List<X> l(@RequestParam(required = false) Integer limit) { return null; } }';
  const bueno = '@RestController class L { @GetMapping List<X> l(@RequestParam(defaultValue = "50") int size) { var p = PaginaAcotada.estricta(0, size, 200); return null; } }';
  const sinTamano = '@RestController class L { @GetMapping List<X> l(@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page) { return null; } }';
  const enUnComentario = '@RestController class L { // @RequestParam int size (viejo)\n @GetMapping List<X> l() { return null; } }';
  assert.equal(recibeUnTamanoSinAcotar(malo), true, 'un size sin PaginaAcotada debe fallar');
  assert.equal(recibeUnTamanoSinAcotar(maloPorLimit), true, 'un limit sin PaginaAcotada debe fallar');
  assert.equal(recibeUnTamanoSinAcotar(maloPorNombre), true, 'un @RequestParam llamado "size" con otro nombre de variable debe fallar');
  assert.equal(recibeUnTamanoSinAcotar(maloConInteger), true, 'un Integer limit debe fallar');
  assert.equal(recibeUnTamanoSinAcotar(bueno), false);
  assert.equal(recibeUnTamanoSinAcotar(sinTamano), false);
  assert.equal(recibeUnTamanoSinAcotar(enUnComentario), false);
});

// ── Familia «pruebas que no corren» (E-27, E-111, E-349, E-654) ───────────────────────────────────────────────────
test('pruebas: toda clase con @Test se llama *Test, *Tests, Test*, *TestCase o *IT (si no, Maven no la corre y nadie se entera)', () => {
  const incluida = (n) => /(Test|Tests|TestCase|IT)$/.test(n) || /^Test/.test(n);
  const sueltas = JAVA_TEST.filter((f) => /@(Test|ParameterizedTest|RepeatedTest|TestFactory)\b/.test(fs.readFileSync(f, 'utf8')))
    .filter((f) => !incluida(path.basename(f, '.java'))).map(rel);
  assert.deepEqual(sueltas, [], 'Renómbrala según el pom (surefire: *Test; failsafe: *IT): E-27.');
});

// ── Familia «contrato y librerías» (E-33, E-65, E-107, E-163: Jackson 2 dentro de Spring Boot 4) ──────────────────
test('Jackson: ninguna anotación de Jackson 2 (com.fasterxml.jackson.databind.annotation) en src/main: Jackson 3 la ignora en silencio (E-65)', () => {
  const malos = JAVA_MAIN.filter((f) => /^import com\.fasterxml\.jackson\.databind\.annotation\./m.test(fs.readFileSync(f, 'utf8'))).map(rel);
  assert.deepEqual(malos, [], 'Usa tools.jackson.databind.annotation.* (Jackson 3).');
});

test('Jackson: ningún bean inyecta el ObjectMapper de Jackson 2 (no existe en el contexto: E-33, E-107, E-163)', () => {
  const malos = JAVA_MAIN.filter((f) => {
    const t = fs.readFileSync(f, 'utf8');
    return /^import com\.fasterxml\.jackson\.databind\.ObjectMapper;/m.test(t)
      && /@(Component|Service|RestController|Controller|Configuration|Repository)\b/.test(t)
      && /(final ObjectMapper \w+;|@Autowired[\s\S]{0,80}ObjectMapper)/.test(t)
      && !/new ObjectMapper|JsonMapper\.builder/.test(t);
  }).map(rel);
  assert.deepEqual(malos, [], 'Inyecta tools.jackson.databind.json.JsonMapper (Jackson 3) o crea el tuyo con JsonMapper.builder().');
});

// ── Familia «dos agentes en paralelo» (E-29, E-32, E-359) ─────────────────────────────────────────────────────────
test('beans: no hay dos @Component/@Service/@Repository/@Controller/@Configuration con el mismo nombre simple (Spring tumba el contexto: E-29)', () => {
  const porNombre = new Map();
  for (const f of JAVA_MAIN) {
    if (!/^@(Component|Service|Repository|RestController|Controller|Configuration)\b/m.test(fs.readFileSync(f, 'utf8'))) continue;
    const n = path.basename(f, '.java');
    porNombre.set(n, [...(porNombre.get(n) ?? []), rel(f)]);
  }
  const dup = [...porNombre.values()].filter((v) => v.length > 1);
  assert.deepEqual(dup, [], 'Renombra una o dale un nombre de bean explícito.');
});

// ── Familia «falla en silencio» (E-109, E-180, E-300) ─────────────────────────────────────────────────────────────
test('silencio: ningún catch vacío sin un comentario que diga por qué se ignora', () => {
  const malos = [];
  for (const f of JAVA_MAIN) {
    const t = fs.readFileSync(f, 'utf8');
    for (const m of t.matchAll(/catch\s*\([^)]*\)\s*\{(\s*)\}/g)) malos.push(`${rel(f)}:${t.slice(0, m.index).split('\n').length}`);
  }
  assert.deepEqual(malos, [], 'Un catch vacío esconde el error (E-300): registra, relanza o explica en un comentario por qué se ignora.');
});

// ── Familia «textos» (E-636, issues #53 y #63: mensajes sin tildes) ───────────────────────────────────────────────
const SIN_TILDE = /\b(habitos?|sesion|contrasena|todavia|tambien|codigo|telefono|informacion|notificacion|accion|direccion|evaluacion|semaforo|dias?|ultim[ao]|pagina|aqui|podras?|deberia|minimo|maximo|numero|invalid[oa]|unic[oa]|electronico|automatico|categoria|leccion|camara|ningun|razon|conexion|operacion|validacion|aprobacion|formacion|duracion|ubicacion)\b/i;
const SQL = /\b(SELECT|INSERT|UPDATE|DELETE|WHERE|FROM|JOIN|VALUES|ORDER BY)\b|:\w+|\$\{/;
function sinTildesPorArchivo() {
  const cuenta = {};
  for (const f of JAVA_MAIN) {
    const lineas = sinComentarios(fs.readFileSync(f, 'utf8')).split('\n');
    lineas.forEach((l, i) => {
      if (!/throw |Exception\(|Error\(/.test(lineas.slice(Math.max(0, i - 2), i + 1).join(' '))) return;
      for (const [, lit] of l.matchAll(/"((?:[^"\\]|\\.)*)"/g)) {
        if (lit.trim().includes(' ') && !SQL.test(lit) && SIN_TILDE.test(lit)) cuenta[rel(f)] = (cuenta[rel(f)] ?? 0) + 1;
      }
    });
  }
  return cuenta;
}

test('textos: los mensajes de error nuevos van con tildes (la deuda vieja solo puede bajar)', () => {
  const ahora = sinTildesPorArchivo();
  const deuda = EXC.mensajesSinTildes.porArchivo;
  const subieron = Object.entries(ahora).filter(([f, n]) => n > (deuda[f] ?? 0)).map(([f, n]) => `${f}: ${n} (deuda ${deuda[f] ?? 0})`);
  assert.deepEqual(subieron, [], 'Escribe el mensaje con sus tildes («sesión», «hábito», «día», «inválido»). Issue de la deuda: ' + EXC.mensajesSinTildes.issue);
  const bajaron = Object.entries(deuda).filter(([f, n]) => (ahora[f] ?? 0) < n).map(([f]) => f);
  assert.deepEqual(bajaron, [], 'Arreglaste mensajes: baja su número en scripts/reglas-de-codigo-excepciones.json (mensajesSinTildes).');
});

// ── Familia «entorno» (E-52, E-62: CRLF que reescribe archivos enteros) ───────────────────────────────────────────
test('entorno: ningún archivo de texto en git tiene finales de línea CRLF (salvo los de Windows y los CSV pegados)', () => {
  const lista = execFileSync('git', ['ls-files', '-z'], { cwd: RAIZ, encoding: 'utf8' }).split('\0').filter(Boolean)
    .filter((f) => /\.(java|md|mjs|js|ts|sh|sql|ya?ml|json|xml|properties|py|txt)$/.test(f) && !EXC.crlf.archivos.includes(f));
  const malos = lista.filter((f) => fs.existsSync(path.join(RAIZ, f)) && fs.readFileSync(path.join(RAIZ, f)).includes(13));
  assert.deepEqual(malos, [], 'Pásalo a LF (E-52): un CRLF hace que un cambio de una línea se vea como el archivo entero.');
});

test('las excepciones tienen fecha y motivo', () => {
  for (const [k, v] of Object.entries(EXC)) {
    if (k.startsWith('$')) continue;
    assert.match(v.fecha ?? '', /^\d{4}-\d{2}-\d{2}$/, `${k} sin fecha`);
    assert.ok((v.motivo ?? '').length > 10, `${k} sin motivo`);
  }
});

test('no hay archivos compilados de Python en git', () => {
  const pyc = execFileSync('git', ['ls-files'], { cwd: RAIZ, encoding: 'utf8' }).split('\n').filter((f) => /__pycache__|\.pyc$/.test(f));
  assert.deepEqual(pyc, []);
});

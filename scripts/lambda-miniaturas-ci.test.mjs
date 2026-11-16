// El despliegue automático de la Lambda de miniaturas del Muro (D-351): el workflow, el permiso mínimo del rol del CI y el
// script del dueño que lo aplica. Sin dependencias y en segundos: `node --test scripts/lambda-miniaturas-ci.test.mjs`
// (lo corren `scripts/verificar.sh` y trazabilidad.yml).
//
// Un workflow de GitHub Actions no se puede ejecutar sin empujar el repositorio (E-139), así que lo que SÍ se puede comprobar aquí
// se comprueba: el orden de los pasos (pruebas antes de construir), los permisos mínimos, las acciones fijadas por SHA, que el
// humo no toque el bucket, que el workflow pida a AWS solo lo que la política permite y que el script del dueño no cambie nada
// sin `--aplicar`. El script se corre de verdad contra un `aws` de mentira: nunca habla con AWS.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const RAIZ = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const LAMBDA = path.join(RAIZ, 'infra', 'lambda-miniaturas-muro');
const WORKFLOW = path.join(RAIZ, '.github', 'workflows', 'lambda-miniaturas.yml');
const SCRIPT = path.join(LAMBDA, 'permiso-del-ci.sh');
const PLANTILLA = path.join(LAMBDA, 'permiso-del-ci.plantilla.json');
const leer = (p) => fs.readFileSync(p, 'utf8');

// ── Un lector de YAML mínimo (lo que usan los workflows de este repo: mapas, listas, `[a, b]`, `|` y comentarios) ──────────
function parsearYaml(texto) {
  const L = texto.split('\n');
  let i = 0;
  const sangria = (l) => l.length - l.trimStart().length;
  const util = (l) => l.trim() !== '' && !l.trim().startsWith('#');
  const saltar = () => { while (i < L.length && !util(L[i])) i++; };
  const sinComentario = (s) => {
    let comilla = null;
    for (let k = 0; k < s.length; k++) {
      const c = s[k];
      if (comilla) { if (c === comilla) comilla = null; } else if (c === '"' || c === "'") comilla = c;
      else if (c === '#' && (k === 0 || /\s/.test(s[k - 1]))) return s.slice(0, k).trimEnd();
    }
    return s.trimEnd();
  };
  const escalar = (crudo) => {
    const s = sinComentario(crudo).trim();
    if (s === '') return null;
    if (s.startsWith('[') && s.endsWith(']')) return s.slice(1, -1).split(',').map(escalar).filter((x) => x !== null);
    if (/^(['"]).*\1$/.test(s)) return s.slice(1, -1);
    if (s === 'true' || s === 'false') return s === 'true';
    if (/^-?\d+$/.test(s)) return Number(s);
    return s;
  };
  function bloqueDeTexto(sangriaPadre, plegado) { // un bloque `|`: las líneas más sangradas que su clave, sin la sangría común
    const bloque = [];
    while (i < L.length && (L[i].trim() === '' || sangria(L[i]) > sangriaPadre)) bloque.push(L[i++]);
    while (bloque.length && bloque[bloque.length - 1].trim() === '') bloque.pop();
    const comun = Math.min(...bloque.filter((l) => l.trim() !== '').map(sangria));
    return bloque.map((l) => l.slice(comun)).join(plegado ? ' ' : '\n');
  }
  function secuencia(n) {
    const lista = [];
    for (saltar(); i < L.length && sangria(L[i]) === n && L[i].trim().startsWith('- '); saltar()) {
      const resto = L[i].trim().slice(2).trim();
      if (/^[^\s'"\[][^:]*:(\s|$)/.test(resto)) { L[i] = ' '.repeat(n + 2) + resto; lista.push(mapa(n + 2)); }
      else { i++; lista.push(escalar(resto)); }
    }
    return lista;
  }
  function mapa(n) {
    const obj = {};
    for (saltar(); i < L.length && sangria(L[i]) === n && !L[i].trim().startsWith('- '); saltar()) {
      const linea = L[i].trim();
      const corte = linea.search(/:(\s|$)/);
      assert.ok(corte > 0, `YAML que este lector no entiende, línea ${i + 1}: ${linea}`);
      const clave = linea.slice(0, corte);
      const valor = sinComentario(linea.slice(corte + 1)).trim();
      i++;
      if (/^[|>][+-]?$/.test(valor)) obj[clave] = bloqueDeTexto(n, valor[0] === '>');
      else if (valor !== '') obj[clave] = escalar(valor);
      else {
        saltar();
        const hijo = i < L.length && (sangria(L[i]) > n || (sangria(L[i]) === n && L[i].trim().startsWith('- ')));
        obj[clave] = hijo ? (L[i].trim().startsWith('- ') ? secuencia(sangria(L[i])) : mapa(sangria(L[i]))) : null;
      }
    }
    return obj;
  }
  saltar();
  return mapa(sangria(L[i]));
}

test('el lector de YAML de esta prueba entiende los workflows del repositorio (si no, las demás pruebas no valen)', () => {
  const y = parsearYaml('a: 1\nb:\n  c: [x, y]  # nota\n  d:\n    - e: 1\n      f: |\n        uno\n\n        dos\n    - g: \'h # no\'\n');
  assert.deepEqual(y, { a: 1, b: { c: ['x', 'y'], d: [{ e: 1, f: 'uno\n\ndos' }, { g: 'h # no' }] } });
  for (const f of fs.readdirSync(path.join(RAIZ, '.github', 'workflows')).filter((n) => n.endsWith('.yml'))) {
    const w = parsearYaml(leer(path.join(RAIZ, '.github', 'workflows', f)));
    assert.ok(w.jobs && Object.keys(w.jobs).length > 0, `${f}: sin jobs`);
  }
});

const wf = parsearYaml(leer(WORKFLOW));
const textoWf = leer(WORKFLOW);
const codigoWf = textoWf.split('\n').filter((l) => !/^\s*#/.test(l)).join('\n'); // sin las líneas de comentario (que sí pueden nombrar lo que NO se usa)
const pasos = wf.jobs.desplegar.steps;
const posicion = (cuando) => { const k = pasos.findIndex(cuando); assert.ok(k >= 0, `no hay un paso que cumpla ${cuando}`); return k; };
const corre = (re) => (p) => typeof p.run === 'string' && re.test(p.run);
const usa = (re) => (p) => typeof p.uses === 'string' && re.test(p.uses);

// ── El workflow ───────────────────────────────────────────────────────────────────────────────────────────────────────
test('workflow: corre en push a master sobre la carpeta de la Lambda y a mano; en un PR no (el rol de AWS solo acepta master)', () => {
  assert.deepEqual(Object.keys(wf.on).sort(), ['push', 'workflow_dispatch']);
  assert.deepEqual(wf.on.push.branches, ['master']);
  assert.ok(wf.on.push.paths.includes('infra/lambda-miniaturas-muro/**'), 'debe dispararse al tocar infra/lambda-miniaturas-muro/**');
  assert.doesNotMatch(codigoWf, /pull_request/, 'ni pull_request ni pull_request_target: un PR no debe llegar a las credenciales de AWS');
});

test('workflow: permisos mínimos (id-token para OIDC y leer el código, nada más) y una sola corrida a la vez sin cortar la de en curso', () => {
  assert.deepEqual(wf.permissions, { 'id-token': 'write', contents: 'read' });
  assert.equal(wf.jobs.desplegar.permissions, undefined, 'si el job declara permisos, pueden ser más amplios que los del workflow');
  assert.ok(typeof wf.concurrency.group === 'string' && wf.concurrency.group.length > 0);
  assert.doesNotMatch(wf.concurrency.group, /\$\{\{/, 'el grupo no debe depender de la rama: dos corridas no pueden pisarse');
  assert.equal(wf.concurrency['cancel-in-progress'], false, 'cortar un update-function-code a medias deja la función en InProgress');
});

test('workflow: toda acción va fijada por SHA de commit con su versión en un comentario (Dependabot lo actualiza)', () => {
  const usos = textoWf.split('\n').filter((l) => /^\s*(- )?uses:/.test(l));
  assert.ok(usos.length >= 3, 'se esperaban checkout, setup-node y configure-aws-credentials');
  for (const l of usos) assert.match(l, /uses:\s+[\w./-]+@[0-9a-f]{40}\s+#\s+v\d+\.\d+\.\d+\s*$/, `acción sin fijar por SHA: ${l.trim()}`);
});

test('workflow: las pruebas corren ANTES de construir y de hablar con AWS, con Node 22 y las dependencias de package-lock.json', () => {
  const instalar = posicion(corre(/\bnpm ci\b/));
  const probar = posicion(corre(/\bnode --test\b/));
  const construir = posicion(corre(/construir\.sh/));
  const verRol = posicion((p) => p.id === 'aws');
  const credenciales = posicion(usa(/^aws-actions\/configure-aws-credentials@/));
  const subir = posicion(corre(/aws lambda update-function-code/));
  const esperar = posicion(corre(/aws lambda wait function-updated-v2/));
  const estado = posicion(corre(/aws lambda get-function-configuration/));
  const invocar = posicion(corre(/aws lambda invoke/));
  assert.ok(posicion(usa(/^actions\/checkout@/)) < posicion(usa(/^actions\/setup-node@/)) && posicion(usa(/^actions\/setup-node@/)) < instalar);
  const orden = [instalar, probar, construir, verRol, credenciales, subir, esperar, estado, invocar];
  assert.deepEqual(orden, [...new Set(orden)].sort((a, b) => a - b), // estrictamente creciente: cada cosa en su propio paso
    'el orden tiene que ser: npm ci → node --test → construir.sh → ver el rol → OIDC → update-function-code → wait → comprobar → invocar. ' +
    'Si construir.sh corriera antes, en una laptop dejaría binarios de arm64 que las pruebas x64 no cargan.');
  assert.equal(pasos[posicion(usa(/^actions\/setup-node@/))].with['node-version'], 22, 'la Lambda corre en nodejs22.x');
  assert.match(pasos[instalar].run, /^npm ci\b/m, 'npm ci y no npm install: lo que se prueba es exactamente lo que fija el lock');
  assert.ok(fs.existsSync(path.join(LAMBDA, 'package-lock.json')), 'npm ci necesita package-lock.json');
  assert.equal(wf.jobs.desplegar.defaults.run['working-directory'], 'infra/lambda-miniaturas-muro');
});

test('workflow: se autentica por OIDC con el rol del CD (vars.AWS_ROLE_ARN), sin claves ni secretos', () => {
  const paso = pasos[posicion(usa(/^aws-actions\/configure-aws-credentials@/))];
  assert.equal(paso.with['role-to-assume'], '${{ vars.AWS_ROLE_ARN }}');
  assert.doesNotMatch(codigoWf, /secrets\./, 'este workflow no necesita ningún secret');
  assert.doesNotMatch(codigoWf, /aws-access-key-id|aws-secret-access-key|AWS_ACCESS_KEY_ID|AWS_SECRET_ACCESS_KEY/i);
});

test('workflow: sin AWS_ROLE_ARN avisa con ::warning:: y no despliega; fuera de master tampoco; todo lo de AWS espera a ese paso', () => {
  const verRol = pasos[posicion((p) => p.id === 'aws')];
  assert.match(verRol.run, /::warning::/);
  assert.match(verRol.run, /configurado=false/);
  assert.match(verRol.run, /refs\/heads\/master/, 'la confianza OIDC del rol solo acepta master');
  assert.equal(verRol.env.ROL, '${{ vars.AWS_ROLE_ARN }}', 'la variable va por env, no interpolada dentro del script');
  const desde = posicion((p) => p.id === 'aws') + 1;
  for (const p of pasos.slice(desde)) {
    if (p.name === 'Resumen') assert.match(p.if, /steps\.aws\.outputs\.configurado == 'true'/);
    else assert.equal(p.if, "steps.aws.outputs.configurado == 'true'", `el paso «${p.name}» habla con AWS y no se frena si falta el rol`);
  }
});

test('workflow: la función y la región son las de siempre (desplegar.sh, el script del dueño y la política)', () => {
  assert.equal(wf.env.FUNCION, 'renaser-miniaturas-muro');
  assert.equal(wf.env.REGION, 'us-east-1');
  assert.match(leer(path.join(LAMBDA, 'desplegar.sh')), /NOMBRE=renaser-miniaturas-muro/);
  assert.match(leer(path.join(LAMBDA, 'desplegar.sh')), /--region us-east-1/);
  const script = leer(SCRIPT);
  assert.match(script, /^FUNCION=renaser-miniaturas-muro$/m);
  assert.match(script, /^REGION=us-east-1$/m);
  assert.match(script, /^ROL=renaser-github-actions$/m);
  assert.match(script, /^POLITICA=actualizar-lambda-miniaturas$/m);
  assert.match(JSON.parse(leer(PLANTILLA)).Statement[0].Resource, /:function:renaser-miniaturas-muro$/);
});

// Cada llamada a AWS del workflow y la acción de IAM que exige. Una llamada nueva que no esté aquí hace fallar la prueba: hay que
// decidir a conciencia si el rol del CI recibe otro permiso (y sumarlo a la plantilla y a la prueba de la política).
const ACCION_DE = {
  'update-function-code': 'lambda:UpdateFunctionCode',
  'wait function-updated-v2': 'lambda:GetFunction', // el waiter consulta GetFunction
  'get-function-configuration': 'lambda:GetFunctionConfiguration',
  invoke: 'lambda:InvokeFunction',
};
const accionesQuePide = () => {
  const pedidas = new Set();
  for (const p of pasos) {
    for (const m of (p.run ?? '').matchAll(/\baws\s+(\w+)\s+(?:(wait)\s+)?([\w-]+)/g)) {
      assert.equal(m[1], 'lambda', `el workflow llama a «aws ${m[1]}»: el rol del CI no tiene (ni debe tener) permisos de ${m[1]}`);
      const llamada = m[2] ? `wait ${m[3]}` : m[3];
      assert.ok(ACCION_DE[llamada], `«aws lambda ${llamada}» no está en la tabla de esta prueba: ¿hay que darle ese permiso al rol?`);
      pedidas.add(ACCION_DE[llamada]);
    }
  }
  return [...pedidas].sort();
};

test('workflow: pide a AWS exactamente lo que la política del rol permite (ni una acción de más, ni una de menos)', () => {
  const politica = JSON.parse(leer(PLANTILLA)).Statement.flatMap((s) => s.Action).sort();
  assert.deepEqual(accionesQuePide(), politica);
});

test('workflow: la invocación de humo usa una clave fuera de muro/fotos/, que la Lambda ignora sin llamar a S3 (no toca el bucket de producción)', async () => {
  const humo = pasos[posicion(corre(/aws lambda invoke/))];
  const evento = JSON.parse(humo.run.match(/EVENTO='(.*)'/)[1]);
  const { rutaDeLaMiniatura } = await import(pathToFileURL(path.join(LAMBDA, 'miniatura.mjs')).href);
  assert.equal(evento.Records.length, 1);
  for (const r of evento.Records) {
    assert.equal(rutaDeLaMiniatura(r.s3.object.key), null, `la clave ${r.s3.object.key} haría que la Lambda leyera o borrara algo en S3`);
    assert.notEqual(r.s3.bucket.name, 'renaser90dias-prod', 'el evento de humo no debe nombrar el bucket de producción');
  }
  assert.match(humo.run, /\{"procesadas":1\}/, 'tiene que exigir la respuesta exacta de la Lambda');
  assert.match(humo.run, /FunctionError/, 'una invocación con FunctionError devuelve 200: hay que mirarlo');
  assert.doesNotMatch(codigoWf, /\baws\s+(s3|s3api|iam|ssm)\b/, 'el workflow no toca S3, IAM ni SSM');
});

test('workflow: comprueba que lo desplegado es el zip construido (CodeSha256), que está Active y que sigue en nodejs22.x / arm64', () => {
  const estado = pasos[posicion(corre(/aws lambda get-function-configuration/))].run;
  for (const dato of ['Active', 'Successful', 'CodeSha256', 'nodejs22.x', 'arm64', 'sha256']) assert.ok(estado.includes(dato), `falta comprobar ${dato}`);
  assert.match(pasos[posicion(corre(/update-function-code/))].run, /fileb:\/\/lambda\.zip/);
  assert.match(pasos[posicion(corre(/update-function-code/))].run, /AccessDenied[\s\S]*permiso-del-ci\.sh/, 'si falta el permiso, el error tiene que decir cómo darlo');
});

// ── La política mínima ────────────────────────────────────────────────────────────────────────────────────────────────
test('política: solo cuatro acciones de Lambda, sobre el ARN de esa función, sin comodines y sin una cuenta escrita', () => {
  const crudo = leer(PLANTILLA);
  const politica = JSON.parse(crudo);
  assert.equal(politica.Statement.length, 1);
  const [s] = politica.Statement;
  assert.equal(s.Effect, 'Allow');
  assert.deepEqual([...s.Action].sort(), ['lambda:GetFunction', 'lambda:GetFunctionConfiguration', 'lambda:InvokeFunction', 'lambda:UpdateFunctionCode']);
  assert.equal(s.Resource, 'arn:aws:lambda:<REGION>:<CUENTA>:function:renaser-miniaturas-muro');
  assert.ok(!JSON.stringify(politica).includes('*'), 'ni comodines en acciones ni en recursos');
  assert.ok(!('NotAction' in s) && !('NotResource' in s) && !('Principal' in s));
  assert.doesNotMatch(crudo, /\b\d{12}\b/, 'el id de la cuenta no se escribe en el repositorio: el script lo pone');
});

// ── Construcción y dependencias ───────────────────────────────────────────────────────────────────────────────────────
test('dependencias: package-lock.json está al día con package.json y el SDK de S3 es devDependency (las pruebas lo importan; en AWS ya viene)', () => {
  const pkg = JSON.parse(leer(path.join(LAMBDA, 'package.json')));
  const lock = JSON.parse(leer(path.join(LAMBDA, 'package-lock.json')));
  assert.deepEqual(lock.packages[''].dependencies, pkg.dependencies, 'npm ci falla si el lock no coincide: corre `npm install --package-lock-only`');
  assert.deepEqual(lock.packages[''].devDependencies, pkg.devDependencies);
  assert.ok(pkg.devDependencies['@aws-sdk/client-s3'] && !pkg.dependencies['@aws-sdk/client-s3'], 'el SDK va como devDependency: no se empaqueta');
  assert.ok(lock.packages['node_modules/@img/sharp-linux-arm64'], 'el lock tiene que traer el binario de arm64 de sharp');
});

test('construir.sh: instala desde el lock, sin dependencias de desarrollo, para arm64, y se niega a dejar un zip con el binario equivocado', () => {
  const s = leer(path.join(LAMBDA, 'construir.sh'));
  assert.match(s, /^set -euo pipefail$/m);
  assert.match(s, /cp index\.mjs miniatura\.mjs package\.json package-lock\.json build\//);
  assert.match(s, /npm ci --omit=dev .*--os=linux --cpu=arm64/);
  assert.match(s, /sharp-linux-arm64/);
  assert.match(s, /sharp-linux-x64/);
  for (const f of ['construir.sh', 'permiso-del-ci.sh']) assert.ok(fs.statSync(path.join(LAMBDA, f)).mode & 0o111, `${f} tiene que ser ejecutable`);
});

// ── El script del dueño: estático ─────────────────────────────────────────────────────────────────────────────────────
test('permiso-del-ci.sh: simulacro por omisión, perfil prod en cada llamada, cuenta verificada y ninguna cuenta escrita', () => {
  const s = leer(SCRIPT);
  const codigo = s.split('\n').filter((l) => !/^\s*#/.test(l));
  assert.match(s, /^set -euo pipefail$/m);
  assert.match(s, /^APLICAR=no; /m, 'cambiar algo exige --aplicar');
  assert.match(s, /^PERFIL=prod$/m);
  assert.match(s, /^aws_prod\(\) \{ aws "\$@" --profile "\$PERFIL" --region "\$REGION"; \}$/m);
  const sueltas = codigo.filter((l) => !/^for h in /.test(l) && !/^aws_prod\(\)/.test(l)).filter((l) => /(^\s*|\$\(\s*|\|\|?\s*|;\s*|&&\s*)aws\s+[a-z]/.test(l));
  assert.deepEqual(sueltas, [], 'toda llamada a AWS pasa por aws_prod (perfil y región explícitos): ' + sueltas.join(' | '));
  assert.match(s, /RENASER_CUENTA_AWS/);
  assert.match(s, /sts get-caller-identity/);
  assert.doesNotMatch(s, /\b\d{12}\b/, 'el id de la cuenta no se escribe en el repositorio');
  assert.ok(codigo.filter((l) => /^\s*trap /.test(l)).every((l) => /^\s*trap "[^"]*" EXIT$/.test(l)), 'trap con comillas dobles (E-679)');
  assert.ok(codigo.some((l) => /iam delete-role-policy/.test(l)) && codigo.some((l) => /--revertir/.test(l)), 'tiene que traer su vuelta atrás');
});

test('permiso-del-ci.sh: la sintaxis es válida y, si shellcheck está instalado, pasa sin avisos', (t) => {
  for (const f of ['permiso-del-ci.sh', 'construir.sh']) {
    const r = spawnSync('bash', ['-n', path.join(LAMBDA, f)], { encoding: 'utf8' });
    assert.equal(r.status, 0, `${f}: ${r.stderr}`);
  }
  const hay = spawnSync('shellcheck', ['--version'], { encoding: 'utf8' });
  if (hay.error) return t.skip('shellcheck no está instalado en esta máquina');
  const r = spawnSync('shellcheck', ['-x', path.join(LAMBDA, 'permiso-del-ci.sh'), path.join(LAMBDA, 'construir.sh')], { encoding: 'utf8' });
  assert.equal(r.status, 0, r.stdout + r.stderr);
});

// ── El script del dueño: corrido de verdad contra un `aws` de mentira ─────────────────────────────────────────────────
const CUENTA_DE_MENTIRA = '123456789012';
const HAY_JQ = spawnSync('jq', ['--version']).status === 0;

const AWS_DE_MENTIRA = `#!/usr/bin/env bash
# Un aws de mentira: anota cada llamada y contesta lo que IAM contestaría. Nunca sale a la red.
echo "$*" >> "$STUB_LOG"
case "$1 $2" in
  "sts get-caller-identity") echo "$STUB_CUENTA" ;;
  "iam get-role") if [ "$STUB_ROL" = no ]; then echo "An error occurred (NoSuchEntity) when calling the GetRole operation" >&2; exit 254; fi
                  echo "arn:aws:iam::$STUB_CUENTA:role/renaser-github-actions" ;;
  "iam get-role-policy")
    if [ -f "$STUB_ESTADO" ]; then cat "$STUB_ESTADO"
    elif [ "$STUB_ERROR_LECTURA" = si ]; then echo "An error occurred (AccessDenied) when calling the GetRolePolicy operation" >&2; exit 254
    else echo "An error occurred (NoSuchEntity) when calling the GetRolePolicy operation: The role policy cannot be found." >&2; exit 254; fi ;;
  "iam put-role-policy")
    while [ $# -gt 0 ]; do if [ "$1" = --policy-document ]; then cp "\${2#file://}" "$STUB_ESTADO"; fi; shift; done ;;
  "iam delete-role-policy") rm -f "$STUB_ESTADO" ;;
  "lambda get-function") echo "arn:aws:lambda:us-east-1:$STUB_CUENTA:function:renaser-miniaturas-muro" ;;
  *) echo "llamada inesperada al aws de mentira: $*" >&2; exit 99 ;;
esac
`;

/** Prepara una carpeta con el `aws` de mentira y corre el script. `ya` = la política que el rol ya tiene (objeto) o null. */
function correr(t, args, { ya = null, cuentaReal = CUENTA_DE_MENTIRA, cuentaEsperada = CUENTA_DE_MENTIRA, rol = 'si', errorLectura = 'no', plantilla = null } = {}) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'permiso-ci-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  fs.mkdirSync(path.join(dir, 'bin'));
  fs.writeFileSync(path.join(dir, 'bin', 'aws'), AWS_DE_MENTIRA, { mode: 0o755 });
  const estado = path.join(dir, 'estado.json');
  if (ya) fs.writeFileSync(estado, JSON.stringify(ya));
  const carpetaDelScript = path.join(dir, 'infra'); // el script busca la plantilla junto a sí mismo
  fs.mkdirSync(carpetaDelScript);
  fs.copyFileSync(SCRIPT, path.join(carpetaDelScript, 'permiso-del-ci.sh'));
  fs.writeFileSync(path.join(carpetaDelScript, 'permiso-del-ci.plantilla.json'), plantilla ?? leer(PLANTILLA));
  const env = {
    PATH: `${path.join(dir, 'bin')}${path.delimiter}${process.env.PATH}`, HOME: dir, RENASER_RESPALDOS: path.join(dir, 'respaldos'),
    STUB_LOG: path.join(dir, 'llamadas.log'), STUB_CUENTA: cuentaReal, STUB_ESTADO: estado, STUB_ROL: rol, STUB_ERROR_LECTURA: errorLectura,
  };
  if (cuentaEsperada) env.RENASER_CUENTA_AWS = cuentaEsperada;
  const r = spawnSync('bash', [path.join(carpetaDelScript, 'permiso-del-ci.sh'), ...args], { env, encoding: 'utf8', cwd: dir });
  const llamadas = fs.existsSync(env.STUB_LOG) ? fs.readFileSync(env.STUB_LOG, 'utf8').split('\n').filter(Boolean) : [];
  const respaldos = fs.existsSync(env.RENASER_RESPALDOS) ? fs.readdirSync(env.RENASER_RESPALDOS) : [];
  return { ...r, llamadas, respaldos, dirRespaldos: env.RENASER_RESPALDOS, estado, hayEstado: fs.existsSync(estado), salida: r.stdout + r.stderr };
}
const cambios = (r) => r.llamadas.filter((l) => /^iam (put|delete)-role-policy/.test(l));
const politicaDeseada = () => JSON.parse(leer(PLANTILLA).replaceAll('<REGION>', 'us-east-1').replaceAll('<CUENTA>', CUENTA_DE_MENTIRA));

test('permiso-del-ci.sh (aws de mentira): sin --aplicar es un simulacro: lee, muestra la política y no cambia nada', { skip: !HAY_JQ && 'falta jq' }, (t) => {
  const r = correr(t, []);
  assert.equal(r.status, 0, r.salida);
  assert.match(r.salida, /SIMULACRO/);
  assert.match(r.salida, new RegExp(`arn:aws:lambda:us-east-1:${CUENTA_DE_MENTIRA}:function:renaser-miniaturas-muro`), 'la cuenta sale de AWS, en el ARN');
  assert.deepEqual(cambios(r), [], 'un simulacro no escribe en IAM');
  assert.ok(r.llamadas.length > 0 && r.llamadas.every((l) => / --profile prod --region us-east-1$/.test(l)), 'toda llamada lleva perfil prod y región: ' + r.llamadas.join(' | '));
  assert.equal(r.hayEstado, false);
  assert.equal(correr(t, ['--simular', '--aplicar']).hayEstado, false, '--simular gana a --aplicar');
});

test('permiso-del-ci.sh (aws de mentira): --aplicar pone la política en línea con las cuatro acciones y la relee', { skip: !HAY_JQ && 'falta jq' }, (t) => {
  const r = correr(t, ['--aplicar']);
  assert.equal(r.status, 0, r.salida);
  const puestas = cambios(r);
  assert.equal(puestas.length, 1);
  assert.match(puestas[0], /^iam put-role-policy --role-name renaser-github-actions --policy-name actualizar-lambda-miniaturas --policy-document file:\/\//);
  const quedo = JSON.parse(fs.readFileSync(r.estado, 'utf8'));
  assert.deepEqual(quedo, politicaDeseada(), 'lo que quedó en IAM es la plantilla con la región y la cuenta puestas');
  assert.deepEqual(quedo.Statement[0].Action.slice().sort(), ['lambda:GetFunction', 'lambda:GetFunctionConfiguration', 'lambda:InvokeFunction', 'lambda:UpdateFunctionCode']);
  assert.equal(quedo.Statement[0].Resource, `arn:aws:lambda:us-east-1:${CUENTA_DE_MENTIRA}:function:renaser-miniaturas-muro`);
  assert.ok(r.llamadas.filter((l) => l.startsWith('iam get-role-policy')).length >= 2, 'lee antes (para ser idempotente) y después (para comprobar)');
  assert.equal(r.respaldos.length, 0, 'no había nada que respaldar');
});

test('permiso-del-ci.sh (aws de mentira): es idempotente (la misma política no se vuelve a poner) y reemplaza con respaldo una distinta', { skip: !HAY_JQ && 'falta jq' }, (t) => {
  const igual = correr(t, ['--aplicar'], { ya: politicaDeseada() });
  assert.equal(igual.status, 0, igual.salida);
  assert.deepEqual(cambios(igual), []);
  assert.match(igual.salida, /ya esta aplicada/);
  const vieja = { Version: '2012-10-17', Statement: [{ Effect: 'Allow', Action: 'lambda:*', Resource: '*' }] };
  const distinta = correr(t, ['--aplicar'], { ya: vieja });
  assert.equal(distinta.status, 0, distinta.salida);
  assert.equal(cambios(distinta).length, 1);
  assert.match(distinta.salida, /DISTINTA/);
  assert.equal(distinta.respaldos.length, 1, 'guarda la política anterior antes de reemplazarla');
  assert.equal(fs.statSync(path.join(distinta.dirRespaldos, distinta.respaldos[0])).mode & 0o777, 0o600);
  assert.deepEqual(JSON.parse(fs.readFileSync(path.join(distinta.dirRespaldos, distinta.respaldos[0]), 'utf8')), vieja);
  const solo = correr(t, [], { ya: vieja });
  assert.deepEqual(cambios(solo), [], 'sin --aplicar tampoco reemplaza');
});

test('permiso-del-ci.sh (aws de mentira): --revertir borra solo esa política (con respaldo y solo con --aplicar) y es idempotente', { skip: !HAY_JQ && 'falta jq' }, (t) => {
  const simulacro = correr(t, ['--revertir'], { ya: politicaDeseada() });
  assert.equal(simulacro.status, 0, simulacro.salida);
  assert.deepEqual(cambios(simulacro), []);
  assert.equal(simulacro.hayEstado, true);
  const real = correr(t, ['--revertir', '--aplicar'], { ya: politicaDeseada() });
  assert.equal(real.status, 0, real.salida);
  assert.deepEqual(cambios(real), ['iam delete-role-policy --role-name renaser-github-actions --policy-name actualizar-lambda-miniaturas --profile prod --region us-east-1']);
  assert.equal(real.hayEstado, false);
  assert.equal(real.respaldos.length, 1);
  const sinNada = correr(t, ['--revertir', '--aplicar']);
  assert.equal(sinNada.status, 0, sinNada.salida);
  assert.deepEqual(cambios(sinNada), []);
  assert.match(sinNada.salida, /nada que quitar/);
});

test('permiso-del-ci.sh (aws de mentira): se niega sin RENASER_CUENTA_AWS, con la cuenta equivocada, con un rol que no existe o con un error de lectura', { skip: !HAY_JQ && 'falta jq' }, (t) => {
  const sinVariable = correr(t, ['--aplicar'], { cuentaEsperada: null });
  assert.notEqual(sinVariable.status, 0);
  assert.match(sinVariable.salida, /RENASER_CUENTA_AWS/);
  assert.deepEqual(sinVariable.llamadas, [], 'sin la cuenta esperada ni siquiera se llama a AWS');
  const mal = correr(t, ['--aplicar'], { cuentaEsperada: 'abc' });
  assert.notEqual(mal.status, 0);
  assert.match(mal.salida, /12 digitos/);
  const otra = correr(t, ['--aplicar'], { cuentaReal: '999999999999' });
  assert.equal(otra.status, 1);
  assert.match(otra.salida, /no a la esperada/);
  assert.deepEqual(cambios(otra), []);
  const sinRol = correr(t, ['--aplicar'], { rol: 'no' });
  assert.equal(sinRol.status, 1);
  assert.match(sinRol.salida, /no pude leer el rol/);
  assert.deepEqual(cambios(sinRol), []);
  const sinPermisoDeLectura = correr(t, ['--aplicar'], { errorLectura: 'si' });
  assert.equal(sinPermisoDeLectura.status, 1, 'un error distinto de NoSuchEntity no se interpreta como «no hay política»');
  assert.deepEqual(cambios(sinPermisoDeLectura), []);
  assert.equal(correr(t, ['--no-existe']).status, 2);
});

test('permiso-del-ci.sh (aws de mentira): si alguien ensancha la plantilla (otra acción o un comodín), se niega antes de tocar IAM', { skip: !HAY_JQ && 'falta jq' }, (t) => {
  const ancha = JSON.parse(leer(PLANTILLA));
  ancha.Statement[0].Action.push('lambda:UpdateFunctionConfiguration');
  const conAccion = correr(t, ['--aplicar'], { plantilla: JSON.stringify(ancha) });
  assert.equal(conAccion.status, 1);
  assert.match(conAccion.salida, /politica minima/);
  assert.deepEqual(cambios(conAccion), []);
  const comodin = JSON.parse(leer(PLANTILLA));
  comodin.Statement[0].Resource = 'arn:aws:lambda:<REGION>:<CUENTA>:function:*';
  const conComodin = correr(t, ['--aplicar'], { plantilla: JSON.stringify(comodin) });
  assert.equal(conComodin.status, 1);
  assert.deepEqual(cambios(conComodin), []);
});

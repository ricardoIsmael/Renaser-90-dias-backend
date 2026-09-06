/**
 * Panel de administracion de SOLICITUDES DE CUENTA de Renaser OS.
 *
 * Alcance deliberadamente chico: listar las pendientes, aprobar y rechazar. Nada mas.
 * No hay logica de negocio aca — cada accion es una llamada a un endpoint que el backend
 * Java ya expone (`/api/v1/account-requests`), y quien decide si el actor puede hacerla
 * sigue siendo el backend (`RequireAdminGuard`, `AccountRequest.approve/reject`).
 *
 * Por que existe: el backend corre en EC2 sobre HTTP plano (sin dominio ni certificado) y
 * su security group solo acepta el 8080 desde la IP del dueno. Una Function URL de Lambda
 * da HTTPS gratis y, puesta dentro de la VPC, llega al backend por IP privada sin abrir
 * nada a internet.
 *
 * IDENTIDAD — la decision importante de este archivo:
 * el panel NO guarda ni acepta un UUID de admin. Pide email + contrasena de un usuario real
 * y llama a `POST /api/v1/auth/login` del backend, que devuelve una sesion opaca en el header
 * `X-Auth-Token`. Esa sesion es la que viaja en cada llamada posterior. Es a proposito: el
 * backend todavia acepta el header `X-Actor-Id` (agujero conocido y documentado en
 * SecurityConfig), y un panel que guardara un UUID de admin para inyectarlo seria justamente
 * la forma trivial de explotarlo. Aca el navegador nunca ve ni manda un UUID.
 *
 * Sin secretos en variables de entorno en claro: la unica credencial que el panel guarda es
 * el VERIFICADOR PBKDF2 de la clave del panel (un hash, no la clave). La clave en claro vive
 * solo en Parameter Store, `/renaser/prod/ADMIN_PANEL_CLAVE`.
 *
 * Sin dependencias externas: solo la biblioteca estandar de Node. El zip pesa unos pocos KB
 * y se actualiza con `scripts/desplegar.sh` sin paso de build.
 */

import { pbkdf2Sync, randomBytes, timingSafeEqual } from 'node:crypto';

const BACKEND = process.env.BACKEND_BASE_URL;
const VERIFICADOR_CLAVE = process.env.CLAVE_PANEL_VERIFICADOR;

const COOKIE_SESION = '__Host-renaser_sesion';
const COOKIE_CSRF = '__Host-renaser_csrf';

const TIMEOUT_BACKEND_MS = 10_000;
const MAX_FALLOS = 5;
const BLOQUEO_MS = 15 * 60 * 1000;
const DEMORA_FALLO_MS = 400;

/**
 * Freno de fuerza bruta. Es por contenedor, no global: Lambda puede levantar varios en
 * paralelo, asi que esto no es un limite duro. Se deja igual porque el backend NO tiene
 * ningun limite propio en `/api/v1/auth/login` (AutenticacionService solo defiende el
 * timing, no la cantidad de intentos), y la clave del panel es la barrera que de verdad
 * evita que la Function URL sea un oraculo de contrasenas abierto a internet.
 */
const fallosPorIp = new Map();

export const handler = async (evento) => {
    const metodo = evento?.requestContext?.http?.method ?? 'GET';
    const ruta = evento?.rawPath ?? '/';
    const ip = evento?.requestContext?.http?.sourceIp ?? 'desconocida';

    try {
        return await enrutar(metodo, ruta, evento, ip);
    } catch (error) {
        // Nunca el error crudo al navegador: puede traer la URL interna del backend.
        console.error('fallo no controlado', { ruta, metodo, error: error?.message });
        return paginaError('Algo se rompio en el panel. Revisa los logs de CloudWatch.');
    }
};

async function enrutar(metodo, ruta, evento, ip) {
    if (metodo === 'GET' && (ruta === '/' || ruta === '/login')) {
        return sesionDe(evento) ? redirigir('/solicitudes') : paginaLogin();
    }
    if (metodo === 'POST' && ruta === '/login') {
        return await iniciarSesion(evento, ip);
    }
    if (metodo === 'POST' && ruta === '/logout') {
        return await cerrarSesion(evento);
    }
    if (metodo === 'GET' && ruta === '/solicitudes') {
        return await listarSolicitudes(evento);
    }
    if (metodo === 'POST' && ruta === '/solicitudes/aprobar') {
        return await decidir(evento, 'aprobar');
    }
    if (metodo === 'POST' && ruta === '/solicitudes/rechazar') {
        return await decidir(evento, 'rechazar');
    }
    if (metodo === 'GET' && ruta === '/diagnostico') {
        return await diagnostico(evento);
    }
    return respuesta(404, pagina('No existe', '<p class="aviso">Esa direccion no existe en el panel.</p>'));
}

// ---------------------------------------------------------------------------
// Sesion
// ---------------------------------------------------------------------------

async function iniciarSesion(evento, ip) {
    const bloqueo = fallosPorIp.get(ip);
    if (bloqueo && bloqueo.hasta > Date.now()) {
        const minutos = Math.ceil((bloqueo.hasta - Date.now()) / 60_000);
        return paginaLogin(`Demasiados intentos fallidos. Volve a probar en ${minutos} minuto(s).`, 429);
    }

    const formulario = leerFormulario(evento);
    const clave = formulario.get('clave') ?? '';
    const email = formulario.get('email') ?? '';
    const contrasena = formulario.get('contrasena') ?? '';

    if (!claveValida(clave)) {
        return await rechazarIntento(ip, 'Clave del panel incorrecta.');
    }
    if (!email || !contrasena) {
        return paginaLogin('Falta el correo o la contrasena.', 400);
    }

    const login = await llamarBackend('POST', '/api/v1/auth/login', {
        cuerpo: { email, contrasena },
    });

    if (login.status === 401) {
        return await rechazarIntento(ip, 'Correo o contrasena incorrectos.');
    }
    if (!login.ok) {
        console.error('login: respuesta inesperada del backend', { status: login.status });
        return paginaLogin('El backend no pudo procesar el ingreso. Revisa /diagnostico.', 502);
    }

    const rol = login.json?.role;
    if (rol !== 'ADMIN' && rol !== 'ALCHEMIST') {
        // Chequeo adelantado, no la autorizacion real: el backend la vuelve a hacer en cada
        // llamada (RequireAdminGuard). Aca solo evita mostrar un panel que no va a funcionar.
        await llamarBackend('POST', '/api/v1/auth/logout', { sesion: login.sesion });
        return await rechazarIntento(ip, 'Esa cuenta no administra solicitudes de cuenta.');
    }
    if (!login.sesion) {
        console.error('login: el backend no devolvio X-Auth-Token');
        return paginaLogin('El backend no devolvio una sesion. Revisa que Redis este arriba.', 502);
    }

    fallosPorIp.delete(ip);
    const csrf = randomBytes(32).toString('base64url');
    return {
        statusCode: 303,
        headers: { Location: '/solicitudes', ...cabecerasSeguridad() },
        cookies: [galleta(COOKIE_SESION, login.sesion), galleta(COOKIE_CSRF, csrf)],
        body: '',
    };
}

async function rechazarIntento(ip, mensaje) {
    const previo = fallosPorIp.get(ip);
    // Si el bloqueo anterior ya vencio, el contador arranca de cero. Sin esto, quien cumplio
    // sus 15 minutos volveria a quedar bloqueado con un solo error de tipeo, porque seguiria
    // arrastrando los 5 fallos viejos.
    const vencido = previo !== undefined && previo.hasta !== 0 && previo.hasta <= Date.now();
    const fallos = (vencido ? 0 : previo?.fallos ?? 0) + 1;
    if (fallosPorIp.size > 1000) {
        fallosPorIp.clear();   // el contenedor no vive lo suficiente como para que crezca mas
    }
    fallosPorIp.set(ip, {
        fallos,
        hasta: fallos >= MAX_FALLOS ? Date.now() + BLOQUEO_MS : 0,
    });
    await new Promise((resolver) => setTimeout(resolver, DEMORA_FALLO_MS));
    return paginaLogin(mensaje, 401);
}

async function cerrarSesion(evento) {
    const sesion = sesionDe(evento);
    if (sesion) {
        await llamarBackend('POST', '/api/v1/auth/logout', { sesion }).catch(() => undefined);
    }
    return {
        statusCode: 303,
        headers: { Location: '/', ...cabecerasSeguridad() },
        cookies: [galletaBorrada(COOKIE_SESION), galletaBorrada(COOKIE_CSRF)],
        body: '',
    };
}

// ---------------------------------------------------------------------------
// Solicitudes
// ---------------------------------------------------------------------------

async function listarSolicitudes(evento, aviso = '', estadoHttp = 200) {
    const sesion = sesionDe(evento);
    if (!sesion) {
        return redirigir('/');
    }

    const listado = await llamarBackend('GET', '/api/v1/account-requests?status=PENDING&page=0&size=100', { sesion });
    if (listado.status === 401 || listado.status === 403 || listado.status === 400) {
        return {
            statusCode: 303,
            headers: { Location: '/', ...cabecerasSeguridad() },
            cookies: [galletaBorrada(COOKIE_SESION), galletaBorrada(COOKIE_CSRF)],
            body: '',
        };
    }
    if (!listado.ok) {
        console.error('listar: respuesta inesperada del backend', { status: listado.status });
        return respuesta(502, pagina('Solicitudes', `${bloqueAviso('El backend no devolvio la lista.', 'malo')}${barra()}`));
    }

    const csrf = cookieDe(evento, COOKIE_CSRF) ?? '';
    const solicitudes = listado.json?.content ?? [];
    const cuerpo = [
        aviso,
        barra(),
        solicitudes.length === 0
            ? '<p class="vacio">No hay solicitudes pendientes.</p>'
            : tablaSolicitudes(solicitudes, csrf),
    ].join('\n');
    return respuesta(estadoHttp, pagina('Solicitudes pendientes', cuerpo));
}

async function decidir(evento, accion) {
    const sesion = sesionDe(evento);
    if (!sesion) {
        return redirigir('/');
    }

    const formulario = leerFormulario(evento);
    if (!csrfValido(evento, formulario)) {
        return await listarSolicitudes(evento, bloqueAviso('La pagina caduco. Recargala y volve a intentar.', 'malo'), 403);
    }

    const id = formulario.get('id') ?? '';
    if (!/^[0-9a-fA-F-]{36}$/.test(id)) {
        return await listarSolicitudes(evento, bloqueAviso('Identificador de solicitud invalido.', 'malo'), 400);
    }

    if (accion === 'rechazar') {
        const motivo = (formulario.get('motivo') ?? '').trim();
        if (!motivo) {
            return await listarSolicitudes(evento, bloqueAviso('Para rechazar hace falta escribir un motivo.', 'malo'), 400);
        }
        const salida = await llamarBackend('POST', `/api/v1/account-requests/${id}/reject`, {
            sesion,
            cuerpo: { reason: motivo },
        });
        return await respuestaDeDecision(evento, salida, 'Solicitud rechazada.');
    }

    const salida = await llamarBackend('POST', `/api/v1/account-requests/${id}/approve`, { sesion });
    return await respuestaDeDecision(evento, salida, 'Solicitud aprobada: la cuenta ya existe.');
}

async function respuestaDeDecision(evento, salida, mensajeExito) {
    if (salida.ok) {
        return await listarSolicitudes(evento, bloqueAviso(mensajeExito, 'bueno'));
    }
    if (salida.status === 403) {
        return await listarSolicitudes(evento, bloqueAviso('El backend rechazo la operacion: esa cuenta no puede administrar solicitudes.', 'malo'), 403);
    }
    if (salida.status === 404) {
        return await listarSolicitudes(evento, bloqueAviso('Esa solicitud ya no existe.', 'malo'), 404);
    }
    console.error('decision: respuesta inesperada del backend', { status: salida.status });
    const detalle = salida.json?.message ? `: ${escapar(salida.json.message)}` : '';
    return await listarSolicitudes(evento, bloqueAviso(`El backend rechazo la operacion (HTTP ${salida.status})${detalle}`, 'malo'), 502);
}

// ---------------------------------------------------------------------------
// Diagnostico — para saber si el camino Lambda -> backend esta vivo
// ---------------------------------------------------------------------------

async function diagnostico(evento) {
    const sesion = sesionDe(evento);
    if (!sesion) {
        return redirigir('/');
    }

    // La sesion se VALIDA contra el backend antes de mostrar nada. Sin esto alcanzaba con
    // escribir la cookie a mano en el navegador para ver la IP privada del backend y su
    // estado de salud, sin haber pasado nunca por el ingreso.
    let identidad;
    try {
        identidad = await llamarBackend('GET', '/api/v1/auth/me', { sesion });
    } catch (error) {
        // No se pudo ni llegar al backend. Eso ES el diagnostico que se vino a buscar, y no
        // dice nada que no se deduzca de que el panel no anda: no se nombra la direccion.
        return respuesta(200, pagina('Diagnostico',
            bloqueAviso(`No se pudo llegar al backend: ${escapar(error?.message ?? 'sin detalle')}`, 'malo')));
    }
    if (!identidad.ok) {
        return redirigir('/');
    }

    const inicio = Date.now();
    let estado;
    try {
        const salida = await llamarBackend('GET', '/actuator/health', {});
        estado = `HTTP ${salida.status} — ${escapar(JSON.stringify(salida.json ?? salida.texto ?? '').slice(0, 200))}`;
    } catch (error) {
        estado = `sin respuesta: ${escapar(error?.message ?? 'error desconocido')}`;
    }
    const cuerpo = `${barra()}
    <table>
      <tr><th>Backend</th><td>${escapar(BACKEND ?? '(sin configurar)')}</td></tr>
      <tr><th>/actuator/health</th><td>${estado}</td></tr>
      <tr><th>Demora</th><td>${Date.now() - inicio} ms</td></tr>
    </table>`;
    return respuesta(200, pagina('Diagnostico', cuerpo));
}

// ---------------------------------------------------------------------------
// Llamadas al backend
// ---------------------------------------------------------------------------

async function llamarBackend(metodo, ruta, { sesion, cuerpo } = {}) {
    const cabeceras = { Accept: 'application/json' };
    if (sesion) {
        // La sesion propia del backend viaja por header, no por cookie
        // (SecurityConfig -> HeaderHttpSessionIdResolver.xAuthToken()).
        cabeceras['X-Auth-Token'] = sesion;
    }
    if (cuerpo !== undefined) {
        cabeceras['Content-Type'] = 'application/json';
    }

    const respuestaHttp = await fetch(`${BACKEND}${ruta}`, {
        method: metodo,
        headers: cabeceras,
        body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
        signal: AbortSignal.timeout(TIMEOUT_BACKEND_MS),
    });

    const texto = await respuestaHttp.text();
    let json;
    try {
        json = texto ? JSON.parse(texto) : undefined;
    } catch {
        json = undefined;
    }
    return {
        ok: respuestaHttp.ok,
        status: respuestaHttp.status,
        json,
        texto,
        sesion: respuestaHttp.headers.get('x-auth-token'),
    };
}

// ---------------------------------------------------------------------------
// Clave del panel, cookies y CSRF
// ---------------------------------------------------------------------------

/**
 * Verifica contra `pbkdf2$sha256$<iteraciones>$<sal_b64>$<hash_b64>`. En la variable de
 * entorno vive solo este verificador; la clave en claro nunca sale de Parameter Store.
 */
function claveValida(clave) {
    if (!VERIFICADOR_CLAVE) {
        console.error('CLAVE_PANEL_VERIFICADOR sin configurar: el panel queda cerrado');
        return false;
    }
    const [etiqueta, algoritmo, iteraciones, salBase64, hashBase64] = VERIFICADOR_CLAVE.split('$');
    if (etiqueta !== 'pbkdf2' || !algoritmo || !iteraciones || !salBase64 || !hashBase64) {
        console.error('CLAVE_PANEL_VERIFICADOR con formato invalido');
        return false;
    }
    const esperado = Buffer.from(hashBase64, 'base64');
    const calculado = pbkdf2Sync(clave, Buffer.from(salBase64, 'base64'),
        Number.parseInt(iteraciones, 10), esperado.length, algoritmo);
    return calculado.length === esperado.length && timingSafeEqual(calculado, esperado);
}

function sesionDe(evento) {
    return cookieDe(evento, COOKIE_SESION);
}

function cookieDe(evento, nombre) {
    for (const galletaCruda of evento?.cookies ?? []) {
        const corte = galletaCruda.indexOf('=');
        if (corte > 0 && galletaCruda.slice(0, corte).trim() === nombre) {
            return galletaCruda.slice(corte + 1);
        }
    }
    return undefined;
}

/**
 * Doble envio: el token va en cookie y en el formulario, y tienen que coincidir. La cookie
 * ya es `SameSite=Strict`, asi que un POST desde otro sitio ni siquiera la lleva; esto es la
 * segunda linea, por si algun navegador no respeta SameSite.
 */
function csrfValido(evento, formulario) {
    const enCookie = cookieDe(evento, COOKIE_CSRF);
    const enFormulario = formulario.get('csrf');
    if (!enCookie || !enFormulario || enCookie.length !== enFormulario.length) {
        return false;
    }
    return timingSafeEqual(Buffer.from(enCookie), Buffer.from(enFormulario));
}

function galleta(nombre, valor) {
    return `${nombre}=${valor}; Path=/; HttpOnly; Secure; SameSite=Strict`;
}

function galletaBorrada(nombre) {
    return `${nombre}=; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=0`;
}

function leerFormulario(evento) {
    const crudo = evento?.isBase64Encoded
        ? Buffer.from(evento.body ?? '', 'base64').toString('utf8')
        : (evento?.body ?? '');
    return new URLSearchParams(crudo);
}

// ---------------------------------------------------------------------------
// HTML
// ---------------------------------------------------------------------------

function escapar(valor) {
    return String(valor ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');
}

function tablaSolicitudes(solicitudes, csrf) {
    const filas = solicitudes.map((solicitud) => {
        const id = escapar(solicitud.id);
        return `<tr>
      <td>
        <strong>${escapar(solicitud.fullName)}</strong>
        <span class="menor">${escapar(solicitud.email)}</span>
      </td>
      <td class="menor">${escapar(solicitud.phone ?? '—')}<br>${escapar(solicitud.city ?? '—')}</td>
      <td class="menor">${escapar(fecha(solicitud.createdAt))}</td>
      <td class="acciones">
        <form method="post" action="/solicitudes/aprobar">
          <input type="hidden" name="csrf" value="${escapar(csrf)}">
          <input type="hidden" name="id" value="${id}">
          <button class="aprobar" type="submit">Aprobar</button>
        </form>
        <form method="post" action="/solicitudes/rechazar">
          <input type="hidden" name="csrf" value="${escapar(csrf)}">
          <input type="hidden" name="id" value="${id}">
          <input type="text" name="motivo" placeholder="Motivo del rechazo" required maxlength="500">
          <button class="rechazar" type="submit">Rechazar</button>
        </form>
      </td>
    </tr>`;
    }).join('\n');

    return `<table class="solicitudes">
    <thead><tr><th>Quien</th><th>Contacto</th><th>Enviada</th><th>Decision</th></tr></thead>
    <tbody>${filas}</tbody>
  </table>
  <p class="menor">${solicitudes.length} solicitud(es) pendiente(s).</p>`;
}

function fecha(iso) {
    if (!iso) {
        return '—';
    }
    // Se muestra en la zona del padron (America/Lima), no en UTC ni en la del navegador:
    // el servidor renderiza el HTML y el navegador no ejecuta nada.
    return new Intl.DateTimeFormat('es-PE', {
        dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/Lima',
    }).format(new Date(iso));
}

function barra() {
    return `<div class="barra">
    <a href="/solicitudes">Solicitudes</a>
    <a href="/diagnostico">Diagnostico</a>
    <form method="post" action="/logout"><button class="salir" type="submit">Salir</button></form>
  </div>`;
}

function bloqueAviso(texto, tono) {
    return `<p class="aviso ${tono}">${escapar(texto)}</p>`;
}

function paginaLogin(aviso = '', estadoHttp = 200) {
    const cuerpo = `${aviso ? bloqueAviso(aviso, 'malo') : ''}
  <form method="post" action="/login" class="ingreso">
    <label>Clave del panel
      <input type="password" name="clave" required autocomplete="off">
    </label>
    <label>Correo de tu cuenta Renaser
      <input type="email" name="email" required autocomplete="username">
    </label>
    <label>Contrasena
      <input type="password" name="contrasena" required autocomplete="current-password">
    </label>
    <button type="submit">Entrar</button>
  </form>
  <p class="menor">Entras con tu propia cuenta de Renaser OS. Solo ADMIN y ALCHEMIST pueden
  decidir sobre solicitudes de cuenta.</p>`;
    return respuesta(estadoHttp, pagina('Ingreso', cuerpo));
}

function paginaError(mensaje) {
    return respuesta(500, pagina('Error', bloqueAviso(mensaje, 'malo')));
}

function pagina(titulo, cuerpo) {
    return `<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex, nofollow">
<title>${escapar(titulo)} — Renaser OS</title>
<style>
  :root { color-scheme: light dark; --tinta:#16181d; --papel:#fbfbfa; --borde:#dcdcd6;
          --suave:#6b7280; --verde:#1a7f4b; --rojo:#b23b3b; }
  @media (prefers-color-scheme: dark) {
    :root { --tinta:#e8e8e6; --papel:#16181d; --borde:#2f333b; --suave:#9aa1ad;
            --verde:#4ade80; --rojo:#f87171; }
  }
  * { box-sizing: border-box; }
  body { margin:0; padding:2rem 1.25rem; background:var(--papel); color:var(--tinta);
         font:15px/1.55 system-ui, -apple-system, "Segoe UI", sans-serif; }
  main { max-width: 62rem; margin: 0 auto; }
  h1 { font-size:1.35rem; margin:0 0 1.25rem; letter-spacing:-0.01em; }
  h1 span { color:var(--suave); font-weight:400; }
  .barra { display:flex; gap:1rem; align-items:center; padding-bottom:1rem;
           border-bottom:1px solid var(--borde); margin-bottom:1.25rem; }
  .barra a { color:var(--tinta); text-decoration:none; border-bottom:1px solid var(--borde); }
  .barra form { margin-left:auto; }
  table { width:100%; border-collapse:collapse; }
  th, td { text-align:left; padding:.7rem .6rem; border-bottom:1px solid var(--borde);
           vertical-align:top; }
  th { font-size:.75rem; text-transform:uppercase; letter-spacing:.06em; color:var(--suave);
       font-weight:600; }
  .menor { color:var(--suave); font-size:.85rem; }
  td .menor { display:block; }
  .acciones { display:flex; flex-wrap:wrap; gap:.5rem; align-items:center; }
  .acciones form { display:flex; gap:.4rem; align-items:center; }
  input[type=text], input[type=email], input[type=password] {
    padding:.45rem .55rem; border:1px solid var(--borde); border-radius:6px;
    background:var(--papel); color:var(--tinta); font:inherit; }
  button { padding:.45rem .85rem; border:1px solid var(--borde); border-radius:6px;
           background:transparent; color:var(--tinta); font:inherit; cursor:pointer; }
  button.aprobar { border-color:var(--verde); color:var(--verde); }
  button.rechazar { border-color:var(--rojo); color:var(--rojo); }
  .ingreso { display:flex; flex-direction:column; gap:.9rem; max-width:22rem; }
  .ingreso label { display:flex; flex-direction:column; gap:.3rem; font-size:.85rem;
                   color:var(--suave); }
  .aviso { padding:.7rem .85rem; border-radius:6px; border:1px solid var(--borde); }
  .aviso.bueno { border-color:var(--verde); color:var(--verde); }
  .aviso.malo { border-color:var(--rojo); color:var(--rojo); }
  .vacio { color:var(--suave); padding:2rem 0; }
</style>
</head>
<body><main>
<h1>Renaser OS <span>· solicitudes de cuenta</span></h1>
${cuerpo}
</main></body>
</html>`;
}

function respuesta(estadoHttp, html) {
    return {
        statusCode: estadoHttp,
        headers: { 'Content-Type': 'text/html; charset=utf-8', ...cabecerasSeguridad() },
        body: html,
    };
}

function redirigir(destino) {
    return { statusCode: 303, headers: { Location: destino, ...cabecerasSeguridad() }, body: '' };
}

/**
 * El panel no ejecuta una linea de JavaScript: todo es HTML servido y formularios. Por eso
 * `script-src 'none'` puede ser literal, y no hay ningun recurso externo que cargar.
 */
function cabecerasSeguridad() {
    return {
        'Content-Security-Policy':
            "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'",
        'Referrer-Policy': 'no-referrer',
        'X-Content-Type-Options': 'nosniff',
        'Cache-Control': 'no-store',
    };
}

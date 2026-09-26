# -*- coding: utf-8 -*-
"""Trocea contenido general del programa y lo indexa en la base de conocimiento (RAG) de Renasia.

POR DEFECTO NO ENVIA NADA. Sin --enviar solo muestra que se mandaria (modo ensayo).

Fuentes (--fuentes, separadas por coma):
  lecciones     las 124 transcripciones de clases de transcripciones/salida/*.json
  drive-audios  "36. SESIONES / Transcripcion de audios": DIA01 ... DIA43
  drive-canal   "37. TRANSCRIPCIONES YT": transcripciones del canal de YouTube
Nada mas. Las carpetas de Drive permitidas estan fijas en drive.py (regla de privacidad).

POR QUE HAY QUE TROCEAR
  Cada texto que se indexa produce UN vector. Una clase entera de 6.000 palabras da un vector
  que es el promedio de todo lo que se hablo ahi: no se parece con precision a nada. Ademas el
  modelo de embeddings acepta ~2.048 tokens y una transcripcion completa son ~9.000.

POR QUE LOS PEDAZOS SE SOLAPAN
  Un corte seco parte ideas al medio. Con solape, la frase cortada aparece completa en el
  pedazo siguiente.

POR QUE LAS LECCIONES VAN ATADAS A SU leccionId
  `leccionId` hace que la recuperacion respete el gate de dia de programa: un aprendiz no recibe
  como cita una leccion que todavia no desbloqueo. Los chunks con leccionId NULO no se filtran
  nunca: el contenido de Drive (sin leccion) queda visible para todos DESDE EL DIA 0.

IDEMPOTENCIA (lo que el script viejo no tenia, ver E-282)
  Cada chunk se identifica por (clave, parte), con clave = leccionId o, si no hay, documentoId
  (el id del video NO sirve: 124 lecciones comparten 90 videos). Cada chunk aceptado se anota
  en el registro local (indexados.jsonl) y se saltea al relanzar. --saltear-archivo agrega lo que
  ya esta en produccion segun consulta-indexados.sh. No existe --desde: era fragil.

ANTE CUALQUIER ERROR SE CORTA EN EL ACTO
  429 (cuota de Gemini), 5xx, 401/403 o un corte de red: se para, se dice que documento quedo a
  medias y como retomar. Nunca se cuenta el error y se sigue con el proximo chunk.

Uso (ver LEEME.md):
  python3 indexar.py                                  # ensayo, no envia nada
  RENASER_ADMIN_TOKEN=... python3 indexar.py --enviar --url https://... \\
      --saltear-archivo consulta/saltear.txt
"""
import argparse
import datetime
import glob
import io
import json
import os
import sys
import urllib.error
import urllib.request

import drive

# ---------------------------------------------------------------------------------------------
# A CONFIRMAR POR EL DUENO: como se etiqueta en base_conocimiento cada fuente nueva.
# Las lecciones conservan lo que ya esta en produccion (TRANSCRIPCION_CLASE + nombre del curso).
# Cambiarlos DESPUES de indexar deja filas con dos nombres distintos: decidirlo antes.
# ---------------------------------------------------------------------------------------------
TIPO_FUENTE_LECCION = 'TRANSCRIPCION_CLASE'
TIPO_FUENTE_CANAL = 'TRANSCRIPCION_CANAL'   # a confirmar por el dueno
CLASE_CANAL = 'CANAL DARREN'                 # a confirmar por el dueno
TIPO_FUENTE_AUDIO = 'AUDIO_DIARIO'           # a confirmar por el dueno
CLASE_AUDIO = 'AUDIOS DIARIOS'               # a confirmar por el dueno

# Mismos valores que el script viejo: si cambian, las partes k/N dejan de coincidir con las que
# ya estan en produccion y el salteo por parte deja de servir.
PALABRAS_POR_CHUNK = 450
SOLAPE = 60

# Cuota gratuita de gemini-embedding-001: 1.000 por dia y por PROYECTO. 900 deja margen para
# el uso normal de la app (cada pregunta al acompanante tambien gasta un embedding).
LIMITE_POR_DEFECTO = 900
FUENTES = ['lecciones', 'drive-audios', 'drive-canal']
VARIABLE_TOKEN = 'RENASER_ADMIN_TOKEN'

AQUI = os.path.dirname(os.path.abspath(__file__))
DIR_LECCIONES = os.path.join(AQUI, 'transcripciones', 'salida')
REGISTRO_POR_DEFECTO = os.path.join(AQUI, 'indexados.jsonl')


def trocear(texto):
    palabras = texto.split()
    if not palabras:
        return []
    pedazos, i = [], 0
    paso = PALABRAS_POR_CHUNK - SOLAPE
    while i < len(palabras):
        pedazos.append(' '.join(palabras[i:i + PALABRAS_POR_CHUNK]))
        if i + PALABRAS_POR_CHUNK >= len(palabras):
            break
        i += paso
    return pedazos


# ------------------------------------ documentos ---------------------------------------------

def documento(tipo, clase, doc_id, leccion_id, titulo, texto=None, descargar=None):
    """Un documento a indexar. `descargar` difiere la bajada de Drive hasta que haga falta."""
    return {'tipoFuente': tipo, 'clase': clase, 'documentoId': doc_id, 'leccionId': leccion_id,
            'titulo': titulo, 'clave': leccion_id or doc_id, 'texto': texto, 'descargar': descargar}


def cargar_lecciones(directorio=DIR_LECCIONES):
    docs = []
    for ruta in sorted(glob.glob(os.path.join(directorio, '*.json'))):
        with io.open(ruta, encoding='utf-8') as f:
            d = json.load(f)
        docs.append(documento(TIPO_FUENTE_LECCION, d['curso'], d['video_id'], d['leccion_id'],
                              d['titulo'], texto=d['texto']))
    return docs


def cargar_drive(fuente, incluir_testimonios, pedir=None):
    tipo, clase = ((TIPO_FUENTE_CANAL, CLASE_CANAL) if fuente == 'drive-canal'
                   else (TIPO_FUENTE_AUDIO, CLASE_AUDIO))
    incluidos, excluidos = drive.listar(fuente, incluir_testimonios, pedir)
    esperados = drive.CARPETAS_PERMITIDAS[fuente]['esperados']
    total = len(incluidos) + len(excluidos)
    print('%s: %d archivos en la carpeta (se esperaban %d), %d excluidos.'
          % (fuente, total, esperados, len(excluidos)))
    if total == 0:
        raise SystemExit('La carpeta de %s vino vacia: ¿dejo de ser publica? No se sigue.' % fuente)
    for _, titulo, motivo in excluidos:
        print('   excluido: %-60s  (%s)' % (titulo[:60], motivo))
    docs = []
    for file_id, titulo in incluidos:
        nombre = titulo[:-4] if titulo.lower().endswith('.txt') else titulo
        docs.append(documento(tipo, clase, file_id, None, nombre,
                              descargar=lambda i=file_id: drive.descargar(i, pedir)))
    return docs


# ------------------------------- registro y salteos ------------------------------------------

def leer_registro(ruta):
    """Partes ya enviadas por este script: {(clave, 'k/N')}."""
    hechas = set()
    if not os.path.exists(ruta):
        return hechas
    with io.open(ruta, encoding='utf-8') as f:
        filas = [json.loads(linea) for linea in f if linea.strip()]
    for r in filas:
        hechas.add((r['clave'], r['parte']))
    return hechas


def anotar_en_registro(ruta, doc, parte):
    fila = {'clave': doc['clave'], 'parte': parte, 'tipoFuente': doc['tipoFuente'],
            'documentoId': doc['documentoId'], 'leccionId': doc['leccionId'],
            'titulo': doc['titulo'], 'en': datetime.datetime.now().isoformat(timespec='seconds')}
    with io.open(ruta, 'a', encoding='utf-8') as f:
        f.write(json.dumps(fila, ensure_ascii=False) + '\n')
        f.flush()
        os.fsync(f.fileno())


def leer_salteos(rutas):
    """Archivo de salteo: una linea `CLAVE` saltea el documento entero; `CLAVE<TAB>k/N` saltea
    solo esa parte (lo usa consulta-indexados.sh para completar lecciones a medias sin duplicar).
    Lineas vacias o que empiezan con # se ignoran."""
    enteros, partes = set(), set()
    for ruta in rutas or []:
        with io.open(ruta, encoding='utf-8') as f:
            lineas = f.readlines()
        for linea in lineas:
            linea = linea.strip()
            if not linea or linea.startswith('#'):
                continue
            campos = linea.split('\t')
            if len(campos) >= 2 and campos[1].strip():
                partes.add((campos[0].strip(), campos[1].strip()))
            else:
                enteros.add(campos[0].strip())
    return enteros, partes


# ------------------------------------- plan --------------------------------------------------

def planificar(docs, enteros, partes_hechas, solo_listar=False):
    """Para cada documento decide que partes faltan. No envia nada."""
    plan = []
    for doc in docs:
        item = {'doc': doc, 'pedazos': None, 'pendientes': [], 'nota': ''}
        plan.append(item)
        if doc['clave'] in enteros:
            item['nota'] = 'ya en produccion'
            continue
        if solo_listar and doc['texto'] is None:
            item['nota'] = 'sin descargar (--solo-listar)'
            continue
        if doc['texto'] is None:
            doc['texto'] = doc['descargar']()
        _completar_item(item, trocear(doc['texto']), partes_hechas)
    return plan


def _completar_item(item, pedazos, partes_hechas):
    item['pedazos'] = pedazos
    n = len(pedazos)
    ya = {p for (c, p) in partes_hechas if c == item['doc']['clave']}
    if any(not p.endswith('/%d' % n) for p in ya):
        item['nota'] = ('OJO: ya indexado con otro total de partes (%s, ahora da %d); se saltea '
                        'entero' % (sorted(ya)[0], n))
        return
    item['pendientes'] = [k for k in range(1, n + 1) if '%d/%d' % (k, n) not in ya]
    if ya and item['pendientes']:
        item['nota'] = 'a medias: faltan %d de %d' % (len(item['pendientes']), n)
    elif not item['pendientes']:
        item['nota'] = 'completo'


def imprimir_plan(plan, limite):
    print('\n%-4s %-16s %-50s %7s %9s  %s' % ('#', 'fuente', 'titulo', 'partes', 'a enviar', 'nota'))
    total = 0
    for n, it in enumerate(plan, 1):
        d = it['doc']
        partes = '?' if it['pedazos'] is None else str(len(it['pedazos']))
        total += len(it['pendientes'])
        print('%-4d %-16s %-50s %7s %9d  %s' % (n, d['tipoFuente'][:16], d['titulo'][:50], partes,
                                                len(it['pendientes']), it['nota']))
    dias = -(-total // limite)
    print('\nEmbeddings a generar (= chunks a enviar): %d. Con tope %d por corrida: %d dia(s).'
          % (total, limite, dias))
    return total


# ------------------------------------- envio -------------------------------------------------

def cuerpo_de(doc, pedazo, k, n):
    return {'tipoFuente': doc['tipoFuente'], 'clase': doc['clase'],
            'documentoId': doc['documentoId'], 'leccionId': doc['leccionId'],
            'contenido': pedazo, 'metadatos': {'titulo': doc['titulo'], 'parte': '%d/%d' % (k, n)}}


def publicar_http(url, token, cuerpo):
    """Un POST, sin reintentos: cualquier falla corta la corrida. Devuelve (estado, detalle)."""
    datos = json.dumps(cuerpo, ensure_ascii=False).encode('utf-8')
    pedido = urllib.request.Request(url + '/api/v1/admin/conocimiento', data=datos, method='POST')
    pedido.add_header('Content-Type', 'application/json')
    pedido.add_header('X-Auth-Token', token)
    try:
        with urllib.request.urlopen(pedido, timeout=90) as r:
            return r.status, None
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode('utf-8', 'replace')[:300]
    except Exception as e:  # red caida, timeout: no se sabe si el servidor lo guardo
        return 0, type(e).__name__ + ': ' + str(e)[:200]


class CorteDeEnvio(Exception):
    """El envio se paro. El mensaje explica que quedo a medias y como retomar."""


def enviar_plan(plan, publicar, ruta_registro, limite):
    """Envia lo pendiente. `publicar(cuerpo) -> (estado, detalle)` se inyecta (en las pruebas es
    falso). Devuelve cuantos chunks entraron; lanza CorteDeEnvio ante el primer error."""
    enviados = 0
    for it in plan:
        pendientes, doc = it['pendientes'], it['doc']
        if not pendientes:
            continue
        if enviados + len(pendientes) > limite:
            print('\nTope de %d embeddings: "%s" (%d partes) no entra en esta corrida sin pasarse. '
                  'Se para aca para no dejarlo a medias. Relanzar manana con el mismo comando.'
                  % (limite, doc['titulo'], len(pendientes)))
            break
        n = len(it['pedazos'])
        for i, k in enumerate(pendientes):
            estado, detalle = publicar(cuerpo_de(doc, it['pedazos'][k - 1], k, n))
            if estado not in (200, 201):
                raise CorteDeEnvio(_explicar_corte(doc, (k, n), pendientes[i:], estado, detalle))
            anotar_en_registro(ruta_registro, doc, '%d/%d' % (k, n))
            enviados += 1
        print('  ok  %-55s %3d partes' % (doc['titulo'][:55], len(pendientes)), flush=True)
    return enviados


_MOTIVOS = {
    429: 'se acabo la cuota diaria de embeddings de Gemini. Esperar a que se reinicie (Google la '
         'reinicia a medianoche, hora del Pacifico) y relanzar el MISMO comando: el registro '
         'saltea lo ya enviado y completa este documento.',
    401: 'el token no sirve (vencido, sesion cerrada o usuario sin permiso de admin). Sacar un '
         'token nuevo (LEEME.md, paso 3) y relanzar el mismo comando.',
    0: 'corte de red o timeout. NO SE SABE si esa parte llego a guardarse. Antes de relanzar, '
       'correr ./consulta-indexados.sh y usar su salteo, para no duplicarla.',
}
_MOTIVOS[403] = _MOTIVOS[401]


def _explicar_corte(doc, parte, faltan, estado, detalle):
    k, n = parte
    lista = ', '.join(str(x) for x in faltan[:20]) + (' ...' if len(faltan) > 20 else '')
    motivo = _MOTIVOS.get(estado, 'error del servidor o pedido rechazado. Revisar el detalle y '
                                  'los logs del backend antes de relanzar el mismo comando.')
    return '\n'.join(['', '=' * 70,
                      'SE CORTO EL ENVIO. HTTP %s en la parte %d/%d de:' % (estado, k, n),
                      '   "%s"  (clave %s)' % (doc['titulo'], doc['clave']),
                      'Detalle: %s' % (detalle or '-'),
                      'Quedaron sin enviar %d parte(s) de ese documento: %s' % (len(faltan), lista),
                      'Motivo: ' + motivo, '=' * 70])


# -------------------------------------- CLI --------------------------------------------------

def argumentos(argv):
    p = argparse.ArgumentParser(description='Indexa contenido general del programa en el RAG. '
                                            'Sin --enviar no envia nada.')
    p.add_argument('--fuentes', default=','.join(FUENTES),
                   help='separadas por coma, de: %s (por defecto, todas)' % ', '.join(FUENTES))
    p.add_argument('--enviar', action='store_true', help='enviar de verdad (sin esto: ensayo)')
    p.add_argument('--url', help='URL base del backend, https obligatorio al enviar')
    p.add_argument('--limite-embeddings', type=int, default=LIMITE_POR_DEFECTO)
    p.add_argument('--saltear-archivo', action='append', default=[],
                   help='ids ya indexados (lo genera consulta-indexados.sh); se puede repetir')
    p.add_argument('--registro', default=REGISTRO_POR_DEFECTO,
                   help='registro local de partes enviadas (por defecto indexados.jsonl)')
    p.add_argument('--incluir-testimonios', action='store_true')
    p.add_argument('--solo-listar', action='store_true',
                   help='en ensayo, no descargar de Drive (no calcula partes de Drive)')
    p.add_argument('--si', action='store_true', help='no pedir confirmacion antes de enviar')
    a = p.parse_args(argv)
    a.fuentes = [f.strip() for f in a.fuentes.split(',') if f.strip()]
    for f in a.fuentes:
        if f not in FUENTES:
            p.error('fuente desconocida: %s' % f)
    if a.limite_embeddings < 1:
        p.error('--limite-embeddings tiene que ser positivo')
    if a.enviar and a.solo_listar:
        p.error('--solo-listar es solo para el ensayo')
    return a


def validar_envio(url, token):
    """Devuelve un mensaje de error, o None si se puede enviar."""
    if not url or not url.lower().startswith('https://'):
        return 'Con --enviar hace falta --url y tiene que empezar con https:// (se recibio: %r).' % url
    if not token or not token.strip():
        return ('Falta el token: exportar la variable %s (nunca como argumento; ver LEEME.md, '
                'paso 3).' % VARIABLE_TOKEN)
    return None


def main(argv=None):
    a = argumentos(sys.argv[1:] if argv is None else argv)
    token = os.environ.get(VARIABLE_TOKEN, '').strip()
    if a.enviar:
        error = validar_envio(a.url, token)
        if error:
            print(error, file=sys.stderr)
            return 2
    docs = []
    for fuente in a.fuentes:
        docs += (cargar_lecciones() if fuente == 'lecciones'
                 else cargar_drive(fuente, a.incluir_testimonios))
    enteros, partes = leer_salteos(a.saltear_archivo)
    partes |= leer_registro(a.registro)
    plan = planificar(docs, enteros, partes, solo_listar=a.solo_listar)
    total = imprimir_plan(plan, a.limite_embeddings)
    if not a.enviar:
        print('\nENSAYO: no se envio nada. Para enviar de verdad: --enviar --url https://...')
        return 0
    if total == 0:
        print('No hay nada pendiente.')
        return 0
    if not a.si and input('\nSe van a enviar hasta %d chunks a %s. Escribir ENVIAR para seguir: '
                          % (min(total, a.limite_embeddings), a.url)).strip() != 'ENVIAR':
        print('Cancelado, no se envio nada.')
        return 1
    url = a.url.rstrip('/')
    try:
        enviados = enviar_plan(plan, lambda c: publicar_http(url, token, c), a.registro,
                               a.limite_embeddings)
    except CorteDeEnvio as corte:
        print(str(corte), file=sys.stderr)
        return 3
    print('\nListo: %d chunks enviados en esta corrida. Registro: %s' % (enviados, a.registro))
    return 0


if __name__ == '__main__':
    sys.exit(main())

# -*- coding: utf-8 -*-
"""Listado y descarga de las DOS carpetas publicas de Drive que se pueden indexar.

REGLA DE PRIVACIDAD (no negociable)
  La carpeta de Drive del programa tiene de todo: sesiones individuales, mentorias grupales,
  atenciones, postulaciones... Eso es informacion de PERSONAS concretas y no entra nunca al RAG:
  el acompanante podria citarle a un aprendiz lo que otro conto en su sesion.

  Por eso este modulo NO recibe ids de carpeta por parametro. Solo conoce las dos carpetas de
  CARPETAS_PERMITIDAS, que son contenido general del programa (categoria A). Agregar otra carpeta
  es una decision del dueno, se hace editando este archivo y queda en el historial de git.
  Tampoco se entra a subcarpetas: solo se toman archivos .txt que esten directamente adentro.

Sin login: el listado sale de la vista embebida publica y la descarga del enlace publico de
descarga. Si una carpeta deja de ser publica, el listado viene vacio y el script lo dice.
"""
import html
import re
import unicodedata
import urllib.request

URL_LISTADO = 'https://drive.google.com/embeddedfolderview?id=%s'
URL_DESCARGA = 'https://drive.google.com/uc?export=download&id=%s'

# Las UNICAS carpetas que el script puede listar. Ver la regla de privacidad arriba.
CARPETAS_PERMITIDAS = {
    'drive-canal': {
        'id': '16TDGlcV9LcmLamZrbhZQR_7fDbUlF647',
        'nombre': '37. TRANSCRIPCIONES YT',
        'esperados': 81,
    },
    'drive-audios': {
        'id': '1m8bz1c44-nwsJ-_uKHG90zawMbJb6bkJ',
        'nombre': '36. SESIONES / Transcripcion de audios',
        'esperados': 43,
    },
}

# Ya esta indexado como leccion del curso (una de las 124): indexarlo otra vez lo duplicaria.
YA_ES_LECCION = ['el engano de creer que sabes']
# Testimonios: hablan personas concretas contando su proceso. Afuera salvo --incluir-testimonios.
MARCA_TESTIMONIO = 'testimonio'
# Red de seguridad: si alguna vez aparece en estas carpetas un archivo con estas palabras en el
# titulo, NO se indexa aunque este en una carpeta permitida. Es contenido de personas.
PALABRAS_PRIVADAS = ['sesion individual', 'mentoria grupal', 'mentorias grupales', 'atencion',
                     'postulacion', 'ficha',
                     'consulta privada', 'entrevista']
# Los audios diarios se llaman "DIA01 - ...", "DIA23- ..." (a veces sin espacio antes del guion).
PATRON_AUDIO = re.compile(r'^DIA\s*(\d{1,2})\s*-', re.IGNORECASE)

_ENTRADA = re.compile(
    r'id="entry-([A-Za-z0-9_-]+)".*?href="([^"]+)".*?flip-entry-title">([^<]*)<', re.DOTALL)


def normalizar(texto):
    """Minusculas y sin tildes, para comparar titulos sin depender de como se escribieron."""
    sin_tildes = unicodedata.normalize('NFKD', texto)
    return ''.join(c for c in sin_tildes if not unicodedata.combining(c)).lower()


def parsear_listado(pagina):
    """Devuelve [(file_id, titulo)] de los ARCHIVOS del listado; las subcarpetas se descartan."""
    archivos = []
    for file_id, enlace, titulo in _ENTRADA.findall(pagina):
        if '/file/d/' not in enlace:
            continue  # es una subcarpeta u otra cosa: no se entra
        archivos.append((file_id, html.unescape(titulo).strip()))
    return archivos


def clasificar(fuente, archivos, incluir_testimonios=False):
    """Separa lo que se indexa de lo que no. Devuelve (incluidos, excluidos[(id, titulo, motivo)])."""
    incluidos, excluidos = [], []
    for file_id, titulo in archivos:
        motivo = _motivo_de_exclusion(fuente, titulo, incluir_testimonios)
        if motivo:
            excluidos.append((file_id, titulo, motivo))
        else:
            incluidos.append((file_id, titulo))
    return sorted(incluidos, key=lambda a: normalizar(a[1])), excluidos


def _motivo_de_exclusion(fuente, titulo, incluir_testimonios):
    t = normalizar(titulo)
    if not t.endswith('.txt'):
        return 'no es .txt'
    if any(p in t for p in PALABRAS_PRIVADAS):
        return 'titulo con palabra de contenido privado'
    if fuente == 'drive-audios' and not PATRON_AUDIO.match(titulo):
        return 'no sigue el patron DIAnn - ...'
    if fuente == 'drive-canal' and any(y in t for y in YA_ES_LECCION):
        return 'ya esta indexado como leccion del curso'
    if MARCA_TESTIMONIO in t and not incluir_testimonios:
        return 'testimonio (usar --incluir-testimonios para sumarlo)'
    return None


def listar(fuente, incluir_testimonios=False, pedir=None):
    """Lista una carpeta permitida. `pedir(url) -> str` se inyecta en las pruebas."""
    carpeta = CARPETAS_PERMITIDAS[fuente]  # KeyError si no es una de las dos: a proposito
    pagina = (pedir or _pedir_texto)(URL_LISTADO % carpeta['id'])
    return clasificar(fuente, parsear_listado(pagina), incluir_testimonios)


def descargar(file_id, pedir=None):
    """Baja el .txt publico. Si Drive devuelve una pagina HTML (archivo privado, aviso de virus,
    limite de descargas) se corta: indexar esa pagina meteria basura al RAG."""
    texto = (pedir or _pedir_texto)(URL_DESCARGA % file_id)
    inicio = texto.lstrip()[:200].lower()
    if inicio.startswith('<!doctype html') or inicio.startswith('<html'):
        raise ValueError('Drive devolvio una pagina HTML en vez del texto del archivo %s '
                         '(dejo de ser publico o Drive limito las descargas).' % file_id)
    return texto


def _pedir_texto(url):
    pedido = urllib.request.Request(url, headers={'User-Agent': 'renaser-indexacion/1.0'})
    with urllib.request.urlopen(pedido, timeout=60) as r:
        return r.read().decode('utf-8-sig', 'replace')

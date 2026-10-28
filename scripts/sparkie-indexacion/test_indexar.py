# -*- coding: utf-8 -*-
"""Pruebas sin red: troceo, registro/salteos, corte ante 429, filtros de Drive y consulta.

Correr:  cd scripts/sparkie-indexacion && python3 -m unittest -v test_indexar
Ninguna prueba toca la red ni produccion: el HTTP y Drive son falsos.
"""
import importlib
import inspect
import io
import json
import os
import sys
import tempfile
import unittest
import unittest.mock
from contextlib import redirect_stdout

import drive
import indexar
import procesar_consulta


def texto_de(n_palabras):
    return ' '.join('p%d' % i for i in range(n_palabras))


def doc_de(clave, n_palabras, leccion=True):
    return indexar.documento('T', 'C', 'vid-' + clave, clave if leccion else None, 'Titulo ' + clave,
                             texto=texto_de(n_palabras))


class HttpFalso:
    """Responde 201 salvo en las llamadas indicadas. Guarda todo lo que se le mando."""

    def __init__(self, fallas=None):
        self.fallas = fallas or {}
        self.cuerpos = []

    def __call__(self, cuerpo):
        self.cuerpos.append(cuerpo)
        return self.fallas.get(len(self.cuerpos), (201, None))


class Troceo(unittest.TestCase):

    def test_texto_vacio_no_da_pedazos(self):
        self.assertEqual(indexar.trocear('   '), [])

    def test_texto_corto_es_un_solo_pedazo(self):
        self.assertEqual(len(indexar.trocear(texto_de(450))), 1)

    def test_pedazos_de_450_con_solape_de_60(self):
        pedazos = indexar.trocear(texto_de(1000))
        self.assertEqual(len(pedazos), 3)  # arranques en 0, 390, 780
        self.assertEqual(len(pedazos[0].split()), 450)
        self.assertEqual(pedazos[0].split()[-60:], pedazos[1].split()[:60])
        self.assertEqual(pedazos[-1].split()[-1], 'p999')

    def test_da_los_mismos_totales_que_el_script_viejo(self):
        # 2526 chunks en total y 1022 en las primeras 47 lecciones: lo que se uso en produccion.
        # Si esto cambia, las partes k/N ya indexadas dejan de coincidir.
        pedazos = [len(indexar.trocear(d['texto'])) for d in indexar.cargar_lecciones()]
        self.assertEqual(len(pedazos), 124)
        self.assertEqual(sum(pedazos), 2526)
        self.assertEqual(sum(pedazos[:47]), 1022)


class RegistroYSalteos(unittest.TestCase):

    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.registro = os.path.join(self.dir, 'indexados.jsonl')

    def _salteo(self, contenido):
        ruta = os.path.join(self.dir, 'saltear.txt')
        with io.open(ruta, 'w', encoding='utf-8') as f:
            f.write(contenido)
        return indexar.leer_salteos([ruta])

    def test_clave_es_la_leccion_y_no_el_video(self):
        # 124 lecciones comparten 90 videos: con el id del video se saltearian lecciones ajenas.
        self.assertEqual(doc_de('L1', 10)['clave'], 'L1')
        self.assertEqual(doc_de('D1', 10, leccion=False)['clave'], 'vid-D1')

    def test_archivo_de_salteo_entero_y_por_parte(self):
        enteros, partes = self._salteo('# comentario\nL1\n\nL2\t1/3\nL2\t3/3\n')
        self.assertEqual(enteros, {'L1'})
        self.assertEqual(partes, {('L2', '1/3'), ('L2', '3/3')})

    def test_plan_saltea_enteros_y_completa_solo_lo_que_falta(self):
        docs = [doc_de('L1', 1000), doc_de('L2', 1000), doc_de('L3', 100)]
        plan = indexar.planificar(docs, {'L1'}, {('L2', '1/3'), ('L2', '3/3')})
        self.assertEqual([it['pendientes'] for it in plan], [[], [2], [1]])
        self.assertIn('a medias', plan[1]['nota'])

    def test_otro_total_de_partes_saltea_el_documento_entero(self):
        plan = indexar.planificar([doc_de('L1', 1000)], set(), {('L1', '1/5')})
        self.assertEqual(plan[0]['pendientes'], [])
        self.assertIn('otro total', plan[0]['nota'])

    def test_relanzar_no_reenvia_lo_anotado_en_el_registro(self):
        docs = [doc_de('L1', 1000), doc_de('L2', 100)]
        with redirect_stdout(io.StringIO()):
            indexar.enviar_plan(indexar.planificar(docs, set(), set()), HttpFalso(),
                                self.registro, 900)
        http = HttpFalso()
        plan = indexar.planificar(docs, set(), indexar.leer_registro(self.registro))
        with redirect_stdout(io.StringIO()):
            self.assertEqual(indexar.enviar_plan(plan, http, self.registro, 900), 0)
        self.assertEqual(http.cuerpos, [])

    def test_el_cuerpo_conserva_el_formato_de_produccion(self):
        http = HttpFalso()
        with redirect_stdout(io.StringIO()):
            indexar.enviar_plan(indexar.planificar([doc_de('L1', 1000)], set(), set()), http,
                                self.registro, 900)
        c = http.cuerpos[1]
        self.assertEqual(set(c), {'tipoFuente', 'clase', 'documentoId', 'leccionId', 'contenido',
                                  'metadatos'})
        self.assertEqual(c['metadatos'], {'titulo': 'Titulo L1', 'parte': '2/3'})
        self.assertEqual(c['leccionId'], 'L1')


class CorteAnteErrores(unittest.TestCase):

    def setUp(self):
        self.registro = os.path.join(tempfile.mkdtemp(), 'indexados.jsonl')

    def _enviar(self, http, docs, limite=900):
        plan = indexar.planificar(docs, set(), set())
        with redirect_stdout(io.StringIO()):
            return indexar.enviar_plan(plan, http, self.registro, limite)

    def test_429_para_en_el_acto_y_dice_que_quedo_a_medias(self):
        http = HttpFalso({2: (429, 'RESOURCE_EXHAUSTED')})
        with self.assertRaises(indexar.CorteDeEnvio) as corte:
            self._enviar(http, [doc_de('L1', 1000), doc_de('L2', 1000)])
        self.assertEqual(len(http.cuerpos), 2)  # no siguio con la parte 3 ni con L2
        mensaje = str(corte.exception)
        self.assertIn('HTTP 429 en la parte 2/3', mensaje)
        self.assertIn('Titulo L1', mensaje)
        self.assertIn('cuota', mensaje)
        self.assertEqual(indexar.leer_registro(self.registro), {('L1', '1/3')})

    def test_despues_del_429_se_retoma_exactamente_donde_quedo(self):
        docs = [doc_de('L1', 1000)]
        with self.assertRaises(indexar.CorteDeEnvio):
            self._enviar(HttpFalso({2: (429, None)}), docs)
        http = HttpFalso()
        plan = indexar.planificar([doc_de('L1', 1000)], set(), indexar.leer_registro(self.registro))
        with redirect_stdout(io.StringIO()):
            indexar.enviar_plan(plan, http, self.registro, 900)
        self.assertEqual([c['metadatos']['parte'] for c in http.cuerpos], ['2/3', '3/3'])

    def test_5xx_401_y_red_caida_tambien_cortan(self):
        for estado, pista in ((500, 'logs del backend'), (401, 'token'), (0, 'NO SE SABE')):
            http = HttpFalso({1: (estado, 'x')})
            with self.assertRaises(indexar.CorteDeEnvio) as corte:
                self._enviar(http, [doc_de('L%d' % estado, 100), doc_de('Z', 100)])
            self.assertEqual(len(http.cuerpos), 1)
            self.assertIn(pista, str(corte.exception))

    def test_el_tope_no_deja_documentos_a_medias(self):
        http = HttpFalso()
        enviados = self._enviar(http, [doc_de('L1', 1000), doc_de('L2', 1000)], limite=4)
        self.assertEqual(enviados, 3)  # L1 entera; L2 (3 partes) no entra y no se empieza
        self.assertEqual({c['leccionId'] for c in http.cuerpos}, {'L1'})


class ValidacionDeEnvio(unittest.TestCase):

    def test_exige_https_y_token(self):
        self.assertIn('https', indexar.validar_envio('http://x', 'tok'))
        self.assertIn('https', indexar.validar_envio(None, 'tok'))
        self.assertIn('RENASER_ADMIN_TOKEN', indexar.validar_envio('https://x', ''))
        self.assertIsNone(indexar.validar_envio('https://x', 'tok'))

    def test_sin_token_con_enviar_no_hace_nada(self):
        os.environ.pop(indexar.VARIABLE_TOKEN, None)
        with redirect_stdout(io.StringIO()), unittest.mock.patch('sys.stderr', io.StringIO()):
            self.assertEqual(indexar.main(['--enviar', '--url', 'https://x']), 2)

    def test_por_defecto_es_ensayo(self):
        a = indexar.argumentos([])
        self.assertFalse(a.enviar)
        self.assertEqual(a.limite_embeddings, 900)
        self.assertEqual(a.fuentes, indexar.FUENTES)


def entrada(file_id, titulo, carpeta=False):
    enlace = ('https://drive.google.com/drive/folders/%s' if carpeta
              else 'https://drive.google.com/file/d/%s/view?usp=drive_web') % file_id
    return ('<div class="flip-entry" id="entry-%s" tabindex="0"><a href="%s"><div '
            'class="flip-entry-title">%s</div></a></div>' % (file_id, enlace, titulo))


class FiltrosDeDrive(unittest.TestCase):

    def test_parsea_titulos_con_entidades_y_descarta_subcarpetas(self):
        pagina = entrada('a1', 'Don&#39;t play.txt') + entrada('c1', 'Sesiones', carpeta=True)
        self.assertEqual(drive.parsear_listado(pagina), [('a1', "Don't play.txt")])

    def test_canal_excluye_leccion_repetida_y_testimonios(self):
        archivos = [('1', 'El engaño de creer que sabes..txt'), ('2', 'TUS MIEDOS.txt'),
                    ('3', 'VUELVE A NACER - TESTIMONIO.txt'), ('4', 'foto.jpg')]
        incluidos, excluidos = drive.clasificar('drive-canal', archivos)
        self.assertEqual(incluidos, [('2', 'TUS MIEDOS.txt')])
        self.assertEqual(len(excluidos), 3)
        incluidos, _ = drive.clasificar('drive-canal', archivos, incluir_testimonios=True)
        self.assertEqual([i for i, _ in incluidos], ['2', '3'])

    def test_audios_solo_con_patron_dia(self):
        archivos = [('1', 'DIA01 - Algo.txt'), ('2', 'DIA23- Sin espacio.txt'),
                    ('3', 'Sesion individual Maria.txt'), ('4', 'Notas.txt')]
        incluidos, _ = drive.clasificar('drive-audios', archivos)
        self.assertEqual([i for i, _ in incluidos], ['1', '2'])

    def test_palabras_privadas_se_excluyen_en_cualquier_carpeta(self):
        _, excluidos = drive.clasificar('drive-canal', [('1', 'Mentoría grupal 3.txt')])
        self.assertIn('privado', excluidos[0][2])

    def test_no_acepta_carpetas_fuera_de_la_lista(self):
        with self.assertRaises(KeyError):
            drive.listar('otra-carpeta', pedir=lambda url: '')

    def test_descarga_que_devuelve_html_corta(self):
        with self.assertRaises(ValueError):
            drive.descargar('x', pedir=lambda url: '<!DOCTYPE html><html>login</html>')

    def test_carga_de_drive_difiere_la_descarga_y_arma_el_documento(self):
        pedidos = []

        def pedir(url):
            pedidos.append(url)
            return entrada('f1', 'DIA01 - Hola.txt') if 'embedded' in url else texto_de(10)
        with redirect_stdout(io.StringIO()):
            docs = indexar.cargar_drive('drive-audios', False, pedir)
        self.assertEqual(len(pedidos), 1)  # solo el listado todavia
        self.assertEqual((docs[0]['tipoFuente'], docs[0]['clase'], docs[0]['documentoId'],
                          docs[0]['leccionId'], docs[0]['titulo']),
                         (indexar.TIPO_FUENTE_AUDIO, indexar.CLASE_AUDIO, 'f1', None, 'DIA01 - Hola'))
        plan = indexar.planificar(docs, set(), set())
        self.assertEqual(plan[0]['pendientes'], [1])
        self.assertIn('uc?export=download&id=f1', pedidos[1])


# El unico video de la lista real (D-285) y dos descartados por ser promocion, no ensenanza.
ID_RIQUEZA = '44uFzhTFp0Y'
IDS_DESCARTADOS = ('ydI2_k3hbxk', '-MINWhAO-Gw')
# Datos FICTICIOS para los escenarios de varios videos: no son ni deben ser de la lista real.
ID_A, ID_B, ID_SIN_SUBS = 'aaaaaaaaaaa', 'bbbbbbbbbbb', 'ccccccccccc'
ID_CON_GUION = '-abcdefghij'  # un id de YouTube puede empezar con guion
LISTA_FICTICIA = [(ID_A, 'Clase ficticia A'), (ID_B, 'Clase ficticia B'),
                  (ID_SIN_SUBS, 'Clase ficticia sin subtitulos')]

VTT_DE_PRUEBA = (
    'WEBVTT\nKind: captions\nLanguage: es\n\n'
    '00:00:00.000 --> 00:00:02.000\nhola a todos\n\n'
    '00:00:02.000 --> 00:00:04.000\nhola a todos\nvamos a crear riqueza\n\n'
    '00:00:04.000 --> 00:00:06.000\n[Musica]\nvamos a crear riqueza\n')


class BajarFalso:
    """Doble de extraer_todo.bajar: escribe un VTT por id, salvo los ids en `sin_subs`."""

    def __init__(self, sin_subs=(), error=None):
        self.dir = tempfile.mkdtemp()
        self.sin_subs = set(sin_subs)
        self.error = error
        self.pedidos = []

    def __call__(self, vid):
        self.pedidos.append(vid)
        if self.error:
            raise self.error
        if vid in self.sin_subs:
            return None
        ruta = os.path.join(self.dir, vid + '.es-orig.vtt')
        with io.open(ruta, 'w', encoding='utf-8') as f:
            f.write(VTT_DE_PRUEBA)
        return ruta


def limpiar_falso(ruta):
    """Doble de extraer_todo.limpiar: un texto largo que da 3 pedazos."""
    return texto_de(1000)


def cargar_youtube_falso(lista=None, bajar=None, limpiar=limpiar_falso):
    """Arma los documentos con dobles. `lista=None` usa la lista REAL; con una lista, la reemplaza
    solo durante la carga (los documentos ya armados no dependen de ella)."""
    bajar = bajar or BajarFalso()
    with redirect_stdout(io.StringIO()):
        if lista is None:
            return bajar, indexar.cargar_youtube(bajar=bajar, limpiar=limpiar)
        with unittest.mock.patch.object(indexar, 'VIDEOS_YOUTUBE_ENSENANZA', lista):
            return bajar, indexar.cargar_youtube(bajar=bajar, limpiar=limpiar)


class YoutubeEnsenanza(unittest.TestCase):

    def setUp(self):
        self.registro = os.path.join(tempfile.mkdtemp(), 'indexados.jsonl')

    # ---- la lista real ----

    def test_la_lista_real_es_solo_la_ensenanza_aprobada(self):
        self.assertEqual(indexar.VIDEOS_YOUTUBE_ENSENANZA, [(ID_RIQUEZA, 'Crea riqueza en el 2025')])
        # promociones evaluadas y descartadas (criterio del dueno): no vuelven sin preguntarle
        for descartado in IDS_DESCARTADOS:
            self.assertNotIn(descartado, [v for v, _ in indexar.VIDEOS_YOUTUBE_ENSENANZA])
        incluidos, excluidos = indexar.clasificar_videos(indexar.VIDEOS_YOUTUBE_ENSENANZA)
        self.assertEqual(len(incluidos), 1)
        self.assertEqual(excluidos, [])  # ningun titulo de la lista real cae en el filtro

    def test_arma_el_documento_con_la_misma_etiqueta_que_el_canal(self):
        _, docs = cargar_youtube_falso()
        self.assertEqual(len(docs), 1)
        d = docs[0]
        self.assertEqual((d['tipoFuente'], d['clase']), ('TRANSCRIPCION_CANAL', 'CANAL DARREN'))
        self.assertEqual((d['tipoFuente'], d['clase']),
                         (indexar.TIPO_FUENTE_CANAL, indexar.CLASE_CANAL))
        self.assertEqual(d['documentoId'], ID_RIQUEZA)
        self.assertIsNone(d['leccionId'])
        self.assertEqual(d['clave'], ID_RIQUEZA)
        self.assertEqual(d['titulo'], 'Crea riqueza en el 2025')

    def test_el_cuerpo_enviado_es_el_del_canal_visible_desde_el_dia_0(self):
        _, docs = cargar_youtube_falso()
        http = HttpFalso()
        plan = indexar.planificar(docs, set(), set())
        with redirect_stdout(io.StringIO()):
            indexar.enviar_plan(plan, http, self.registro, 900)
        self.assertEqual(len(http.cuerpos), 3)
        c = http.cuerpos[0]
        self.assertEqual(set(c), {'tipoFuente', 'clase', 'documentoId', 'leccionId', 'contenido',
                                  'metadatos'})
        self.assertEqual((c['tipoFuente'], c['clase'], c['documentoId'], c['leccionId']),
                         ('TRANSCRIPCION_CANAL', 'CANAL DARREN', ID_RIQUEZA, None))
        self.assertEqual(c['metadatos'], {'titulo': 'Crea riqueza en el 2025', 'parte': '1/3'})

    def test_entra_en_todas_por_defecto(self):
        self.assertIn('youtube-ensenanza', indexar.FUENTES)
        self.assertEqual(indexar.argumentos([]).fuentes, indexar.FUENTES)
        self.assertEqual(indexar.argumentos(['--fuentes', 'youtube-ensenanza']).fuentes,
                         ['youtube-ensenanza'])

    def test_sin_enviar_no_manda_nada(self):
        # main() en ensayo con la fuente nueva: ni HTTP ni registro.
        _, docs = cargar_youtube_falso()
        with unittest.mock.patch.object(indexar, 'cargar_youtube', return_value=docs), \
                unittest.mock.patch.object(indexar, 'publicar_http') as publicar, \
                redirect_stdout(io.StringIO()) as salida:
            self.assertEqual(indexar.main(['--fuentes', 'youtube-ensenanza', '--registro',
                                           self.registro]), 0)
        publicar.assert_not_called()
        self.assertFalse(os.path.exists(self.registro))
        self.assertIn('ENSAYO: no se envio nada', salida.getvalue())

    def test_la_lista_no_acepta_ids_de_afuera(self):
        # 1) la linea de comandos no tiene ninguna opcion para pasar videos
        for opcion in ('--videos', '--video', '--video-id', '--ids', '--url-video'):
            with self.subTest(opcion=opcion), unittest.mock.patch('sys.stderr', io.StringIO()):
                with self.assertRaises(SystemExit):
                    indexar.argumentos(['--fuentes', 'youtube-ensenanza', opcion, ID_RIQUEZA])
        # 2) la funcion que arma la fuente no recibe ids: solo los dobles de bajar y limpiar
        self.assertEqual(set(inspect.signature(indexar.cargar_youtube).parameters),
                         {'bajar', 'limpiar'})
        # 3) lo que se baja es exactamente la lista escrita a mano, nada mas
        bajar, docs = cargar_youtube_falso()
        with redirect_stdout(io.StringIO()):
            indexar.planificar(docs, set(), set())
        self.assertEqual(bajar.pedidos, [v for v, _ in indexar.VIDEOS_YOUTUBE_ENSENANZA])

    # ---- el mecanismo, con una lista ficticia de varios videos ----

    def test_un_video_sin_subtitulos_se_saltea_con_aviso_y_no_rompe_la_corrida(self):
        bajar, docs = cargar_youtube_falso(LISTA_FICTICIA, BajarFalso(sin_subs=[ID_SIN_SUBS]))
        salida = io.StringIO()
        with redirect_stdout(salida):
            plan = indexar.planificar(docs, set(), set())
            indexar.imprimir_plan(plan, 900)
        self.assertEqual([it['pendientes'] for it in plan], [[1, 2, 3], [1, 2, 3], []])
        self.assertTrue(plan[2]['sin_transcripcion'])
        self.assertIn('SIN TRANSCRIPCION', plan[2]['nota'])
        texto = salida.getvalue()
        self.assertIn('AVISO', texto)
        self.assertIn(ID_SIN_SUBS, texto)
        self.assertIn('No se inventa una transcripcion', texto)
        # y lo demas se envia igual, sin tocar al video sin subtitulos
        http = HttpFalso()
        with redirect_stdout(io.StringIO()):
            self.assertEqual(indexar.enviar_plan(plan, http, self.registro, 900), 6)
        self.assertEqual({c['documentoId'] for c in http.cuerpos}, {ID_A, ID_B})

    def test_un_subtitulo_vacio_tambien_se_saltea(self):
        _, docs = cargar_youtube_falso(LISTA_FICTICIA, limpiar=lambda ruta: '   ')
        with redirect_stdout(io.StringIO()):
            plan = indexar.planificar(docs, set(), set())
        self.assertEqual([it['pendientes'] for it in plan], [[], [], []])
        self.assertTrue(all(it['sin_transcripcion'] for it in plan))

    def test_lo_que_ya_esta_en_produccion_no_toca_youtube(self):
        bajar, docs = cargar_youtube_falso(LISTA_FICTICIA)
        with redirect_stdout(io.StringIO()):
            plan = indexar.planificar(docs, {ID_A, ID_B, ID_SIN_SUBS}, set())
        self.assertEqual(bajar.pedidos, [])
        self.assertEqual([it['nota'] for it in plan], ['ya en produccion'] * 3)

    def test_solo_listar_no_baja_nada(self):
        bajar, docs = cargar_youtube_falso(LISTA_FICTICIA)
        with redirect_stdout(io.StringIO()):
            indexar.planificar(docs, set(), set(), solo_listar=True)
        self.assertEqual(bajar.pedidos, [])

    def test_es_idempotente_con_el_registro_y_con_el_salteo_por_parte(self):
        _, docs = cargar_youtube_falso(LISTA_FICTICIA)
        with redirect_stdout(io.StringIO()):
            indexar.enviar_plan(indexar.planificar(docs, set(), set()), HttpFalso(), self.registro, 900)
        _, docs = cargar_youtube_falso(LISTA_FICTICIA)
        http = HttpFalso()
        with redirect_stdout(io.StringIO()):
            plan = indexar.planificar(docs, set(), indexar.leer_registro(self.registro))
            self.assertEqual(indexar.enviar_plan(plan, http, self.registro, 900), 0)
        self.assertEqual(http.cuerpos, [])
        # a medias: consulta-indexados.sh anota la parte 2/3 y solo faltan la 1 y la 3
        _, docs = cargar_youtube_falso(LISTA_FICTICIA)
        with redirect_stdout(io.StringIO()):
            plan = indexar.planificar(docs[:1], set(), {(ID_A, '2/3')})
        self.assertEqual(plan[0]['pendientes'], [1, 3])

    def test_un_error_de_envio_corta_en_el_acto(self):
        _, docs = cargar_youtube_falso(LISTA_FICTICIA)
        http = HttpFalso({2: (429, 'RESOURCE_EXHAUSTED')})
        with redirect_stdout(io.StringIO()):
            plan = indexar.planificar(docs, set(), set())
            with self.assertRaises(indexar.CorteDeEnvio) as corte:
                indexar.enviar_plan(plan, http, self.registro, 900)
        self.assertEqual(len(http.cuerpos), 2)  # no siguio con la parte 3 ni con el video B
        self.assertIn('Clase ficticia A', str(corte.exception))

    def test_un_id_mal_formado_o_repetido_en_la_lista_corta(self):
        for malo in ('', 'corto', 'https://youtu.be/44uFzhTFp0Y', '--version -h', 'a' * 12,
                     '44uFzhTFp0Y; rm', None):
            with self.subTest(id=malo), self.assertRaises(ValueError):
                indexar.clasificar_videos([(malo, 'Titulo')])
        with self.assertRaises(ValueError):
            indexar.clasificar_videos([(ID_A, 'A'), (ID_A, 'B')])
        # un id valido puede empezar con guion
        self.assertEqual(indexar.clasificar_videos([(ID_CON_GUION, 'Ok')]),
                         ([(ID_CON_GUION, 'Ok')], []))

    def test_el_filtro_de_privacidad_de_titulos_tambien_corre_aqui(self):
        videos = [('aaaaaaaaaaa', 'Testimonio de Ana - Mi proceso'),
                  ('bbbbbbbbbbb', 'Sesión individual con Pedro'),
                  ('ccccccccccc', 'Mentoría grupal 3'),
                  ('ddddddddddd', 'Atención a una aprendiz'),
                  ('eeeeeeeeeee', 'Entrevista a un alumno'),
                  ('fffffffffff', 'Ficha de ingreso'),
                  ('ggggggggggg', 'Crea riqueza en el 2025')]
        incluidos, excluidos = indexar.clasificar_videos(videos)
        self.assertEqual(incluidos, [('ggggggggggg', 'Crea riqueza en el 2025')])
        self.assertEqual([v for v, _, _ in excluidos], [v for v, _ in videos[:6]])
        self.assertIn('testimonio', excluidos[0][2])
        self.assertIn('privado', excluidos[1][2])

    def test_un_titulo_prohibido_de_la_lista_se_excluye_y_no_se_baja(self):
        lista = [(ID_RIQUEZA, 'Crea riqueza en el 2025'), (ID_A, 'TESTIMONIO de Luis')]
        bajar = BajarFalso()
        salida = io.StringIO()
        with unittest.mock.patch.object(indexar, 'VIDEOS_YOUTUBE_ENSENANZA', lista), \
                redirect_stdout(salida):
            docs = indexar.cargar_youtube(bajar=bajar, limpiar=limpiar_falso)
            indexar.planificar(docs, set(), set())
        self.assertEqual([d['documentoId'] for d in docs], [ID_RIQUEZA])
        self.assertEqual(bajar.pedidos, [ID_RIQUEZA])
        self.assertIn('excluido', salida.getvalue())
        # --incluir-testimonios es solo de Drive: no abre esta puerta
        self.assertEqual(indexar.argumentos(['--incluir-testimonios']).fuentes, indexar.FUENTES)
        self.assertEqual(indexar.clasificar_videos([(ID_A, 'TESTIMONIO de Luis')])[0], [])

    def test_sin_yt_dlp_corta_con_un_mensaje_claro(self):
        bajar, docs = cargar_youtube_falso(bajar=BajarFalso(error=FileNotFoundError('yt-dlp')))
        with self.assertRaises(SystemExit) as corte, redirect_stdout(io.StringIO()):
            indexar.planificar(docs, set(), set())
        self.assertIn('yt-dlp', str(corte.exception))

    def test_con_el_limpiar_real_el_vtt_sale_en_prosa_sin_repeticiones(self):
        import extraer_todo
        bajar, docs = cargar_youtube_falso(limpiar=extraer_todo.limpiar)
        with redirect_stdout(io.StringIO()):
            plan = indexar.planificar(docs, set(), set())
        self.assertEqual(plan[0]['doc']['texto'], 'hola a todos vamos a crear riqueza')
        self.assertEqual(plan[0]['pendientes'], [1])

    def test_importar_extraer_todo_no_lanza_la_extraccion_de_las_lecciones(self):
        # Antes el modulo corria las 124 lecciones al importarse (y yt-dlp, si faltaba un json).
        sys.modules.pop('extraer_todo', None)
        salida = io.StringIO()
        with redirect_stdout(salida), unittest.mock.patch('subprocess.run') as correr:
            modulo = importlib.import_module('extraer_todo')
        self.assertEqual(salida.getvalue(), '')
        correr.assert_not_called()
        self.assertEqual(modulo.id_de_youtube('https://youtu.be/' + ID_CON_GUION), ID_CON_GUION)


class ConsultaDeProduccion(unittest.TestCase):

    SALIDA = ['FILA\tA\tT\t2\t2\t2\t1\t0\t\n', 'FILA\tB\tT\t2\t2\t3\t1\t0\t1,3\n',
              'FILA\tC\tT\t2\t1\t1\t1\t0\t1\n', 'FIN\t3\n']

    def test_completo_entero_a_medias_por_parte_duplicado_entero(self):
        saltear, informe = procesar_consulta.procesar(self.SALIDA)
        self.assertEqual(saltear, ['A', 'B\t1/3', 'B\t3/3', 'C'])
        self.assertTrue(any('DUPLICADOS (1)' in l for l in informe))

    def test_salida_cortada_no_genera_salteo(self):
        with self.assertRaises(ValueError):
            procesar_consulta.procesar(self.SALIDA[:2])


if __name__ == '__main__':
    unittest.main()

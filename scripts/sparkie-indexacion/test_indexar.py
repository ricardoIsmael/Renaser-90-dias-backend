# -*- coding: utf-8 -*-
"""Pruebas sin red: troceo, registro/salteos, corte ante 429, filtros de Drive y consulta.

Correr:  cd scripts/sparkie-indexacion && python3 -m unittest -v test_indexar
Ninguna prueba toca la red ni produccion: el HTTP y Drive son falsos.
"""
import io
import json
import os
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

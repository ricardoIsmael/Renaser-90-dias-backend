# -*- coding: utf-8 -*-
"""Convierte la salida de consulta-indexados.sh en el archivo de salteo de indexar.py.

Entrada (una linea por documento en produccion, separada por tabs):
  FILA clave tipo_fuente filas partes_distintas n_total totales_distintos sin_parte partes_presentes
  FIN  cantidad_de_documentos   <- si falta o no coincide, la salida vino cortada y no se usa.

Salida:
  saltear.txt  `CLAVE` = saltear el documento entero; `CLAVE<TAB>k/N` = esa parte ya esta.
  informe.txt  resumen para leer, con las lecciones a medias y las duplicadas.

Criterio (lo que evita duplicar filas en produccion):
  completo           -> se saltea entero.
  a medias           -> se anotan solo las partes presentes: indexar.py manda las que faltan.
  duplicado o raro   -> se saltea ENTERO y se avisa: arreglarlo requiere un DELETE aprobado.
"""
import io
import os
import sys


def clasificar(campos):
    clave, tipo, filas, distintas, n, totales, sin_parte, partes = campos
    filas, distintas, n, totales, sin_parte = map(int, (filas, distintas, n, totales, sin_parte))
    presentes = [p for p in partes.split(',') if p]
    if filas > distintas + sin_parte:
        return 'duplicado', '%s (%s): %d filas para %d partes distintas de %d' % (
            clave, tipo, filas, distintas, n)
    if totales > 1 or sin_parte or n == 0:
        return 'raro', '%s (%s): %d filas, %d sin "parte", %d totales distintos' % (
            clave, tipo, filas, sin_parte, totales)
    if distintas < n:
        return 'a_medias', (clave, n, presentes, tipo)
    return 'completo', clave


def procesar(lineas):
    """Devuelve (lineas_saltear, lineas_informe). Lanza ValueError si la salida vino cortada."""
    filas = [l.rstrip('\n').split('\t') for l in lineas if l.startswith('FILA\t')]
    fin = [l.split('\t') for l in lineas if l.startswith('FIN\t')]
    if not fin or int(fin[-1][1]) != len(filas):
        raise ValueError('La salida de produccion vino incompleta (sin FIN o con otra cantidad de '
                         'documentos). No se genera el salteo: volver a correr la consulta.')
    saltear, grupos = [], {'completo': [], 'a_medias': [], 'duplicado': [], 'raro': []}
    for f in filas:
        if len(f) == 8:
            f.append('')  # la columna de partes vacia puede llegar sin su tab final
        if len(f) != 9:
            raise ValueError('Fila con formato inesperado: %r' % f)
        if f[1] == '(sin clave)':
            grupos['raro'].append('%s filas sin leccion_id ni documento_id' % f[3])
            continue
        tipo, dato = clasificar(f[1:])
        grupos[tipo].append(dato)
        if tipo == 'a_medias':
            clave, n, presentes, _ = dato
            saltear += ['%s\t%s/%d' % (clave, k, n) for k in presentes]
        else:
            saltear.append(f[1])  # completo, duplicado o raro: se saltea entero
    return saltear, _informe(grupos)


def _informe(g):
    lineas = ['Documentos completos en produccion: %d' % len(g['completo']),
              '', 'A MEDIAS (%d): indexar.py manda solo las partes que faltan, sin duplicar:'
              % len(g['a_medias'])]
    for clave, n, presentes, tipo in g['a_medias']:
        lineas.append('  %s (%s): tiene %d de %d partes' % (clave, tipo, len(presentes), n))
    lineas += ['', 'DUPLICADOS (%d): se saltean enteros. Limpiarlos es un DELETE en produccion: '
               'ver LEEME.md, "Lecciones a medias o duplicadas".' % len(g['duplicado'])]
    lineas += ['  ' + d for d in g['duplicado']]
    lineas += ['', 'RAROS (%d): se saltean enteros, revisar a mano:' % len(g['raro'])]
    lineas += ['  ' + d for d in g['raro']]
    return lineas


def main():
    destino = sys.argv[1]
    saltear, informe = procesar(io.open(0, encoding='utf-8', errors='replace').readlines())
    os.makedirs(destino, exist_ok=True)
    with io.open(os.path.join(destino, 'saltear.txt'), 'w', encoding='utf-8') as f:
        f.write('# Generado por consulta-indexados.sh. Ya esta en produccion: no reenviar.\n')
        f.write('\n'.join(saltear) + '\n')
    with io.open(os.path.join(destino, 'informe.txt'), 'w', encoding='utf-8') as f:
        f.write('\n'.join(informe) + '\n')
    print('\n'.join(informe))


if __name__ == '__main__':
    main()

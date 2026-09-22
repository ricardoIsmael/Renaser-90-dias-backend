/**
 * Contrato publico de `rag` hacia otros modulos.
 *
 * <p><b>Corregido 2026-09-15.</b> Este javadoc decia <i>"Hoy vacio a proposito: ningun modulo
 * consume nada de `rag` — es el consumidor final de la cadena"</i>. Ya no: vive aca
 * {@link com.renaser.os.rag.api.PatronDeMalestarRepetidoEvent}, que {@code notifications} escucha
 * para ponerle un aviso en la bandeja a ADMIN/ALCHEMIST. Sigue valiendo que `rag` lee de casi todos
 * (de {@code habits} via {@code EntradaDiarioFinder}, de {@code users} via sus finders, de
 * {@code academy} via {@code LeccionesVisiblesFinder}) y que nadie le consulta datos: lo unico que
 * sale de este modulo es ese evento.
 *
 * <p>El paquete existia igual desde el dia uno, con su {@code @NamedInterface} declarada, para que
 * el dia que hiciera falta el lugar ya estuviera y no hubiera que decidirlo con apuro. Fue el caso.
 */
@org.springframework.modulith.NamedInterface("api")
package com.renaser.os.rag.api;

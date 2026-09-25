package com.renaser.os.points.api;

import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/**
 * D-43 ({@code docs/MODULOS_A_AVANZAR.md} §8): expone el % de coherencia de Rocas
 * Diarias de TODOS los participantes pedidos EN LOTE, sin el N+1 que rompió producción con ~30 cuentas
 * activas ({@code prisma/migrations/general_ranking_scores_function.sql},
 * cabecera). Una implementación que por dentro itere participantes llamando a
 * una consulta por cada uno no cumple el contrato de este puerto.
 *
 * <p><b>Quien lo consume, desde el 2026-09-22.</b> El ranking GENERAL ya no: las rocas —hoy
 * OBJETIVOS— salieron de esa formula, que quedo en 75% habitos + 25% cursos. Este porcentaje
 * <b>ES</b> la coherencia, y sigue vivo en los dos lugares donde esa es la pregunta: la coherencia
 * que muestra Hoy ({@code HomeAgregadoService.coherenciaDe}, D-128) y el orden del ranking de
 * CELULA ({@code RankingService.generar(CELL, ...)}). No se borra nada.
 *
 * <p>Fórmula (D-128, {@code rocks.domain.model.coherencia.PorcentajeRocas}): ventana de 7 días
 * terminando en {@code hasta} (incluido), que quien llama calcula como HOY EN LA ZONA DEL
 * PARTICIPANTE (regla 02 §1); una Roca Diaria nunca es opcional, así que toda roca planificada
 * entra al total; se cuentan <b>acciones, no días</b>: cumplidas ÷ planificadas de la ventana
 * entera, con 1 decimal.
 *
 * <p>El valor es un {@link BigDecimal} de 1 decimal, no un {@code Integer}: truncar acá a entero
 * perdería precisión que {@code points} necesita para su propio redondeo final.
 *
 * <p><b>Un participante sin ninguna acción planificada en la ventana NO aparece en el mapa</b>:
 * sin clave es "sin dato", ni un cero ni un cien (D-128). Quien consume lee la ausencia de clave
 * como "no planificó su semana" ({@code HomeAgregadoService.coherenciaDe} la devuelve como
 * {@code null}).
 *
 * <p><b>Corregido 2026-09-23.</b> Este javadoc seguía diciendo lo de antes de D-128 (2026-09-15):
 * "ventana de 7 días UTC cerrados", "cada día se redondea a entero primero y LUEGO se promedia
 * (doble redondeo deliberado); ventana sin días calificables → 100" y "Cada {@code UserId} pedido
 * aparece en el mapa devuelto — incluso un participante sin ninguna Roca Diaria calificable en la
 * ventana entera, con valor {@code 100.0}". La implementación ({@code PorcentajeRocasService})
 * omite la clave desde D-128, y un consumidor que confiara en el contrato viejo mostraría un 100
 * inventado o reventaría con un {@code null} inesperado.
 *
 * <p><b>Por que vive en `points` y no en el modulo que lo implementa (DIP):</b> declararlo
 * en el modulo proveedor creaba un CICLO que Spring Modulith rechaza — `habits` ya depende
 * de `points` para otorgar puntos al completar, asi que `points` no puede depender de
 * `habits` en la otra direccion. Invirtiendo la dependencia, el consumidor declara lo que
 * necesita y el proveedor lo implementa: la flecha queda en un solo sentido.
 */
public interface PorcentajeRocasFinder {

    Map<UserId, BigDecimal> porcentajePorParticipante(Collection<UserId> participantes, LocalDate hasta);
}

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
 * <p>Fórmula (ver {@code docs/MODULO_ROCKS.md} §8 para el detalle citado
 * archivo:línea): ventana de 7 días UTC cerrados terminando en {@code hasta}
 * (incluido); una Roca Diaria nunca es opcional, así que toda roca
 * planificada ese día entra al total; cada día se redondea a entero primero y
 * LUEGO se promedia (doble redondeo deliberado); ventana sin días calificables
 * → 100.
 *
 * <p>El valor es un {@link BigDecimal} de 1 decimal, no un {@code Integer}:
 * la fórmula original ya redondea a 1 decimal
 * ({@code round(avg(day_score) * 10) / 10}) y truncar acá a entero perdería
 * precisión que {@code points} necesita para su propio redondeo final.
 *
 * <p>Cada {@code UserId} pedido aparece en el mapa devuelto — incluso un
 * participante sin ninguna Roca Diaria calificable en la ventana entera, con
 * valor {@code 100.0} (ventana vacía, no un castigo).

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

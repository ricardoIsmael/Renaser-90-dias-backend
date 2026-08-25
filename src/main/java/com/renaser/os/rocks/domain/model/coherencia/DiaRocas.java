package com.renaser.os.rocks.domain.model.coherencia;

import java.time.LocalDate;

/**
 * Conteo crudo, ya agregado, de Rocas Diarias calificables de UN participante
 * en UN día — el resultado de la consulta EN LOTE (D-43,
 * {@code CargarConteoDiarioRocasPort}), nunca de una query por participante.
 *
 * <p>A diferencia de un hábito (ver {@code habits}), una Roca Diaria NUNCA es
 * opcional — no existe el concepto en {@code rocas_diarias} ni en el
 * {@code DailyRock} del repo viejo (confirmado en
 * {@code prisma/schema.prisma:1574-1605}, sin columna {@code isOptional}). Por
 * eso {@code total} ya es, sin filtrar nada más, la cantidad de Rocas Diarias
 * que ese participante tenía planificadas ese día (1 a 3, una por eje con
 * plan semanal) y {@code completadas} las que tienen evidencia aceptada.
 */
public record DiaRocas(LocalDate fecha, int total, int completadas) {

    public DiaRocas {
        if (fecha == null) {
            throw new IllegalArgumentException("fecha es obligatoria");
        }
        if (total <= 0) {
            throw new IllegalArgumentException("total debe ser mayor a 0 (un día sin rocas no se reporta)");
        }
        if (completadas < 0 || completadas > total) {
            throw new IllegalArgumentException("completadas fuera de rango: " + completadas + "/" + total);
        }
    }

    /**
     * completadas/total × 100, redondeado a entero — el PRIMER redondeo del
     * doble redondeo deliberado de Ley VI (mismo criterio que
     * {@code computeDailyCompletionHistory} en el repo viejo:
     * {@code Math.round((completed / total) * 100)}). {@link PorcentajeRocas}
     * promedia estos enteros, nunca las fracciones crudas.
     */
    public int puntajeDelDia() {
        return Math.toIntExact(Math.round(completadas * 100.0 / total));
    }
}

package com.renaser.os.points.api;

import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * Contrato público de `points` para leer, desde `rocks`, las Rocas Diarias planificadas
 * hoy de un participante (gap #21, widget {@code rocksToday} de {@code GET /home}). Vive
 * en `points.api` (no en `rocks.api`) por el mismo motivo que {@link PorcentajeRocasFinder}:
 * `rocks` ya depende de `points` para otorgar puntos, así que `points` no puede depender
 * de `rocks` en la otra dirección sin crear un ciclo — DIP, `rocks.RocaDiariaService`
 * implementa lo que este módulo declara.
 *
 * <p>"Hoy" se calcula con la MISMA zona horaria del participante que ya usa
 * {@code ConsultarRocasDeHoyUseCase.hoy(UserId)} (`RocaDiariaService`) — este finder
 * reutiliza esa cuenta de días, no la duplica. A diferencia del caso de uso interno, no
 * exige que el actor sea TRAINEE ni chequea suspensión: esa autorización es responsabilidad
 * del módulo que compone el agregador sobre su propio endpoint, no de este finder de lectura.
 */
public interface RocasDelDiaFinder {

    /**
     * @return lista vacía si el participante no existe, no tiene Rocas planificadas
     *         hoy, o no es un TRAINEE con progreso de Rocas — nunca null
     */
    List<RocaDelDiaResumen> deHoy(UserId participanteId);
}

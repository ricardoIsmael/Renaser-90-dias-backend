package com.renaser.os.points.api;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrato público de `points` para leer, desde `calendar`, el próximo evento futuro
 * visible para un participante (gap #21, {@code GET /home}). Vive en `points.api` (no en
 * `calendar.api`) por el mismo motivo que {@link PorcentajeCursosFinder}: `calendar`
 * depende transitivamente de `points` (vía `academy` → `habits` → `evidence` → `points`),
 * así que `points` no puede depender de `calendar` en la otra dirección sin crear un ciclo
 * — DIP, `calendar.ProximoEventoService` implementa lo que este módulo declara.
 *
 * <p>Reutiliza la MISMA resolución de audiencia/elegibilidad que ya usa
 * {@code ListarEventosParaVisorUseCase} (paquete interno de `calendar`, sin
 * {@code @NamedInterface}) — este finder no reimplementa esa lógica, la reutiliza acotada
 * a una sola ocurrencia.
 *
 * <p>DTO deliberadamente pobre (id/titulo/inicio) — nunca {@code Evento} completo, mismo
 * criterio que {@code RecordatorioEventoDebidoEvent} (CLAUDE.MD §5.1: solo {@code api/} es
 * público, y lo que cruza esa frontera es una proyección, no la entidad de dominio).
 */
public interface ProximoEventoFinder {

    /**
     * @param participanteId cuenta suspendida o inexistente: mismo comportamiento que
     *                        {@code AccesoEventoService.requireProgreso} (propaga
     *                        {@code NotAuthorizedException}/{@code NoSuchElementException} —
     *                        el caller decide el HTTP, este puerto no lo sabe)
     * @return el evento futuro más cercano (primera ocurrencia con inicio &gt;= ahora dentro
     *         de la ventana de visor de `calendar`), o {@code Optional.empty()} si no hay
     *         ninguno visible para ese rol
     */
    Optional<ProximoEvento> proximoEventoDe(UserId participanteId);

    record ProximoEvento(UUID eventoId, String titulo, Instant iniciaEn) {
    }
}

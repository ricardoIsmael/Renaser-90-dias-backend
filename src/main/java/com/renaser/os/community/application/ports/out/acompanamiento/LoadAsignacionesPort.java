package com.renaser.os.community.application.ports.out.acompanamiento;

import com.renaser.os.community.domain.model.acompanamiento.AsignacionCelula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Optional;

public interface LoadAsignacionesPort {

    /** Historial completo de una persona: vigentes y cerradas. La evaluación necesita las dos. */
    List<AsignacionCelula> porUsuario(UserId usuarioId);

    /** Todas las asignaciones de un grupo, para leer su composición en cualquier instante. */
    List<AsignacionCelula> porCelula(CelulaId celulaId);

    /**
     * Las asignaciones de VARIOS grupos en UNA consulta (auditoria de consultas del 2026-11-02, H-04c): {@code gruposOperativos} hacia una consulta por grupo
     * (1 + C, con C de 30 a 100). Un grupo sin asignaciones aparece con la lista vacia. El cuerpo por defecto pregunta de a uno (para los dobles de prueba que
     * no necesitan el lote); el adaptador real lo sobreescribe.
     */
    default java.util.Map<CelulaId, List<AsignacionCelula>> porCelulas(java.util.Collection<CelulaId> celulaIds) {
        java.util.Map<CelulaId, List<AsignacionCelula>> resultado = new java.util.LinkedHashMap<>();
        for (CelulaId celulaId : celulaIds) {
            resultado.put(celulaId, porCelula(celulaId));
        }
        return resultado;
    }

    /** Idempotencia: repetir un comando encuentra lo que ya hizo en vez de volver a hacerlo. */
    Optional<AsignacionCelula> porClaveOperacion(String claveOperacion);
}

package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.renaser.os.community.application.ports.in.celula.ConsultarCandidatosCelulaUseCase.MentorCandidato;
import com.renaser.os.community.domain.model.celula.CelulaId;

import java.util.List;

/**
 * {@code cellId} null si el mentor todavia no lidera ninguna celula (candidato para
 * "mentores-disponibles"; en "mentores" viaja para marcar a quien ya esta ocupado).
 *
 * <p>{@code cellIds} trae TODOS los grupos que lidera, y {@code cellId} el primero de esa lista.
 * Los dos porque desde D-141 un mentor puede liderar varios: {@code cellId} se conserva para los
 * clientes que ya lo leen, pero preguntarle "¿lidera este grupo?" miente en cuanto el grupo
 * buscado no es el primero. Un cliente que necesite decidir eso usa {@code cellIds}.
 *
 * <p>{@code specialty} entra con el SDD 003 y es NEGOCIO, MENTE, RELACIONES o {@code null}. Null
 * significa que el mentor no la declaro —asi quedaron los perfiles anteriores a V48— y la pantalla
 * lo muestra "Sin especialidad definida". No se rellena con ninguna de las tres: el administrador
 * elige el mentor del grupo justamente por esto, y adivinarla seria decidir por el.
 */
public record MentorCandidatoResponse(String userId, String fullName, String avatarUrl, String cellId,
                                       List<String> cellIds, String specialty) {

    public static MentorCandidatoResponse from(MentorCandidato candidato) {
        return new MentorCandidatoResponse(candidato.userId().toString(), candidato.nombreCompleto(),
                candidato.avatarUrl(), candidato.celulaActual() != null ? candidato.celulaActual().toString() : null,
                candidato.celulasActuales().stream().map(CelulaId::toString).toList(),
                candidato.especialidad() != null ? candidato.especialidad().name() : null);
    }
}

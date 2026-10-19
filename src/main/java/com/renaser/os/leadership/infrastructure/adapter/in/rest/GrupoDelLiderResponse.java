package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.ports.in.ConsultarGrupoDelLiderUseCase.GrupoDelLider;
import com.renaser.os.leadership.domain.model.cumplimiento.ConteoDeLaLista;
import com.renaser.os.leadership.domain.model.cumplimiento.PersonaDeLaLista;
import com.renaser.os.points.api.DiaDelSemaforo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * La lista de un grupo para el líder, en el JSON de {@code specs/005-lider-cumplimiento-por-persona/contracts}: la
 * misma forma que la tabla del semáforo del grupo (docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md §4.3), con el orden del
 * líder, el rol de cada cuenta y quien lleva su propio programa con {@code color: NO_SE_MIDE}.
 *
 * <p>Mapeo a mano y no automático (CLAUDE.MD §5.4.5): un campo nuevo del dominio no puede aparecer solo en la respuesta.
 */
public record GrupoDelLiderResponse(UUID grupoId, String grupoNombre, LocalDate desde, LocalDate hasta,
                                    boolean cerrada, ConteoResponse resumen, List<PersonaResponse> aprendices) {

    public static GrupoDelLiderResponse from(GrupoDelLider grupo) {
        return new GrupoDelLiderResponse(grupo.grupoId(), grupo.grupoNombre(), grupo.desde(), grupo.hasta(),
                grupo.cerrada(), ConteoResponse.from(grupo.resumen()),
                grupo.personas().stream().map(PersonaResponse::from).toList());
    }

    public record ConteoResponse(int verde, int amarillo, int rojo, int sinDatos, int total) {

        static ConteoResponse from(ConteoDeLaLista conteo) {
            return new ConteoResponse(conteo.verde(), conteo.amarillo(), conteo.rojo(), conteo.sinDatos(),
                    conteo.total());
        }
    }

    /**
     * @param color  ROJO, AMARILLO, SIN_DATOS, VERDE o NO_SE_MIDE; nunca viaja un color solo (RL-30): va con su palabra
     * @param motivo por qué está «Sin datos»; null si tiene color
     */
    public record PersonaResponse(UUID aprendizId, String nombre, String avatarUrl, String rol, BigDecimal porcentaje,
                                  String color, String etiqueta, int diasConDatos, List<DiaResponse> dias,
                                  String motivo) {

        static PersonaResponse from(PersonaDeLaLista persona) {
            return new PersonaResponse(persona.personaId(), persona.nombre(), persona.avatarUrl(),
                    persona.rol() == null ? null : persona.rol().name(), persona.porcentaje(), persona.color().name(),
                    persona.color().etiqueta(), persona.diasConDatos(),
                    persona.dias().stream().map(DiaResponse::from).toList(), persona.motivo());
        }
    }

    /** El día resumido de la lista, igual que el de la tabla del semáforo: la palabra acompaña al color. */
    public record DiaResponse(LocalDate fecha, String estado, Integer porcentaje, String color, String etiqueta) {

        static DiaResponse from(DiaDelSemaforo dia) {
            return new DiaResponse(dia.fecha(), dia.estado().name(), dia.porcentaje(), dia.color().name(),
                    dia.color().etiqueta());
        }
    }
}

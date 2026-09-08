package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.mapa;

import com.renaser.os.onboarding.domain.model.mapa.AccionMapa;
import com.renaser.os.onboarding.domain.model.mapa.AreaMapa;
import com.renaser.os.onboarding.domain.model.mapa.EvidenciaAccion;
import com.renaser.os.onboarding.domain.model.mapa.MomentoAccion;
import com.renaser.os.onboarding.domain.model.mapa.ProtocoloReemplazoMapa;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mapper a mano (D-28): hay traduccion real en los dos sentidos — enums de dominio contra las
 * claves de texto del esquema, y {@code DayOfWeek} contra el {@code smallint} 1..7 de
 * `dias_accion_mapa`. MapStruct solo se usa para mapeo plano campo a campo.
 */
@Component
class MapaPersistenceMapper {

    AccionMapa toDomain(AccionMapaJpaEntity entidad) {
        return AccionMapa.rehydrate(entidad.getId(), UserId.of(entidad.getUsuarioId()), entidad.getAccionId(),
                AreaMapa.desdeClave(entidad.getArea()), entidad.getTexto(), entidad.getFrecuenciaSemanal(),
                aDias(entidad.getDias()), MomentoAccion.desdeClave(entidad.getMomento()),
                EvidenciaAccion.desdeClave(entidad.getEvidencia()), entidad.getHabitoId(), entidad.getCreadoEn(),
                entidad.getActualizadoEn());
    }

    AccionMapaJpaEntity toEntity(AccionMapa accion) {
        return new AccionMapaJpaEntity(accion.id(), accion.usuarioId().value(), accion.accionId(),
                accion.area().clave(), accion.texto(), (short) accion.frecuenciaSemanal(),
                accion.momento() == null ? null : accion.momento().clave(),
                accion.evidencia() == null ? null : accion.evidencia().clave(), accion.habitoId(),
                accion.creadoEn(), accion.actualizadoEn(), aSmallints(accion.dias()));
    }

    ProtocoloReemplazoMapa toDomain(ProtocoloReemplazoMapaJpaEntity entidad) {
        return ProtocoloReemplazoMapa.rehydrate(entidad.getId(), UserId.of(entidad.getUsuarioId()),
                entidad.getProtocoloId(), entidad.getPatron(), entidad.getDisparador(),
                entidad.getConductaActual(), entidad.getRespuestaAlternativa(), entidad.getCreadoEn(),
                entidad.getActualizadoEn());
    }

    ProtocoloReemplazoMapaJpaEntity toEntity(ProtocoloReemplazoMapa protocolo) {
        return new ProtocoloReemplazoMapaJpaEntity(protocolo.id(), protocolo.usuarioId().value(),
                protocolo.protocoloId(), protocolo.patron(), protocolo.disparador(), protocolo.conductaActual(),
                protocolo.respuestaAlternativa(), protocolo.creadoEn(), protocolo.actualizadoEn());
    }

    private static Set<DayOfWeek> aDias(Set<Short> crudos) {
        return crudos == null ? Set.of()
                : crudos.stream().map(d -> DayOfWeek.of(d.intValue()))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<Short> aSmallints(Set<DayOfWeek> dias) {
        return dias.stream().map(d -> (short) d.getValue())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}

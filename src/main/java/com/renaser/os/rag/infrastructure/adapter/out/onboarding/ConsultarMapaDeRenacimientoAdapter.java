package com.renaser.os.rag.infrastructure.adapter.out.onboarding;

import com.renaser.os.onboarding.api.MapaDeRenacimientoFinder;
import com.renaser.os.onboarding.api.MapaDeRenacimientoFinder.MapaDeRenacimiento;
import com.renaser.os.rag.application.ports.out.mapa.ConsultarMapaDeRenacimientoPort;
import com.renaser.os.rag.domain.model.mapa.MapaDeLaPersona;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

/** Traduce {@code onboarding.api.MapaDeRenacimientoFinder} al tipo propio de {@code rag} (D-41, D-233). */
@Component
class ConsultarMapaDeRenacimientoAdapter implements ConsultarMapaDeRenacimientoPort {

    private final MapaDeRenacimientoFinder finder;

    ConsultarMapaDeRenacimientoAdapter(MapaDeRenacimientoFinder finder) {
        this.finder = finder;
    }

    @Override
    public MapaDeLaPersona de(UserId participanteId) {
        MapaDeRenacimiento mapa = finder.delParticipante(participanteId);
        if (!mapa.recorrido()) {
            return MapaDeLaPersona.sinMapa();
        }
        return new MapaDeLaPersona(true, mapa.completado(), mapa.prioridad(),
                mapa.objetivos().stream().map(o -> new MapaDeLaPersona.Objetivo(o.area(), o.tipo(), o.lineaBase(),
                        o.metaDia90(), o.unidad(), o.periodo(), o.conductaNueva(), o.evidencia(), o.porque(),
                        o.metaRedactada())).toList(),
                mapa.hitos().stream().map(h -> new MapaDeLaPersona.Hito(h.area(), h.dia(), h.texto())).toList(),
                mapa.protocoloDeRetorno(),
                mapa.acciones().stream()
                        .map(a -> new MapaDeLaPersona.Accion(a.area(), a.texto(), a.vecesPorSemana())).toList(),
                mapa.reemplazos());
    }
}

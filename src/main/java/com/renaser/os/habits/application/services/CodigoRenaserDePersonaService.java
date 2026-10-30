package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.CodigoRenaserDePersonaFinder;
import com.renaser.os.habits.application.ports.in.radar.ConsultarHistorialRadarUseCase.HistorialRadarPage;
import com.renaser.os.habits.domain.model.radar.MiRadar;
import com.renaser.os.habits.domain.model.radar.RegistroRadar;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Implementa {@link CodigoRenaserDePersonaFinder} (spec 012): la pagina del historial que ya arma
 * {@link LecturaDelRadar}, traducida al contrato publico. No autoriza: lo hace quien llama (ver el contrato). Sin
 * {@code @Transactional}: es una lectura.
 */
@Service
class CodigoRenaserDePersonaService implements CodigoRenaserDePersonaFinder {

    private final LecturaDelRadar lectura;

    CodigoRenaserDePersonaService(LecturaDelRadar lectura) {
        this.lectura = lectura;
    }

    @Override
    public CodigoRenaserDePersona de(UserId personaId, Instant cursor, int tamanoPagina) {
        HistorialRadarPage pagina = lectura.pagina(personaId, cursor, tamanoPagina);
        return new CodigoRenaserDePersona(miRadarDe(pagina.miRadar()),
                pagina.entradas().stream().map(CodigoRenaserDePersonaService::registroDe).toList(),
                pagina.siguienteCursor());
    }

    private static MiRadarDeLaPersona miRadarDe(MiRadar miRadar) {
        return miRadar == null ? null : new MiRadarDeLaPersona(miRadar.estaEncendido(), miRadar.horas(),
                miRadar.dias().stream().map(dia -> new DiaDeLaPersona(dia.dia(), dia.horas())).toList());
    }

    private static RegistroDelRadar registroDe(RegistroRadar r) {
        return new RegistroDelRadar(r.id().value(), r.queHago(), r.quePienso(), r.queSiento(), r.nivelEnergia(),
                r.queEvito(), r.creadoEn(), r.horaPrevista());
    }
}

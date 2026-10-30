package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.CodigoRenaserDePersonaFinder;
import com.renaser.os.habits.application.ports.in.radar.ConsultarHistorialRadarUseCase.HistorialRadarPage;
import com.renaser.os.habits.domain.model.radar.DiaDelRadar;
import com.renaser.os.habits.domain.model.radar.PlanDeMiRadar;
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

    private static MiRadarDeLaPersona miRadarDe(PlanDeMiRadar plan) {
        return plan == null ? null : new MiRadarDeLaPersona(plan.encendido(), plan.hoy(),
                plan.dias().stream().map(CodigoRenaserDePersonaService::diaDe).toList());
    }

    private static DiaDeLaPersona diaDe(DiaDelRadar dia) {
        return new DiaDeLaPersona(dia.dia(), dia.fecha(), dia.modo().name(), dia.maximo(), dia.veces(),
                dia.sinRadar(), dia.editable(), dia.despertar(), dia.dormir(), dia.horas(),
                dia.motivo() == null ? null : dia.motivo().name());
    }

    private static RegistroDelRadar registroDe(RegistroRadar r) {
        return new RegistroDelRadar(r.id().value(), r.queHago(), r.quePienso(), r.queSiento(), r.nivelEnergia(),
                r.queEvito(), r.creadoEn(), r.horaPrevista());
    }
}

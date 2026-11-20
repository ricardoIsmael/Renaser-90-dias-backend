package com.renaser.os.community.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.api.AcompanamientoFinder.GrupoBasico;
import com.renaser.os.community.api.ZonaDelAcompananteFinder;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

/** Implementa {@link ZonaDelAcompananteFinder}: programa si participa, cohorte de su primer grupo vigente, Lima. */
@Service
public class ZonaDelAcompananteService implements ZonaDelAcompananteFinder {

    private static final ZoneId ZONA_POR_DEFECTO = ZoneId.of("America/Lima");

    private final AcompanamientoFinder acompanamiento;
    private final ParticipacionProgramaFinder participaciones;

    ZonaDelAcompananteService(AcompanamientoFinder acompanamiento, ParticipacionProgramaFinder participaciones) {
        this.acompanamiento = acompanamiento;
        this.participaciones = participaciones;
    }

    @Override
    @Transactional(readOnly = true)
    public ZoneId zonaDe(UserId acompanante, Instant ahora) {
        return participaciones.deParticipante(acompanante).filter(ParticipacionPrograma::inscrito).map(ParticipacionPrograma::zona)
                .orElseGet(() -> zonaDelPrimerGrupo(acompanante, ahora));
    }

    private ZoneId zonaDelPrimerGrupo(UserId acompanante, Instant ahora) {
        return acompanamiento.tramosDeAcompanante(acompanante, ahora, null).stream().findFirst()
                .flatMap(tramo -> acompanamiento.grupo(tramo.grupoId())).map(GrupoBasico::zonaHoraria)
                .flatMap(ZonaDelAcompananteService::comoZona).orElse(ZONA_POR_DEFECTO);
    }

    private static Optional<ZoneId> comoZona(String zona) {
        try {
            return Optional.of(ZoneId.of(zona));
        } catch (DateTimeException | NullPointerException e) {
            return Optional.empty();
        }
    }
}

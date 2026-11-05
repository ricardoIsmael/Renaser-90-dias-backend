package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.RutaDeCarreraConsultadaEvent;
import com.renaser.os.habits.api.RutaDeCarreraFinder;
import com.renaser.os.habits.application.ports.in.carrera.ConsultarRutaPropiaUseCase;
import com.renaser.os.habits.application.ports.out.carrera.CarrerasPort;
import com.renaser.os.habits.domain.model.carrera.RutaDeCarrera;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * La ruta de una carrera (spec 042, FR-041): la propia ({@link ConsultarRutaPropiaUseCase}) y la de otra persona para su
 * acompañamiento ({@link RutaDeCarreraFinder}, que ya viene autorizado y deja constancia). Una sola lectura por id compuesto
 * {@code (persona, carreraId)}: la clave del teléfono es única por persona, no en toda la base.
 */
@Service
class RutaDeCarreraService implements ConsultarRutaPropiaUseCase, RutaDeCarreraFinder {

    private final CarrerasPort carreras;
    private final ApplicationEventPublisher eventos;
    private final IdGenerator ids;
    private final Clock clock;

    RutaDeCarreraService(CarrerasPort carreras, ApplicationEventPublisher eventos, IdGenerator ids, Clock clock) {
        this.carreras = carreras;
        this.eventos = eventos;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RutaDeCarrera> rutaPropia(UserId actorId, UUID carreraId) {
        return carreras.rutaDe(actorId, carreraId);
    }

    /** Lee y, si había ruta y quien mira no es la persona, publica la constancia en esta misma transacción. */
    @Override
    @Transactional
    public Optional<RutaLeida> rutaDe(UserId persona, UUID carreraId, LectorDeLaRuta lector) {
        Optional<RutaDeCarrera> ruta = carreras.rutaDe(persona, carreraId);
        if (ruta.isPresent() && !lector.consultorId().equals(persona)) {
            eventos.publishEvent(new RutaDeCarreraConsultadaEvent(ids.newId(), lector.consultorId(), persona, lector.grupoId(),
                    lector.mentorId(), clock.now()));
        }
        return ruta.map(r -> new RutaLeida(r.polyline(), r.puntos()));
    }
}

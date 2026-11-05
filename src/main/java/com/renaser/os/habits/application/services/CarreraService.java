package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.politica.PoliticaKilometros;
import com.renaser.os.habits.application.ports.in.carrera.RegistrarCarreraUseCase;
import com.renaser.os.habits.application.ports.out.carrera.CarrerasPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.domain.model.carrera.Carrera;
import com.renaser.os.habits.domain.model.carrera.DatosDeCarrera;
import com.renaser.os.habits.domain.model.carrera.EfectoEnElHabito;
import com.renaser.os.habits.domain.model.carrera.EvaluacionDeCarrera;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Spec 042 (D-323) — {@link RegistrarCarreraUseCase}: la única escritura de una carrera con GPS.
 *
 * <p><b>Idempotencia (FR-019).</b> La clave es {@code (persona, carreraId)} y la resuelve la base con un índice único: primero se
 * mira si ya está; si no, se intenta guardar con {@code ON CONFLICT DO NOTHING}. Si otro pedido igual está en vuelo, ese
 * {@code INSERT} espera a que termine y no inserta: entonces se lee lo guardado. Con la misma huella se responde lo guardado
 * ({@code repetida}), sin tocar el hábito; con otra, 409.
 *
 * <p><b>Una sola transacción (FR-020).</b> Guardar la carrera, aplicarla al hábito ({@link CarreraEnElHabito}, que cierra por
 * {@code RegistroService.completar}) y anotar el efecto van juntos: si algo inesperado falla, no queda nada a medias.
 *
 * <p>El día de la carrera es el de su inicio en la zona de la persona (FR-021). Quien no tiene programa (personal sin Día 1) no
 * tiene zona propia: vale la del padrón por defecto, y su carrera se guarda sin hábito.
 */
@Service
public class CarreraService implements RegistrarCarreraUseCase {

    /** La zona por defecto del padrón ({@code participantes_programa.timezone}). */
    static final ZoneId ZONA_POR_DEFECTO = ZoneId.of("America/Lima");

    private final CarrerasPort carreras;
    private final CarreraEnElHabito enElHabito;
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final PoliticaKilometros politica;
    private final IdGenerator ids;
    private final Clock clock;

    public CarreraService(CarrerasPort carreras, CarreraEnElHabito enElHabito, ConsultarProgresoParticipanteHabitsPort progresoPort,
                          PoliticaKilometros politica, IdGenerator ids, Clock clock) {
        this.carreras = carreras;
        this.enElHabito = enElHabito;
        this.progresoPort = progresoPort;
        this.politica = politica;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CarreraRegistrada registrar(RegistrarCarreraCommand command) {
        Optional<ProgresoParticipanteHabits> progreso = progresoPort.deParticipante(command.actorId());
        if (progreso.map(ProgresoParticipanteHabits::suspendido).orElse(false)) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        String huella = command.huellaDelEnvio();
        Optional<Carrera> guardada = carreras.de(command.actorId(), command.datos().carreraId());
        if (guardada.isPresent()) {
            return repeticion(guardada.get(), huella);
        }
        // FR-043: la zona de privacidad la quita el servidor; lo que se evalúa y se guarda ya viene sin ella.
        DatosDeCarrera datos = command.datosAGuardar();
        Instant ahora = clock.now();
        EvaluacionDeCarrera evaluacion = politica.evaluarCarrera(datos, ahora);
        Carrera nueva = Carrera.nueva(ids.newId(), command.actorId(), datos, huella, zonaDe(progreso), evaluacion, ahora);
        if (!carreras.guardarSiEsNueva(nueva)) {
            return repeticion(carreras.de(command.actorId(), command.datos().carreraId()).orElseThrow(), huella);
        }
        EfectoEnElHabito efecto = enElHabito.aplicar(nueva, progreso);
        carreras.anotarEfecto(nueva.id(), efecto);
        return new CarreraRegistrada(nueva.conEfecto(efecto), false);
    }

    private static CarreraRegistrada repeticion(Carrera guardada, String huella) {
        if (!guardada.esElMismoEnvio(huella)) {
            throw new CarreraYaRegistradaException();
        }
        return new CarreraRegistrada(guardada, true);
    }

    private static ZoneId zonaDe(Optional<ProgresoParticipanteHabits> progreso) {
        return progreso.map(p -> ZoneId.of(p.timezone())).orElse(ZONA_POR_DEFECTO);
    }
}

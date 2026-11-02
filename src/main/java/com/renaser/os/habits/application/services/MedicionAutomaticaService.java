package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.politica.PoliticaKilometros;
import com.renaser.os.habits.application.ports.in.medicion.RegistrarMedicionAutomaticaUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.medicion.MetaDeMedicionPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.medicion.LecturaAutomatica;
import com.renaser.os.habits.domain.model.medicion.MedicionDiaria;
import com.renaser.os.habits.domain.model.politica.DecisionPolitica;
import com.renaser.os.habits.domain.model.politica.GestoCompletar;
import com.renaser.os.habits.domain.model.registro.EstadoRegistro;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.NoSuchElementException;

/**
 * Spec 037 (D-310) — {@link RegistrarMedicionAutomaticaUseCase}: valida la lectura de Health Connect y cierra el hábito de
 * kilómetros por {@link CompletarRegistroUseCase}. No reimplementa nada de lo compartido: los puntos, la ventana, el evento y
 * la regla del día cerrado (D-259) siguen siendo de {@code RegistroService}; la meta, los topes y lo «por revisar», de
 * {@link PoliticaKilometros}.
 *
 * <p>Llega con el gesto {@link GestoCompletar#PROPIO_DEL_HABITO}: el genérico exige la captura y el número escrito, que es
 * justo lo que este camino reemplaza, y trae su propia validación acá. El registro se lee CON cerrojo y en la misma
 * transacción que lo completa: dos envíos juntos se serializan y el segundo encuentra el hábito cumplido (idempotencia).
 *
 * <p>La pertenencia se mira antes de leer nada más: si no, un actor ajeno sabría por el error si el registro existe.
 */
@Service
public class MedicionAutomaticaService implements RegistrarMedicionAutomaticaUseCase {

    /** Lo que se tolera de reloj mal puesto en el teléfono: una lectura «del futuro» más allá de esto es inválida. */
    static final Duration TOLERANCIA_DEL_RELOJ = Duration.ofMinutes(5);

    private final LoadRegistroHabitoPort loadRegistroPort;
    private final LoadHabitoPort loadHabitoPort;
    private final MetaDeMedicionPort metaPort;
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final CompletarRegistroUseCase completarRegistroUseCase;
    private final PoliticaKilometros politica;
    private final Clock clock;

    public MedicionAutomaticaService(LoadRegistroHabitoPort loadRegistroPort, LoadHabitoPort loadHabitoPort,
                                     MetaDeMedicionPort metaPort, ConsultarProgresoParticipanteHabitsPort progresoPort,
                                     CompletarRegistroUseCase completarRegistroUseCase, PoliticaKilometros politica,
                                     Clock clock) {
        this.loadRegistroPort = loadRegistroPort;
        this.loadHabitoPort = loadHabitoPort;
        this.metaPort = metaPort;
        this.progresoPort = progresoPort;
        this.completarRegistroUseCase = completarRegistroUseCase;
        this.politica = politica;
        this.clock = clock;
    }

    @Override
    @Transactional
    public RegistroHabito registrar(RegistrarMedicionAutomaticaCommand command) {
        RegistroHabito registro = loadRegistroPort.byIdParaEscritura(command.registroId())
                .orElseThrow(() -> new NoSuchElementException("Registro no encontrado: " + command.registroId()));
        if (!command.actorId().equals(registro.participanteId())) {
            throw new NotAuthorizedException("Solo el propio participante registra su medición");
        }
        Habito habito = loadHabitoPort.byId(registro.habitoId())
                .orElseThrow(() -> new NoSuchElementException("Hábito no encontrado: " + registro.habitoId()));
        requireHabitoDeKilometros(habito);
        if (registro.estado() == EstadoRegistro.COMPLETADO) {
            return yaCumplido(registro);
        }
        requireLecturaDeEseDia(command.leidoEn(), registro);
        MedicionDiaria medicion = medicionDe(command);
        requireQueProcede(medicion, habito);
        return completarRegistroUseCase.completar(new CompletarRegistroCommand(command.actorId(), registro.id(), null,
                null, GestoCompletar.PROPIO_DEL_HABITO, politica.conRevision(medicion)));
    }

    private static void requireHabitoDeKilometros(Habito habito) {
        if (!PoliticaKilometros.CLAVE_SISTEMA.equals(habito.claveSistema())) {
            throw new IllegalArgumentException("Este hábito no se registra con tus pasos.");
        }
    }

    /** Idempotencia: lo que ya cerró Health Connect se devuelve igual; lo que cerró la captura no se pisa. */
    private static RegistroHabito yaCumplido(RegistroHabito registro) {
        MedicionDiaria medicion = registro.medicion();
        if (medicion != null && medicion.esAutomatica()) {
            return registro;
        }
        throw new IllegalStateException(medicion == null ? "Este hábito ya está registrado."
                : "Ya registraste este hábito con tu captura.");
    }

    /** Ni del futuro (más allá del reloj mal puesto) ni de antes de que empezara el día del registro, en la zona de la persona. */
    private void requireLecturaDeEseDia(Instant leidoEn, RegistroHabito registro) {
        Instant ahora = clock.now();
        if (leidoEn.isAfter(ahora.plus(TOLERANCIA_DEL_RELOJ))) {
            throw new IllegalArgumentException("La hora de la lectura no es válida. Revisa la hora de tu teléfono.");
        }
        ZoneId zona = ZoneId.of(progresoPort.deParticipante(registro.participanteId())
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + registro.participanteId()))
                .timezone());
        if (leidoEn.isBefore(registro.fechaEjecucion().atStartOfDay(zona).toInstant())) {
            throw new IllegalArgumentException("Esa lectura es de otro día. Actualiza y vuelve a intentarlo.");
        }
    }

    private static MedicionDiaria medicionDe(RegistrarMedicionAutomaticaCommand c) {
        return MedicionDiaria.automatica(c.kilometros(),
                new LecturaAutomatica(c.pasos(), c.fuente(), c.dispositivo(), c.leidoEn(), false));
    }

    private void requireQueProcede(MedicionDiaria medicion, Habito habito) {
        BigDecimal meta = PoliticaKilometros.metaEfectiva(metaPort.metaDe(habito.id()).orElse(null));
        switch (politica.validarMedicionAutomatica(medicion, meta)) {
            case DecisionPolitica.Procede ignorada -> {
                // sigue el camino compartido
            }
            case DecisionPolitica.NoProcede(String motivo) -> throw new IllegalArgumentException(motivo);
        }
    }
}

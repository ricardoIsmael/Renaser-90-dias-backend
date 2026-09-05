package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.espiritu.CompletarPastillaRenacerUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.registro.EstadoRegistro;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Implementa {@link CompletarPastillaRenacerUseCase} delegando en
 * {@link CompletarRegistroUseCase} el calculo de puntos, la ventana de entrega y el evento de
 * dominio — ese calculo vive en un solo lugar ({@code RegistroService}, ver el javadoc de
 * {@code PoliticaHabito}: "una politica decide SI una accion procede y por que; nunca
 * reimplementa lo compartido"). Esta clase solo localiza el track de HOY del habito
 * {@code PASTILLA_RENACER}.
 *
 * <p>Copia deliberada de la forma de {@code ClaseDiariaHabitoService}: son el mismo problema
 * (un modulo que sabe que ocurrio un hecho y necesita cerrar el habito que le corresponde, sin
 * manejar {@code RegistroHabitoId} ajenos). Se prefirio repetir esa forma antes que
 * generalizarla en un "completar por clave", por el motivo documentado en el javadoc del
 * puerto.
 *
 * <p><b>Diferencia con Clase Diaria:</b> ahi la ausencia del track de hoy es un error del
 * llamador (400/404); aca es un caso normal — el aprendiz puede tener "Pastilla Renacer"
 * pausado (D-87) o el habito puede no haberse generado hoy, y su entrega del resumen de
 * Espiritu sigue siendo valida igual. Por eso devuelve {@link Optional#empty()} en vez de
 * lanzar.
 */
@Service
public class PastillaRenacerHabitoService implements CompletarPastillaRenacerUseCase {

    private final LoadHabitoPort loadHabitoPort;
    private final LoadRegistroHabitoPort loadRegistroPort;
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final CompletarRegistroUseCase completarRegistroUseCase;
    private final Clock clock;

    public PastillaRenacerHabitoService(LoadHabitoPort loadHabitoPort, LoadRegistroHabitoPort loadRegistroPort,
                                         ConsultarProgresoParticipanteHabitsPort progresoPort,
                                         CompletarRegistroUseCase completarRegistroUseCase, Clock clock) {
        this.loadHabitoPort = loadHabitoPort;
        this.loadRegistroPort = loadRegistroPort;
        this.progresoPort = progresoPort;
        this.completarRegistroUseCase = completarRegistroUseCase;
        this.clock = clock;
    }

    @Override
    public Optional<RegistroCompletado> completarDeHoy(UserId participanteId, String resumen) {
        Optional<Habito> habito = loadHabitoPort.porClaveSistema(CLAVE_SISTEMA_PASTILLA_RENACER);
        if (habito.isEmpty()) {
            // El habito puede estar fuera del catalogo de este entorno (fixtures, QA): no es
            // motivo para tumbar la entrega del resumen, que es lo que el aprendiz vino a hacer.
            return Optional.empty();
        }

        LocalDate hoy = fechaHoyEnZonaDe(requireProgresoNoSuspendido(participanteId).timezone());
        Optional<RegistroHabito> registro = loadRegistroPort
                .porParticipanteHabitoYFecha(participanteId, habito.get().id(), hoy);
        if (registro.isEmpty()) {
            return Optional.empty();
        }

        RegistroHabito track = registro.get();
        if (track.estado() == EstadoRegistro.COMPLETADO) {
            return Optional.of(new RegistroCompletado(track.id().value(), track.puntosOtorgados()));
        }

        RegistroHabito completado = completarRegistroUseCase.completar(
                new CompletarRegistroCommand(participanteId, track.id(), resumen, null));
        return Optional.of(new RegistroCompletado(completado.id().value(), completado.puntosOtorgados()));
    }

    /**
     * Defensa propia, mismo criterio que {@code ClaseDiariaHabitoService}: no se confia en que
     * todo llamador futuro haya validado la suspension antes de llegar aca.
     */
    private ProgresoParticipanteHabits requireProgresoNoSuspendido(UserId participanteId) {
        ProgresoParticipanteHabits progreso = progresoPort.deParticipante(participanteId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + participanteId));
        if (progreso.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        return progreso;
    }

    /** Mismo criterio que {@code RachaService.fechaHoyDe}: reloj inyectado, nunca el del sistema. */
    private LocalDate fechaHoyEnZonaDe(String timezone) {
        return clock.now().atZone(ZoneId.of(timezone)).toLocalDate();
    }
}

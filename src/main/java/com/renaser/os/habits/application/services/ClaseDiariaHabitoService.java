package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.CompletarClaseDiariaHabitoUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.politica.GestoCompletar;
import com.renaser.os.habits.domain.model.registro.EstadoRegistro;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.NoSuchElementException;

/**
 * Implementa {@link CompletarClaseDiariaHabitoUseCase} delegando en {@link CompletarRegistroUseCase}
 * para el cálculo de puntos, ventana de entrega y evento de dominio — ese cálculo vive en un
 * solo lugar ({@code RegistroService}, ver javadoc de {@code PoliticaHabito}), esta clase solo
 * localiza el track de HOY del hábito {@code DAILY_CLASS} sin exponer su identidad al llamador.
 */
@Service
public class ClaseDiariaHabitoService implements CompletarClaseDiariaHabitoUseCase {

    private final LoadHabitoPort loadHabitoPort;
    private final LoadRegistroHabitoPort loadRegistroPort;
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final CompletarRegistroUseCase completarRegistroUseCase;
    private final Clock clock;

    public ClaseDiariaHabitoService(LoadHabitoPort loadHabitoPort, LoadRegistroHabitoPort loadRegistroPort,
                                     ConsultarProgresoParticipanteHabitsPort progresoPort,
                                     CompletarRegistroUseCase completarRegistroUseCase, Clock clock) {
        this.loadHabitoPort = loadHabitoPort;
        this.loadRegistroPort = loadRegistroPort;
        this.progresoPort = progresoPort;
        this.completarRegistroUseCase = completarRegistroUseCase;
        this.clock = clock;
    }

    @Override
    public RegistroCompletado completarDeHoy(CompletarClaseDiariaHabitoCommand command) {
        UserId participanteId = command.participanteId();
        Habito habitoDailyClass = loadHabitoPort.porClaveSistema(CLAVE_SISTEMA_DAILY_CLASS)
                .orElseThrow(() -> new NoSuchElementException(
                        "No existe en el catalogo un habito con claveSistema=" + CLAVE_SISTEMA_DAILY_CLASS));

        LocalDate hoy = fechaHoyEnZonaDe(requireProgresoNoSuspendido(participanteId).timezone());
        RegistroHabito registro = loadRegistroPort
                .porParticipanteHabitoYFecha(participanteId, habitoDailyClass.id(), hoy)
                .orElseThrow(() -> new NoSuchElementException("No hay Clase Diaria generada para hoy"));

        if (registro.estado() == EstadoRegistro.COMPLETADO) {
            return yaCompletadaHoy(registro);
        }

        // GESTO PROPIO: este ES el unico camino valido para cerrar la Clase Diaria, asi que la
        // politica que cierra el generico (PoliticaClaseDiaria) no gobierna esta invocacion. Ver
        // GestoCompletar para por que el dato viaja en el comando y no en el ContextoCompletar.
        RegistroHabito completado = completarRegistroUseCase.completar(new CompletarRegistroCommand(
                participanteId, registro.id(), command.resumen(), null, GestoCompletar.PROPIO_DEL_HABITO));
        return new RegistroCompletado(completado.id().value(), completado.puntosOtorgados());
    }

    /**
     * El registro de hoy ya esta COMPLETADO. Hay dos situaciones distintas detras de ese mismo
     * estado, y antes las dos devolvian 200 descartando {@code command.resumen()} en silencio
     * (E-120): la persona escribia su resumen, el servidor respondia OK y el texto no se guardaba
     * en ningun lado.
     *
     * <ul>
     *   <li><b>Ya hay resumen guardado</b> — es un reintento genuino (doble toque, reenvio tras
     *       un corte de red, el POST repetido que el contrato documenta como idempotente). No se
     *       pierde nada: el texto de la persona esta en la fila. Se devuelve el resultado ya
     *       otorgado, sin volver a pagar. <b>Esto es lo que el contrato promete y no cambia.</b></li>
     *   <li><b>No hay resumen guardado</b> — el registro se cerro por un camino que ahora
     *       {@link com.renaser.os.habits.application.politica.PoliticaClaseDiaria} tiene prohibido.
     *       Es un estado que ya no se puede producir; si aparece, es una fila vieja anterior al
     *       arreglo. Se rechaza con 409 y un mensaje que la persona entiende, en vez de contestar
     *       200 sobre un texto que se tiro.</li>
     * </ul>
     *
     * <p><b>Por que rechazar y no guardar el resumen tardio.</b> Guardarlo obliga a escribir sobre
     * un agregado en estado terminal, y el dominio lo prohibe a proposito: {@code EstadoRegistro}
     * declara COMPLETADO/FALLIDO/EXPIRADO terminales y {@code RegistroHabito} lo hace cumplir con
     * un {@code requireNoTerminal()} en cada mutador. Abrir un mutador de excepcion —aunque solo
     * tocara {@code respuestaTexto} y no el estado ni los puntos— deja instalada en el dominio una
     * puerta de reparacion de datos que la proxima persona reusa para "un campo mas", y la
     * invariante se erosiona. El precio de no abrirla es acotado y conocido: la unica forma de
     * llegar a esta rama es una fila producida por el bug, y no hay entorno desplegado (regla 04)
     * — o sea, filas de desarrollo. Cambiar una invariante del dominio de forma permanente para
     * reparar datos que solo existen en una base local es un mal negocio. Si el dueno decide mas
     * adelante que el resumen tardio debe guardarse, es una regla de negocio nueva y se decide
     * como tal, no como efecto colateral de este arreglo.
     */
    private RegistroCompletado yaCompletadaHoy(RegistroHabito registro) {
        if (registro.respuestaTexto() == null || registro.respuestaTexto().isBlank()) {
            throw new IllegalStateException("Tu Clase Diaria de hoy quedo cerrada sin resumen, asi que este texto no "
                    + "se puede guardar. Copialo antes de salir de la pantalla.");
        }
        return new RegistroCompletado(registro.id().value(), registro.puntosOtorgados());
    }

    /**
     * Defensa propia: aunque hoy el unico llamador (`academy`) ya valida suspension antes de
     * llegar aca (resolviendo la Clase Diaria del dia), este puerto es publico
     * ({@code habits.api}) y no debe confiar en que todo futuro llamador repita ese chequeo —
     * mismo criterio que {@code RegistroService.requireProgreso}.
     */
    private ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits requireProgresoNoSuspendido(
            UserId participanteId) {
        var progreso = progresoPort.deParticipante(participanteId)
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

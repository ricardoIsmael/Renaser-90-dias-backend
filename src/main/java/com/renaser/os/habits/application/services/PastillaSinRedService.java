package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.espiritu.CompletarPastillaRenacerUseCase;
import com.renaser.os.habits.application.ports.in.espiritu.EntregarResumenEspirituSinRedUseCase;
import com.renaser.os.habits.application.ports.in.espiritu.EntregarResumenEspirituUseCase.EntregarResumenEspirituCommand;
import com.renaser.os.habits.application.ports.out.espiritu.LoadRegistroEspirituPort;
import com.renaser.os.habits.application.ports.out.espiritu.SaveRegistroEspirituPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.domain.model.espiritu.EstadoRegistroEspiritu;
import com.renaser.os.habits.domain.model.espiritu.RegistroEspiritu;
import com.renaser.os.shared.application.EntregaSinRed;
import com.renaser.os.shared.application.EntregaSinRed.Aplicado;
import com.renaser.os.shared.application.EntregaSinRed.Envio;
import com.renaser.os.shared.application.ports.out.EnviosSinRedPort;
import com.renaser.os.shared.application.ports.out.EnviosSinRedPort.EnvioRegistrado;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.CodigoNoAplicado;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.MotivoSinPuntos;
import com.renaser.os.shared.domain.NoAplicadoSinRedException;
import com.renaser.os.shared.domain.RevisionDeLaHora;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.NoSuchElementException;

/**
 * La Pastilla Renacer entregada sin red (spec 048, issue #186, FR-008, FR-010, FR-015). Antes {@code POST /spirit-audio/submit} solo
 * recibía {@code day} y {@code summaryText} y la registraba con la hora en que LLEGA: la de las 11:00 que llegaba a las 13:00 contaba
 * tardía, y la que llegaba otro día daba 409 «…: PERDIDO» aunque se hubiera entregado a tiempo. Ahora, con las tres cabeceras de la
 * fase 3, las reglas de {@link EspirituService#entregar} corren con la hora real ({@link RevisionDeLaHora#referencia()}):
 *
 * <ul>
 *   <li><b>El plazo</b> (mediodía del día del audio) se mide contra la hora real: a tiempo queda ENTREGADO con
 *       {@code entregado_en} = esa hora, aunque la lectura perezosa ya la hubiera pasado a PERDIDO porque la entrega todavía no
 *       había llegado ({@link RegistroEspiritu#entregarSinRed}); tardía, el texto queda guardado y el estado no cambia;</li>
 *   <li><b>El día</b>: si la hora real no cae en el día de ese audio, el día ya cerró y no se aplica ({@code FUERA_DE_SU_DIA}, D29): el
 *       audio siguiente es otro y nunca se le da el resumen de ayer. No hay «PERDIDO» que rebote: es el mismo aviso de las demás áreas;</li>
 *   <li><b>El choque</b> (D13): una entrega desde otro lado hecha después de lo que hiciste gana
 *       ({@code CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR}); si ese día ya estaba entregado antes, {@code YA_ESTABA_HECHO}. Sin registro de ese
 *       día, {@code RECURSO_YA_NO_EXISTE}: con cabeceras un 404 sería «reintentar» para siempre en la cola de la app;</li>
 *   <li><b>El hábito «Pastilla Renacer»</b> del día de la hora real se cierra con esa hora (el mismo cálculo de puntos de siempre).
 *       Hora futura o llegada pasadas 48 h lo dejan sin puntos, y la respuesta dice por qué (D14: nada rebota).</li>
 * </ul>
 *
 * <p>Con el mismo {@code idCliente} dos veces: una sola (candado + fila en {@code envios_sin_red}, {@link EntregaSinRed}). Lo que escribió
 * la persona no se loguea. El resumen nunca viaja a la bitácora: el {@code recurso} del envío es solo el día del audio.
 */
@Service
class PastillaSinRedService implements EntregarResumenEspirituSinRedUseCase {

    private static final Logger log = LoggerFactory.getLogger(PastillaSinRedService.class);

    static final String TIPO_DE_ENVIO = "ENTREGAR_PASTILLA";

    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final LoadRegistroEspirituPort loadPort;
    private final SaveRegistroEspirituPort savePort;
    private final CompletarPastillaRenacerUseCase espejo;
    private final EntregaSinRed entrega;
    private final Clock clock;
    /** Mismo motivo que en {@code EspirituService}: un fallo del hábito ajeno no puede deshacer la entrega del resumen. */
    private final TransactionTemplate transaccionPropia;

    PastillaSinRedService(ConsultarProgresoParticipanteHabitsPort progresoPort, LoadRegistroEspirituPort loadPort,
                          SaveRegistroEspirituPort savePort, CompletarPastillaRenacerUseCase espejo, EnviosSinRedPort envios,
                          Clock clock, PlatformTransactionManager transactionManager) {
        this.progresoPort = progresoPort;
        this.loadPort = loadPort;
        this.savePort = savePort;
        this.espejo = espejo;
        this.entrega = new EntregaSinRed(envios, clock);
        this.clock = clock;
        this.transaccionPropia = new TransactionTemplate(transactionManager);
        this.transaccionPropia.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
    }

    @Override
    @Transactional(noRollbackFor = NoAplicadoSinRedException.class)
    public ResultadoEntregaSinRed entregar(EntregarResumenEspirituCommand command, HechoSinRed sinRed) {
        ProgresoParticipanteHabits progreso = EspirituService.exigirHabilitado(progresoPort, command.actorId());
        RevisionDeLaHora hora = RevisionDeLaHora.de(sinRed, clock.now());
        Envio envio = new Envio(command.actorId(), sinRed, TIPO_DE_ENVIO, "pastilla:" + command.dia());
        return entrega.entregar(envio, () -> aplicar(command, ZoneId.of(progreso.timezone()), hora),
                previo -> releer(command, previo));
    }

    private Aplicado<ResultadoEntregaSinRed> aplicar(EntregarResumenEspirituCommand command, ZoneId zona, RevisionDeLaHora hora) {
        RegistroEspiritu registro = loadPort.porParticipanteYDia(command.actorId(), command.dia())
                .orElseThrow(() -> new NoAplicadoSinRedException(CodigoNoAplicado.RECURSO_YA_NO_EXISTE));
        LocalDate diaDelAudio = registro.fechaLimite().atZone(zona).toLocalDate();
        exigirQueAunSeaSuDia(diaDelAudio, hora, zona);
        exigirQueNadaMasNuevoLaHayaCambiado(registro, hora);

        Instant referencia = hora.referencia();
        boolean aTiempo = registro.entregarSinRed(command.resumenTexto(), referencia);
        savePort.save(registro);
        MotivoSinPuntos sinPuntos = hora.motivoSinPuntos(diaDelAudio, zona).orElse(null);
        reflejarEnPastillaRenacer(command, diaDelAudio, referencia, sinPuntos == null);
        String motivo = sinPuntos == null ? null : sinPuntos.name();
        return new Aplicado<>(new ResultadoEntregaSinRed(aTiempo, motivo), motivo, registro.id().value());
    }

    /** D29: la hora real tiene que caer en el día de ese audio (el de su plazo, en la zona de la persona), no en otro. */
    private static void exigirQueAunSeaSuDia(LocalDate diaDelAudio, RevisionDeLaHora hora, ZoneId zona) {
        if (!hora.hoyEn(zona).equals(diaDelAudio)) {
            throw new NoAplicadoSinRedException(CodigoNoAplicado.FUERA_DE_SU_DIA);
        }
    }

    /**
     * D13. Lo único que cuenta como «cambió» es una ENTREGA posterior a la hora real (desde otro teléfono o con red); el PERDIDO que la
     * lectura perezosa pone cuando la entrega todavía no llegó no es un cambio de la persona: es justo lo que esta entrega corrige.
     */
    private static void exigirQueNadaMasNuevoLaHayaCambiado(RegistroEspiritu registro, RevisionDeLaHora hora) {
        if (registro.entregadoEn() != null && hora.hayCambioMasNuevo(registro.entregadoEn())) {
            throw new NoAplicadoSinRedException(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR);
        }
        if (registro.estado() == EstadoRegistroEspiritu.ENTREGADO) {
            throw new NoAplicadoSinRedException(CodigoNoAplicado.YA_ESTABA_HECHO);
        }
    }

    /** Best-effort y en su propia transacción, como {@code EspirituService.reflejarEnPastillaRenacer}: el resumen ya está guardado. */
    private void reflejarEnPastillaRenacer(EntregarResumenEspirituCommand command, LocalDate dia, Instant referencia, boolean conPuntos) {
        UserId persona = command.actorId();
        try {
            transaccionPropia.executeWithoutResult(status ->
                    espejo.completarSinRed(persona, command.resumenTexto(), dia, referencia, conPuntos));
        } catch (RuntimeException fallaDelHabitoAjeno) {
            log.warn("[offline] la Pastilla de {} se guardó, pero no se pudo reflejar en el hábito: {}", persona,
                    fallaDelHabitoAjeno.toString());
        }
    }

    /** Un reintento de un envío aplicado: lo que quedó, con la misma forma que la primera vez. */
    private ResultadoEntregaSinRed releer(EntregarResumenEspirituCommand command, EnvioRegistrado previo) {
        boolean aTiempo = loadPort.porParticipanteYDia(command.actorId(), command.dia())
                .map(r -> r.estado() == EstadoRegistroEspiritu.ENTREGADO)
                .orElseThrow(() -> new NoSuchElementException("La Pastilla de ese envío ya no está"));
        return new ResultadoEntregaSinRed(aTiempo, previo.motivoSinPuntos());
    }
}

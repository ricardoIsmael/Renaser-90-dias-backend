package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.RecordarConsultaAlMentorUseCase;
import com.renaser.os.leadership.application.services.EntregaDeAvisosAlMentor.Entrega;
import com.renaser.os.leadership.application.services.PersonasDelPrograma.Mirada;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.RechazoConCodigoException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.api.ConsultaDeTicketFinder;
import com.renaser.os.support.api.ConsultaDeTicketFinder.ConsultaDeTicket;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;

/**
 * «Recordar al mentor» (spec 056, FR-029): el líder le recuerda al mentor que tiene una consulta sin responder. El mensaje nombra al aprendiz, dice hace
 * cuántos días espera y trae la consulta tal cual. Se entrega como un «Avisar a su mentor» ({@link EntregaDeAvisosAlMentor}): chat, aviso del teléfono y una
 * sola vez por {@code claveOperacion}.
 *
 * <p>El mentor es el que acompaña hoy al aprendiz (el de su grupo), el mismo que ve la consulta en su bandeja. Una consulta ya respondida no se recuerda
 * (409 {@code YA_RESPONDIDA}).
 */
@Service
public class RecordatorioDeConsultaService implements RecordarConsultaAlMentorUseCase {

    static final String YA_RESPONDIDA = "YA_RESPONDIDA";
    static final String SIN_MENTOR = AvisoAlMentorService.SIN_MENTOR;
    /** Lo que cabe de la consulta en el mensaje: la observación guardada admite 1000 caracteres y el resto lo ocupa el encabezado. */
    static final int LARGO_MAXIMO_DE_LA_CONSULTA = 600;

    private final AccesoDeLiderazgo acceso;
    private final ConsultaDeTicketFinder consultas;
    private final PersonasDelPrograma personas;
    private final EntregaDeAvisosAlMentor entrega;
    private final Clock clock;

    RecordatorioDeConsultaService(AccesoDeLiderazgo acceso, ConsultaDeTicketFinder consultas, PersonasDelPrograma personas, EntregaDeAvisosAlMentor entrega,
                                  Clock clock) {
        this.acceso = acceso;
        this.consultas = consultas;
        this.personas = personas;
        this.entrega = entrega;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void recordar(RecordarConsultaCommand comando) {
        acceso.requireLiderazgoActivo(comando.actorId());
        if (comando.claveOperacion() == null || comando.claveOperacion().isBlank() || comando.claveOperacion().trim().length() > 100) {
            throw new IllegalArgumentException("La clave de operación es obligatoria y de hasta 100 caracteres");
        }
        ConsultaDeTicket consulta = consultas.consulta(comando.ticketId())
                .orElseThrow(() -> new NoSuchElementException("Consulta no encontrada: " + comando.ticketId()));
        entrega.exigirQueSeaDeSusFormaciones(comando.actorId(), consulta.participanteId());
        Mirada mirada = personas.mirar(comando.actorId(), consulta.participanteId(), clock.now());
        if (entrega.yaEntregada(comando.actorId(), consulta.participanteId(), comando.claveOperacion()).isPresent()) {
            return;
        }
        if (consulta.respondida()) {
            throw RechazoConCodigoException.conflicto(YA_RESPONDIDA, "Esa consulta ya está respondida.");
        }
        UserId mentor = mirada.mentorId();
        if (mentor == null) {
            throw RechazoConCodigoException.conflicto(SIN_MENTOR, "Este aprendiz no tiene un mentor a quien recordarle la consulta.");
        }
        String texto = texto(consulta, mirada);
        entrega.entregar(new Entrega(comando.actorId(), mentor, consulta.participanteId(), texto, texto, comando.claveOperacion()));
    }

    /** «Consulta de Ana Pérez sin responder desde hace 3 días: …». Tipo oración, sin relleno. */
    private String texto(ConsultaDeTicket consulta, Mirada mirada) {
        long dias = Math.max(0, ChronoUnit.DAYS.between(consulta.creadaEn(), clock.now()));
        String hace = dias == 0 ? "desde hoy" : dias == 1 ? "desde hace 1 día" : "desde hace " + dias + " días";
        String bloqueo = consulta.descripcionDelBloqueo().strip();
        if (bloqueo.length() > LARGO_MAXIMO_DE_LA_CONSULTA) {
            bloqueo = bloqueo.substring(0, LARGO_MAXIMO_DE_LA_CONSULTA - 1).stripTrailing() + "…";
        }
        return "Consulta de " + mirada.miembro().perfil().fullName() + " sin responder " + hace + ": " + bloqueo;
    }
}

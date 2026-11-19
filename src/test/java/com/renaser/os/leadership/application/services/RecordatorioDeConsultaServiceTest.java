package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.api.ObservacionDelLiderEvent;
import com.renaser.os.leadership.application.ports.in.RecordarConsultaAlMentorUseCase.RecordarConsultaCommand;
import com.renaser.os.leadership.application.services.PersonasDelGrupo.Miembro;
import com.renaser.os.leadership.application.services.PersonasDelPrograma.Mirada;
import com.renaser.os.leadership.domain.model.cumplimiento.GrupoDeLaPersona;
import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.RechazoConCodigoException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.api.ConsultaDeTicketFinder;
import com.renaser.os.support.api.ConsultaDeTicketFinder.ConsultaDeTicket;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import com.renaser.os.users.api.AlcanceDeFormacionPermisivo;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.renaser.os.leadership.application.services.PadronDeMentoresServiceTest.ANA;
import static com.renaser.os.leadership.application.services.PadronDeMentoresServiceTest.LIDER;
import static com.renaser.os.leadership.application.services.PadronDeMentoresServiceTest.LUISA;
import static com.renaser.os.leadership.application.services.PadronDeMentoresServiceTest.RAUL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * «Recordar al mentor» (spec 056, FR-029): el mentor asignado al aprendiz recibe en su chat con el líder un mensaje con la consulta y un aviso en el
 * teléfono, una sola vez por clave; no se recuerda una consulta ya respondida; y solo las de las formaciones del líder.
 */
class RecordatorioDeConsultaServiceTest {

    private static final UUID FENIX = UUID.fromString("00000000-0000-0000-0000-00000000f001");
    private static final UUID TICKET = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    private final BancoDeLiderazgo banco = new BancoDeLiderazgo();
    private final PersonasDelPrograma personas = mock(PersonasDelPrograma.class);
    private final ConsultaDeTicketFinder consultas = mock(ConsultaDeTicketFinder.class);
    private Set<Formacion> formacionesDelAprendiz = Set.of(Formacion.ESCUELA);
    private RecordatorioDeConsultaService servicio;

    @BeforeEach
    void preparar() {
        PadronDeMentoresServiceTest.prepararCuerpo(banco);
        AlcanceDeFormacionFinder alcance = new AlcanceDeFormacionPermisivo() {
            @Override
            public AlcanceDeFormacion de(UserId actor) {
                return actor.equals(LIDER) ? AlcanceDeFormacion.de(Formacion.ESCUELA) : AlcanceDeFormacion.todas();
            }

            @Override
            public Set<Formacion> formacionesDe(UserId persona) {
                return formacionesDelAprendiz;
            }
        };
        AccesoDeLiderazgo acceso = new AccesoDeLiderazgo(banco.usuariosFinder, banco.fichas, alcance);
        EntregaDeAvisosAlMentor entrega = new EntregaDeAvisosAlMentor(acceso, alcance, banco.almacen, banco.almacen, banco.chatDirecto,
                new AvisosDeObservaciones(banco.publicador), banco.ids, banco.reloj);
        servicio = new RecordatorioDeConsultaService(acceso, consultas, personas, entrega, banco.reloj);
        conConsulta("Me bloquea el módulo de hábitos y no sé por dónde empezar", false, 3);
        conMentor(LUISA);
    }

    private void conConsulta(String bloqueo, boolean respondida, int diasAbierta) {
        when(consultas.consulta(TICKET)).thenReturn(Optional.of(new ConsultaDeTicket(TICKET, ANA, bloqueo,
                BancoDeLiderazgo.AHORA.minus(Duration.ofDays(diasAbierta)), respondida)));
    }

    private void conMentor(UserId mentor) {
        UserSummary ana = new UserSummary(ANA, "Ana Pérez", null, UserRole.TRAINEE, UserStatus.ACTIVE);
        when(personas.mirar(any(), any(), any())).thenReturn(new Mirada(new Miembro(ana, null, false),
                new GrupoDeLaPersona(FENIX, "Grupo Fénix", mentor, false)));
    }

    private RecordarConsultaCommand recordatorio(String clave) {
        return new RecordarConsultaCommand(LIDER, TICKET, clave);
    }

    // spec 056 FR-029
    @Test
    @DisplayName("spec 056 FR-029 SC-005: el mentor asignado recibe en su chat con el líder la consulta, con quién pregunta y hace cuántos días, y el aviso del teléfono")
    void elMentorRecibeLaConsulta() {
        servicio.recordar(recordatorio("k1"));

        assertThat(banco.mensajesDirectos).singleElement().satisfies(mensaje -> {
            assertThat(mensaje.de()).isEqualTo(LIDER);
            assertThat(mensaje.para()).isEqualTo(LUISA);
            assertThat(mensaje.texto()).isEqualTo("Consulta de Ana Pérez sin responder desde hace 3 días: Me bloquea el módulo de hábitos y no sé por dónde empezar");
        });
        assertThat(banco.avisosPublicados).singleElement().isInstanceOfSatisfying(ObservacionDelLiderEvent.class,
                evento -> assertThat(evento.mentorId()).isEqualTo(LUISA.value()));
        assertThat(banco.observaciones).singleElement().satisfies(observacion -> assertThat(observacion.personaId()).isEqualTo(ANA));
    }

    // spec 056 FR-029
    @Test
    @DisplayName("spec 056 FR-029: repetir la misma operationKey no manda otro mensaje ni otro aviso")
    void idempotentePorOperationKey() {
        servicio.recordar(recordatorio("k1"));
        servicio.recordar(recordatorio("k1"));

        assertThat(banco.mensajesDirectos).hasSize(1);
        assertThat(banco.avisosPublicados).hasSize(1);
    }

    // spec 056 FR-029
    @Test
    @DisplayName("spec 056 FR-029: una consulta larga se corta con puntos suspensivos para que el mensaje quepa; una de hoy dice «desde hoy»")
    void unaConsultaLargaSeCorta() {
        conConsulta("x".repeat(900), false, 0);

        servicio.recordar(recordatorio("k1"));

        String texto = banco.mensajesDirectos.getFirst().texto();
        assertThat(texto).startsWith("Consulta de Ana Pérez sin responder desde hoy: ").endsWith("…");
        assertThat(texto.length()).isLessThanOrEqualTo(1000);
    }

    // spec 056 FR-029
    @Test
    @DisplayName("spec 056 FR-029: una consulta ya respondida es 409 YA_RESPONDIDA y no se manda nada")
    void unaConsultaRespondidaNoSeRecuerda() {
        conConsulta("Me bloquea el módulo de hábitos", true, 3);

        assertThatThrownBy(() -> servicio.recordar(recordatorio("k1"))).isInstanceOfSatisfying(RechazoConCodigoException.class, e -> {
            assertThat(e.estado()).isEqualTo(RechazoConCodigoException.CONFLICTO);
            assertThat(e.codigo()).isEqualTo("YA_RESPONDIDA");
        });

        assertThat(banco.mensajesDirectos).isEmpty();
    }

    // spec 056 FR-029
    @Test
    @DisplayName("spec 056 FR-029: un aprendiz sin mentor (grupo sin mentor o sin grupo) es 409 SIN_MENTOR")
    void sinMentorEsUnConflictoConCodigo() {
        conMentor(null);

        assertThatThrownBy(() -> servicio.recordar(recordatorio("k1"))).isInstanceOfSatisfying(RechazoConCodigoException.class,
                e -> assertThat(e.codigo()).isEqualTo("SIN_MENTOR"));

        assertThat(banco.mensajesDirectos).isEmpty();
    }

    // spec 056 FR-029
    @Test
    @DisplayName("spec 056 FR-029: una consulta que no existe es 404, y la de un aprendiz de otra formación es 403 FUERA_DE_SU_ALCANCE")
    void noExisteOEsDeOtraFormacion() {
        when(consultas.consulta(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.recordar(new RecordarConsultaCommand(LIDER, UUID.randomUUID(), "k1"))).isInstanceOf(NoSuchElementException.class);

        conConsulta("Me bloquea", false, 3);
        formacionesDelAprendiz = Set.of(Formacion.DIPLOMADO);
        assertThatThrownBy(() -> servicio.recordar(recordatorio("k2"))).isInstanceOfSatisfying(NotAuthorizedException.class,
                e -> assertThat(e.codigo()).isEqualTo(NotAuthorizedException.FUERA_DE_SU_ALCANCE));

        assertThat(banco.mensajesDirectos).isEmpty();
    }

    // spec 056 FR-029
    @Test
    @DisplayName("spec 056 FR-029: un mentor no recuerda (403), una cuenta de líder suspendida tampoco, y sin clave se rechaza antes de tocar el chat")
    void soloElLiderazgoActivoRecuerda() {
        assertThatThrownBy(() -> servicio.recordar(new RecordarConsultaCommand(RAUL, TICKET, "k1"))).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> servicio.recordar(recordatorio(" "))).isInstanceOf(IllegalArgumentException.class);
        banco.usuario(LIDER, "Líder", UserRole.MENTOR_LEAD, UserStatus.SUSPENDED);

        assertThatThrownBy(() -> servicio.recordar(recordatorio("k2"))).isInstanceOf(NotAuthorizedException.class);

        assertThat(banco.mensajesDirectos).isEmpty();
    }
}

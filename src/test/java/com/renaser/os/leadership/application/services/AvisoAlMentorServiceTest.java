package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.api.ObservacionDelLiderEvent;
import com.renaser.os.leadership.application.ports.in.AvisarAlMentorUseCase.AvisarAlMentorCommand;
import com.renaser.os.leadership.application.services.PersonasDelGrupo.Miembro;
import com.renaser.os.leadership.application.services.PersonasDelPrograma.Mirada;
import com.renaser.os.leadership.domain.model.cumplimiento.GrupoDeLaPersona;
import com.renaser.os.leadership.domain.model.observacion.LineaDeLaFicha;
import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor;
import com.renaser.os.leadership.domain.model.observacion.TipoObservacion;
import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.RechazoConCodigoException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import com.renaser.os.users.api.AlcanceDeFormacionPermisivo;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;
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
 * «Avisar a su mentor» (spec 056, FR-019 y SC-005): el mentor vigente de la persona recibe el mensaje con el enlace a la ficha en su chat con el líder y un
 * aviso en el teléfono; sin mentor, 409 {@code SIN_MENTOR}; repetir la clave no manda nada dos veces; y solo para personas de las formaciones del líder.
 */
class AvisoAlMentorServiceTest {

    private static final UUID FENIX = UUID.fromString("00000000-0000-0000-0000-00000000f001");

    private final BancoDeLiderazgo banco = new BancoDeLiderazgo();
    private final PersonasDelPrograma personas = mock(PersonasDelPrograma.class);
    private Set<Formacion> formacionesDeLaPersona = Set.of(Formacion.ESCUELA);
    private AvisoAlMentorService servicio;

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
                return formacionesDeLaPersona;
            }
        };
        AccesoDeLiderazgo acceso = new AccesoDeLiderazgo(banco.usuariosFinder, banco.fichas, alcance);
        EntregaDeAvisosAlMentor entrega = new EntregaDeAvisosAlMentor(acceso, alcance, banco.almacen, banco.almacen, banco.chatDirecto,
                new AvisosDeObservaciones(banco.publicador), banco.ids, banco.reloj);
        servicio = new AvisoAlMentorService(acceso, personas, entrega, banco.reloj);
        conMentor(LUISA);
    }

    private Mirada mirada(GrupoDeLaPersona grupo) {
        UserSummary ana = new UserSummary(ANA, "Ana Pérez", null, UserRole.TRAINEE, UserStatus.ACTIVE);
        return new Mirada(new Miembro(ana, null, false), grupo);
    }

    private void conMentor(UserId mentor) {
        when(personas.mirar(any(), any(), any())).thenReturn(mirada(new GrupoDeLaPersona(FENIX, "Grupo Fénix", mentor, false)));
    }

    private AvisarAlMentorCommand aviso(String clave) {
        return new AvisarAlMentorCommand(LIDER, ANA, "Ana lleva tres días en rojo, ¿puedes llamarla?", clave);
    }

    // spec 056 FR-019
    @Test
    @DisplayName("spec 056 FR-019 SC-005: el mentor vigente recibe el mensaje con el enlace a la ficha en su chat con el líder, y el aviso del teléfono")
    void elMentorRecibeElMensajeConElEnlaceYElAviso() {
        servicio.avisar(aviso("k1"));

        assertThat(banco.mensajesDirectos).singleElement().satisfies(mensaje -> {
            assertThat(mensaje.de()).isEqualTo(LIDER);
            assertThat(mensaje.para()).isEqualTo(LUISA);
            assertThat(mensaje.texto()).startsWith("Ana lleva tres días en rojo, ¿puedes llamarla?")
                    .endsWith("Ficha de Ana Pérez: /mentor/groups/" + FENIX + "/learners/" + ANA.value());
        });
        assertThat(banco.avisosPublicados).singleElement().isInstanceOfSatisfying(ObservacionDelLiderEvent.class, evento -> {
            assertThat(evento.mentorId()).isEqualTo(LUISA.value());
            assertThat(evento.rutaApp()).isEqualTo("/chat/" + banco.mensajesDirectos.getFirst().conversacionId());
        });
    }

    // spec 056 FR-019
    @Test
    @DisplayName("spec 056 FR-019: la ficha va separada en su propia línea final, con el formato «Ficha de <nombre>: /mentor/groups/<g>/learners/<p>» que la app reconoce")
    void laFichaVaEnSuPropiaLineaFinal() {
        servicio.avisar(aviso("k1"));

        String[] lineas = banco.mensajesDirectos.getFirst().texto().split("\n", -1);
        assertThat(lineas).hasSize(3);
        assertThat(lineas[0]).isEqualTo("Ana lleva tres días en rojo, ¿puedes llamarla?");
        assertThat(lineas[1]).isEmpty();
        assertThat(lineas[2]).isEqualTo("Ficha de Ana Pérez: /mentor/groups/" + FENIX + "/learners/" + ANA.value());
        assertThat(LineaDeLaFicha.PATRON.matcher(lineas[2]).matches()).isTrue();
    }

    // spec 056 FR-019
    @Test
    @DisplayName("spec 056 FR-019: queda como observación sobre la persona (spec 005, R-7), con su texto y su envío, para «Lo que le dijiste»")
    void quedaComoObservacionSobreLaPersona() {
        servicio.avisar(aviso("k1"));

        assertThat(banco.observaciones).singleElement().satisfies((ObservacionDeMentor observacion) -> {
            assertThat(observacion.tipo()).isEqualTo(TipoObservacion.SUGERENCIA);
            assertThat(observacion.mentorId()).isEqualTo(LUISA);
            assertThat(observacion.personaId()).isEqualTo(ANA);
            assertThat(observacion.texto()).as("lo que escribió el líder, sin el enlace").isEqualTo("Ana lleva tres días en rojo, ¿puedes llamarla?");
            assertThat(observacion.enviadaPorChat()).isTrue();
            assertThat(observacion.mensajeId()).isEqualTo(banco.mensajesDirectos.getFirst().mensajeId());
        });
    }

    // spec 056 FR-019
    @Test
    @DisplayName("spec 056 FR-019: repetir la misma operationKey no manda otro mensaje ni otro aviso")
    void idempotentePorOperationKey() {
        servicio.avisar(aviso("k1"));
        servicio.avisar(aviso("k1"));

        assertThat(banco.mensajesDirectos).hasSize(1);
        assertThat(banco.avisosPublicados).hasSize(1);
        assertThat(banco.observaciones).hasSize(1);
    }

    // spec 056 FR-019
    @Test
    @DisplayName("spec 056 FR-019: la misma clave para otra persona es un error del cliente, no un aviso nuevo")
    void laMismaClaveParaOtraPersonaEsUnError() {
        servicio.avisar(aviso("k1"));
        UserId luis = UserId.of(UUID.randomUUID());
        when(personas.mirar(any(), any(), any())).thenReturn(new Mirada(new Miembro(
                new UserSummary(luis, "Luis Soto", null, UserRole.TRAINEE, UserStatus.ACTIVE), null, false),
                new GrupoDeLaPersona(FENIX, "Grupo Fénix", LUISA, false)));

        assertThatThrownBy(() -> servicio.avisar(new AvisarAlMentorCommand(LIDER, luis, "Otro aviso", "k1"))).isInstanceOf(IllegalArgumentException.class);

        assertThat(banco.mensajesDirectos).hasSize(1);
    }

    // spec 056 FR-019
    @Test
    @DisplayName("spec 056 FR-019: una persona sin mentor (grupo sin mentor, recepción o sin grupo) es 409 SIN_MENTOR y no se manda nada")
    void sinMentorEsUnConflictoConCodigo() {
        when(personas.mirar(any(), any(), any())).thenReturn(mirada(new GrupoDeLaPersona(FENIX, "Grupo Fénix", null, false)));
        assertThatThrownBy(() -> servicio.avisar(aviso("k1"))).isInstanceOfSatisfying(RechazoConCodigoException.class, e -> {
            assertThat(e.estado()).isEqualTo(RechazoConCodigoException.CONFLICTO);
            assertThat(e.codigo()).isEqualTo("SIN_MENTOR");
        });

        when(personas.mirar(any(), any(), any())).thenReturn(mirada(new GrupoDeLaPersona(FENIX, "Recepción", LUISA, true)));
        assertThatThrownBy(() -> servicio.avisar(aviso("k2"))).isInstanceOf(RechazoConCodigoException.class);

        when(personas.mirar(any(), any(), any())).thenReturn(mirada(null));
        assertThatThrownBy(() -> servicio.avisar(aviso("k3"))).isInstanceOf(RechazoConCodigoException.class);

        assertThat(banco.mensajesDirectos).isEmpty();
        assertThat(banco.avisosPublicados).isEmpty();
        assertThat(banco.observaciones).isEmpty();
    }

    // spec 056 FR-019
    @Test
    @DisplayName("spec 056 FR-019: una persona de otra formación es 403 FUERA_DE_SU_ALCANCE para el líder, y no se manda nada")
    void fueraDeSuAlcance() {
        formacionesDeLaPersona = Set.of(Formacion.DIPLOMADO);

        assertThatThrownBy(() -> servicio.avisar(aviso("k1"))).isInstanceOfSatisfying(NotAuthorizedException.class,
                e -> assertThat(e.codigo()).isEqualTo(NotAuthorizedException.FUERA_DE_SU_ALCANCE));

        assertThat(banco.mensajesDirectos).isEmpty();
    }

    // spec 056 FR-019
    @Test
    @DisplayName("spec 056 FR-019: un mentor no avisa (403), y una cuenta de líder suspendida tampoco")
    void soloElLiderazgoActivoAvisa() {
        assertThatThrownBy(() -> servicio.avisar(new AvisarAlMentorCommand(RAUL, ANA, "Hola", "k1"))).isInstanceOf(NotAuthorizedException.class);
        banco.usuario(LIDER, "Líder", UserRole.MENTOR_LEAD, UserStatus.SUSPENDED);

        assertThatThrownBy(() -> servicio.avisar(aviso("k2"))).isInstanceOf(NotAuthorizedException.class);

        assertThat(banco.mensajesDirectos).isEmpty();
    }

    // spec 056 FR-019
    @Test
    @DisplayName("spec 056 FR-019: si el chat no puede entregar el mensaje el error sube con su motivo y no queda nada guardado")
    void siElChatRechazaElErrorSube() {
        banco.chatRechaza = new NotAuthorizedException("Los chats directos no están disponibles en esta formación");

        assertThatThrownBy(() -> servicio.avisar(aviso("k1"))).isInstanceOf(NotAuthorizedException.class).hasMessageContaining("chats directos");

        assertThat(banco.observaciones).isEmpty();
        assertThat(banco.avisosPublicados).isEmpty();
    }

    // spec 056 FR-019
    @Test
    @DisplayName("spec 056 FR-019: un aviso en blanco, de más de 1000 caracteres o sin clave se rechaza antes de tocar el chat")
    void textoYClaveSeValidan() {
        assertThatThrownBy(() -> servicio.avisar(new AvisarAlMentorCommand(LIDER, ANA, "   ", "k1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.avisar(new AvisarAlMentorCommand(LIDER, ANA, "x".repeat(1001), "k1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.avisar(new AvisarAlMentorCommand(LIDER, ANA, "Hola", " "))).isInstanceOf(IllegalArgumentException.class);

        assertThat(banco.mensajesDirectos).isEmpty();
    }

    // spec 056 FR-019
    @Test
    @DisplayName("spec 056 FR-019: una persona que no es del programa es 404 (la responde quien la mira)")
    void unaPersonaQueNoExisteEs404() {
        when(personas.mirar(any(), any(), any())).thenThrow(new NoSuchElementException("Esa persona no esta en el programa"));

        assertThatThrownBy(() -> servicio.avisar(aviso("k1"))).isInstanceOf(NoSuchElementException.class);
    }
}

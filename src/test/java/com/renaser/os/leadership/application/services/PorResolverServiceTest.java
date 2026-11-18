package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.ConsultarPorResolverUseCase.PorResolver;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.api.AtencionDeTicketsFinder.TicketPendiente;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.renaser.os.leadership.application.services.PadronDeMentoresServiceTest.ADMIN;
import static com.renaser.os.leadership.application.services.PadronDeMentoresServiceTest.ANA;
import static com.renaser.os.leadership.application.services.PadronDeMentoresServiceTest.LIDER;
import static com.renaser.os.leadership.application.services.PadronDeMentoresServiceTest.LUISA;
import static com.renaser.os.leadership.application.services.PadronDeMentoresServiceTest.PIA;
import static com.renaser.os.leadership.application.services.PadronDeMentoresServiceTest.RAUL;
import static com.renaser.os.leadership.application.services.PadronDeMentoresServiceTest.prepararCuerpo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * «Por resolver» de «Mis mentores» (spec 056, FR-002): los grupos sin mentor, las consultas sin responder y el mentor con la peor
 * evaluación del mes, con las fuentes aisladas (RL-21). El cuerpo es el del padrón: Luisa lleva Fénix (Ana, Luis), Raúl lleva Aurora (Pía);
 * hoy es el 30 de septiembre en Lima.
 */
class PorResolverServiceTest {

    private static final UUID HUERFANO = UUID.fromString("00000000-0000-0000-0000-00000000f0a1");
    private static final UUID DEL_DIPLOMADO = UUID.fromString("00000000-0000-0000-0000-00000000f0a2");

    private final BancoDeLiderazgo banco = new BancoDeLiderazgo();

    @BeforeEach
    void preparar() {
        prepararCuerpo(banco);
    }

    private PorResolver porResolver(UserId actor) {
        return banco.porResolver().porResolver(actor);
    }

    @Test
    @DisplayName("spec 056 FR-002: cuenta los grupos en curso sin mentor de las formaciones del líder")
    void gruposSinMentor() {
        banco.grupoSinMentor(HUERFANO, "Huérfano", Formacion.ESCUELA);
        banco.grupoSinMentor(DEL_DIPLOMADO, "Del Diplomado", Formacion.DIPLOMADO);

        // El banco no filtra formaciones (alcance permisivo): ve las dos. El filtro real lo prueba CupoYPorResolverIT.
        assertThat(porResolver(LIDER).gruposSinMentor()).isEqualTo(2);
    }

    @Test
    @DisplayName("spec 056 FR-002: sin grupos huérfanos son cero: un dato verdadero, no un dato que falta")
    void sinGruposHuerfanos() {
        assertThat(porResolver(LIDER).gruposSinMentor()).isZero();
    }

    @Test
    @DisplayName("spec 056 FR-002: cuenta las consultas abiertas de los aprendices de sus mentores y los días de la más vieja")
    void consultasSinResponder() {
        banco.pendientes.add(new TicketPendiente(UUID.randomUUID(), ANA, Instant.parse("2026-09-25T15:00:00Z")));
        banco.pendientes.add(new TicketPendiente(UUID.randomUUID(), PIA, Instant.parse("2026-09-29T15:00:00Z")));
        UserId sinGrupo = PadronDeMentoresServiceTest.id("b9");
        banco.pendientes.add(new TicketPendiente(UUID.randomUUID(), sinGrupo, Instant.parse("2026-09-01T15:00:00Z")));

        PorResolver resultado = porResolver(LIDER);

        assertThat(resultado.consultas().cantidad()).as("la de quien no está en un grupo no le toca a ningún mentor").isEqualTo(2);
        assertThat(resultado.consultas().diasDeLaMasVieja()).as("del 25/9 a las 15:00 al 30/9 a las 22:00 en Lima: 5 días completos").isEqualTo(5);
    }

    @Test
    @DisplayName("spec 056 FR-002: una consulta de quien está en dos grupos de dos mentores se cuenta una sola vez")
    void unaConsultaNoSeCuentaDosVeces() {
        UUID brisa = UUID.fromString("00000000-0000-0000-0000-00000000f003");
        banco.grupo(brisa, "Grupo Brisa", RAUL, java.util.List.of(ANA));
        banco.pendientes.add(new TicketPendiente(UUID.randomUUID(), ANA, Instant.parse("2026-09-27T15:00:00Z")));

        assertThat(porResolver(LIDER).consultas().cantidad()).isEqualTo(1);
    }

    @Test
    @DisplayName("spec 056 FR-002: sin consultas abiertas son cero y sin días, no «hace 0 días»")
    void sinConsultas() {
        PorResolver resultado = porResolver(LIDER);

        assertThat(resultado.consultas().cantidad()).isZero();
        assertThat(resultado.consultas().diasDeLaMasVieja()).isNull();
    }

    @Test
    @DisplayName("spec 056 FR-002: el mentor con la peor evaluación del mes, si está por debajo del 60 %")
    void peorMentor() {
        banco.evaluacion(LUISA, "45.5");
        banco.evaluacion(RAUL, "58.0");

        PorResolver resultado = porResolver(LIDER);

        assertThat(resultado.peorMentor().id()).isEqualTo(LUISA.value());
        assertThat(resultado.peorMentor().nombre()).isEqualTo("Luisa Rojas");
        assertThat(resultado.peorMentor().porcentaje()).isEqualByComparingTo(new BigDecimal("45.5"));
    }

    @Test
    @DisplayName("spec 056 FR-002: con todos en 60 % o más, o sin evaluación calculada, no hay mentor que mostrar")
    void sinPeorMentor() {
        banco.evaluacion(LUISA, "60.0");

        assertThat(porResolver(LIDER).peorMentor()).as("60 % justo ya no está por debajo; Raúl y Marta sin historial").isNull();
    }

    @Test
    @DisplayName("spec 056 RL-21: con los tickets caídos solo las consultas quedan sin dato; lo demás se entrega")
    void ticketsCaidos() {
        banco.grupoSinMentor(HUERFANO, "Huérfano", Formacion.ESCUELA);
        banco.evaluacion(LUISA, "40.0");
        banco.ticketsCaidos = true;

        PorResolver resultado = porResolver(LIDER);

        assertThat(resultado.consultas()).isNull();
        assertThat(resultado.gruposSinMentor()).isEqualTo(1);
        assertThat(resultado.peorMentor()).isNotNull();
    }

    @Test
    @DisplayName("spec 056 RL-21: con el semáforo caído no se sabe de quién son las consultas: sin dato, nunca «ninguna»")
    void semaforoCaido() {
        banco.medicionCaida = true;

        assertThat(porResolver(LIDER).consultas()).isNull();
    }

    @Test
    @DisplayName("spec 056 FR-042: Administración también lo ve; el mentor, el aprendiz y un líder suspendido reciben 403")
    void autorizacionNegativa() {
        assertThat(porResolver(ADMIN)).isNotNull();
        assertThatThrownBy(() -> porResolver(LUISA)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> porResolver(ANA)).isInstanceOf(NotAuthorizedException.class);
        banco.usuario(LIDER, "Líder", UserRole.MENTOR_LEAD, UserStatus.SUSPENDED);
        assertThatThrownBy(() -> porResolver(LIDER)).isInstanceOf(NotAuthorizedException.class);
    }
}

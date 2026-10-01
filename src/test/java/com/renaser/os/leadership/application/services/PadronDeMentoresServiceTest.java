package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.ConsultarPadronDeMentoresUseCase.PadronDeMentores;
import com.renaser.os.leadership.application.ports.in.IndicadoresDeMentor;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.api.AtencionDeTicketsFinder.TicketPendiente;
import com.renaser.os.support.api.AtencionDeTicketsFinder.TicketRespondido;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static com.renaser.os.leadership.application.services.BancoDeLiderazgo.semaforo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El padrón del cuerpo de mentores (SDD 002, RL-04/RL-05/RL-09/RL-10/RL-21/RL-26; D-241). */
class PadronDeMentoresServiceTest {

    static final UserId LIDER = id("c1");
    static final UserId ADMIN = id("d1");
    static final UserId LUISA = id("a1");
    static final UserId RAUL = id("a2");
    static final UserId MARTA = id("a3");
    static final UserId ANA = id("b1");
    static final UserId LUIS = id("b2");
    static final UserId PIA = id("b3");
    static final UUID FENIX = UUID.fromString("00000000-0000-0000-0000-00000000f001");
    static final UUID AURORA = UUID.fromString("00000000-0000-0000-0000-00000000f002");

    private final BancoDeLiderazgo banco = new BancoDeLiderazgo();

    static UserId id(String sufijo) {
        return UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000" + sufijo));
    }

    @BeforeEach
    void preparar() {
        prepararCuerpo(banco);
    }

    /** Luisa lleva Fénix (Ana, Luis), Raúl lleva Aurora (Pía) y Marta recién llega, sin grupo. */
    static void prepararCuerpo(BancoDeLiderazgo banco) {
        banco.usuario(LIDER, "Líder", UserRole.MENTOR_LEAD, UserStatus.ACTIVE);
        banco.usuario(ADMIN, "Admin", UserRole.ADMIN, UserStatus.ACTIVE);
        banco.usuario(LUISA, "Luisa Rojas", UserRole.MENTOR, UserStatus.ACTIVE);
        banco.usuario(RAUL, "Raúl Soto", UserRole.MENTOR, UserStatus.ACTIVE);
        banco.usuario(MARTA, "Marta Ruiz", UserRole.MENTOR, UserStatus.ACTIVE);
        banco.usuario(ANA, "Ana Pérez", UserRole.TRAINEE, UserStatus.ACTIVE);
        banco.perfil(LUISA, "N1", "YELLOW");
        banco.grupo(FENIX, "Grupo Fénix", LUISA, List.of(ANA, LUIS));
        banco.grupo(AURORA, "Grupo Aurora", RAUL, List.of(PIA));
        banco.semaforoDe(LUISA, semaforo(1, 0, 1, 0, "70.0"));
        banco.semaforoDe(RAUL, semaforo(1, 0, 0, 0, "90.0"));
        banco.evaluacion(LUISA, "80.0");
    }

    private IndicadoresDeMentor de(PadronDeMentores padron, UserId mentor) {
        return padron.mentores().stream().filter(m -> m.mentorId().equals(mentor)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("el lider ve a cada mentor con sus grupos, aprendices, semaforo, atencion y evaluacion")
    void padronConIndicadores() {
        banco.pendientes.add(new TicketPendiente(UUID.randomUUID(), ANA, Instant.parse("2026-09-27T15:00:00Z")));
        banco.respondidos.add(new TicketRespondido(UUID.randomUUID(), LUISA, Instant.parse("2026-09-20T10:00:00Z"),
                Instant.parse("2026-09-20T14:00:00Z")));

        PadronDeMentores padron = banco.padron().padron(LIDER);

        assertThat(padron.mentores()).extracting(IndicadoresDeMentor::nombre)
                .containsExactly("Luisa Rojas", "Marta Ruiz", "Raúl Soto");
        IndicadoresDeMentor luisa = de(padron, LUISA);
        assertThat(luisa.grupos().valor()).singleElement().satisfies(g -> assertThat(g.nombre()).isEqualTo("Grupo Fénix"));
        assertThat(luisa.aprendices()).isEqualTo(2);
        assertThat(luisa.semaforo().valor().promedio()).isEqualByComparingTo(new BigDecimal("70.0"));
        assertThat(luisa.atencion().valor().pendientes()).isEqualTo(1);
        assertThat(luisa.atencion().valor().diasDelMasAntiguo()).isEqualTo(3);
        assertThat(luisa.atencion().valor().respondidas()).isEqualTo(1);
        assertThat(luisa.atencion().valor().medianaHoras()).isEqualByComparingTo(new BigDecimal("4.0"));
        assertThat(luisa.evaluacion().valor().porcentaje()).isEqualByComparingTo(new BigDecimal("80.0"));
    }

    @Test
    @DisplayName("regla 02: a las 03:00 UTC del 1/10 en Lima es septiembre, y de septiembre es la evaluacion")
    void elMesEsElDeLima() {
        PadronDeMentores padron = banco.padron().padron(LIDER);

        assertThat(padron.mes()).isEqualTo("2026-09");
        assertThat(padron.zona()).isEqualTo("America/Lima");
        assertThat(banco.mesesEvaluados).containsOnly(YearMonth.of(2026, 9));
    }

    @Test
    @DisplayName("RL-10: lo que respondio Luisa sigue siendo de Luisa aunque el aprendiz hoy este con Raul")
    void atribucionPorQuienRespondio() {
        // Pía hoy está en el grupo de Raúl, pero el ticket se lo respondió Luisa.
        banco.respondidos.add(new TicketRespondido(UUID.randomUUID(), LUISA, Instant.parse("2026-09-10T10:00:00Z"),
                Instant.parse("2026-09-10T12:00:00Z")));

        PadronDeMentores padron = banco.padron().padron(LIDER);

        assertThat(de(padron, LUISA).atencion().valor().respondidas()).isEqualTo(1);
        assertThat(de(padron, RAUL).atencion().valor().respondidas()).isZero();
    }

    @Test
    @DisplayName("RL-05: un mentor sin grupo tiene grupos vacios y semaforo sin valor, no un verde inventado")
    void mentorSinGrupo() {
        IndicadoresDeMentor marta = de(banco.padron().padron(LIDER), MARTA);

        assertThat(marta.grupos().disponible()).isTrue();
        assertThat(marta.grupos().valor()).isEmpty();
        assertThat(marta.aprendices()).isZero();
        assertThat(marta.semaforo().disponible()).isTrue();
        assertThat(marta.semaforo().valor()).isNull();
        assertThat(marta.atencion().valor().medianaHoras()).isNull();
        assertThat(marta.evaluacion().valor().porcentaje()).isNull();
    }

    @Test
    @DisplayName("RL-21: con el semaforo caido, grupos y atencion quedan no disponibles y la evaluacion se entrega")
    void fuenteDeGruposCaida() {
        banco.medicionCaida = true;

        IndicadoresDeMentor luisa = de(banco.padron().padron(LIDER), LUISA);

        assertThat(luisa.grupos().disponible()).isFalse();
        assertThat(luisa.aprendices()).isNull();
        assertThat(luisa.semaforo().disponible()).isFalse();
        assertThat(luisa.atencion().disponible()).isFalse();
        assertThat(luisa.evaluacion().disponible()).isTrue();
    }

    @Test
    @DisplayName("RL-21: con los tickets caidos, solo la atencion queda no disponible")
    void fuenteDeTicketsCaida() {
        banco.ticketsCaidos = true;

        IndicadoresDeMentor luisa = de(banco.padron().padron(LIDER), LUISA);

        assertThat(luisa.atencion().disponible()).isFalse();
        assertThat(luisa.grupos().disponible()).isTrue();
        assertThat(luisa.semaforo().disponible()).isTrue();
    }

    @Test
    @DisplayName("ADMIN tambien gestiona el cuerpo de mentores")
    void adminTambien() {
        assertThat(banco.padron().padron(ADMIN).mentores()).hasSize(3);
    }

    @Test
    @DisplayName("RL-26: MENTOR y TRAINEE reciben 403 aunque el interceptor los deje pasar; un lider suspendido tambien")
    void autorizacionNegativa() {
        PadronDeMentoresService servicio = banco.padron();

        assertThatThrownBy(() -> servicio.padron(LUISA)).isInstanceOf(NotAuthorizedException.class)
                .hasMessage("Solo MENTOR_LEAD/ADMIN/ALCHEMIST gestionan el cuerpo de mentores");
        assertThatThrownBy(() -> servicio.padron(ANA)).isInstanceOf(NotAuthorizedException.class);
        banco.usuario(LIDER, "Líder", UserRole.MENTOR_LEAD, UserStatus.SUSPENDED);
        assertThatThrownBy(() -> servicio.padron(LIDER)).isInstanceOf(NotAuthorizedException.class)
                .hasMessage("La cuenta esta suspendida");
        assertThat(banco.mesesEvaluados).isEmpty();
    }
}

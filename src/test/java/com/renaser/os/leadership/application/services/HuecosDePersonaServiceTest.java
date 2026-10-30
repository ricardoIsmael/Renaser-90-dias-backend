package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.ConsultarHuecosDePersonaUseCase.ConsultaHuecosDePersona;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.ConsultaSemanaDePersona;
import com.renaser.os.leadership.application.ports.out.GuardarConsultaDeFichaPort;
import com.renaser.os.leadership.domain.model.consulta.ConsultaDeFicha;
import com.renaser.os.leadership.domain.model.consulta.ReglaDelResumenDeConsultas;
import com.renaser.os.leadership.domain.model.consulta.ReglaDelResumenDeConsultas.DiaDelMentor;
import com.renaser.os.mentoring.api.HuecosDeHorarioFinder;
import com.renaser.os.mentoring.api.HuecosDeLaSemana;
import com.renaser.os.mentoring.application.services.SemanasDeHuecosDePrueba;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.AHORA;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.DESDE_VIGENTE;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.FENIX;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.LIMA;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.SIN_MENTOR;
import static com.renaser.os.mentoring.application.services.VentanasDePrueba.ventanaPareja;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Los huecos de horario de una persona para el líder (spec 015) y la constancia de la consulta (FR-027): la misma que
 * deja la ficha de la semana (spec 005, FR-016), en la misma tabla. Lo que se prueba es lo del líder: a quién deja ver,
 * qué registra y, sobre todo, cuándo NO registra. El cálculo de los huecos es de {@code mentoring} y acá es un doble.
 */
class HuecosDePersonaServiceTest {

    private static final UserId LIDER = id("c1");
    private static final UserId ADMIN = id("d1");
    private static final UserId ALQUIMISTA = id("d2");
    private static final UserId SOPORTE = id("d3");
    private static final UserId MENTORA = id("a1");
    private static final UserId ANA = id("b1");
    private static final UserId LUIS = id("b2");
    private static final UserId AJENA = id("b9");
    /** El lunes de la semana en curso de Ana a {@link BancoDeCumplimiento#AHORA} (lunes 19 de octubre, 10:00 en Lima). */
    private static final LocalDate LUNES_EN_CURSO = LocalDate.of(2026, 10, 19);
    private static final LocalDate LUNES_ANTERIOR = LocalDate.of(2026, 10, 12);
    private static final LocalDate PRIMERA_SEMANA = LocalDate.of(2026, 8, 17);

    private final BancoDeCumplimiento banco = new BancoDeCumplimiento();
    /** Cada pedido que llegó al cálculo: {persona, lunes pedido}. */
    private final List<Object[]> pedidosAlCalculo = new ArrayList<>();
    private RuntimeException fallaDelCalculo;
    private HuecosDePersonaService servicio;

    private final HuecosDeHorarioFinder calculo = (persona, lunes) -> {
        pedidosAlCalculo.add(new Object[]{persona, lunes});
        if (fallaDelCalculo != null) {
            throw fallaDelCalculo;
        }
        // Como el cálculo real: sin lunes, la semana en curso de la persona; con uno, ese.
        return SemanasDeHuecosDePrueba.respuestaDelContrato(lunes == null ? LUNES_EN_CURSO : lunes, PRIMERA_SEMANA,
                false);
    };

    private static UserId id(String sufijo) {
        return UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000" + sufijo));
    }

    private HuecosDePersonaService servicioCon(GuardarConsultaDeFichaPort consultas) {
        return new HuecosDePersonaService(banco.acceso(), banco.personas(), calculo, consultas, banco.ids,
                banco.reloj);
    }

    @BeforeEach
    void preparar() {
        servicio = servicioCon(banco.guardarConsulta);
        banco.usuario(LIDER, "Lider Uno", UserRole.MENTOR_LEAD, UserStatus.ACTIVE);
        banco.usuario(ADMIN, "Admin", UserRole.ADMIN, UserStatus.ACTIVE);
        banco.usuario(ALQUIMISTA, "Alquimista", UserRole.ALCHEMIST, UserStatus.ACTIVE);
        banco.usuario(SOPORTE, "Soporte TI", UserRole.IT_SUPPORT, UserStatus.ACTIVE);
        banco.grupoConMentor(FENIX, "Grupo Fénix", MENTORA, "Luisa Rojas");
        banco.aprendiz(FENIX, ANA, "Ana Pérez");
        banco.vigente(ANA, ventanaPareja(DESDE_VIGENTE, false, 70));
    }

    private HuecosDeLaSemana huecosDe(UserId actor, UserId persona, LocalDate semana) {
        return servicio.huecosDe(new ConsultaHuecosDePersona(actor, FENIX, persona.value(), semana));
    }

    // ── la lectura ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("el lider recibe lo que calculo mentoring, para esa persona y la semana pedida")
    void devuelveLoQueCalculaMentoring() {
        HuecosDeLaSemana huecos = huecosDe(LIDER, ANA, LUNES_ANTERIOR);

        assertThat(huecos.semana()).isEqualTo(LUNES_ANTERIOR);
        assertThat(huecos.dias()).hasSize(7);
        assertThat(pedidosAlCalculo).singleElement().satisfies(pedido -> {
            assertThat(pedido[0]).isEqualTo(ANA);
            assertThat(pedido[1]).isEqualTo(LUNES_ANTERIOR);
        });
    }

    // ── la constancia (FR-027) ──────────────────────────────────────────────

    @Test
    @DisplayName("FR-027: leer los huecos registra quien, con que rol, a quien, de que grupo, a que mentor, el lunes y cuando")
    void registraLaConsulta() {
        huecosDe(LIDER, ANA, null);

        assertThat(banco.consultas).singleElement().satisfies((ConsultaDeFicha c) -> {
            assertThat(c.consultorId()).isEqualTo(LIDER);
            assertThat(c.rolConsultor()).isEqualTo(UserRole.MENTOR_LEAD);
            assertThat(c.personaId()).isEqualTo(ANA);
            assertThat(c.grupoId()).isEqualTo(FENIX);
            assertThat(c.mentorId()).isEqualTo(MENTORA);
            assertThat(c.semanaDesde()).isEqualTo(LUNES_EN_CURSO);
            assertThat(c.consultadaEn()).isEqualTo(AHORA);
        });
    }

    @Test
    @DisplayName("sin semana pedida se anota el lunes que el calculo resolvio (la semana en curso de la persona), no un nulo")
    void sinSemanaAnotaLaResuelta() {
        huecosDe(LIDER, ANA, null);

        assertThat(pedidosAlCalculo.getFirst()[1]).isNull();
        assertThat(banco.consultas).extracting(ConsultaDeFicha::semanaDesde).containsExactly(LUNES_EN_CURSO);
    }

    @Test
    @DisplayName("con una semana pedida se anota esa; cambiar de semana o repetirla es otra llamada y otra fila")
    void unaFilaPorLlamada() {
        huecosDe(LIDER, ANA, null);
        huecosDe(LIDER, ANA, LUNES_ANTERIOR);
        huecosDe(LIDER, ANA, LUNES_ANTERIOR);

        assertThat(banco.consultas).extracting(ConsultaDeFicha::semanaDesde)
                .containsExactly(LUNES_EN_CURSO, LUNES_ANTERIOR, LUNES_ANTERIOR);
        assertThat(banco.consultas).extracting(ConsultaDeFicha::id).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Administracion y Alquimista tambien la dejan, con su rol; el resumen al mentor no las cuenta despues")
    void administracionYAlquimistaTambien() {
        huecosDe(ADMIN, ANA, null);
        huecosDe(ALQUIMISTA, ANA, null);

        assertThat(banco.consultas).extracting(ConsultaDeFicha::rolConsultor)
                .containsExactly(UserRole.ADMIN, UserRole.ALCHEMIST);
        assertThat(ReglaDelResumenDeConsultas.resumir(DiaDelMentor.de(MENTORA, LIMA, AHORA), banco.consultas))
                .isEmpty();
    }

    @Test
    @DisplayName("un error de lectura no deja constancia: semana invalida, persona que el calculo no encuentra, cuenta suspendida")
    void unErrorDeLecturaNoLaDeja() {
        for (RuntimeException falla : List.of(new IllegalArgumentException("Esa semana todavia no llega"),
                new NoSuchElementException("Persona no encontrada"),
                new NotAuthorizedException("La cuenta esta suspendida"))) {
            fallaDelCalculo = falla;

            assertThatThrownBy(() -> huecosDe(LIDER, ANA, null)).isSameAs(falla);
        }

        assertThat(pedidosAlCalculo).hasSize(3);
        assertThat(banco.consultas).isEmpty();
    }

    @Test
    @DisplayName("un rol sin permiso, un lider suspendido, otro grupo o un grupo sin mentor no leen ni dejan constancia")
    void unRechazoNoDejaConstancia() {
        UserId otroMentor = id("a2");
        banco.usuario(otroMentor, "Otro Mentor", UserRole.MENTOR, UserStatus.ACTIVE);
        banco.usuario(AJENA, "Ana de otro grupo", UserRole.TRAINEE, UserStatus.ACTIVE);
        banco.programa(AJENA, UserRole.TRAINEE, LocalDate.of(2026, 8, 17), true);

        // 403: un MENTOR (el interceptor lo deja pasar, A-1), un aprendiz y Soporte TI.
        for (UserId actor : List.of(otroMentor, MENTORA, ANA, SOPORTE)) {
            assertThatThrownBy(() -> huecosDe(actor, ANA, null)).isInstanceOf(NotAuthorizedException.class);
        }
        // 403: el lider y Administracion con la cuenta suspendida.
        banco.usuario(LIDER, "Lider Uno", UserRole.MENTOR_LEAD, UserStatus.SUSPENDED);
        banco.usuario(ADMIN, "Admin", UserRole.ADMIN, UserStatus.SUSPENDED);
        assertThatThrownBy(() -> huecosDe(LIDER, ANA, null)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> huecosDe(ADMIN, ANA, null)).isInstanceOf(NotAuthorizedException.class);
        // 404: una persona que hoy no es del grupo, una que no existe y un grupo sin mentor.
        assertThatThrownBy(() -> huecosDe(ALQUIMISTA, AJENA, null)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> huecosDe(ALQUIMISTA, id("ff"), null)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> servicio.huecosDe(new ConsultaHuecosDePersona(ALQUIMISTA, SIN_MENTOR, ANA.value(),
                null))).isInstanceOf(NoSuchElementException.class);

        assertThat(pedidosAlCalculo).isEmpty();
        assertThat(banco.consultas).isEmpty();
    }

    @Test
    @DisplayName("si no se puede dejar la constancia, no se entrega la lectura (igual que la ficha de la semana)")
    void sinConstanciaNoHayLectura() {
        IllegalStateException sinBase = new IllegalStateException("no se pudo insertar");
        HuecosDePersonaService conBaseCaida = servicioCon(consulta -> {
            throw sinBase;
        });

        assertThatThrownBy(() -> conBaseCaida.huecosDe(new ConsultaHuecosDePersona(LIDER, FENIX, ANA.value(), null)))
                .isSameAs(sinBase);
    }

    @Test
    @DisplayName("lectura y constancia van en la misma transaccion: el caso de uso es @Transactional (no solo lectura)")
    void laMismaTransaccion() throws NoSuchMethodException {
        Transactional transaccional = HuecosDePersonaService.class
                .getMethod("huecosDe", ConsultaHuecosDePersona.class).getAnnotation(Transactional.class);

        assertThat(transaccional).isNotNull();
        assertThat(transaccional.readOnly()).isFalse();
    }

    // ── el resumen del mentor ───────────────────────────────────────────────

    @Test
    @DisplayName("el resumen de las 20:00 cuenta una vez a la persona que el lider abrio por la semana y por los huecos el mismo dia")
    void elResumenNoCuentaDosVeces() {
        banco.aprendiz(FENIX, LUIS, "Luis Vega");
        SemanaDePersonaService semana = banco.semanaService();
        semana.semanaDe(new ConsultaSemanaDePersona(LIDER, FENIX, ANA.value(), null));
        huecosDe(LIDER, ANA, null);
        huecosDe(LIDER, LUIS, null);
        huecosDe(LIDER, ANA, LUNES_ANTERIOR);

        assertThat(banco.consultas).hasSize(4);
        assertThat(ReglaDelResumenDeConsultas.resumir(DiaDelMentor.de(MENTORA, LIMA, AHORA), banco.consultas))
                .get().satisfies(resumen -> {
                    // Ana una sola vez (dos veces por los huecos y una por la semana), y Luis, que solo abrio huecos.
                    assertThat(resumen.personas()).containsExactly(ANA, LUIS);
                    assertThat(resumen.grupoDeLaRuta()).isEqualTo(FENIX);
                });
    }

    @Test
    @DisplayName("solo los huecos del lider ya bastan para que el mentor reciba el resumen del dia")
    void soloHuecosTambienAvisan() {
        huecosDe(LIDER, ANA, null);

        assertThat(ReglaDelResumenDeConsultas.resumir(DiaDelMentor.de(MENTORA, LIMA, AHORA), banco.consultas))
                .get().satisfies(resumen -> assertThat(resumen.personas()).containsExactly(ANA));
    }
}

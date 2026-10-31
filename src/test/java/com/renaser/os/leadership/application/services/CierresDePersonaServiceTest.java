package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.ConsultarCierresDePersonaUseCase.ConsultaCierresDePersona;
import com.renaser.os.leadership.application.ports.out.GuardarConsultaDeFichaPort;
import com.renaser.os.leadership.domain.model.consulta.ConsultaDeFicha;
import com.renaser.os.leadership.domain.model.consulta.ReglaDelResumenDeConsultas;
import com.renaser.os.leadership.domain.model.consulta.ReglaDelResumenDeConsultas.DiaDelMentor;
import com.renaser.os.rocks.api.CierresDelDiaFinder;
import com.renaser.os.rocks.api.CierresDelDiaFinder.CierresDeLaSemana;
import com.renaser.os.rocks.api.CierresDelDiaFinder.ResumenDeCierres;
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
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.FENIX;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.LIMA;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.SIN_MENTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Los cierres del día de una persona para el líder (spec 017) y la constancia de la consulta: la misma que deja la ficha de la semana
 * (spec 005, FR-016) y los huecos (spec 015, FR-027), en la misma tabla. Lo que se prueba es lo del líder: a quién deja ver, qué
 * registra y cuándo NO registra. La lectura de los cierres es de {@code rocks} y acá es un doble.
 */
class CierresDePersonaServiceTest {

    private static final UserId LIDER = id("c1");
    private static final UserId ADMIN = id("d1");
    private static final UserId ALQUIMISTA = id("d2");
    private static final UserId SOPORTE = id("d3");
    private static final UserId MENTORA = id("a1");
    private static final UserId ANA = id("b1");
    private static final UserId AJENA = id("b9");
    /** El lunes de la semana en curso de Ana a {@link BancoDeCumplimiento#AHORA} (lunes 19 de octubre, 10:00 en Lima). */
    private static final LocalDate LUNES_EN_CURSO = LocalDate.of(2026, 10, 19);
    private static final LocalDate LUNES_ANTERIOR = LocalDate.of(2026, 10, 12);

    private final BancoDeCumplimiento banco = new BancoDeCumplimiento();
    private final List<Object[]> pedidosALaLectura = new ArrayList<>();
    private RuntimeException fallaDeLaLectura;
    private CierresDePersonaService servicio;

    private final CierresDelDiaFinder lectura = (persona, lunes) -> {
        pedidosALaLectura.add(new Object[]{persona, lunes});
        if (fallaDeLaLectura != null) {
            throw fallaDeLaLectura;
        }
        // Como la lectura real: sin lunes, la semana en curso de la persona; con uno, ese.
        return new CierresDeLaSemana(lunes == null ? LUNES_EN_CURSO : lunes, LocalDate.of(2026, 8, 17), List.of(),
                new ResumenDeCierres(0, 0, List.of()));
    };

    private static UserId id(String sufijo) {
        return UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000" + sufijo));
    }

    private CierresDePersonaService servicioCon(GuardarConsultaDeFichaPort consultas) {
        return new CierresDePersonaService(banco.acceso(), banco.personas(), banco.programa(), lectura, consultas, banco.ids,
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
    }

    private CierresDeLaSemana cierresDe(UserId actor, UserId persona, LocalDate semana) {
        return servicio.cierresDe(new ConsultaCierresDePersona(actor, FENIX, persona.value(), semana));
    }

    @Test
    @DisplayName("el lider recibe lo que leyo rocks, para esa persona y la semana pedida")
    void devuelveLoQueLeeRocks() {
        CierresDeLaSemana cierres = cierresDe(LIDER, ANA, LUNES_ANTERIOR);

        assertThat(cierres.semana()).isEqualTo(LUNES_ANTERIOR);
        assertThat(pedidosALaLectura).singleElement().satisfies(pedido -> {
            assertThat(pedido[0]).isEqualTo(ANA);
            assertThat(pedido[1]).isEqualTo(LUNES_ANTERIOR);
        });
    }

    @Test
    @DisplayName("leer los cierres registra quien, con que rol, a quien, de que grupo, a que mentor, el lunes que miro y cuando")
    void registraLaConsulta() {
        cierresDe(LIDER, ANA, null);

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
    @DisplayName("sin semana pedida se anota el lunes que la lectura resolvio, no un nulo; cada llamada es una fila")
    void unaFilaPorLlamada() {
        cierresDe(LIDER, ANA, null);
        cierresDe(LIDER, ANA, LUNES_ANTERIOR);

        assertThat(pedidosALaLectura.getFirst()[1]).isNull();
        assertThat(banco.consultas).extracting(ConsultaDeFicha::semanaDesde).containsExactly(LUNES_EN_CURSO, LUNES_ANTERIOR);
        assertThat(banco.consultas).extracting(ConsultaDeFicha::id).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Administracion y Alquimista tambien la dejan, con su rol; el resumen de las 20:00 solo cuenta al lider")
    void administracionYAlquimistaTambien() {
        cierresDe(ADMIN, ANA, null);
        cierresDe(ALQUIMISTA, ANA, null);

        assertThat(banco.consultas).extracting(ConsultaDeFicha::rolConsultor).containsExactly(UserRole.ADMIN, UserRole.ALCHEMIST);
        assertThat(ReglaDelResumenDeConsultas.resumir(DiaDelMentor.de(MENTORA, LIMA, AHORA), banco.consultas)).isEmpty();
    }

    @Test
    @DisplayName("solo los cierres del lider ya bastan para que el mentor reciba el resumen del dia, con la persona una sola vez")
    void elResumenDelMentor() {
        cierresDe(LIDER, ANA, null);
        cierresDe(LIDER, ANA, LUNES_ANTERIOR);

        assertThat(ReglaDelResumenDeConsultas.resumir(DiaDelMentor.de(MENTORA, LIMA, AHORA), banco.consultas))
                .get().satisfies(resumen -> assertThat(resumen.personas()).containsExactly(ANA));
    }

    @Test
    @DisplayName("un error de lectura no deja constancia: semana invalida, persona que no se encuentra, cuenta suspendida")
    void unErrorDeLecturaNoLaDeja() {
        for (RuntimeException falla : List.of(new IllegalArgumentException("Esa semana todavia no llega"),
                new NoSuchElementException("Persona no encontrada"), new NotAuthorizedException("Cuenta suspendida"))) {
            fallaDeLaLectura = falla;

            assertThatThrownBy(() -> cierresDe(LIDER, ANA, null)).isSameAs(falla);
        }

        assertThat(pedidosALaLectura).hasSize(3);
        assertThat(banco.consultas).isEmpty();
    }

    @Test
    @DisplayName("un rol sin permiso, un lider suspendido, otro grupo o un grupo sin mentor no leen ni dejan constancia")
    void unRechazoNoDejaConstancia() {
        UserId otroMentor = id("a2");
        banco.usuario(otroMentor, "Otro Mentor", UserRole.MENTOR, UserStatus.ACTIVE);
        banco.usuario(AJENA, "Ana de otro grupo", UserRole.TRAINEE, UserStatus.ACTIVE);
        banco.programa(AJENA, UserRole.TRAINEE, LocalDate.of(2026, 8, 17), true);

        // 403: un MENTOR (el interceptor lo deja pasar, A-1), el mentor del propio grupo, un aprendiz y Soporte TI.
        for (UserId actor : List.of(otroMentor, MENTORA, ANA, SOPORTE)) {
            assertThatThrownBy(() -> cierresDe(actor, ANA, null)).isInstanceOf(NotAuthorizedException.class);
        }
        // 403: el lider y Administracion con la cuenta suspendida.
        banco.usuario(LIDER, "Lider Uno", UserRole.MENTOR_LEAD, UserStatus.SUSPENDED);
        banco.usuario(ADMIN, "Admin", UserRole.ADMIN, UserStatus.SUSPENDED);
        assertThatThrownBy(() -> cierresDe(LIDER, ANA, null)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> cierresDe(ADMIN, ANA, null)).isInstanceOf(NotAuthorizedException.class);
        // 404: una persona que hoy no es del grupo, una que no existe y un grupo sin mentor.
        assertThatThrownBy(() -> cierresDe(ALQUIMISTA, AJENA, null)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> cierresDe(ALQUIMISTA, id("ff"), null)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> servicio.cierresDe(new ConsultaCierresDePersona(ALQUIMISTA, SIN_MENTOR, ANA.value(), null)))
                .isInstanceOf(NoSuchElementException.class);

        assertThat(pedidosALaLectura).isEmpty();
        assertThat(banco.consultas).isEmpty();
    }

    @Test
    @DisplayName("si no se puede dejar la constancia, no se entrega la lectura")
    void sinConstanciaNoHayLectura() {
        IllegalStateException sinBase = new IllegalStateException("no se pudo insertar");
        CierresDePersonaService conBaseCaida = servicioCon(consulta -> {
            throw sinBase;
        });

        assertThatThrownBy(() -> conBaseCaida.cierresDe(new ConsultaCierresDePersona(LIDER, FENIX, ANA.value(), null)))
                .isSameAs(sinBase);
    }

    @Test
    @DisplayName("lectura y constancia van en la misma transaccion: el caso de uso es @Transactional (no solo lectura)")
    void laMismaTransaccion() throws NoSuchMethodException {
        Transactional transaccional = CierresDePersonaService.class
                .getMethod("cierresDe", ConsultaCierresDePersona.class).getAnnotation(Transactional.class);

        assertThat(transaccional).isNotNull();
        assertThat(transaccional.readOnly()).isFalse();
    }
}

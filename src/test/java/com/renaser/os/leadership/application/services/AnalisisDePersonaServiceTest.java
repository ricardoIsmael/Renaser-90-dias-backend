package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.ConsultarAnalisisDePersonaUseCase.ConsultaAnalisisDePersona;
import com.renaser.os.leadership.application.ports.in.ConsultarAnalisisDePersonaUseCase.ConsultaAnalisisDeUnaPersona;
import com.renaser.os.leadership.application.ports.out.GuardarConsultaDeFichaPort;
import com.renaser.os.leadership.domain.model.consulta.ConsultaDeFicha;
import com.renaser.os.rag.api.Analisis8020Finder;
import com.renaser.os.rag.api.Analisis8020Finder.Tipo;
import com.renaser.os.rag.api.Analisis8020Finder.VistaDelAnalisis;
import com.renaser.os.rocks.api.Pareto8020Finder.CifrasDel8020;
import com.renaser.os.rocks.api.Pareto8020Finder.ReglaDeLasVerdes;
import com.renaser.os.rocks.api.Pareto8020Finder.ResumenDelCierre;
import com.renaser.os.shared.application.InterruptoresDePrueba;
import com.renaser.os.shared.domain.ClavesDeFuncionalidad;
import com.renaser.os.shared.domain.FuncionalidadApagadaException;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.AHORA;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.FENIX;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.SIN_MENTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El 80/20 y el análisis de una persona para el líder (spec 031, 031-2 A): a quién deja ver, el interruptor «Código Renaser en las fichas» de
 * la spec 034 (el líder no, Administración y Alquimista sí) y la constancia de la consulta, la misma tabla que el resto de la ficha. La lectura
 * es de {@code rag} y acá es un doble.
 */
class AnalisisDePersonaServiceTest {

    private static final UserId LIDER = id("c1");
    private static final UserId ADMIN = id("d1");
    private static final UserId ALQUIMISTA = id("d2");
    private static final UserId SOPORTE = id("d3");
    private static final UserId MENTORA = id("a1");
    private static final UserId ANA = id("b1");
    private static final UserId AJENA = id("b9");
    private static final LocalDate INICIO_EN_CURSO = LocalDate.of(2026, 10, 19);
    private static final LocalDate INICIO_ANTERIOR = LocalDate.of(2026, 10, 12);

    private final BancoDeCumplimiento banco = new BancoDeCumplimiento();
    private final List<Object[]> pedidosALaLectura = new ArrayList<>();
    private RuntimeException fallaDeLaLectura;

    private final Analisis8020Finder lectura = new Analisis8020Finder() {
        @Override
        public VistaDelAnalisis de(UserId personaId, Tipo tipo, LocalDate inicio) {
            pedidosALaLectura.add(new Object[]{personaId, tipo, inicio});
            if (fallaDeLaLectura != null) {
                throw fallaDeLaLectura;
            }
            LocalDate resuelto = inicio == null ? INICIO_EN_CURSO : inicio;
            return new VistaDelAnalisis(tipo, 10, resuelto, resuelto.plusDays(6), false, null, null, vacia(resuelto), List.of(),
                    "EN_CURSO", List.of(), 5, null, null, null);
        }

        @Override
        public java.util.Optional<AnalisisLeido> ultimoListo(UserId personaId, Tipo tipo) {
            return java.util.Optional.empty();
        }
    };

    private static UserId id(String sufijo) {
        return UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000" + sufijo));
    }

    private static CifrasDel8020 vacia(LocalDate desde) {
        return new CifrasDel8020(desde, desde.plusDays(6), 0, 0, 0, 0, 0, 0, null, new ReglaDeLasVerdes(0, 0, 0, null), null, List.of(),
                List.of(), List.of(), false, new ResumenDelCierre(0, 0, List.of(), List.of()));
    }

    private AnalisisDePersonaService servicioCon(GuardarConsultaDeFichaPort consultas, InterruptoresDePrueba interruptores) {
        return new AnalisisDePersonaService(banco.acceso(), banco.personas(), banco.programa(), lectura, interruptores, consultas, banco.ids,
                banco.reloj);
    }

    private AnalisisDePersonaService servicio;

    @BeforeEach
    void preparar() {
        servicio = servicioCon(banco.guardarConsulta, InterruptoresDePrueba.conApagadas());
        banco.usuario(LIDER, "Lider Uno", UserRole.MENTOR_LEAD, UserStatus.ACTIVE);
        banco.usuario(ADMIN, "Admin", UserRole.ADMIN, UserStatus.ACTIVE);
        banco.usuario(ALQUIMISTA, "Alquimista", UserRole.ALCHEMIST, UserStatus.ACTIVE);
        banco.usuario(SOPORTE, "Soporte TI", UserRole.IT_SUPPORT, UserStatus.ACTIVE);
        banco.grupoConMentor(FENIX, "Grupo Fénix", MENTORA, "Luisa Rojas");
        banco.aprendiz(FENIX, ANA, "Ana Pérez");
    }

    private VistaDelAnalisis analisisDe(UserId actor, UserId persona, LocalDate inicio) {
        return servicio.analisisDe(new ConsultaAnalisisDePersona(actor, FENIX, persona.value(), Tipo.SEMANA, inicio));
    }

    @Test
    @DisplayName("el líder, Administración y Alquimista leen el análisis de una persona de un grupo con mentor")
    void losTresRolesLoLeen() {
        for (UserId actor : List.of(LIDER, ADMIN, ALQUIMISTA)) {
            assertThat(analisisDe(actor, ANA, INICIO_ANTERIOR).inicio()).isEqualTo(INICIO_ANTERIOR);
        }

        assertThat(pedidosALaLectura).hasSize(3).allSatisfy(pedido -> assertThat(pedido[0]).isEqualTo(ANA));
    }

    @Test
    @DisplayName("FR-021: leer el análisis registra quién, con qué rol, a quién, de qué grupo, a qué mentor, el periodo que miró y cuándo")
    void registraLaConsulta() {
        analisisDe(LIDER, ANA, null);

        assertThat(banco.consultas).singleElement().satisfies((ConsultaDeFicha c) -> {
            assertThat(c.consultorId()).isEqualTo(LIDER);
            assertThat(c.rolConsultor()).isEqualTo(UserRole.MENTOR_LEAD);
            assertThat(c.personaId()).isEqualTo(ANA);
            assertThat(c.grupoId()).isEqualTo(FENIX);
            assertThat(c.mentorId()).isEqualTo(MENTORA);
            assertThat(c.semanaDesde()).isEqualTo(INICIO_EN_CURSO);
            assertThat(c.consultadaEn()).isEqualTo(AHORA);
        });
    }

    @Test
    @DisplayName("Administración y Alquimista también dejan constancia, con su rol")
    void administracionYAlquimistaTambien() {
        analisisDe(ADMIN, ANA, null);
        analisisDe(ALQUIMISTA, ANA, null);

        assertThat(banco.consultas).extracting(ConsultaDeFicha::rolConsultor).containsExactly(UserRole.ADMIN, UserRole.ALCHEMIST);
    }

    @Test
    @DisplayName("sin pasar por un grupo (spec 021) lee lo mismo y deja la misma constancia")
    void sinGrupo() {
        banco.aprendizSinGrupo(AJENA, "Persona sin grupo");

        VistaDelAnalisis vista = servicio.analisisDeUnaPersona(new ConsultaAnalisisDeUnaPersona(LIDER, AJENA.value(), Tipo.MES, null));

        assertThat(vista.tipo()).isEqualTo(Tipo.MES);
        assertThat(banco.consultas).singleElement().satisfies(c -> {
            assertThat(c.personaId()).isEqualTo(AJENA);
            assertThat(c.grupoId()).isNull();
        });
    }

    @Test
    @DisplayName("034 / 031-2 A: con «Código Renaser en las fichas» apagado el líder recibe 404 (sin leer ni dejar constancia); Administración y Alquimista siguen leyendo")
    void conElInterruptorApagado() {
        servicio = servicioCon(banco.guardarConsulta, InterruptoresDePrueba.conApagadas(ClavesDeFuncionalidad.RADAR_EN_FICHAS));

        assertThatThrownBy(() -> analisisDe(LIDER, ANA, null)).isInstanceOf(FuncionalidadApagadaException.class);
        assertThat(pedidosALaLectura).isEmpty();
        assertThat(banco.consultas).isEmpty();
    }

    @Test
    @DisplayName("un rol sin permiso, un líder suspendido, otro grupo o un grupo sin mentor no leen ni dejan constancia")
    void unRechazoNoDejaConstancia() {
        UserId otroMentor = id("a2");
        banco.usuario(otroMentor, "Otro Mentor", UserRole.MENTOR, UserStatus.ACTIVE);
        banco.usuario(AJENA, "Ana de otro grupo", UserRole.TRAINEE, UserStatus.ACTIVE);
        banco.programa(AJENA, UserRole.TRAINEE, LocalDate.of(2026, 8, 17), true);

        for (UserId actor : List.of(otroMentor, MENTORA, ANA, SOPORTE)) {
            assertThatThrownBy(() -> analisisDe(actor, ANA, null)).isInstanceOf(NotAuthorizedException.class);
        }
        banco.usuario(LIDER, "Lider Uno", UserRole.MENTOR_LEAD, UserStatus.SUSPENDED);
        assertThatThrownBy(() -> analisisDe(LIDER, ANA, null)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> analisisDe(ALQUIMISTA, AJENA, null)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> servicio.analisisDe(new ConsultaAnalisisDePersona(ALQUIMISTA, SIN_MENTOR, ANA.value(), Tipo.SEMANA, null)))
                .isInstanceOf(NoSuchElementException.class);

        assertThat(pedidosALaLectura).isEmpty();
        assertThat(banco.consultas).isEmpty();
    }

    @Test
    @DisplayName("un error de lectura (periodo inválido, persona sin el programa activado) no deja constancia")
    void unErrorDeLecturaNoLaDeja() {
        for (RuntimeException falla : List.of(new IllegalArgumentException("Ese periodo todavía no llega"),
                new NoSuchElementException("Todavía no activó su programa"))) {
            fallaDeLaLectura = falla;
            assertThatThrownBy(() -> analisisDe(LIDER, ANA, null)).isSameAs(falla);
        }

        assertThat(banco.consultas).isEmpty();
    }

    @Test
    @DisplayName("si no se puede dejar la constancia, no se entrega la lectura, y las dos van en la misma transacción")
    void sinConstanciaNoHayLectura() throws NoSuchMethodException {
        IllegalStateException sinBase = new IllegalStateException("no se pudo insertar");
        AnalisisDePersonaService conBaseCaida = servicioCon(consulta -> {
            throw sinBase;
        }, InterruptoresDePrueba.conApagadas());

        assertThatThrownBy(() -> conBaseCaida.analisisDe(new ConsultaAnalisisDePersona(LIDER, FENIX, ANA.value(), Tipo.SEMANA, null)))
                .isSameAs(sinBase);
        Transactional transaccional = AnalisisDePersonaService.class.getMethod("analisisDe", ConsultaAnalisisDePersona.class)
                .getAnnotation(Transactional.class);
        assertThat(transaccional).isNotNull();
        assertThat(transaccional.readOnly()).isFalse();
    }
}

package com.renaser.os.leadership.application.services;

import com.renaser.os.evidence.api.EntregaDeEvidencia;
import com.renaser.os.evidence.api.EntregasPorRegistroFinder;
import com.renaser.os.habits.api.CumplimientoDelDiaFinder;
import com.renaser.os.habits.api.CumplimientoDelDiaFinder.RegistroDelDia;
import com.renaser.os.habits.api.EstadoObligacion;
import com.renaser.os.habits.api.HorarioDelDiaFinder;
import com.renaser.os.habits.api.HorarioDelDiaFinder.HorarioResuelto;
import com.renaser.os.habits.api.HorarioDelDiaFinder.HorariosDelDia;
import com.renaser.os.leadership.application.ports.in.ConsultarHabitosDeTodosUseCase.ConsultaHabitosDeTodos;
import com.renaser.os.leadership.application.ports.in.ConsultarHabitosDeTodosUseCase.ConsultaPersonasDeUnHabito;
import com.renaser.os.leadership.application.ports.in.ConsultarHabitosDeTodosUseCase.FilaDePersona;
import com.renaser.os.leadership.application.ports.in.ConsultarHabitosDeTodosUseCase.HabitosDeTodos;
import com.renaser.os.leadership.application.ports.in.ConsultarHabitosDeTodosUseCase.PersonasDeUnHabito;
import com.renaser.os.leadership.application.services.PersonasDelPrograma.Alumno;
import com.renaser.os.leadership.domain.model.cumplimiento.GrupoDeLaPersona;
import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.FichaDeMentorFinder;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Spec 029: «Hábitos de todos». Las cifras, el orden, los filtros, la ventana, los permisos y el alcance de formación. */
@ExtendWith(MockitoExtension.class)
class HabitosDeTodosServiceTest {

    /** 2026-10-31 15:00Z = 10:00 en Lima. */
    private static final Instant AHORA = Instant.parse("2026-10-31T15:00:00Z");
    private static final LocalDate HOY = LocalDate.of(2026, 10, 31);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Mock
    private UserSummaryFinder usuarios;
    @Mock
    private FichaDeMentorFinder fichas;
    @Mock
    private AlcanceDeFormacionFinder formaciones;
    @Mock
    private PersonasDelPrograma programa;
    @Mock
    private CumplimientoDelDiaFinder cumplimiento;
    @Mock
    private HorarioDelDiaFinder horarios;
    @Mock
    private EntregasPorRegistroFinder entregas;
    @Mock
    private ParticipacionProgramaFinder participaciones;

    private final UserId lider = UserId.of(UUID.randomUUID());
    private final UserId ana = UserId.of(UUID.randomUUID());
    private final UserId beto = UserId.of(UUID.randomUUID());
    private final UserId carla = UserId.of(UUID.randomUUID());
    private final UUID despertar = UUID.randomUUID();
    private final UUID agua = UUID.randomUUID();
    private final UUID fenix = UUID.randomUUID();
    private HabitosDeTodosService servicio;

    @BeforeEach
    void preparar() {
        servicio = servicioA(AHORA);
        quien(lider, UserRole.MENTOR_LEAD, UserStatus.ACTIVE);
        lenient().when(formaciones.de(lider)).thenReturn(AlcanceDeFormacion.de(Formacion.ESCUELA));
        lenient().when(programa.alumnos(any(), any())).thenReturn(List.of(alumno(ana, "Ana", fenix), alumno(beto, "Beto", fenix), alumno(carla, "Carla", null)));
        lenient().when(entregas.porRegistros(any())).thenReturn(java.util.Map.of());
        lenient().when(horarios.deFecha(any(), any())).thenReturn(new HorariosDelDia(HOY, 21, List.of(), null));
    }

    private HabitosDeTodosService servicioA(Instant ahora) {
        return new HabitosDeTodosService(new AccesoDeLiderazgo(usuarios, fichas, formaciones), programa, cumplimiento, horarios,
                entregas, participaciones, formaciones, FixedClock.at(ahora));
    }

    private void quien(UserId id, UserRole rol, UserStatus estado) {
        lenient().when(usuarios.findById(id)).thenReturn(Optional.of(new UserSummary(id, "Alguien", null, rol, estado)));
    }

    private static Alumno alumno(UserId id, String nombre, UUID grupo) {
        UserSummary perfil = new UserSummary(id, nombre, null, UserRole.TRAINEE, UserStatus.ACTIVE);
        return new Alumno(perfil, grupo == null ? null : new GrupoDeLaPersona(grupo, "Fénix", null, false));
    }

    private RegistroDelDia registro(UserId persona, UUID habito, String titulo, EstadoObligacion estado, boolean opcional) {
        return new RegistroDelDia(UUID.randomUUID(), persona, habito, titulo, "CUERPO", "BODY", "ESCUELA", estado, opcional,
                estado.cumplido() ? Instant.parse("2026-10-31T11:42:00Z") : null);
    }

    private void registros(RegistroDelDia... registros) {
        lenient().when(cumplimiento.deVariasPersonas(any(), any(), any())).thenReturn(List.of(registros));
    }

    // ── Permisos ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("un mentor, una guía, un aprendiz, Soporte TI o un líder suspendido reciben 403 y no se lee nada (SC-007)")
    void soloElLiderazgo() {
        for (UserRole rol : List.of(UserRole.MENTOR, UserRole.TRAINEE_GUIDE, UserRole.TRAINEE, UserRole.IT_SUPPORT)) {
            UserId actor = UserId.of(UUID.randomUUID());
            quien(actor, rol, UserStatus.ACTIVE);
            assertThatThrownBy(() -> servicio.resumen(new ConsultaHabitosDeTodos(actor, null, null, null, null)))
                    .as(rol.name()).isInstanceOf(NotAuthorizedException.class);
            assertThatThrownBy(() -> servicio.personas(new ConsultaPersonasDeUnHabito(actor, despertar, null, null, null, null, 0, 30)))
                    .as(rol.name()).isInstanceOf(NotAuthorizedException.class);
        }
        UserId suspendido = UserId.of(UUID.randomUUID());
        quien(suspendido, UserRole.MENTOR_LEAD, UserStatus.SUSPENDED);
        assertThatThrownBy(() -> servicio.resumen(new ConsultaHabitosDeTodos(suspendido, null, null, null, null)))
                .isInstanceOf(NotAuthorizedException.class);
        verifyNoInteractions(programa, cumplimiento, horarios, entregas);
    }

    @Test
    @DisplayName("Administración y Alquimista usan la misma lectura")
    void adminYAlquimista() {
        for (UserRole rol : List.of(UserRole.ADMIN, UserRole.ALCHEMIST)) {
            UserId actor = UserId.of(UUID.randomUUID());
            quien(actor, rol, UserStatus.ACTIVE);
            when(formaciones.de(actor)).thenReturn(AlcanceDeFormacion.todas());
            registros();

            assertThat(servicio.resumen(new ConsultaHabitosDeTodos(actor, null, null, null, null)).habitos()).isEmpty();
        }
    }

    // ── Las cifras y el orden ───────────────────────────────────────────────

    @Test
    @DisplayName("por hábito: cuántos cumplieron sobre cuántos les tocaba; el de menos cumplimiento va primero")
    void cifrasYOrden() {
        registros(registro(ana, despertar, "Despertar", EstadoObligacion.COMPLETADO, false),
                registro(beto, despertar, "Despertar", EstadoObligacion.PENDIENTE, false),
                registro(ana, agua, "Agua", EstadoObligacion.PENDIENTE, false),
                registro(beto, agua, "Agua", EstadoObligacion.EN_CURSO, false),
                registro(carla, agua, "Agua", EstadoObligacion.COMPLETADO, false));

        HabitosDeTodos dia = servicio.resumen(new ConsultaHabitosDeTodos(lider, null, null, null, null));

        assertThat(dia.fecha()).isEqualTo(HOY);
        assertThat(dia.esHoy()).isTrue();
        assertThat(dia.habitos()).extracting(h -> h.titulo()).containsExactly("Agua", "Despertar");
        assertThat(dia.habitos().get(0)).satisfies(agua -> {
            assertThat(agua.esperados()).isEqualTo(3);
            assertThat(agua.cumplieron()).isEqualTo(1);
            assertThat(agua.pendientes()).isEqualTo(2);
            assertThat(agua.sinCumplir()).isZero();
        });
        assertThat(dia.totales().alumnos()).isEqualTo(3);
        assertThat(dia.totales().conHabitos()).isEqualTo(3);
        assertThat(dia.totales().esperados()).isEqualTo(5);
        assertThat(dia.totales().cumplieron()).isEqualTo(2);
    }

    @Test
    @DisplayName("un día cerrado: lo no cumplido es «sin cumplir» (vencido), y hoy es «pendiente»; el estado lo dice el propio registro")
    void pendienteOSinCumplir() {
        registros(registro(ana, despertar, "Despertar", EstadoObligacion.EXPIRADO, false),
                registro(beto, despertar, "Despertar", EstadoObligacion.FALLIDO, false),
                registro(carla, despertar, "Despertar", EstadoObligacion.PENDIENTE, false));

        var despertarDelDia = servicio.resumen(new ConsultaHabitosDeTodos(lider, null, null, null, null)).habitos().get(0);

        assertThat(despertarDelDia.sinCumplir()).isEqualTo(2);
        assertThat(despertarDelDia.pendientes()).isEqualTo(1);
    }

    @Test
    @DisplayName("lo opcional que no se hizo no es una falta: no entra a lo esperado, y un hábito solo con eso no aparece (FR-007, FR-008)")
    void opcionalNoCuenta() {
        registros(registro(ana, despertar, "Despertar", EstadoObligacion.COMPLETADO, false),
                registro(beto, despertar, "Despertar", EstadoObligacion.EXPIRADO, true),
                registro(ana, agua, "Agua", EstadoObligacion.EXPIRADO, true));

        HabitosDeTodos dia = servicio.resumen(new ConsultaHabitosDeTodos(lider, null, null, null, null));

        assertThat(dia.habitos()).extracting(h -> h.titulo()).containsExactly("Despertar");
        assertThat(dia.habitos().get(0).esperados()).isEqualTo(1);
        assertThat(dia.totales().sinCumplir()).isZero();
    }

    @Test
    @DisplayName("un hábito que a nadie le toca ese día no aparece; sin registros, la lista viene vacía (no un error)")
    void sinRegistros() {
        registros();

        HabitosDeTodos dia = servicio.resumen(new ConsultaHabitosDeTodos(lider, null, null, null, null));

        assertThat(dia.habitos()).isEmpty();
        assertThat(dia.totales().alumnos()).isEqualTo(3);
        assertThat(dia.totales().conHabitos()).isZero();
    }

    // ── Filtros ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("filtrar por grupo (y «sin grupo») y por categoría ajusta los alumnos y los hábitos")
    void filtros() {
        registros(registro(ana, despertar, "Despertar", EstadoObligacion.COMPLETADO, false));

        servicio.resumen(new ConsultaHabitosDeTodos(lider, null, fenix.toString(), null, null));
        ArgumentCaptor<Collection<UserId>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(cumplimiento).deVariasPersonas(ids.capture(), eq(HOY), any());
        assertThat(ids.getValue()).containsExactlyInAnyOrder(ana, beto);

        assertThat(servicio.resumen(new ConsultaHabitosDeTodos(lider, null, "none", null, null)).totales().alumnos()).isEqualTo(1);
        assertThat(servicio.resumen(new ConsultaHabitosDeTodos(lider, null, null, null, "MENTE")).habitos()).isEmpty();
        assertThat(servicio.resumen(new ConsultaHabitosDeTodos(lider, null, null, null, "CUERPO")).habitos()).hasSize(1);
    }

    // ── Formación (spec 022/023) ────────────────────────────────────────────

    @Test
    @DisplayName("todo se lee con el alcance de formación de quien pide: los alumnos, los registros y el filtro «formación»")
    void alcanceDeFormacion() {
        registros();
        servicio.resumen(new ConsultaHabitosDeTodos(lider, null, null, null, null));
        servicio.resumen(new ConsultaHabitosDeTodos(lider, null, null, Formacion.DIPLOMADO, null));

        ArgumentCaptor<AlcanceDeFormacion> alcances = ArgumentCaptor.forClass(AlcanceDeFormacion.class);
        verify(programa, org.mockito.Mockito.times(2)).alumnos(alcances.capture(), any());
        assertThat(alcances.getAllValues().get(0)).isEqualTo(AlcanceDeFormacion.de(Formacion.ESCUELA));
        // Pedir una formación que el líder no ve deja el alcance vacío: no revela nada.
        assertThat(alcances.getAllValues().get(1).esVacio()).isTrue();
        ArgumentCaptor<AlcanceDeFormacion> paraRegistros = ArgumentCaptor.forClass(AlcanceDeFormacion.class);
        verify(cumplimiento, org.mockito.Mockito.times(2)).deVariasPersonas(any(), any(), paraRegistros.capture());
        assertThat(paraRegistros.getAllValues().get(0)).isEqualTo(AlcanceDeFormacion.de(Formacion.ESCUELA));
    }

    @Test
    @DisplayName("un líder con dos formaciones la elige con el filtro; el alcance «todas» de Administración también se acota")
    void filtroDeFormacion() {
        UserId admin = UserId.of(UUID.randomUUID());
        quien(admin, UserRole.ADMIN, UserStatus.ACTIVE);
        when(formaciones.de(admin)).thenReturn(AlcanceDeFormacion.todas());
        registros();

        servicio.resumen(new ConsultaHabitosDeTodos(admin, null, null, Formacion.CERTIFICACION, null));

        verify(programa).alumnos(eq(AlcanceDeFormacion.de(Formacion.CERTIFICACION)), any());
    }

    // ── La ventana (FR-011, SC-006) ─────────────────────────────────────────

    @Test
    @DisplayName("se puede mirar hoy y hasta 7 días atrás; ni el futuro ni más viejo (400)")
    void ventana() {
        registros();

        assertThat(servicio.resumen(new ConsultaHabitosDeTodos(lider, HOY.minusDays(7), null, null, null)).esHoy()).isFalse();
        assertThatThrownBy(() -> servicio.resumen(new ConsultaHabitosDeTodos(lider, HOY.plusDays(1), null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.resumen(new ConsultaHabitosDeTodos(lider, HOY.minusDays(8), null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("con el reloj en la madrugada UTC todavía es el día anterior en Lima: «hoy» es ese día y el de «mañana» UTC es futuro")
    void madrugadaUtc() {
        HabitosDeTodosService de3AM = servicioA(Instant.parse("2026-10-31T03:00:00Z"));
        registros();

        HabitosDeTodos dia = de3AM.resumen(new ConsultaHabitosDeTodos(lider, null, null, null, null));

        assertThat(dia.fecha()).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(dia.esHoy()).isTrue();
        assertThatThrownBy(() -> de3AM.resumen(new ConsultaHabitosDeTodos(lider, LocalDate.of(2026, 10, 31), null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Las personas de un hábito ───────────────────────────────────────────

    @Test
    @DisplayName("las personas de un hábito: primero a quien hay que escribirle, luego quienes cumplieron y al final quienes no tenían nada que medir")
    void personasDeUnHabito() {
        RegistroDelDia deAna = registro(ana, despertar, "Despertar", EstadoObligacion.COMPLETADO, false);
        registros(deAna, registro(beto, despertar, "Despertar", EstadoObligacion.EXPIRADO, false));
        for (UserId persona : List.of(ana, beto, carla)) {
            when(participaciones.deParticipante(persona)).thenReturn(Optional.of(programaDe(persona, HOY.minusDays(20), true)));
            lenient().when(horarios.deFecha(eq(persona), eq(HOY))).thenReturn(new HorariosDelDia(HOY, 21, List.of(
                    new HorarioResuelto(despertar, "Despertar", LocalTime.of(6, 0), null, false, false, false, false, null)), null));
        }
        UUID evidencia = UUID.randomUUID();
        when(entregas.porRegistros(any())).thenReturn(java.util.Map.of(deAna.registroId(),
                new EntregaDeEvidencia(deAna.registroId(), evidencia, Instant.EPOCH, null, 1)));

        PersonasDeUnHabito resultado = servicio.personas(new ConsultaPersonasDeUnHabito(lider, despertar, null, null, null, null, 0, 30));

        assertThat(resultado.habito().esperados()).isEqualTo(2);
        assertThat(resultado.total()).isEqualTo(3);
        assertThat(resultado.personas()).extracting(FilaDePersona::nombre).containsExactly("Beto", "Ana", "Carla");
        assertThat(resultado.personas()).extracting(FilaDePersona::estado).containsExactly("SIN_CUMPLIR", "CUMPLIDO", "SIN_DATOS");
        FilaDePersona filaDeAna = resultado.personas().get(1);
        assertThat(filaDeAna.cumplioA()).isEqualTo("06:42");
        assertThat(filaDeAna.evidenciaId()).isEqualTo(evidencia);
        assertThat(filaDeAna.horaPlanificada()).isEqualTo(LocalTime.of(6, 0));
        assertThat(resultado.personas().get(2).motivo()).isEqualTo("NO_LE_TOCABA");
    }

    @Test
    @DisplayName("quien todavía no empezaba (antes de su Día 1) o no lo tenía elegido dice «antes del Día 1», nunca un incumplimiento")
    void antesDelDiaUno() {
        registros(registro(ana, despertar, "Despertar", EstadoObligacion.COMPLETADO, false));
        when(participaciones.deParticipante(ana)).thenReturn(Optional.of(programaDe(ana, HOY.minusDays(20), true)));
        when(participaciones.deParticipante(beto)).thenReturn(Optional.of(programaDe(beto, HOY.plusDays(2), true)));
        when(participaciones.deParticipante(carla)).thenReturn(Optional.of(programaDe(carla, HOY.minusDays(3), false)));

        PersonasDeUnHabito resultado = servicio.personas(new ConsultaPersonasDeUnHabito(lider, despertar, null, null, null, null, 0, 30));

        assertThat(resultado.personas()).filteredOn(f -> f.estado().equals("SIN_DATOS")).extracting(FilaDePersona::motivo)
                .containsOnly("ANTES_DEL_DIA_1");
    }

    @Test
    @DisplayName("buscar por nombre (sin tildes ni mayúsculas) y paginar; la hora planificada solo se pide de la página")
    void busquedaYPaginas() {
        registros(registro(ana, despertar, "Despertar", EstadoObligacion.COMPLETADO, false));
        lenient().when(participaciones.deParticipante(any())).thenReturn(Optional.empty());

        PersonasDeUnHabito busqueda = servicio.personas(new ConsultaPersonasDeUnHabito(lider, despertar, null, null, null, "ANA", 0, 30));
        assertThat(busqueda.personas()).extracting(FilaDePersona::nombre).containsExactly("Ana");

        PersonasDeUnHabito pagina = servicio.personas(new ConsultaPersonasDeUnHabito(lider, despertar, null, null, null, null, 1, 1));
        assertThat(pagina.total()).isEqualTo(3);
        assertThat(pagina.personas()).hasSize(1);
        verify(horarios, never()).deFecha(eq(carla), any());
    }

    @Test
    @DisplayName("un hábito que nadie tiene ese día en este alcance (o de otra formación) responde 404, igual que uno inexistente (SC-003)")
    void habitoDeOtraFormacion() {
        registros(registro(ana, agua, "Agua", EstadoObligacion.COMPLETADO, false));

        assertThatThrownBy(() -> servicio.personas(new ConsultaPersonasDeUnHabito(lider, despertar, null, null, null, null, 0, 30)))
                .isInstanceOf(NoSuchElementException.class);
    }

    private static ParticipacionPrograma programaDe(UserId id, LocalDate inicio, boolean activado) {
        return new ParticipacionPrograma(id, true, 10, inicio, LIMA, FasePrograma.PHASE_1_REBIRTH, null, null, UserRole.TRAINEE,
                false, activado);
    }
}

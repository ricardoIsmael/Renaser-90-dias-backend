package com.renaser.os.community.application.services;

import com.renaser.os.community.api.ComposicionDeCelulaCambiadaEvent;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadAsignacionesPort;
import com.renaser.os.community.application.ports.out.acompanamiento.SaveAsignacionPort;
import com.renaser.os.community.application.ports.out.celula.ConsultarRecepcionVigentePort;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionCelula;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionId;
import com.renaser.os.community.domain.model.acompanamiento.FuncionAcompanamiento;
import com.renaser.os.community.domain.model.acompanamiento.MotivoAsignacion;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AsignacionCelulaPort;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La entrada automática al grupo de bienvenida.
 *
 * <p>Es lo único automático que queda del acompañamiento, así que lo que más importa aquí es
 * cuándo NO actúa: sobre alguien que no es aprendiz, sobre quien ya tiene grupo, y cuando no hay
 * recepción abierta.
 *
 * <p><b>Y qué escribe cuando SÍ actúa.</b> Hasta el 2026-09-14 esta prueba solo miraba el
 * {@code SaveAsignacionPort}, y por eso no vio nunca que entrar a un grupo son tres escrituras:
 * el intervalo, los punteros de proyección que lee la app, y el aviso que reconcilia el chat.
 * Faltaban las dos últimas —ver el javadoc de {@link IngresoARecepcionService}— y la bienvenida
 * automática escribía una fila que no leía nadie. Los casos de abajo cubren las tres juntas,
 * porque van juntas o no van.
 */
class IngresoARecepcionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-10T15:00:00Z");
    private static final CelulaId RECEPCION = CelulaId.of(UUID.randomUUID());

    private final List<AsignacionCelula> guardadas = new ArrayList<>();
    private final List<Sincronizacion> sincronizadas = new ArrayList<>();
    private final List<Object> publicados = new ArrayList<>();

    /** Lo que se escribió en `participantes_programa` para un aprendiz. */
    private record Sincronizacion(UserId aprendiz, UUID celulaId, UserId mentorId) {
    }

    private IngresoARecepcionService servicio(UserRole rol, Optional<CelulaId> recepcion,
                                               List<AsignacionCelula> yaTiene) {
        return servicio(rol, recepcion, yaTiene, List.of());
    }

    private IngresoARecepcionService servicio(UserRole rol, Optional<CelulaId> recepcion,
                                               List<AsignacionCelula> yaTiene,
                                               List<AsignacionCelula> delGrupo) {
        ConsultarRecepcionVigentePort recepcionPort = dia -> recepcion;
        LoadAsignacionesPort load = new LoadAsignacionesPort() {
            @Override
            public List<AsignacionCelula> porUsuario(UserId usuarioId) {
                return yaTiene;
            }

            @Override
            public List<AsignacionCelula> porCelula(CelulaId celulaId) {
                return delGrupo;
            }

            @Override
            public Optional<AsignacionCelula> porClaveOperacion(String claveOperacion) {
                return guardadas.stream().filter(a -> a.claveOperacion().equals(claveOperacion)).findFirst();
            }
        };
        SaveAsignacionPort save = a -> {
            guardadas.add(a);
            return a;
        };
        ParticipacionProgramaFinder finder = new FinderDeRol(rol);
        AsignacionCelulaPort punteros = new PunterosEspia(sincronizadas);
        return new IngresoARecepcionService(recepcionPort, load, save, finder, punteros,
                publicados::add, () -> UUID.randomUUID(), FixedClock.at(AHORA));
    }

    @Test
    @DisplayName("Un aprendiz recien registrado entra al grupo de recepcion vigente")
    void unAprendizEntra() {
        servicio(UserRole.TRAINEE, Optional.of(RECEPCION), List.of()).ingresar(UserId.of(UUID.randomUUID()));

        assertThat(guardadas).singleElement().satisfies(a -> {
            assertThat(a.celulaId()).isEqualTo(RECEPCION);
            assertThat(a.funcion()).isEqualTo(FuncionAcompanamiento.APRENDIZ);
            assertThat(a.motivo()).isEqualTo(MotivoAsignacion.RECEPCION);
        });
    }

    /**
     * La regresión que da sentido a toda esta corrección. `GET /me/cell` resuelve el grupo por
     * `participantes_programa.celula_id`, no por el historial: sin esta escritura la persona
     * entraba a la bienvenida y su app seguía diciendo que no tenía grupo.
     */
    @Test
    @DisplayName("Entrar sincroniza el puntero que lee la app, no solo el historial")
    void entrarSincronizaElPuntero() {
        UserId aprendiz = UserId.of(UUID.randomUUID());

        servicio(UserRole.TRAINEE, Optional.of(RECEPCION), List.of()).ingresar(aprendiz);

        assertThat(sincronizadas).singleElement().satisfies(s -> {
            assertThat(s.aprendiz()).isEqualTo(aprendiz);
            assertThat(s.celulaId()).isEqualTo(RECEPCION.value());
        });
    }

    /** El puntero de mentor sale del intervalo vigente del grupo, que es la fuente de verdad. */
    @Test
    @DisplayName("Si la bienvenida ya tiene mentor, el aprendiz queda apuntandolo")
    void entrarApuntaAlMentorDelGrupo() {
        UserId mentor = UserId.of(UUID.randomUUID());
        AsignacionCelula delMentor = AsignacionCelula.abrir(AsignacionId.of(UUID.randomUUID()),
                RECEPCION, mentor, FuncionAcompanamiento.MENTOR, AHORA.minusSeconds(86_400),
                MotivoAsignacion.ADMINISTRATIVO, null, "mentor-de-la-bienvenida");

        servicio(UserRole.TRAINEE, Optional.of(RECEPCION), List.of(), List.of(delMentor))
                .ingresar(UserId.of(UUID.randomUUID()));

        assertThat(sincronizadas).singleElement()
                .satisfies(s -> assertThat(s.mentorId()).isEqualTo(mentor));
    }

    /**
     * Una bienvenida sin mentor es válida (D-05). El puntero queda en null y no en cualquier cosa:
     * es quien decide a quién se le autoriza la evidencia del aprendiz.
     */
    @Test
    @DisplayName("Una bienvenida sin mentor deja el puntero de mentor en null")
    void sinMentorElPunteroQuedaVacio() {
        servicio(UserRole.TRAINEE, Optional.of(RECEPCION), List.of()).ingresar(UserId.of(UUID.randomUUID()));

        assertThat(sincronizadas).singleElement()
                .satisfies(s -> assertThat(s.mentorId()).isNull());
    }

    /** El chat reconcilia su lista de participantes con este aviso: sin publicarlo, quien entra
     * por la bienvenida no aparece en la conversación del grupo. */
    @Test
    @DisplayName("Entrar avisa del cambio de composicion para que el chat lo incorpore")
    void entrarAvisaAlChat() {
        servicio(UserRole.TRAINEE, Optional.of(RECEPCION), List.of()).ingresar(UserId.of(UUID.randomUUID()));

        assertThat(publicados).singleElement()
                .isInstanceOfSatisfying(ComposicionDeCelulaCambiadaEvent.class,
                        e -> assertThat(e.celulaId()).isEqualTo(RECEPCION.value()));
    }

    @Test
    @DisplayName("Un MENTOR que se registra NO entra: acompana, no cursa la bienvenida")
    void unMentorNoEntra() {
        servicio(UserRole.MENTOR, Optional.of(RECEPCION), List.of()).ingresar(UserId.of(UUID.randomUUID()));

        assertThat(guardadas).isEmpty();
        assertThat(sincronizadas).isEmpty();
        assertThat(publicados).isEmpty();
    }

    /**
     * Sin recepción abierta no se inventa nada. Es una tarea pendiente del administrador, no un
     * fallo del sistema — y el servicio deja un WARN para que no pase en silencio.
     */
    @Test
    @DisplayName("Sin grupo de recepcion vigente no se asigna a nadie")
    void sinRecepcionNoPasaNada() {
        servicio(UserRole.TRAINEE, Optional.empty(), List.of()).ingresar(UserId.of(UUID.randomUUID()));

        assertThat(guardadas).isEmpty();
        assertThat(sincronizadas).isEmpty();
        assertThat(publicados).isEmpty();
    }

    /**
     * Si el administrador ya lo coloco a mano, su decision manda sobre la automatica.
     *
     * <p>El motivo es ese y no la base. Hasta D-139 ademas chocaba contra
     * `asignaciones_un_grupo_por_aprendiz`, pero V56 levanto esa exclusion: hoy la base aceptaria
     * meterlo tambien en la recepcion. Quien impide pisarle el grupo que le pusieron a mano es
     * este guard, no el motor — y por eso esta prueba pasa a ser la unica red que queda.
     */
    @Test
    @DisplayName("Quien ya tiene grupo vivo no se toca")
    void conGrupoVivoNoSeToca() {
        UserId aprendiz = UserId.of(UUID.randomUUID());
        AsignacionCelula yaAsignado = AsignacionCelula.abrir(AsignacionId.of(UUID.randomUUID()),
                CelulaId.of(UUID.randomUUID()), aprendiz, FuncionAcompanamiento.APRENDIZ,
                AHORA.minusSeconds(3600), MotivoAsignacion.ADMINISTRATIVO, null, "puesto-a-mano");

        servicio(UserRole.TRAINEE, Optional.of(RECEPCION), List.of(yaAsignado)).ingresar(aprendiz);

        assertThat(guardadas).isEmpty();
        assertThat(sincronizadas).isEmpty();
        assertThat(publicados).isEmpty();
    }

    /**
     * El outbox de Modulith es at-least-once: el mismo registro puede reentregarse. La clave se
     * deriva del USUARIO y no del instante, asi que la segunda vuelta no abre otro intervalo — ni
     * vuelve a escribir los punteros ni a avisar al chat.
     */
    @Test
    @DisplayName("Reentregar el mismo registro no abre un segundo intervalo")
    void reentregarNoDuplica() {
        UserId aprendiz = UserId.of(UUID.randomUUID());
        IngresoARecepcionService servicio = servicio(UserRole.TRAINEE, Optional.of(RECEPCION), List.of());

        servicio.ingresar(aprendiz);
        servicio.ingresar(aprendiz);

        assertThat(guardadas).hasSize(1);
        assertThat(sincronizadas).hasSize(1);
        assertThat(publicados).hasSize(1);
    }

    /** Espía de los punteros de `participantes_programa`: anota lo que se le pide escribir. */
    private record PunterosEspia(List<Sincronizacion> anotadas) implements AsignacionCelulaPort {

        @Override
        public void asignarCelula(UserId actorId, UserId traineeId, UUID celulaId) {
            throw new AssertionError("El ingreso automatico no tiene actor humano: va por "
                    + "sincronizarAcompanamiento, no por asignarCelula");
        }

        @Override
        public void quitarCelula(UserId actorId, UserId traineeId) {
            throw new AssertionError("El ingreso a la bienvenida no quita a nadie de su grupo");
        }

        @Override
        public void sincronizarAcompanamiento(UserId traineeId, UUID celulaId, UserId mentorId) {
            anotadas.add(new Sincronizacion(traineeId, celulaId, mentorId));
        }
    }

    /** Doble mínimo del finder: solo se le pregunta el rol. */
    private record FinderDeRol(UserRole rol) implements ParticipacionProgramaFinder {

        @Override
        public Optional<ParticipacionPrograma> deParticipante(UserId participanteId) {
            // Orden del record: (id, inscrito, diaPrograma, fechaInicio, zona, FASE, celulaId,
            // mentorId, ROL, suspendido, activado). El rol va en la novena posicion, no en la sexta.
            return Optional.of(new ParticipacionPrograma(participanteId, true, 1, LocalDate.of(2026, 9, 1),
                    java.time.ZoneId.of("America/Lima"), com.renaser.os.users.api.FasePrograma.initial(),
                    null, null, rol, false, true));
        }

        @Override
        public List<UserId> miembrosActivosDeCelula(UUID celulaId) {
            return List.of();
        }

        @Override
        public List<UserId> miembrosDeCelula(UUID celulaId) {
            return List.of();
        }

        @Override
        public List<UserId> usuariosActivosConRol(java.util.Set<UserRole> roles) {
            return List.of();
        }

        @Override
        public List<UsuarioConDiaPrograma> usuariosActivosConDiaPrograma(java.util.Set<UserRole> roles) {
            return List.of();
        }

        @Override
        public List<UserId> participantesInscritosActivos() {
            return List.of();
        }

        @Override
        public int contarMiembrosDeCelula(UUID celulaId) {
            return 0;
        }
    }
}

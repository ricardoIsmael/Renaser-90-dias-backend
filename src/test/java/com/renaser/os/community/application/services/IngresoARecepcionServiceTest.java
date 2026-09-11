package com.renaser.os.community.application.services;

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
 */
class IngresoARecepcionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-10T15:00:00Z");
    private static final CelulaId RECEPCION = CelulaId.of(UUID.randomUUID());

    private final List<AsignacionCelula> guardadas = new ArrayList<>();

    private IngresoARecepcionService servicio(UserRole rol, Optional<CelulaId> recepcion,
                                               List<AsignacionCelula> yaTiene) {
        ConsultarRecepcionVigentePort recepcionPort = dia -> recepcion;
        LoadAsignacionesPort load = new LoadAsignacionesPort() {
            @Override
            public List<AsignacionCelula> porUsuario(UserId usuarioId) {
                return yaTiene;
            }

            @Override
            public List<AsignacionCelula> porCelula(CelulaId celulaId) {
                return List.of();
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
        return new IngresoARecepcionService(recepcionPort, load, save, finder,
                () -> UUID.randomUUID(), FixedClock.at(AHORA));
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

    @Test
    @DisplayName("Un MENTOR que se registra NO entra: acompana, no cursa la bienvenida")
    void unMentorNoEntra() {
        servicio(UserRole.MENTOR, Optional.of(RECEPCION), List.of()).ingresar(UserId.of(UUID.randomUUID()));

        assertThat(guardadas).isEmpty();
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
    }

    /**
     * Si el administrador ya lo coloco a mano, su decision manda sobre la automatica. Ademas,
     * insertar aqui chocaria contra `asignaciones_un_grupo_por_aprendiz`.
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
    }

    /**
     * El outbox de Modulith es at-least-once: el mismo registro puede reentregarse. La clave se
     * deriva del USUARIO y no del instante, asi que la segunda vuelta no abre otro intervalo.
     */
    @Test
    @DisplayName("Reentregar el mismo registro no abre un segundo intervalo")
    void reentregarNoDuplica() {
        UserId aprendiz = UserId.of(UUID.randomUUID());
        IngresoARecepcionService servicio = servicio(UserRole.TRAINEE, Optional.of(RECEPCION), List.of());

        servicio.ingresar(aprendiz);
        servicio.ingresar(aprendiz);

        assertThat(guardadas).hasSize(1);
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

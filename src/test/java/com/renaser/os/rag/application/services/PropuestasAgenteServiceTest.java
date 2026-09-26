package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase.PropuestaCreada;
import com.renaser.os.rag.application.ports.out.propuesta.LoadPropuestaAccionPort;
import com.renaser.os.rag.application.ports.out.propuesta.PropuestaModificadaEnParaleloException;
import com.renaser.os.rag.application.ports.out.propuesta.SavePropuestaAccionPort;
import com.renaser.os.rag.application.services.herramientas.AccionConfirmable;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.rag.domain.model.propuesta.EstadoPropuesta;
import com.renaser.os.rag.domain.model.propuesta.HuellaArgumentos;
import com.renaser.os.rag.domain.model.propuesta.PropuestaAccion;
import com.renaser.os.rag.domain.model.propuesta.PropuestaAccionId;
import com.renaser.os.rag.domain.model.propuesta.PropuestaNoDisponibleException;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reglas de {@link PropuestasAgenteService} sin Spring ni base: el repositorio en memoria imita el
 * bloqueo optimista (rechaza guardar sobre una version vieja), que es lo que sostiene el "una
 * sola ejecucion" ante un doble toque. La carrera contra Postgres real esta en
 * {@code PropuestaAccionPersistenceAdapterIT}.
 */
class PropuestasAgenteServiceTest {

    private static final String HERRAMIENTA = "marcar_habito_completado";
    private static final Duration VIGENCIA = Duration.ofMinutes(10);
    /** 02:00 UTC = dia anterior en Lima (regla 02): la propuesta no depende del dia local. */
    private static final Instant INICIO = Instant.parse("2026-09-23T02:00:00Z");

    private final UserId dueno = UserId.of(UUID.randomUUID());
    private final UserId otro = UserId.of(UUID.randomUUID());
    private final UserId suspendido = UserId.of(UUID.randomUUID());
    private final RelojMovible reloj = new RelojMovible(INICIO);
    private final RepositorioEnMemoria repositorio = new RepositorioEnMemoria();
    private final AtomicInteger ejecuciones = new AtomicInteger();
    private BiFunction<UserId, InvocacionHerramienta, ResultadoHerramienta> comportamiento;
    private PropuestasAgenteService service;

    @BeforeEach
    void setUp() {
        comportamiento = (actor, invocacion) -> ResultadoHerramienta.exito("Listo, marque Meditar.");
        AccionConfirmable accion = new AccionConfirmable() {
            @Override
            public String herramienta() {
                return HERRAMIENTA;
            }

            @Override
            public ResultadoHerramienta aplicar(UserId actorId, InvocacionHerramienta invocacion) {
                ejecuciones.incrementAndGet();
                return comportamiento.apply(actorId, invocacion);
            }
        };
        FakeUserSummaryFinder usuarios = new FakeUserSummaryFinder()
                .conActor(dueno, UserRole.TRAINEE)
                .conActor(otro, UserRole.TRAINEE)
                .conActor(suspendido, UserRole.TRAINEE, UserStatus.SUSPENDED);
        service = new PropuestasAgenteService(repositorio, repositorio, List.of(accion), usuarios, reloj,
                UUID::randomUUID, VIGENCIA,
                new com.renaser.os.rag.application.services.herramientas.PedidosDeEvidenciaDelTurno(reloj));
    }

    private PropuestaCreada proponer(UserId actor) {
        return service.proponer(actor, new InvocacionHerramienta(HERRAMIENTA, Map.of("registro_id", "r-1")),
                "Marcar Meditar como hecho");
    }

    @Test
    @DisplayName("proponer guarda una PENDIENTE que vence segun la vigencia configurada, sin ejecutar nada")
    void proponerNoEjecuta() {
        PropuestaCreada creada = proponer(dueno);

        assertThat(creada.venceEn()).isEqualTo(INICIO.plus(VIGENCIA));
        assertThat(repositorio.estadoDe(creada.id())).isEqualTo(EstadoPropuesta.PENDIENTE);
        assertThat(ejecuciones).hasValue(0);
    }

    @Test
    @DisplayName("confirmar ejecuta la accion una vez y guarda el resultado")
    void confirmarEjecuta() {
        PropuestaCreada creada = proponer(dueno);

        ResultadoHerramienta resultado = service.confirmar(dueno, creada.id());

        assertThat(resultado).isEqualTo(ResultadoHerramienta.exito("Listo, marque Meditar."));
        assertThat(ejecuciones).hasValue(1);
        assertThat(repositorio.estadoDe(creada.id())).isEqualTo(EstadoPropuesta.CONFIRMADA);
    }

    @Test
    @DisplayName("un segundo confirmar devuelve el mismo resultado sin volver a ejecutar")
    void confirmarEsIdempotente() {
        PropuestaCreada creada = proponer(dueno);
        service.confirmar(dueno, creada.id());

        ResultadoHerramienta segundo = service.confirmar(dueno, creada.id());

        assertThat(segundo).isEqualTo(ResultadoHerramienta.exito("Listo, marque Meditar."));
        assertThat(ejecuciones).hasValue(1);
    }

    @Test
    @DisplayName("doble toque en carrera: el que leyo la version vieja pierde y no ejecuta")
    void dobleToqueEnCarreraEjecutaUnaVez() {
        PropuestaCreada creada = proponer(dueno);
        PropuestaAccion leidaPorElSegundoToque = repositorio.porId(PropuestaAccionId.of(creada.id())).orElseThrow();
        service.confirmar(dueno, creada.id());
        repositorio.devolverUnaVez(leidaPorElSegundoToque);

        ResultadoHerramienta segundo = service.confirmar(dueno, creada.id());

        assertThat(ejecuciones).hasValue(1);
        assertThat(segundo).isEqualTo(ResultadoHerramienta.exito("Listo, marque Meditar."));
    }

    @Test
    @DisplayName("un toque que llega mientras la accion todavia corre avisa, no ejecuta otra vez")
    void toqueDuranteLaEjecucion() {
        PropuestaCreada creada = proponer(dueno);
        List<ResultadoHerramienta> delSegundoToque = new ArrayList<>();
        comportamiento = (actor, invocacion) -> {
            delSegundoToque.add(service.confirmar(dueno, creada.id()));
            return ResultadoHerramienta.exito("Listo.");
        };

        service.confirmar(dueno, creada.id());

        assertThat(ejecuciones).hasValue(1);
        assertThat(delSegundoToque).containsExactly(
                ResultadoHerramienta.exito(PropuestasAgenteService.MENSAJE_EN_EJECUCION));
    }

    @Test
    @DisplayName("si el negocio rechaza al ejecutar, queda FALLIDA con el motivo y no se reintenta")
    void rechazoDelNegocio() {
        comportamiento = (actor, invocacion) -> ResultadoHerramienta.fallo("Ese habito ya vencio.");
        PropuestaCreada creada = proponer(dueno);

        assertThat(service.confirmar(dueno, creada.id())).isEqualTo(ResultadoHerramienta.fallo("Ese habito ya vencio."));
        assertThat(service.confirmar(dueno, creada.id())).isEqualTo(ResultadoHerramienta.fallo("Ese habito ya vencio."));
        assertThat(repositorio.estadoDe(creada.id())).isEqualTo(EstadoPropuesta.FALLIDA);
        assertThat(ejecuciones).hasValue(1);
    }

    @Test
    @DisplayName("una excepcion de la accion no deja la propuesta colgada: queda FALLIDA con un mensaje legible")
    void excepcionDeLaAccion() {
        comportamiento = (actor, invocacion) -> {
            throw new IllegalStateException("detalle interno");
        };
        PropuestaCreada creada = proponer(dueno);

        ResultadoHerramienta resultado = service.confirmar(dueno, creada.id());

        assertThat(resultado).isEqualTo(ResultadoHerramienta.fallo(PropuestasAgenteService.MENSAJE_ERROR_INESPERADO));
        assertThat(repositorio.estadoDe(creada.id())).isEqualTo(EstadoPropuesta.FALLIDA);
    }

    @Test
    @DisplayName("la propuesta de otra persona es 403 y no ejecuta")
    void propuestaAjena() {
        PropuestaCreada creada = proponer(dueno);

        assertThatThrownBy(() -> service.confirmar(otro, creada.id())).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.cancelar(otro, creada.id())).isInstanceOf(NotAuthorizedException.class);
        assertThat(ejecuciones).hasValue(0);
        assertThat(repositorio.estadoDe(creada.id())).isEqualTo(EstadoPropuesta.PENDIENTE);
    }

    @Test
    @DisplayName("una cuenta suspendida no confirma ni siquiera su propia propuesta")
    void cuentaSuspendida() {
        PropuestaCreada creada = proponer(suspendido);

        assertThatThrownBy(() -> service.confirmar(suspendido, creada.id()))
                .isInstanceOf(NotAuthorizedException.class);
        assertThat(ejecuciones).hasValue(0);
    }

    @Test
    @DisplayName("una propuesta vencida se rechaza con mensaje legible y sigue PENDIENTE")
    void vencida() {
        PropuestaCreada creada = proponer(dueno);
        reloj.avanzar(VIGENCIA);

        assertThatThrownBy(() -> service.confirmar(dueno, creada.id()))
                .isInstanceOf(PropuestaNoDisponibleException.class)
                .hasMessageContaining("vencio");
        assertThat(ejecuciones).hasValue(0);
        assertThat(repositorio.estadoDe(creada.id())).isEqualTo(EstadoPropuesta.PENDIENTE);
    }

    @Test
    @DisplayName("una cancelada ya no se confirma")
    void canceladaNoSeConfirma() {
        PropuestaCreada creada = proponer(dueno);
        service.cancelar(dueno, creada.id());

        assertThatThrownBy(() -> service.confirmar(dueno, creada.id()))
                .isInstanceOf(PropuestaNoDisponibleException.class);
        assertThat(ejecuciones).hasValue(0);
        assertThat(repositorio.estadoDe(creada.id())).isEqualTo(EstadoPropuesta.CANCELADA);
    }

    @Test
    @DisplayName("una inexistente es NoSuchElement (404)")
    void inexistente() {
        assertThatThrownBy(() -> service.confirmar(dueno, UUID.randomUUID()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("argumentos alterados en la base: no ejecuta y queda FALLIDA")
    void argumentosAlterados() {
        PropuestaCreada creada = proponer(dueno);
        repositorio.alterarArgumentos(creada.id(), Map.of("registro_id", "otro-registro"));

        ResultadoHerramienta resultado = service.confirmar(dueno, creada.id());

        assertThat(resultado).isEqualTo(ResultadoHerramienta.fallo(PropuestasAgenteService.MENSAJE_ALTERADA));
        assertThat(ejecuciones).hasValue(0);
        assertThat(repositorio.estadoDe(creada.id())).isEqualTo(EstadoPropuesta.FALLIDA);
    }

    @Test
    @DisplayName("sin accion registrada para la herramienta: Fallo legible, sin excepcion")
    void sinAccion() {
        PropuestaCreada creada = service.proponer(dueno, InvocacionHerramienta.sinArgumentos("herramienta_retirada"),
                "Algo que ya no existe");

        ResultadoHerramienta resultado = service.confirmar(dueno, creada.id());

        assertThat(resultado).isEqualTo(ResultadoHerramienta.fallo(PropuestasAgenteService.MENSAJE_SIN_ACCION));
        assertThat(repositorio.estadoDe(creada.id())).isEqualTo(EstadoPropuesta.FALLIDA);
    }

    @Test
    @DisplayName("cancelar es idempotente, pero no deshace una ya confirmada")
    void cancelar() {
        PropuestaCreada aCancelar = proponer(dueno);
        service.cancelar(dueno, aCancelar.id());
        service.cancelar(dueno, aCancelar.id());
        PropuestaCreada confirmada = proponer(dueno);
        service.confirmar(dueno, confirmada.id());

        assertThat(repositorio.estadoDe(aCancelar.id())).isEqualTo(EstadoPropuesta.CANCELADA);
        assertThatThrownBy(() -> service.cancelar(dueno, confirmada.id()))
                .isInstanceOf(PropuestaNoDisponibleException.class);
    }

    @Test
    @DisplayName("las propuestas del turno son las pendientes y vigentes del actor desde el inicio del turno")
    void pendientesDelTurno() {
        PropuestaCreada anterior = proponer(dueno);
        reloj.avanzar(Duration.ofMinutes(1));
        Instant inicioDelTurno = reloj.now();
        PropuestaCreada delTurno = proponer(dueno);
        PropuestaCreada cancelada = proponer(dueno);
        service.cancelar(dueno, cancelada.id());
        proponer(otro);

        assertThat(service.pendientesCreadasDesde(dueno, inicioDelTurno))
                .extracting(PropuestaCreada::id).containsExactly(delTurno.id());
        assertThat(anterior.id()).isNotEqualTo(delTurno.id());

        reloj.avanzar(VIGENCIA);
        assertThat(service.pendientesCreadasDesde(dueno, inicioDelTurno)).isEmpty();
    }

    /** Reloj que avanza a mano: el vencimiento es derivado y hay que poder cruzarlo. */
    private static final class RelojMovible implements Clock {

        private Instant ahora;

        RelojMovible(Instant inicio) {
            this.ahora = inicio;
        }

        void avanzar(Duration cuanto) {
            ahora = ahora.plus(cuanto);
        }

        @Override
        public Instant now() {
            return ahora;
        }

        @Override
        public LocalDate today() {
            return ahora.atZone(ZoneOffset.UTC).toLocalDate();
        }
    }

    /**
     * Guarda copias (el agregado es mutable) y rechaza una version vieja, igual que {@code @Version}.
     * {@link #devolverUnaVez} simula la lectura de un segundo toque hecha antes de que el primero
     * escribiera.
     */
    private static final class RepositorioEnMemoria implements LoadPropuestaAccionPort, SavePropuestaAccionPort {

        private final Map<PropuestaAccionId, PropuestaAccion> filas = new HashMap<>();
        private PropuestaAccion lecturaVieja;

        void devolverUnaVez(PropuestaAccion vieja) {
            lecturaVieja = vieja;
        }

        EstadoPropuesta estadoDe(UUID id) {
            return filas.get(PropuestaAccionId.of(id)).estado();
        }

        void alterarArgumentos(UUID id, Map<String, String> argumentos) {
            PropuestaAccion p = filas.get(PropuestaAccionId.of(id));
            filas.put(p.id(), PropuestaAccion.rehidratar(p.id(), p.participanteId(),
                    new InvocacionHerramienta(p.invocacion().nombre(), argumentos), p.huella(), p.resumen(),
                    p.estado(), p.creadaEn(), p.venceEn(), p.resueltaEn(), p.resultado(), p.version()));
        }

        @Override
        public Optional<PropuestaAccion> porId(PropuestaAccionId id) {
            if (lecturaVieja != null && lecturaVieja.id().equals(id)) {
                PropuestaAccion vieja = lecturaVieja;
                lecturaVieja = null;
                return Optional.of(copia(vieja, vieja.version()));
            }
            return Optional.ofNullable(filas.get(id)).map(p -> copia(p, p.version()));
        }

        @Override
        public List<PropuestaAccion> pendientesCreadasDesde(UserId participanteId, Instant desde) {
            return filas.values().stream()
                    .filter(p -> p.participanteId().equals(participanteId))
                    .filter(p -> p.estado() == EstadoPropuesta.PENDIENTE && !p.creadaEn().isBefore(desde))
                    .sorted(Comparator.comparing(PropuestaAccion::creadaEn))
                    .map(p -> copia(p, p.version()))
                    .toList();
        }

        @Override
        public PropuestaAccion save(PropuestaAccion propuesta) {
            PropuestaAccion actual = filas.get(propuesta.id());
            Long versionActual = actual == null ? null : actual.version();
            if (!java.util.Objects.equals(versionActual, propuesta.version())) {
                throw new PropuestaModificadaEnParaleloException("version vieja", null);
            }
            PropuestaAccion guardada = copia(propuesta, versionActual == null ? 0L : versionActual + 1);
            filas.put(guardada.id(), guardada);
            return copia(guardada, guardada.version());
        }

        private static PropuestaAccion copia(PropuestaAccion p, Long version) {
            return PropuestaAccion.rehidratar(p.id(), p.participanteId(), p.invocacion(),
                    new HuellaArgumentos(p.huella().valor()), p.resumen(), p.estado(), p.creadaEn(), p.venceEn(),
                    p.resueltaEn(), p.resultado(), version);
        }
    }
}

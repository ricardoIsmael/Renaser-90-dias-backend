package com.renaser.os.onboarding.application.services.pagos;

import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.estado.LoadEstadoOnboardingPort;
import com.renaser.os.onboarding.application.ports.out.estado.SaveEstadoOnboardingPort;
import com.renaser.os.onboarding.application.ports.out.mapa.EtapaOnboardingPort;
import com.renaser.os.onboarding.application.ports.out.pagos.CuotasDePagoPort;
import com.renaser.os.onboarding.domain.model.estado.EstadoOnboarding;
import com.renaser.os.onboarding.domain.model.pagos.Cuota;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Las salidas de los servicios de pagos en memoria: los tests ejercen el servicio y el dominio de verdad, sin simular cada llamada. Cada una se parece a su adaptador: las cuotas se leen
 * siempre por persona, y el estado se guarda y se lee tal cual.
 */
final class PagosEnMemoria {

    private PagosEnMemoria() {
    }

    static final class Estados implements LoadEstadoOnboardingPort, SaveEstadoOnboardingPort {
        final Map<UserId, EstadoOnboarding> filas = new HashMap<>();

        @Override
        public Optional<EstadoOnboarding> deUsuario(UserId usuarioId) {
            return Optional.ofNullable(filas.get(usuarioId));
        }

        @Override
        public ResumenEstadosOnboarding contarResumen(Set<UserId> sinContar) {
            return new ResumenEstadosOnboarding(filas.size(), 0, 0);
        }

        @Override
        public EstadoOnboarding guardar(EstadoOnboarding estado) {
            filas.put(estado.usuarioId(), estado);
            return estado;
        }
    }

    static final class Cuotas implements CuotasDePagoPort {
        final Map<UserId, Map<UUID, Cuota>> porPersona = new HashMap<>();
        int escrituras;

        @Override
        public List<Cuota> deLaPersona(UserId persona) {
            List<Cuota> lista = new ArrayList<>(porPersona.getOrDefault(persona, Map.of()).values());
            lista.sort(Comparator.comparing(Cuota::venceEl).thenComparing(Cuota::creadaEn).thenComparing(Cuota::id));
            return lista;
        }

        @Override
        public void guardar(UserId persona, Cuota cuota) {
            escrituras++;
            porPersona.computeIfAbsent(persona, p -> new HashMap<>()).put(cuota.id(), cuota);
        }

        @Override
        public void eliminar(UserId persona, UUID cuota) {
            escrituras++;
            porPersona.getOrDefault(persona, new HashMap<>()).remove(cuota);
        }

        @Override
        public void eliminarTodas(UserId persona) {
            escrituras++;
            porPersona.remove(persona);
        }
    }

    /** Las cuentas que existen: quién es de Administración y quién está suspendido. */
    static final class Actores implements ConsultarActorPort {
        private final Map<UserId, ActorOnboarding> cuentas = new HashMap<>();

        UserId cuenta(String nombre, boolean administra, boolean suspendida) {
            UserId id = UserId.of(UUID.randomUUID());
            cuentas.put(id, new ActorOnboarding(id, suspendida, nombre, administra));
            return id;
        }

        @Override
        public Optional<ActorOnboarding> deActor(UserId actorId) {
            return Optional.ofNullable(cuentas.get(actorId));
        }
    }

    static final class Etapas implements EtapaOnboardingPort {
        final Set<String> completadas = new HashSet<>();

        @Override
        public Set<String> flujosCompletados(UserId participanteId) {
            return completadas;
        }

        @Override
        public void marcarCompletada(UserId participanteId, String flujo) {
            completadas.add(flujo);
        }

        @Override
        public Optional<Instant> completadaEn(UserId participanteId, String flujo) {
            return Optional.empty();
        }
    }

    static final class Ids implements IdGenerator {
        private final AtomicInteger siguiente = new AtomicInteger();

        @Override
        public UUID newId() {
            return new UUID(0L, siguiente.incrementAndGet());
        }
    }
}

package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.mapa;

import com.renaser.os.onboarding.application.ports.out.mapa.EtapaOnboardingPort;
import com.renaser.os.onboarding.application.ports.out.mapa.LoadMapaPort;
import com.renaser.os.onboarding.application.ports.out.mapa.ReemplazarListaMapaPort;
import com.renaser.os.onboarding.domain.model.mapa.AccionesDelMapa;
import com.renaser.os.onboarding.domain.model.mapa.ProtocolosDelMapa;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
class MapaPersistenceAdapter implements LoadMapaPort, ReemplazarListaMapaPort, EtapaOnboardingPort {

    private final SpringDataAccionMapaRepository accionesRepository;
    private final SpringDataProtocoloReemplazoMapaRepository protocolosRepository;
    private final SpringDataEtapaOnboardingRepository etapasRepository;
    private final MapaPersistenceMapper mapper;
    private final Clock clock;

    MapaPersistenceAdapter(SpringDataAccionMapaRepository accionesRepository,
                            SpringDataProtocoloReemplazoMapaRepository protocolosRepository,
                            SpringDataEtapaOnboardingRepository etapasRepository, MapaPersistenceMapper mapper,
                            Clock clock) {
        this.accionesRepository = accionesRepository;
        this.protocolosRepository = protocolosRepository;
        this.etapasRepository = etapasRepository;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public AccionesDelMapa accionesDe(UserId participanteId) {
        return new AccionesDelMapa(accionesRepository.findByUsuarioIdOrderByCreadoEnAsc(participanteId.value())
                .stream().map(mapper::toDomain).toList());
    }

    @Override
    public ProtocolosDelMapa protocolosDe(UserId participanteId) {
        return new ProtocolosDelMapa(
                protocolosRepository.findByUsuarioIdOrderByCreadoEnAsc(participanteId.value())
                        .stream().map(mapper::toDomain).toList());
    }

    /**
     * Reemplaza el sistema de ejecucion entero, <b>conservando el habito que cada accion ya haya
     * generado</b>.
     *
     * <p>Sin esa conservacion, reeditar V06 despues de activar el mapa dejaria las acciones
     * apuntando a {@code null} y el proximo "Activar" crearia los habitos DE NUEVO — que es
     * exactamente lo que AC-07 no permite. La clave para reconocerlas entre el borrado y el alta es
     * {@code accionId}, el id que genera el cliente, no el UUID de la fila.
     */
    @Override
    public void reemplazarAcciones(UserId participanteId, AccionesDelMapa acciones) {
        Map<String, UUID> habitosYaVinculados = new HashMap<>();
        for (var existente : accionesRepository.findByUsuarioIdOrderByCreadoEnAsc(participanteId.value())) {
            if (existente.getHabitoId() != null) {
                habitosYaVinculados.put(existente.getAccionId(), existente.getHabitoId());
            }
        }
        accionesRepository.deleteByUsuarioId(participanteId.value());
        accionesRepository.flush();

        var entidades = acciones.acciones().stream().map(accion -> {
            var entidad = mapper.toEntity(accion);
            entidad.setHabitoId(habitosYaVinculados.get(accion.accionId()));
            return entidad;
        }).toList();
        accionesRepository.saveAll(entidades);
    }

    @Override
    public void reemplazarProtocolos(UserId participanteId, ProtocolosDelMapa protocolos) {
        protocolosRepository.deleteByUsuarioId(participanteId.value());
        protocolosRepository.flush();
        protocolosRepository.saveAll(protocolos.protocolos().stream().map(mapper::toEntity).toList());
    }

    @Override
    public Set<String> flujosCompletados(UserId participanteId) {
        return etapasRepository.findByClaveUsuarioId(participanteId.value()).stream()
                .map(e -> e.getClave().getFlujo())
                .collect(Collectors.toSet());
    }

    /** Idempotente: si la etapa ya estaba marcada, se conserva la fecha original. */
    @Override
    public void marcarCompletada(UserId participanteId, String flujo) {
        var clave = new EtapaOnboardingCompletadaJpaEntity.Clave(participanteId.value(), flujo);
        if (etapasRepository.existsById(clave)) {
            return;
        }
        etapasRepository.save(new EtapaOnboardingCompletadaJpaEntity(clave, clock.now()));
    }
}

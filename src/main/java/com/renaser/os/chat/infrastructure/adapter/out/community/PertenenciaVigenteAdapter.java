package com.renaser.os.chat.infrastructure.adapter.out.community;

import com.renaser.os.chat.application.ports.out.participante.PertenenciaVigentePort;
import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Traduce la pregunta del chat a la API pública de {@code community}.
 *
 * <p>Vive en infraestructura y no en application para que el caso de uso no importe otro
 * módulo: el servicio conoce {@link PertenenciaVigentePort}, y quién responde esa pregunta es
 * un detalle de cableado.
 */
@Component
class PertenenciaVigenteAdapter implements PertenenciaVigentePort {

    private final AcompanamientoFinder acompanamientoFinder;
    private final Clock clock;

    PertenenciaVigenteAdapter(AcompanamientoFinder acompanamientoFinder, Clock clock) {
        this.acompanamientoFinder = acompanamientoFinder;
        this.clock = clock;
    }

    @Override
    public boolean perteneceAlGrupo(UUID celulaId, UserId usuarioId) {
        return acompanamientoFinder.esIntegranteVigente(celulaId, usuarioId, clock.now());
    }

    @Override
    public List<UserId> integrantesDelGrupo(UUID celulaId) {
        return acompanamientoFinder.integrantesVigentes(celulaId, clock.now());
    }
}

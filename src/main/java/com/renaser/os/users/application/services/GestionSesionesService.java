package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.autenticacion.CerrarTodasLasSesionesUseCase;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/**
 * Revoca TODAS las sesiones activas de un usuario (cualquier instancia), no solo la de la
 * request actual — eso lo resuelve {@code SesionWebAdapter.cerrar}. Se apoya directo en
 * {@link FindByIndexNameSessionRepository} sin un puerto de indireccion propio: mismo criterio
 * que {@code PasswordEncoder} en {@code AutenticacionService} (docs/MODULO_AUTH.md §3) — no es
 * HTTP ni JPA, y Spring Session es la unica implementacion de "sesion" de este sistema (D-50),
 * asi que un puerto extra no compraria nada.
 *
 * <p>Requiere {@code spring.session.redis.repository-type: indexed} (ya configurado en
 * application.yaml): sin eso Redis no mantiene el indice por principal y
 * {@code findByPrincipalName} no encuentra nada.
 */
@Service
class GestionSesionesService implements CerrarTodasLasSesionesUseCase {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    GestionSesionesService(FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    public void cerrarTodas(UserId usuarioId) {
        // Bucle explicito, no forEach: borrar es un efecto y §5.4.7 los quiere a la vista
        // en el caso de uso, no escondidos dentro de una lambda.
        for (String sesionId : sessionRepository.findByPrincipalName(usuarioId.value().toString()).keySet()) {
            sessionRepository.deleteById(sesionId);
        }
    }
}

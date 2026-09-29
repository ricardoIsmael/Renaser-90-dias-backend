package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.conversacion.AbrirChatsConAcompananteUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.CompletarChatsDeAprendicesUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.RellenarConversacionesDeSoporteUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.RellenarConversacionesDeSoporteUseCase.ResultadoRelleno;
import com.renaser.os.chat.application.ports.out.participante.AcompanamientoDelGrupoPort;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Completa los chats que le faltan a los aprendices (D-224) reusando EXACTAMENTE los caminos que ya
 * los crean: el relleno del soporte de {@link ConversacionSoporteService} (sin bienvenida) y
 * {@link ChatsConAcompananteService#abrirParaGrupo}, el mismo que corre cuando cambia un grupo. Aquí no
 * se decide quién merece qué chat: eso ya lo deciden esos dos servicios, con sus reglas (solo cuentas
 * activas, solo inscritos, solo mentor y guías vigentes, sin staff en el chat de dos).
 *
 * <p>Sin {@code @Transactional}, a propósito (regla 02 §4): cada chat se crea en su propia transacción
 * dentro de esos servicios, y un grupo que falla se cuenta y no detiene a los demás.
 */
@Service
public class CompletarChatsDeAprendicesService implements CompletarChatsDeAprendicesUseCase {

    private static final Logger log = LoggerFactory.getLogger(CompletarChatsDeAprendicesService.class);

    private final RellenarConversacionesDeSoporteUseCase soportes;
    private final AbrirChatsConAcompananteUseCase chatsDeDos;
    private final AcompanamientoDelGrupoPort acompanamientoPort;

    public CompletarChatsDeAprendicesService(RellenarConversacionesDeSoporteUseCase soportes,
                                             AbrirChatsConAcompananteUseCase chatsDeDos,
                                             AcompanamientoDelGrupoPort acompanamientoPort) {
        this.soportes = soportes;
        this.chatsDeDos = chatsDeDos;
        this.acompanamientoPort = acompanamientoPort;
    }

    @Override
    public ResultadoCompletar completarTodos() {
        ResultadoRelleno relleno = soportes.rellenarPendientes();
        List<UUID> grupos = acompanamientoPort.gruposOperativos();
        ChatsAbiertos abiertos = abrirEn(grupos);
        return new ResultadoCompletar(relleno.creadas(), relleno.fallidas(), grupos.size(),
                abiertos.abiertos(), abiertos.fallidos());
    }

    @Override
    public void completarDe(UserId aprendizId) {
        if (soportes.rellenarDe(aprendizId)) {
            log.info("[chat.completar] soporte creado para {} al reactivarse su cuenta", aprendizId);
        }
        ChatsAbiertos abiertos = abrirEn(acompanamientoPort.gruposOperativosDe(aprendizId));
        if (abiertos.fallidos() > 0) {
            // El evento se da por atendido igual: el barrido horario reintenta lo que falte.
            log.warn("[chat.completar] {} grupo(s) de {} quedaron con chats de dos pendientes", abiertos.fallidos(), aprendizId);
        }
    }

    /** Un grupo que falla no puede detener el barrido (regla 02 §4). */
    private ChatsAbiertos abrirEn(List<UUID> grupos) {
        int abiertos = 0;
        int fallidos = 0;
        for (UUID grupo : grupos) {
            try {
                abiertos += chatsDeDos.abrirParaGrupo(grupo);
            } catch (RuntimeException e) {
                log.warn("[chat.completar] no se pudieron abrir los chats de dos del grupo {}", grupo, e);
                fallidos++;
            }
        }
        return new ChatsAbiertos(abiertos, fallidos);
    }

    private record ChatsAbiertos(int abiertos, int fallidos) {
    }
}

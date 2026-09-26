package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.conversacion.AbrirChatsConAcompananteUseCase;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.conversacion.SaveConversacionPort;
import com.renaser.os.chat.application.ports.out.participante.AcompanamientoDelGrupoPort;
import com.renaser.os.chat.application.ports.out.participante.AgregarParticipantePort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.Participante;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Abre el chat de dos entre cada aprendiz y quien lo acompaña (D-173): el mentor de su grupo o,
 * en la recepción de los primeros 7 días, cada guía. El staff administrativo no entra: ya tiene
 * su propio chat con cada aprendiz (D-136).
 *
 * <p><b>Una sola consulta para saber qué falta.</b> La recepción no tiene tope y cada ingreso
 * vuelve a mirar el grupo entero, así que preguntar pareja por pareja sería una consulta por
 * aprendiz y por guía en cada alta.
 *
 * <p><b>Cada chat en su transacción</b>, mismo criterio que {@code ConversacionService} (C-10) y
 * {@code ConversacionSoporteService}: si otro camino abrió el mismo chat en paralelo, el UNIQUE de
 * {@code clave_directa} gana y solo se deshace esa creación. Y un fallo con una pareja no deja
 * sin chat a las demás (.claude/rules/02).
 */
@Service
public class ChatsConAcompananteService implements AbrirChatsConAcompananteUseCase {

    private static final Logger log = LoggerFactory.getLogger(ChatsConAcompananteService.class);

    private final AcompanamientoDelGrupoPort acompanamientoPort;
    private final LoadConversacionPort loadConversacionPort;
    private final SaveConversacionPort saveConversacionPort;
    private final AgregarParticipantePort agregarParticipantePort;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final TransactionTemplate transaccionPropia;

    public ChatsConAcompananteService(AcompanamientoDelGrupoPort acompanamientoPort,
                                       LoadConversacionPort loadConversacionPort,
                                       SaveConversacionPort saveConversacionPort,
                                       AgregarParticipantePort agregarParticipantePort,
                                       Clock clock, IdGenerator idGenerator,
                                       PlatformTransactionManager transactionManager) {
        this.acompanamientoPort = acompanamientoPort;
        this.loadConversacionPort = loadConversacionPort;
        this.saveConversacionPort = saveConversacionPort;
        this.agregarParticipantePort = agregarParticipantePort;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.transaccionPropia = new TransactionTemplate(transactionManager);
        this.transaccionPropia.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
    }

    @Override
    public int abrirParaGrupo(UUID celulaId) {
        Map<String, Pareja> parejas = parejasDelGrupo(celulaId);
        if (parejas.isEmpty()) {
            return 0;
        }
        Set<String> yaAbiertas = loadConversacionPort.clavesDirectasExistentes(parejas.keySet());
        int abiertas = 0;
        for (Map.Entry<String, Pareja> entrada : parejas.entrySet()) {
            if (!yaAbiertas.contains(entrada.getKey()) && abrir(entrada.getKey(), entrada.getValue())) {
                abiertas++;
            }
        }
        return abiertas;
    }

    /** Cada aprendiz con cada acompañante, por clave. Si alguien fuera las dos cosas, no se habla solo. */
    private Map<String, Pareja> parejasDelGrupo(UUID celulaId) {
        List<UserId> acompanantes = acompanamientoPort.acompanantesVigentes(celulaId);
        Map<String, Pareja> parejas = new LinkedHashMap<>();
        if (acompanantes.isEmpty()) {
            return parejas;
        }
        for (UserId aprendiz : acompanamientoPort.aprendicesVigentes(celulaId)) {
            for (UserId acompanante : acompanantes) {
                if (!aprendiz.equals(acompanante)) {
                    parejas.put(Conversacion.claveDirectaDe(aprendiz, acompanante), new Pareja(aprendiz, acompanante));
                }
            }
        }
        return parejas;
    }

    /** @return {@code true} si la abrió; {@code false} si ya la había abierto otro o falló. */
    private boolean abrir(String clave, Pareja pareja) {
        try {
            transaccionPropia.executeWithoutResult(status -> {
                Conversacion guardada = saveConversacionPort.save(
                        Conversacion.crearDirecta(ConversacionId.of(idGenerator.newId()), clave, clock.now()));
                agregarParticipantePort.agregar(Participante.unirse(guardada.id(), pareja.aprendiz(), clock.now()));
                agregarParticipantePort.agregar(Participante.unirse(guardada.id(), pareja.acompanante(), clock.now()));
            });
            return true;
        } catch (DataIntegrityViolationException laAbrioOtroPrimero) {
            log.debug("[chat.acompanante] el chat {} ya existia al intentar abrirlo", clave);
            return false;
        } catch (RuntimeException e) {
            log.warn("[chat.acompanante] no se pudo abrir el chat entre {} y {}", pareja.aprendiz(), pareja.acompanante(), e);
            return false;
        }
    }

    private record Pareja(UserId aprendiz, UserId acompanante) {
    }
}

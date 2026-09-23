package com.renaser.os.rag.infrastructure.adapter.out.persistence.propuesta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.propuesta.EstadoPropuesta;
import com.renaser.os.rag.domain.model.propuesta.HuellaArgumentos;
import com.renaser.os.rag.domain.model.propuesta.PropuestaAccion;
import com.renaser.os.rag.domain.model.propuesta.PropuestaAccionId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Mapper a mano (regla 04): hay traduccion real — el mapa de argumentos va y viene como JSON, y
 * el estado es texto en la base y enum en el dominio.
 */
@Component
class PropuestaAccionPersistenceMapper {

    private static final TypeReference<Map<String, String>> MAPA_DE_TEXTO = new TypeReference<>() {
    };

    private final ObjectMapper json = new ObjectMapper();

    PropuestaAccion toDomain(PropuestaAccionJpaEntity e) {
        InvocacionHerramienta invocacion = new InvocacionHerramienta(e.getHerramienta(), leerArgumentos(e));
        return PropuestaAccion.rehidratar(PropuestaAccionId.of(e.getId()), UserId.of(e.getParticipanteId()),
                invocacion, new HuellaArgumentos(e.getArgumentosHash()), e.getResumen(),
                EstadoPropuesta.valueOf(e.getEstado()), e.getCreadaEn(), e.getVenceEn(), e.getResueltaEn(),
                e.getResultado(), e.getVersion());
    }

    PropuestaAccionJpaEntity toEntity(PropuestaAccion d) {
        return new PropuestaAccionJpaEntity(d.id().value(), d.participanteId().value(), d.invocacion().nombre(),
                escribirArgumentos(d.invocacion().argumentos()), d.huella().valor(), d.resumen(), d.estado().name(),
                d.creadaEn(), d.venceEn(), d.resueltaEn(), d.resultado(), d.version());
    }

    private Map<String, String> leerArgumentos(PropuestaAccionJpaEntity e) {
        try {
            return json.readValue(e.getArgumentos(), MAPA_DE_TEXTO);
        } catch (JsonProcessingException ex) {
            // Una fila que no se puede leer no se puede confirmar: que falle fuerte, no en silencio.
            throw new IllegalStateException("Argumentos ilegibles en la propuesta " + e.getId(), ex);
        }
    }

    private String escribirArgumentos(Map<String, String> argumentos) {
        try {
            return json.writeValueAsString(argumentos);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudieron serializar los argumentos de la propuesta", ex);
        }
    }
}

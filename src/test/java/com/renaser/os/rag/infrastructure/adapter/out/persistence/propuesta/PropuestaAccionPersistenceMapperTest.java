package com.renaser.os.rag.infrastructure.adapter.out.persistence.propuesta;

import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.propuesta.EstadoPropuesta;
import com.renaser.os.rag.domain.model.propuesta.PropuestaAccion;
import com.renaser.os.rag.domain.model.propuesta.PropuestaAccionId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PropuestaAccionPersistenceMapperTest {

    private final PropuestaAccionPersistenceMapper mapper = new PropuestaAccionPersistenceMapper();

    @Test
    @DisplayName("ida y vuelta: argumentos como JSON, estado como texto, huella y version intactas")
    void idaYVuelta() {
        PropuestaAccion propuesta = PropuestaAccion.crear(PropuestaAccionId.of(UUID.randomUUID()),
                UserId.of(UUID.randomUUID()),
                new InvocacionHerramienta("cambiar_horario", Map.of("hora", "07:00", "nota", "con \"comillas\"")),
                "Meditar a las 07:00", Instant.parse("2026-09-23T02:00:00Z"), Duration.ofMinutes(10));
        propuesta.confirmar(Instant.parse("2026-09-23T02:01:00Z"));

        PropuestaAccionJpaEntity entidad = mapper.toEntity(propuesta);
        entidad.setVersion(3L);
        PropuestaAccion vuelta = mapper.toDomain(entidad);

        assertThat(entidad.getEstado()).isEqualTo("CONFIRMADA");
        assertThat(vuelta.invocacion()).isEqualTo(propuesta.invocacion());
        assertThat(vuelta.argumentosIntegros()).isTrue();
        assertThat(vuelta.estado()).isEqualTo(EstadoPropuesta.CONFIRMADA);
        assertThat(vuelta.resueltaEn()).isEqualTo(propuesta.resueltaEn());
        assertThat(vuelta.version()).isEqualTo(3L);
    }

    @Test
    @DisplayName("una propuesta nunca guardada viaja con version null, para que Spring Data la inserte")
    void nuevaSinVersion() {
        PropuestaAccion propuesta = PropuestaAccion.crear(PropuestaAccionId.of(UUID.randomUUID()),
                UserId.of(UUID.randomUUID()), InvocacionHerramienta.sinArgumentos("pausar_habito"), "Pausar",
                Instant.parse("2026-09-23T02:00:00Z"), Duration.ofMinutes(10));

        assertThat(mapper.toEntity(propuesta).getVersion()).isNull();
        assertThat(mapper.toEntity(propuesta).getArgumentos()).isEqualTo("{}");
    }
}

package com.renaser.os.community.infrastructure.adapter.out.persistence.celula;

import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.celula.PeriodoGrupo;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las dos columnas de V48 cruzando el mapper. Es un POJO puro: no hace falta Postgres para
 * comprobar que las fechas no se pierden ni se inventan en el camino.
 */
class CelulaPersistenceMapperTest {

    private static final Instant AHORA = Instant.parse("2026-09-10T03:00:00Z");
    private static final PeriodoGrupo SEPTIEMBRE = new PeriodoGrupo(
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

    private final CelulaPersistenceMapper mapper = new CelulaPersistenceMapper();

    @Test
    @DisplayName("El periodo va y vuelve entero, con el ultimo dia incluido")
    void elPeriodoSobreviveLaIdaYVuelta() {
        Celula fenix = Celula.crear(CelulaId.of(UUID.randomUUID()), "Fenix", CohorteId.of(UUID.randomUUID()),
                null, SEPTIEMBRE, AHORA);

        CelulaJpaEntity fila = mapper.toEntity(fenix);

        assertThat(fila.getPeriodoInicio()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(fila.getPeriodoFin()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(mapper.toDomain(fila).periodo()).isEqualTo(SEPTIEMBRE);
    }

    /** Las dos columnas son nulables y el null es un valor legitimo: grupo que no caduca. */
    @Test
    @DisplayName("Un grupo sin periodo escribe las dos columnas en NULL y vuelve sin periodo")
    void sinPeriodoNoSeInventaNinguno() {
        Celula sinPeriodo = Celula.crear(CelulaId.of(UUID.randomUUID()), "Celula 1",
                CohorteId.of(UUID.randomUUID()), null, AHORA);

        CelulaJpaEntity fila = mapper.toEntity(sinPeriodo);

        assertThat(fila.getPeriodoInicio()).isNull();
        assertThat(fila.getPeriodoFin()).isNull();
        assertThat(mapper.toDomain(fila).tienePeriodo()).isFalse();
    }

    /**
     * `creado_en` y `actualizado_en` son NOT NULL con DEFAULT now(), y un NULL explicito no activa
     * el DEFAULT: lo pisa y el INSERT revienta. Esta prueba es la red contra ese defecto conocido
     * — si alguien deja de mapearlos, falla aca y no en produccion.
     */
    @Test
    @DisplayName("creado_en y actualizado_en nunca viajan en null")
    void lasMarcasDeTiempoNuncaVanEnNull() {
        Celula fenix = Celula.crear(CelulaId.of(UUID.randomUUID()), "Fenix", CohorteId.of(UUID.randomUUID()),
                null, SEPTIEMBRE, AHORA);

        CelulaJpaEntity fila = mapper.toEntity(fenix);

        assertThat(fila.getCreadoEn()).isEqualTo(AHORA);
        assertThat(fila.getActualizadoEn()).isEqualTo(AHORA);
    }
}

package com.renaser.os.calendar.infrastructure.adapter.out.persistence.elegibilidad;

import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fija el comportamiento del NoOp DELIBERADO (docs/MODULO_CALENDAR.md §6, pregunta abierta
 * #1): sin el % de cumplimiento semanal real de `habits`+`rocks`, la unica respuesta honesta
 * es "no elegible". Si algun dia se implementa el calculo real, este test debe FALLAR — es
 * su recordatorio, no un contrato a preservar.
 */
class ElegibilidadEventoNoOpAdapterTest {

    private final ElegibilidadEventoNoOpAdapter adapter = new ElegibilidadEventoNoOpAdapter();

    @ParameterizedTest
    @EnumSource(TipoEvento.class)
    void nadieEsElegibleMientrasNoExistaElCalculoReal(TipoEvento tipoEvento) {
        assertThat(adapter.esElegible(UserId.of(UUID.randomUUID()), tipoEvento)).isFalse();
    }

    @Test
    void laRespuestaNoDependeDelUsuario() {
        assertThat(adapter.esElegible(UserId.of(UUID.randomUUID()), TipoEvento.MENTORIA_ALQUIMISTA)).isFalse();
        assertThat(adapter.esElegible(UserId.of(UUID.randomUUID()), TipoEvento.MENTORIA_ALQUIMISTA)).isFalse();
    }
}

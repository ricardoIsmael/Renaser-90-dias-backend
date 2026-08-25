package com.renaser.os.chat.domain.model.conversacion;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Replica en dominio el CHECK `tipo_coherente` (V1__baseline_renaser.sql:1286-1290)
 * ANTES de llegar a Postgres: un dato invalido debe fallar aca con 400, no con un 500 de
 * violacion de CHECK en la base (CLAUDE.MD sec. 5.4.4). */
class ConversacionTest {

    private static final Instant AHORA = Instant.parse("2026-08-25T10:00:00Z");

    @Test
    void crearCelulaExigeCelulaId() {
        assertThatThrownBy(() -> Conversacion.crearCelula(null, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void crearCelulaProduceUnaConversacionCoherente() {
        UUID celulaId = UUID.randomUUID();
        Conversacion c = Conversacion.crearCelula(celulaId, AHORA);

        assertThat(c.tipo()).isEqualTo(TipoConversacion.CELULA);
        assertThat(c.celulaId()).isEqualTo(celulaId);
        assertThat(c.claveDirecta()).isNull();
    }

    @Test
    void crearDirectaExigeClaveDirecta() {
        assertThatThrownBy(() -> Conversacion.crearDirecta(null, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Conversacion.crearDirecta("  ", AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void crearDirectaProduceUnaConversacionCoherente() {
        Conversacion c = Conversacion.crearDirecta("a_b", AHORA);

        assertThat(c.tipo()).isEqualTo(TipoConversacion.DIRECTA);
        assertThat(c.claveDirecta()).isEqualTo("a_b");
        assertThat(c.celulaId()).isNull();
    }

    @Test
    void crearGlobalNoLlevaCelulaNiClave() {
        Conversacion c = Conversacion.crearGlobal(AHORA);

        assertThat(c.tipo()).isEqualTo(TipoConversacion.GLOBAL);
        assertThat(c.celulaId()).isNull();
        assertThat(c.claveDirecta()).isNull();
    }

    @Test
    void rehydrateRechazaUnaCelulaSinCelulaId() {
        assertThatThrownBy(() -> Conversacion.rehydrate(ConversacionId.newId(), TipoConversacion.CELULA, null, null,
                null, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rehydrateRechazaUnaCelulaConClaveDirecta() {
        assertThatThrownBy(() -> Conversacion.rehydrate(ConversacionId.newId(), TipoConversacion.CELULA,
                UUID.randomUUID(), "a_b", null, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rehydrateRechazaUnaDirectaSinClave() {
        assertThatThrownBy(() -> Conversacion.rehydrate(ConversacionId.newId(), TipoConversacion.DIRECTA, null, null,
                null, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rehydrateRechazaUnaGlobalConCelulaId() {
        assertThatThrownBy(() -> Conversacion.rehydrate(ConversacionId.newId(), TipoConversacion.GLOBAL,
                UUID.randomUUID(), null, null, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rehydrateAceptaUnaConversacionCoherente() {
        UUID celulaId = UUID.randomUUID();
        Conversacion c = Conversacion.rehydrate(ConversacionId.newId(), TipoConversacion.CELULA, celulaId, null,
                null, AHORA);

        assertThat(c.celulaId()).isEqualTo(celulaId);
    }

    @Test
    void claveDirectaDeEsSimetricaSinImportarElOrdenDeLosArgumentos() {
        var a = com.renaser.os.shared.domain.UserId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        var b = com.renaser.os.shared.domain.UserId.of(UUID.fromString("22222222-2222-2222-2222-222222222222"));

        assertThat(Conversacion.claveDirectaDe(a, b)).isEqualTo(Conversacion.claveDirectaDe(b, a));
    }
}

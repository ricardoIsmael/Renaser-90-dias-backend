package com.renaser.os.chat.domain.model.conversacion;

import com.renaser.os.shared.domain.UserId;
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
    private static final ConversacionId ID = ConversacionId.of(
            UUID.fromString("33333333-3333-3333-3333-333333333333"));
    private static final UserId APRENDIZ = UserId.of(UUID.fromString("44444444-4444-4444-4444-444444444444"));
    private static final UserId STAFF = UserId.of(UUID.fromString("55555555-5555-5555-5555-555555555555"));

    @Test
    void crearCelulaExigeCelulaId() {
        assertThatThrownBy(() -> Conversacion.crearCelula(ID, null, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void crearCelulaProduceUnaConversacionCoherente() {
        UUID celulaId = UUID.randomUUID();
        Conversacion c = Conversacion.crearCelula(ID, celulaId, AHORA);

        assertThat(c.id()).isEqualTo(ID);
        assertThat(c.tipo()).isEqualTo(TipoConversacion.CELULA);
        assertThat(c.celulaId()).isEqualTo(celulaId);
        assertThat(c.claveDirecta()).isNull();
    }

    @Test
    void crearDirectaExigeClaveDirecta() {
        assertThatThrownBy(() -> Conversacion.crearDirecta(ID, null, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Conversacion.crearDirecta(ID, "  ", AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void crearDirectaProduceUnaConversacionCoherente() {
        Conversacion c = Conversacion.crearDirecta(ID, "a_b", AHORA);

        assertThat(c.tipo()).isEqualTo(TipoConversacion.DIRECTA);
        assertThat(c.claveDirecta()).isEqualTo("a_b");
        assertThat(c.celulaId()).isNull();
    }

    @Test
    void crearGlobalNoLlevaCelulaNiClave() {
        Conversacion c = Conversacion.crearGlobal(ID, AHORA);

        assertThat(c.tipo()).isEqualTo(TipoConversacion.GLOBAL);
        assertThat(c.celulaId()).isNull();
        assertThat(c.claveDirecta()).isNull();
    }

    @Test
    void rehydrateRechazaUnaCelulaSinCelulaId() {
        assertThatThrownBy(() -> Conversacion.rehydrate(ConversacionId.of(UUID.randomUUID()),
                TipoConversacion.CELULA, null, null, null, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rehydrateRechazaUnaCelulaConClaveDirecta() {
        assertThatThrownBy(() -> Conversacion.rehydrate(ConversacionId.of(UUID.randomUUID()),
                TipoConversacion.CELULA, UUID.randomUUID(), "a_b", null, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rehydrateRechazaUnaDirectaSinClave() {
        assertThatThrownBy(() -> Conversacion.rehydrate(ConversacionId.of(UUID.randomUUID()),
                TipoConversacion.DIRECTA, null, null, null, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rehydrateRechazaUnaGlobalConCelulaId() {
        assertThatThrownBy(() -> Conversacion.rehydrate(ConversacionId.of(UUID.randomUUID()),
                TipoConversacion.GLOBAL, UUID.randomUUID(), null, null, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rehydrateAceptaUnaConversacionCoherente() {
        UUID celulaId = UUID.randomUUID();
        Conversacion c = Conversacion.rehydrate(ConversacionId.of(UUID.randomUUID()),
                TipoConversacion.CELULA, celulaId, null, null, AHORA);

        assertThat(c.celulaId()).isEqualTo(celulaId);
    }

    @Test
    void claveDirectaDeEsSimetricaSinImportarElOrdenDeLosArgumentos() {
        var a = com.renaser.os.shared.domain.UserId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        var b = com.renaser.os.shared.domain.UserId.of(UUID.fromString("22222222-2222-2222-2222-222222222222"));

        assertThat(Conversacion.claveDirectaDe(a, b)).isEqualTo(Conversacion.claveDirectaDe(b, a));
    }

    @Test
    void renombradaCambiaElNombreDeUnaGlobal() {
        Conversacion global = Conversacion.crearGlobal(ID, AHORA);

        Conversacion renombrada = global.renombrada("  Comunidad Renaser  ");

        assertThat(renombrada.nombre()).isEqualTo("Comunidad Renaser");
        assertThat(renombrada.id()).isEqualTo(global.id());
        // "cambiar" devuelve una instancia nueva, nunca muta la original (CLAUDE.MD sec. 5.4.7).
        assertThat(global.nombre()).isEqualTo("Global");
    }

    @Test
    void renombradaRechazaUnNombreVacio() {
        Conversacion global = Conversacion.crearGlobal(ID, AHORA);

        assertThatThrownBy(() -> global.renombrada("   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> global.renombrada(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void renombradaRechazaUnaConversacionQueNoEsGlobal() {
        Conversacion celula = Conversacion.crearCelula(ID, UUID.randomUUID(), AHORA);
        Conversacion directa = Conversacion.crearDirecta(ID, "a_b", AHORA);

        assertThatThrownBy(() -> celula.renombrada("Nuevo nombre")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> directa.renombrada("Nuevo nombre")).isInstanceOf(IllegalStateException.class);
    }

    // ── Chat de soporte por aprendiz (D-136) ────────────────────────────────────────────────

    @Test
    void crearSoporteGuardaLaClaveCanonicaDelAprendizYNingunaCelula() {
        Conversacion c = Conversacion.crearSoporte(ID, APRENDIZ, "Soporte - Ana", AHORA);

        assertThat(c.tipo()).isEqualTo(TipoConversacion.SOPORTE);
        assertThat(c.claveDirecta()).isEqualTo("soporte:" + APRENDIZ.value());
        assertThat(c.celulaId()).isNull();
        assertThat(c.nombre()).isEqualTo("Soporte - Ana");
    }

    /** La clave es lo que hace que el UNIQUE de `clave_directa` impida un segundo soporte para la
     * misma persona: si dejara de ser determinista, la base dejaria de proteger nada. */
    @Test
    void laClaveDeSoporteEsSiempreLaMismaParaElMismoAprendiz() {
        assertThat(Conversacion.claveSoporteDe(APRENDIZ)).isEqualTo(Conversacion.claveSoporteDe(APRENDIZ));
        assertThat(Conversacion.claveSoporteDe(APRENDIZ)).isNotEqualTo(Conversacion.claveSoporteDe(STAFF));
    }

    /** Las claves de DM son `<uuid>_<uuid>`: no pueden chocar con `soporte:<uuid>` en la columna
     * que las dos comparten. */
    @Test
    void laClaveDeSoporteNoColisionaConLaDeUnMensajeDirecto() {
        assertThat(Conversacion.claveSoporteDe(APRENDIZ))
                .isNotEqualTo(Conversacion.claveDirectaDe(APRENDIZ, STAFF));
        assertThat(Conversacion.claveDirectaDe(APRENDIZ, STAFF)).doesNotStartWith("soporte:");
    }

    @Test
    void crearSoporteExigeAprendizYNombre() {
        assertThatThrownBy(() -> Conversacion.crearSoporte(ID, null, "Soporte", AHORA))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Conversacion.crearSoporte(ID, APRENDIZ, "   ", AHORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Conversacion.crearSoporte(ID, APRENDIZ, null, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Quien no se puede ir. El staff si; el aprendiz dueño no. */
    @Test
    void elSoporteReconoceASuAprendizYNoConfundeAlStaff() {
        Conversacion soporte = Conversacion.crearSoporte(ID, APRENDIZ, "Soporte - Ana", AHORA);

        assertThat(soporte.esAprendizDeSoporte(APRENDIZ)).isTrue();
        assertThat(soporte.esAprendizDeSoporte(STAFF)).isFalse();
        assertThat(soporte.esAprendizDeSoporte(null)).isFalse();
    }

    /** Un DM no tiene "aprendiz dueño": preguntarselo tiene que dar `false`, no un falso positivo
     * por parecerse la clave. */
    @Test
    void unaConversacionQueNoEsDeSoporteNoTieneAprendizDueno() {
        Conversacion directa = Conversacion.crearDirecta(ID, Conversacion.claveSoporteDe(APRENDIZ), AHORA);

        assertThat(directa.esAprendizDeSoporte(APRENDIZ)).isFalse();
    }

    @Test
    void rehydrateRechazaUnSoporteSinClave() {
        assertThatThrownBy(() -> Conversacion.rehydrate(ConversacionId.of(UUID.randomUUID()),
                TipoConversacion.SOPORTE, null, null, "Soporte", AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rehydrateRechazaUnSoporteConCelulaId() {
        assertThatThrownBy(() -> Conversacion.rehydrate(ConversacionId.of(UUID.randomUUID()),
                TipoConversacion.SOPORTE, UUID.randomUUID(), "soporte:x", "Soporte", AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void renombradaRechazaUnSoporte() {
        Conversacion soporte = Conversacion.crearSoporte(ID, APRENDIZ, "Soporte - Ana", AHORA);

        assertThatThrownBy(() -> soporte.renombrada("Otro")).isInstanceOf(IllegalStateException.class);
    }
}

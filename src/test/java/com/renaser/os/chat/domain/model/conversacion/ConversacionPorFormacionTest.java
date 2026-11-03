package com.renaser.os.chat.domain.model.conversacion;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cada conversación es de UNA formación (spec 024): las claves de soporte y de los directos y los nombres de los chats. */
class ConversacionPorFormacionTest {

    private static final Instant AHORA = Instant.parse("2026-10-31T15:00:00Z");
    private static final ConversacionId ID = ConversacionId.of(UUID.fromString("33333333-3333-3333-3333-333333333333"));
    private static final UserId ANA = UserId.of(UUID.fromString("44444444-4444-4444-4444-444444444444"));
    private static final UserId BETO = UserId.of(UUID.fromString("55555555-5555-5555-5555-555555555555"));

    @Test
    @DisplayName("Lo que ya existía es de la Escuela: las fábricas de siempre no piden formación")
    void porDefectoEsLaEscuela() {
        assertThat(Conversacion.crearGlobal(ID, AHORA).formacion()).isEqualTo(Formacion.ESCUELA);
        assertThat(Conversacion.crearDirecta(ID, "a_b", AHORA).formacion()).isEqualTo(Formacion.ESCUELA);
        assertThat(Conversacion.crearCelula(ID, UUID.randomUUID(), AHORA).formacion()).isEqualTo(Formacion.ESCUELA);
        assertThat(Conversacion.crearSoporte(ID, ANA, "Ana", AHORA).formacion()).isEqualTo(Formacion.ESCUELA);
        assertThat(Conversacion.crearGlobal(ID, AHORA).nombre()).isEqualTo("Formación Renaser Global");
    }

    @Test
    @DisplayName("La clave del soporte de la Escuela no cambia; la de las otras lleva el código, así quien cambia de formación tiene uno por cada una")
    void clavesDeSoporte() {
        assertThat(Conversacion.claveSoporteDe(ANA, Formacion.ESCUELA)).isEqualTo(Conversacion.claveSoporteDe(ANA))
                .isEqualTo("soporte:" + ANA.value());
        assertThat(Conversacion.claveSoporteDe(ANA, Formacion.DIPLOMADO)).isEqualTo("soporte:" + ANA.value() + ":DIPLOMADO");
        assertThat(Conversacion.claveSoporteDe(ANA, Formacion.CERTIFICACION)).isNotEqualTo(Conversacion.claveSoporteDe(ANA, Formacion.DIPLOMADO));
    }

    @Test
    @DisplayName("Un soporte reconoce a su aprendiz y su formación, con o sin sufijo en la clave")
    void elSoporteReconoceASuAprendiz() {
        Conversacion deLaEscuela = Conversacion.crearSoporte(ID, ANA, "Ana", AHORA, Formacion.ESCUELA);
        Conversacion delDiplomado = Conversacion.crearSoporte(ID, ANA, "Ana", AHORA, Formacion.DIPLOMADO);

        assertThat(deLaEscuela.esAprendizDeSoporte(ANA)).isTrue();
        assertThat(delDiplomado.esAprendizDeSoporte(ANA)).isTrue();
        assertThat(delDiplomado.esAprendizDeSoporte(BETO)).isFalse();
        assertThat(deLaEscuela.aprendizDelSoporte()).contains(ANA);
        assertThat(delDiplomado.aprendizDelSoporte()).contains(ANA);
        assertThat(delDiplomado.seGanaPorRolDeStaff(BETO)).isTrue();
        assertThat(delDiplomado.seGanaPorRolDeStaff(ANA)).isFalse();
        assertThat(delDiplomado.formacion()).isEqualTo(Formacion.DIPLOMADO);
    }

    @Test
    @DisplayName("Dos personas que comparten dos formaciones tienen un directo en cada una; el de la Escuela conserva la clave de siempre")
    void clavesDeDirectos() {
        String escuela = Conversacion.claveDirectaDe(ANA, BETO, Formacion.ESCUELA);
        String diplomado = Conversacion.claveDirectaDe(BETO, ANA, Formacion.DIPLOMADO);

        assertThat(escuela).isEqualTo(Conversacion.claveDirectaDe(ANA, BETO));
        assertThat(diplomado).startsWith(escuela).endsWith(":DIPLOMADO").isNotEqualTo(escuela);
        assertThat(Conversacion.crearDirecta(ID, diplomado, AHORA, Formacion.DIPLOMADO).formacion()).isEqualTo(Formacion.DIPLOMADO);
    }

    @Test
    @DisplayName("La comunidad general de otra formación lleva su nombre; la formación es obligatoria")
    void laComunidadGeneralDeOtraFormacion() {
        Conversacion g = Conversacion.crearGlobal(ID, NombreDelChat.deLaComunidad("Diplomado Renaser"), AHORA, Formacion.DIPLOMADO);

        assertThat(g.nombre()).isEqualTo("Diplomado Renaser Global");
        assertThat(g.formacion()).isEqualTo(Formacion.DIPLOMADO);
        assertThat(g.renombrada("Diplomado: la comunidad").formacion()).isEqualTo(Formacion.DIPLOMADO);
        assertThatThrownBy(() -> Conversacion.crearGlobal(ID, "x", AHORA, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Conversacion.crearGlobal(ID, " ", AHORA, Formacion.DIPLOMADO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Nombres: el soporte de la Escuela sigue siendo «Pedro – Formación Renaser»; el de otra formación, «Pedro – Diplomado Renaser»")
    void nombresDeLosChats() {
        assertThat(NombreDelChat.deSoporte("Pedro Ruiz")).isEqualTo("Pedro – Formación Renaser");
        assertThat(NombreDelChat.deSoporte("Pedro Ruiz", "Diplomado Renaser")).isEqualTo("Pedro – Diplomado Renaser");
        assertThat(NombreDelChat.deSoporte(null, "Diplomado Renaser")).isEqualTo("Diplomado Renaser");
        assertThat(NombreDelChat.deLaComunidad("Certificación Renaser")).isEqualTo("Certificación Renaser Global");
        assertThat(NombreDelChat.DE_LA_COMUNIDAD).isEqualTo("Formación Renaser Global");
    }
}

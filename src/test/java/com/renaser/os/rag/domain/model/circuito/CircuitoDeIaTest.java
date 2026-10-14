package com.renaser.os.rag.domain.model.circuito;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Las reglas del circuito de la IA (spec 002, FR-016): abre a la quinta falla y no a la cuarta, espera 2, 5 y 15 min en
 * las reaperturas seguidas, deja pasar un solo pedido de prueba y se cierra si sale bien. El reloj es un instante fijo
 * en la madrugada UTC (regla 02), aunque acá no hay días de por medio.
 */
class CircuitoDeIaTest {

    private static final Instant AHORA = Instant.parse("2026-10-13T03:00:00Z");
    private static final ReglasDelCircuito REGLAS = ReglasDelCircuito.POR_DEFECTO;

    private static CircuitoDeIa abiertoConFallas(Instant en) {
        return CircuitoDeIa.cerrado(ParteDeLaIa.CHAT).trasFalla(5, en, REGLAS).orElseThrow().despues();
    }

    @Test
    @DisplayName("cerrado: la cuarta falla en la ventana no abre; la quinta sí, con 2 min de espera y la causa")
    void abreALaQuinta() {
        CircuitoDeIa cerrado = CircuitoDeIa.cerrado(ParteDeLaIa.CHAT);

        assertThat(cerrado.trasFalla(4, AHORA, REGLAS)).isEmpty();
        CambioDelCircuito cambio = cerrado.trasFalla(5, AHORA, REGLAS).orElseThrow();

        assertThat(cambio.despues().estado()).isEqualTo(EstadoDelCircuito.ABIERTO);
        assertThat(cambio.despues().proximaPrueba()).isEqualTo(AHORA.plus(Duration.ofMinutes(2)));
        assertThat(cambio.despues().abiertoDesde()).isEqualTo(AHORA);
        assertThat(cambio.despues().version()).isEqualTo(1);
        assertThat(cambio.causa()).isEqualTo("5 fallas del proveedor en 2 min; se prueba de nuevo en 2 min");
        assertThat(cambio.cierra()).isFalse();
    }

    @Test
    @DisplayName("abierto corta el paso hasta la próxima prueba; vencida la espera ya no corta y admite la prueba")
    void abiertoCortaHastaLaPrueba() {
        CircuitoDeIa abierto = abiertoConFallas(AHORA);
        Instant justoAntes = AHORA.plus(Duration.ofMinutes(2)).minusMillis(1);
        Instant alVencer = AHORA.plus(Duration.ofMinutes(2));

        assertThat(abierto.cortaElPaso(justoAntes)).isTrue();
        assertThat(abierto.admitePrueba(justoAntes)).isFalse();
        assertThat(abierto.estadoEn(justoAntes)).isEqualTo(EstadoDelCircuito.ABIERTO);
        assertThat(abierto.cortaElPaso(alVencer)).isFalse();
        assertThat(abierto.admitePrueba(alVencer)).isTrue();
        assertThat(abierto.estadoEn(alVencer)).isEqualTo(EstadoDelCircuito.PROBANDO);
    }

    @Test
    @DisplayName("medio abierto: un pedido toma la prueba y corta a los demás hasta que vence el plazo de la prueba")
    void unaSolaPrueba() {
        Instant alVencer = AHORA.plus(Duration.ofMinutes(2));
        CircuitoDeIa probando = abiertoConFallas(AHORA).conPruebaEnCurso(alVencer, REGLAS);

        assertThat(probando.estado()).isEqualTo(EstadoDelCircuito.PROBANDO);
        assertThat(probando.cortaElPaso(alVencer.plusSeconds(30))).isTrue();
        assertThat(probando.admitePrueba(alVencer.plusSeconds(30))).isFalse();
        assertThatThrownBy(() -> probando.conPruebaEnCurso(alVencer.plusSeconds(30), REGLAS))
                .isInstanceOf(IllegalStateException.class);
        Instant vencioLaPrueba = alVencer.plus(REGLAS.plazoDeLaPrueba());
        assertThat(probando.admitePrueba(vencioLaPrueba)).as("la prueba se cortó sin avisar: otro puede probar")
                .isTrue();
    }

    @Test
    @DisplayName("si la prueba falla, se reabre con la espera siguiente: 2, 5, 15 y 15 min; abiertoDesde no cambia")
    void esperasCrecientes() {
        CircuitoDeIa circuito = abiertoConFallas(AHORA);
        Instant ahora = AHORA;
        for (Duration esperada : List.of(Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofMinutes(15))) {
            ahora = circuito.proximaPrueba();
            CambioDelCircuito cambio = circuito.conPruebaEnCurso(ahora, REGLAS).trasFalla(1, ahora, REGLAS)
                    .orElseThrow();
            circuito = cambio.despues();
            assertThat(circuito.estado()).isEqualTo(EstadoDelCircuito.ABIERTO);
            assertThat(circuito.proximaPrueba()).isEqualTo(ahora.plus(esperada));
            assertThat(circuito.abiertoDesde()).isEqualTo(AHORA);
            assertThat(cambio.causa()).startsWith("Falló el pedido de prueba; se prueba de nuevo en");
        }
        assertThat(circuito.aperturasSeguidas()).isEqualTo(4);
    }

    @Test
    @DisplayName("si la prueba sale bien se cierra, y la próxima apertura vuelve a esperar 2 min")
    void cierreReiniciaLaEspera() {
        Instant prueba = AHORA.plus(Duration.ofMinutes(2));
        CircuitoDeIa reabierto = abiertoConFallas(AHORA).conPruebaEnCurso(prueba, REGLAS).trasFalla(1, prueba, REGLAS)
                .orElseThrow().despues();
        Instant segundaPrueba = reabierto.proximaPrueba();

        CambioDelCircuito cierre = reabierto.conPruebaEnCurso(segundaPrueba, REGLAS).trasExito(segundaPrueba)
                .orElseThrow();

        assertThat(cierre.cierra()).isTrue();
        assertThat(cierre.causa()).isEqualTo("Salió bien el pedido de prueba");
        assertThat(cierre.despues().aperturasSeguidas()).isZero();
        assertThat(cierre.despues().cortaElPaso(segundaPrueba)).isFalse();
        CircuitoDeIa otraVez = cierre.despues().trasFalla(5, segundaPrueba, REGLAS).orElseThrow().despues();
        assertThat(otraVez.proximaPrueba()).isEqualTo(segundaPrueba.plus(Duration.ofMinutes(2)));
    }

    @Test
    @DisplayName("una respuesta solo cierra si está medio abierto; una falla con el circuito abierto no cambia nada")
    void loQueNoCambia() {
        CircuitoDeIa abierto = abiertoConFallas(AHORA);

        assertThat(CircuitoDeIa.cerrado(ParteDeLaIa.VOZ).trasExito(AHORA)).isEmpty();
        assertThat(abierto.trasExito(AHORA)).isEmpty();
        assertThat(abierto.trasFalla(9, AHORA.plusSeconds(10), REGLAS)).isEmpty();
    }

    @Test
    @DisplayName("cada escritura sube la versión: abrir, tomar la prueba y cerrar")
    void versionSube() {
        Instant prueba = AHORA.plus(Duration.ofMinutes(2));
        CircuitoDeIa abierto = abiertoConFallas(AHORA);
        CircuitoDeIa probando = abierto.conPruebaEnCurso(prueba, REGLAS);
        CircuitoDeIa cerrado = probando.trasExito(prueba).orElseThrow().despues();

        assertThat(List.of(abierto.version(), probando.version(), cerrado.version())).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("abierto o medio abierto sin fechas no existe; la parte sale de su clave")
    void invariantesYClaves() {
        assertThatThrownBy(() -> new CircuitoDeIa(ParteDeLaIa.CHAT, EstadoDelCircuito.ABIERTO, 1, null, null, null, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ParteDeLaIa.deClave("ia.voz")).contains(ParteDeLaIa.VOZ);
        assertThat(ParteDeLaIa.deClave("ia")).isEmpty();
        assertThat(ParteDeLaIa.deClave("ia.propuestas")).isEmpty();
    }

    @Test
    @DisplayName("las reglas: la última espera se repite y un valor inválido no arranca")
    void reglas() {
        assertThat(REGLAS.esperaTrasLaApertura(1)).isEqualTo(Duration.ofMinutes(2));
        assertThat(REGLAS.esperaTrasLaApertura(7)).isEqualTo(Duration.ofMinutes(15));
        assertThatThrownBy(() -> new ReglasDelCircuito(0, Duration.ofMinutes(2), List.of(Duration.ofMinutes(2)),
                Duration.ofMinutes(2))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReglasDelCircuito(5, Duration.ofMinutes(2), List.of(), Duration.ofMinutes(2)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(CircuitoDeIa.enPalabras(Duration.ofSeconds(45))).isEqualTo("45 s");
    }
}

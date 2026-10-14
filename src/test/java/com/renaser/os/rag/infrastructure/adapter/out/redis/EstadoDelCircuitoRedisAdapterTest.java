package com.renaser.os.rag.infrastructure.adapter.out.redis;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.rag.application.ports.out.circuito.EstadoCompartidoDelCircuitoPort;
import com.renaser.os.rag.application.services.circuito.CopiaDelCircuito;
import com.renaser.os.rag.domain.model.circuito.CircuitoDeIa;
import com.renaser.os.rag.domain.model.circuito.EstadoDelCircuito;
import com.renaser.os.rag.domain.model.circuito.ParteDeLaIa;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El estado del circuito contra Redis real (Testcontainers): el compare-and-set por versión y la ventana de fallas son
 * scripts Lua, y eso no se puede demostrar con un mock. Las fechas son de 2020 a propósito: si otra prueba del mismo
 * contexto llegara a leer este estado, la espera ya venció y no le corta nada.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EstadoDelCircuitoRedisAdapterTest {

    private static final Instant ANTES = Instant.parse("2020-01-01T03:00:00Z");
    private static final Duration VENTANA = Duration.ofMinutes(2);

    @Autowired
    private EstadoCompartidoDelCircuitoPort estado;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private CopiaDelCircuito copia;

    @BeforeEach
    @AfterEach
    void limpiar() {
        redis.delete(EstadoDelCircuitoRedisAdapter.clave(ParteDeLaIa.VOZ));
        redis.delete(EstadoDelCircuitoRedisAdapter.claveDeFallas(ParteDeLaIa.VOZ));
        copia.recargar();
    }

    @Test
    @DisplayName("sin nada guardado está cerrado en la versión 0; lo que se guarda vuelve igual, instantes incluidos")
    void idaYVuelta() {
        assertThat(estado.leer(ParteDeLaIa.VOZ)).isEqualTo(CircuitoDeIa.cerrado(ParteDeLaIa.VOZ));
        CircuitoDeIa abierto = new CircuitoDeIa(ParteDeLaIa.VOZ, EstadoDelCircuito.PROBANDO, 2, ANTES,
                ANTES.plus(Duration.ofMinutes(5)), ANTES.plus(Duration.ofMinutes(7)), 1);

        assertThat(estado.reemplazar(CircuitoDeIa.cerrado(ParteDeLaIa.VOZ), abierto)).isTrue();

        assertThat(estado.leer(ParteDeLaIa.VOZ)).isEqualTo(abierto);
    }

    @Test
    @DisplayName("compare-and-set: con una versión vieja no escribe (otra instancia ganó)")
    void compareAndSet() {
        CircuitoDeIa cerrado = CircuitoDeIa.cerrado(ParteDeLaIa.VOZ);
        CircuitoDeIa v1 = new CircuitoDeIa(ParteDeLaIa.VOZ, EstadoDelCircuito.ABIERTO, 1, ANTES, ANTES, null, 1);
        CircuitoDeIa otraV1 = new CircuitoDeIa(ParteDeLaIa.VOZ, EstadoDelCircuito.ABIERTO, 1, ANTES,
                ANTES.plusSeconds(1), null, 1);

        assertThat(estado.reemplazar(cerrado, v1)).isTrue();
        assertThat(estado.reemplazar(cerrado, otraV1)).isFalse();
        assertThat(estado.leer(ParteDeLaIa.VOZ)).isEqualTo(v1);
    }

    @Test
    @DisplayName("la ventana de fallas cuenta las de los últimos 2 min, poda las viejas y se olvida al cerrar")
    void ventanaDeFallas() {
        assertThat(estado.anotarFalla(ParteDeLaIa.VOZ, ANTES, VENTANA)).isEqualTo(1);
        assertThat(estado.anotarFalla(ParteDeLaIa.VOZ, ANTES.plusSeconds(60), VENTANA)).isEqualTo(2);
        assertThat(estado.anotarFalla(ParteDeLaIa.VOZ, ANTES.plus(VENTANA), VENTANA))
                .as("la del inicio quedó justo afuera").isEqualTo(2);
        assertThat(estado.fallasEnVentana(ParteDeLaIa.VOZ, ANTES.plus(VENTANA).plusSeconds(61), VENTANA))
                .isEqualTo(1);

        estado.olvidarFallas(ParteDeLaIa.VOZ);

        assertThat(estado.fallasEnVentana(ParteDeLaIa.VOZ, ANTES.plus(VENTANA), VENTANA)).isZero();
    }
}

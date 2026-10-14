package com.renaser.os.rag.application.services.circuito;

import com.renaser.os.rag.api.CircuitoDeIaCambiadoEvent;
import com.renaser.os.rag.domain.model.circuito.ParteDeLaIa;
import com.renaser.os.rag.domain.model.circuito.ReglasDelCircuito;
import com.renaser.os.shared.application.EstadoAutomaticoDeFuncionalidades.CircuitoAutomatico;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.FuncionalidadApagadaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * El circuito de la IA con su estado compartido (spec 002, FR-016 y contrato §5/§6), con un reloj que la prueba mueve
 * desde la madrugada UTC (regla 02) y un «Redis» en memoria que comparten dos instancias.
 */
class ControlDelCircuitoServiceTest {

    private static final ReglasDelCircuito REGLAS = ReglasDelCircuito.POR_DEFECTO;
    private static final String AVISO = "SER está en pausa por un problema con el proveedor. Vuelve en unos minutos.";

    private final RelojMovible reloj = new RelojMovible(Instant.parse("2026-10-13T03:00:00Z"));
    private final EstadoCompartidoEnMemoria redis = new EstadoCompartidoEnMemoria();
    private final List<String> auditoria = new CopyOnWriteArrayList<>();
    private final List<Object> eventos = new CopyOnWriteArrayList<>();
    private final List<String> avisos = new CopyOnWriteArrayList<>();
    private final Instancia una = new Instancia();

    /** Una instancia del backend: su copia en memoria, su control y su estado automático, sobre el mismo «Redis». */
    private final class Instancia {
        final CopiaDelCircuito copia = new CopiaDelCircuito(redis);
        final ControlDelCircuitoService control = new ControlDelCircuitoService(redis,
                parte -> avisos.add(parte.clave()), copia, new AnotadorDeCambiosDelCircuito(
                (clave, antes, despues, causa) -> auditoria.add(clave + " " + despues.get("estado") + ": " + causa),
                eventos::add), REGLAS, reloj);
        final EstadoAutomaticoDeLaIa automatico = new EstadoAutomaticoDeLaIa(copia, redis, REGLAS, reloj);

        void fallas(int cuantas) {
            for (int i = 0; i < cuantas; i++) {
                control.anotarFalla(ParteDeLaIa.CHAT);
            }
        }
    }

    @Test
    @DisplayName("abre a la quinta falla del proveedor en la ventana, no a la cuarta: aviso automático, fila y evento")
    void abreALaQuinta() {
        una.fallas(4);

        assertThat(una.control.dejaPasar(ParteDeLaIa.CHAT)).isTrue();
        assertThat(una.automatico.apagadaAutomaticamente("ia.chat")).isEmpty();
        assertThat(auditoria).isEmpty();

        una.fallas(1);

        assertThat(una.control.dejaPasar(ParteDeLaIa.CHAT)).isFalse();
        assertThat(una.automatico.apagadaAutomaticamente("ia.chat")).contains(AVISO);
        assertThat(una.automatico.apagadaAutomaticamente("ia.voz")).as("la voz tiene su propio circuito").isEmpty();
        assertThat(una.automatico.apagadaAutomaticamente("ia")).isEmpty();
        assertThat(auditoria).containsExactly("ia.chat ABIERTO: 5 fallas del proveedor en 2 min; se prueba de nuevo en 2 min");
        assertThat(eventos).singleElement().isInstanceOfSatisfying(CircuitoDeIaCambiadoEvent.class, evento -> {
            assertThat(evento.parte()).isEqualTo("ia.chat");
            assertThat(evento.estado()).isEqualTo("ABIERTO");
            assertThat(evento.occurredAt()).isEqualTo(reloj.now());
        });
        assertThat(avisos).containsExactly("ia.chat");
        assertThatThrownBy(() -> una.control.exigirPaso(ParteDeLaIa.CHAT))
                .isInstanceOf(FuncionalidadApagadaException.class).hasMessage(AVISO);
    }

    @Test
    @DisplayName("las fallas que salen de la ventana de 2 min ya no cuentan")
    void ventanaDeslizante() {
        una.fallas(4);
        reloj.avanzar(Duration.ofMinutes(2).plusMillis(1));
        una.fallas(1);

        assertThat(una.control.dejaPasar(ParteDeLaIa.CHAT)).isTrue();
        assertThat(auditoria).isEmpty();
    }

    @Test
    @DisplayName("esperas crecientes: 2, 5 y 15 min; vencida cada una pasa un pedido de prueba y uno solo")
    void esperasCrecientesYMedioAbierto() {
        una.fallas(5);
        for (Duration espera : List.of(Duration.ofMinutes(2), Duration.ofMinutes(5), Duration.ofMinutes(15))) {
            reloj.avanzar(espera.minusSeconds(1));
            assertThat(una.control.dejaPasar(ParteDeLaIa.CHAT)).as("antes de %s", espera).isFalse();
            reloj.avanzar(Duration.ofSeconds(1));
            assertThat(una.automatico.apagadaAutomaticamente("ia.chat")).as("vencida, la app vuelve a ver SER")
                    .isEmpty();
            assertThat(una.control.dejaPasar(ParteDeLaIa.CHAT)).as("la prueba").isTrue();
            assertThat(una.control.dejaPasar(ParteDeLaIa.CHAT)).as("otro pedido, con la prueba en curso").isFalse();
            assertThat(una.automatico.apagadaAutomaticamente("ia.chat")).contains(AVISO);
            una.control.anotarFalla(ParteDeLaIa.CHAT);
        }
        assertThat(auditoria).hasSize(4).last().asString()
                .isEqualTo("ia.chat ABIERTO: Falló el pedido de prueba; se prueba de nuevo en 15 min");
        reloj.avanzar(Duration.ofMinutes(15).minusSeconds(1));
        assertThat(una.control.dejaPasar(ParteDeLaIa.CHAT)).as("la cuarta espera repite la última").isFalse();
    }

    @Test
    @DisplayName("una prueba que se corta sin avisar vence a los 2 min y otro pedido puede probar")
    void pruebaQueSeCorta() {
        una.fallas(5);
        reloj.avanzar(Duration.ofMinutes(2));
        assertThat(una.control.dejaPasar(ParteDeLaIa.CHAT)).isTrue();

        reloj.avanzar(REGLAS.plazoDeLaPrueba());

        assertThat(una.control.dejaPasar(ParteDeLaIa.CHAT)).isTrue();
    }

    @Test
    @DisplayName("si la prueba sale bien se cierra: fila y evento CERRADO, fallas olvidadas, y la espera vuelve a 2 min")
    void cierre() {
        una.fallas(5);
        reloj.avanzar(Duration.ofMinutes(2));
        una.control.dejaPasar(ParteDeLaIa.CHAT);
        una.control.anotarFalla(ParteDeLaIa.CHAT);
        reloj.avanzar(Duration.ofMinutes(5));
        una.control.dejaPasar(ParteDeLaIa.CHAT);

        una.control.anotarExito(ParteDeLaIa.CHAT);

        assertThat(una.control.dejaPasar(ParteDeLaIa.CHAT)).isTrue();
        assertThat(una.automatico.apagadaAutomaticamente("ia.chat")).isEmpty();
        assertThat(auditoria).last().asString().isEqualTo("ia.chat CERRADO: Salió bien el pedido de prueba");
        assertThat(eventos).last().isInstanceOfSatisfying(CircuitoDeIaCambiadoEvent.class,
                evento -> assertThat(evento.estado()).isEqualTo("CERRADO"));
        una.fallas(4);
        assertThat(una.control.dejaPasar(ParteDeLaIa.CHAT)).as("las fallas de antes ya no cuentan").isTrue();
        una.fallas(1);
        reloj.avanzar(Duration.ofMinutes(2));
        assertThat(una.control.dejaPasar(ParteDeLaIa.CHAT)).as("la espera se reinició a 2 min").isTrue();
    }

    @Test
    @DisplayName("con el circuito cerrado, pedir paso y anotar un éxito no tocan Redis")
    void caminoDeSiempreSinRedis() {
        una.control.dejaPasar(ParteDeLaIa.CHAT);
        una.control.anotarExito(ParteDeLaIa.CHAT);
        una.control.exigirPaso(ParteDeLaIa.VOZ);

        assertThat(redis.lecturas).isZero();
    }

    @Test
    @DisplayName("sin Redis, el circuito deja pasar y no lanza: un circuito roto no apaga SER")
    void fallaAbierto() {
        una.fallas(5);
        redis.caido = true;
        reloj.avanzar(Duration.ofMinutes(1));

        assertThat(una.control.dejaPasar(ParteDeLaIa.CHAT)).isTrue();
        assertThatNoException().isThrownBy(() -> {
            una.control.anotarFalla(ParteDeLaIa.CHAT);
            una.control.anotarExito(ParteDeLaIa.CHAT);
        });
        assertThat(una.automatico.circuitos()).as("la consola ve la copia").hasSize(2);
    }

    @Test
    @DisplayName("dos instancias: entre las dos suman cinco fallas, el circuito abre una sola vez y la otra lo ve")
    void dosInstancias() {
        Instancia otra = new Instancia();
        una.fallas(3);
        otra.fallas(2);

        assertThat(eventos).hasSize(1);
        assertThat(otra.control.dejaPasar(ParteDeLaIa.CHAT)).isFalse();
        una.copia.recargar();
        assertThat(una.automatico.apagadaAutomaticamente("ia.chat")).contains(AVISO);
        assertThat(una.automatico.version()).isEqualTo(otra.automatico.version());
    }

    @Test
    @DisplayName("la versión que suma /funcionalidades/mias cambia al abrir y al cerrar; la consola ve cada parte")
    void versionYConsola() {
        long antes = una.automatico.version();
        una.fallas(5);
        long abierto = una.automatico.version();
        List<CircuitoAutomatico> circuitos = una.automatico.circuitos();

        assertThat(abierto).isGreaterThan(antes);
        assertThat(circuitos).extracting(CircuitoAutomatico::parte).containsExactly("ia.chat", "ia.voz");
        assertThat(circuitos.getFirst().estado()).isEqualTo("ABIERTO");
        assertThat(circuitos.getFirst().fallasEnVentana()).isEqualTo(5);
        assertThat(circuitos.getFirst().proximaPrueba()).isEqualTo(reloj.now().plus(Duration.ofMinutes(2)));
        assertThat(circuitos.get(1).estado()).isEqualTo("CERRADO");
        assertThat(circuitos.get(1).abiertoDesde()).isNull();

        reloj.avanzar(Duration.ofMinutes(2));
        assertThat(una.automatico.circuitos().getFirst().estado()).as("vencida la espera").isEqualTo("PROBANDO");
        una.control.dejaPasar(ParteDeLaIa.CHAT);
        una.control.anotarExito(ParteDeLaIa.CHAT);
        assertThat(una.automatico.version()).isGreaterThan(abierto);
    }

    @Test
    @DisplayName("si Redis se vacía la versión no retrocede, y la siguiente apertura la vuelve a subir")
    void versionTrasVaciarRedis() {
        una.fallas(5);
        long abierto = una.automatico.version();

        redis.vaciar();
        una.copia.recargar();
        long vaciado = una.automatico.version();
        una.fallas(5);

        assertThat(una.automatico.apagadaAutomaticamente("ia.chat")).contains(AVISO);
        assertThat(vaciado).isGreaterThan(abierto);
        assertThat(una.automatico.version()).isGreaterThan(vaciado);
    }

    /** Reloj que la prueba mueve. */
    static final class RelojMovible implements Clock {

        private volatile Instant ahora;

        RelojMovible(Instant ahora) {
            this.ahora = ahora;
        }

        void avanzar(Duration cuanto) {
            ahora = ahora.plus(cuanto);
        }

        @Override
        public Instant now() {
            return ahora;
        }

        @Override
        public LocalDate today() {
            return ahora.atZone(ZoneOffset.UTC).toLocalDate();
        }
    }
}

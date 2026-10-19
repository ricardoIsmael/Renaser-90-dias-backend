package com.renaser.os.points.application.services;

import com.renaser.os.points.domain.model.semaforo.mensaje.HabitosDestacados;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los datos de la persona que no cambian en el día (nombre, hábitos destacados), guardados en memoria por persona y
 * día local (D-277, SC-004). Vencen a la medianoche de la zona de la persona según el {@code Clock}, no a la UTC ni
 * a las 24 horas: todas las pruebas de vencimiento ponen el reloj donde el día UTC y el local NO coinciden.
 */
class DatosEstablesDelDiaTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final UserId ANA = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UserId LUIS = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000b2"));
    private static final LocalDate DIA_18 = LocalDate.of(2026, 10, 18);

    private final RelojAjustable reloj = new RelojAjustable(Instant.parse("2026-10-19T03:00:00Z"));
    private final AtomicInteger lecturas = new AtomicInteger();
    private DatosEstablesDelDia datos;

    @BeforeEach
    void armar() {
        datos = new DatosEstablesDelDia(reloj);
    }

    private String nombreDe(UserId persona, LocalDate dia, ZoneId zona) {
        return datos.de(persona, dia, zona).nombre(() -> {
            lecturas.incrementAndGet();
            return Optional.of("Nombre " + lecturas.get());
        }).orElseThrow();
    }

    @Test
    @DisplayName("el nombre se lee una sola vez por persona y día, y las siguientes lecturas salen de memoria")
    void seLeeUnaVez() {
        String primera = nombreDe(ANA, DIA_18, LIMA);
        String segunda = nombreDe(ANA, DIA_18, LIMA);

        assertThat(segunda).isEqualTo(primera);
        assertThat(lecturas).hasValue(1);
    }

    @Test
    @DisplayName("otra persona, otro día local u otra zona son otra entrada")
    void cadaPersonaYDiaTieneLaSuya() {
        nombreDe(ANA, DIA_18, LIMA);
        nombreDe(LUIS, DIA_18, LIMA);
        nombreDe(ANA, DIA_18.plusDays(1), LIMA);
        nombreDe(ANA, DIA_18, ZoneId.of("Europe/Madrid"));

        assertThat(lecturas).hasValue(4);
    }

    @Test
    @DisplayName("los hábitos destacados se guardan igual: una lectura por persona y día, y un null no se confunde con «sin leer»")
    void losDestacadosSeGuardanIgual() {
        AtomicInteger lecturasDeDestacados = new AtomicInteger();
        HabitosDestacados sinDatos = new HabitosDestacados(null, null);

        for (int i = 0; i < 3; i++) {
            datos.de(ANA, DIA_18, LIMA).destacados(() -> {
                lecturasDeDestacados.incrementAndGet();
                return sinDatos;
            });
        }

        assertThat(lecturasDeDestacados).hasValue(1);
    }

    @Test
    @DisplayName("vence a la medianoche de Lima: con el reloj a las 03:00 UTC (22:00 del 18 en Lima) sigue hasta las 04:59:59 UTC")
    void venceALaMedianocheDeLima() {
        nombreDe(ANA, DIA_18, LIMA);

        reloj.poner(Instant.parse("2026-10-19T04:59:59Z"));
        nombreDe(ANA, DIA_18, LIMA);
        assertThat(lecturas).as("a las 23:59:59 de Lima sigue en memoria").hasValue(1);

        reloj.poner(Instant.parse("2026-10-19T05:00:00Z"));
        nombreDe(ANA, DIA_18, LIMA);
        assertThat(lecturas).as("a las 00:00:00 de Lima del 19 venció y se lee otra vez").hasValue(2);
    }

    @Test
    @DisplayName("no vence a la medianoche UTC: guardado a las 18:30 de Lima del 18, sigue pasadas las 00:00 UTC del 19")
    void noVenceALaMedianocheUtc() {
        reloj.poner(Instant.parse("2026-10-18T23:30:00Z"));
        nombreDe(ANA, DIA_18, LIMA);

        reloj.poner(Instant.parse("2026-10-19T00:30:00Z"));
        nombreDe(ANA, DIA_18, LIMA);

        assertThat(lecturas).as("a las 19:30 de Lima del 18 todavía es el mismo día").hasValue(1);
    }

    @Test
    @DisplayName("vence a la medianoche de la zona de cada persona, no a la de Lima: Madrid (UTC+2 en octubre)")
    void venceEnLaZonaDeLaPersona() {
        ZoneId madrid = ZoneId.of("Europe/Madrid");
        reloj.poner(Instant.parse("2026-10-18T20:00:00Z"));
        nombreDe(ANA, DIA_18, madrid);

        reloj.poner(Instant.parse("2026-10-18T21:59:59Z"));
        nombreDe(ANA, DIA_18, madrid);
        assertThat(lecturas).as("23:59:59 en Madrid").hasValue(1);

        reloj.poner(Instant.parse("2026-10-18T22:00:00Z"));
        nombreDe(ANA, DIA_18, madrid);
        assertThat(lecturas).as("00:00:00 del 19 en Madrid").hasValue(2);
    }

    @Test
    @DisplayName("la entrada de un día que ya pasó no se guarda: cada lectura va a la fuente")
    void unDiaPasadoNoSeGuarda() {
        reloj.poner(Instant.parse("2026-10-19T15:00:00Z"));

        nombreDe(ANA, DIA_18, LIMA);
        nombreDe(ANA, DIA_18, LIMA);

        assertThat(lecturas).hasValue(2);
    }

    @Test
    @DisplayName("está acotada: con muchas más personas que el tope no crece sin límite")
    void estaAcotada() {
        for (int i = 0; i < DatosEstablesDelDia.MAXIMO_DE_ENTRADAS + 500; i++) {
            nombreDe(UserId.of(new UUID(0, i)), DIA_18, LIMA);
        }

        assertThat(datos.guardadas()).isLessThanOrEqualTo(DatosEstablesDelDia.MAXIMO_DE_ENTRADAS);
    }

    private static final class RelojAjustable implements Clock {

        private volatile Instant ahora;

        RelojAjustable(Instant ahora) {
            this.ahora = ahora;
        }

        void poner(Instant nuevo) {
            this.ahora = nuevo;
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

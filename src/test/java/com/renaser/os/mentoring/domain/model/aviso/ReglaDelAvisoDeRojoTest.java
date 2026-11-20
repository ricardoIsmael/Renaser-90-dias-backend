package com.renaser.os.mentoring.domain.model.aviso;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Cuándo sale y cómo se deduplica el aviso de rojo (spec 057, FR-031 y FR-032). */
class ReglaDelAvisoDeRojoTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Test
    @DisplayName("spec 057 FR-031: toca de las 19:00 a las 19:29 de la zona del mentor; las 18:59 y las 19:30 no")
    void laVentanaDeLas19() { // spec 057 FR-031
        // Lima es UTC-5: las 19:00 locales son las 00:00 UTC del día siguiente (la madrugada UTC que esconde el bug de la regla 03).
        assertThat(ReglaDelAvisoDeRojo.toca(Instant.parse("2026-11-21T00:00:00Z"), LIMA)).isTrue();
        assertThat(ReglaDelAvisoDeRojo.toca(Instant.parse("2026-11-21T00:29:59Z"), LIMA)).isTrue();
        assertThat(ReglaDelAvisoDeRojo.toca(Instant.parse("2026-11-20T23:59:59Z"), LIMA)).isFalse();
        assertThat(ReglaDelAvisoDeRojo.toca(Instant.parse("2026-11-21T00:30:00Z"), LIMA)).isFalse();
    }

    @Test
    @DisplayName("spec 057 FR-031: en una zona de media hora (Kolkata) y de 45 minutos (Katmandú) la ventana también cae en una corrida de :00 o :30 UTC")
    void zonasConDesfaseDeMinutos() { // spec 057 FR-031
        // 19:00 en Kolkata (+5:30) = 13:30 UTC; 19:00 en Katmandú (+5:45) = 13:15 UTC, y su ventana llega hasta las 13:44 UTC.
        assertThat(ReglaDelAvisoDeRojo.toca(Instant.parse("2026-11-20T13:30:00Z"), ZoneId.of("Asia/Kolkata"))).isTrue();
        assertThat(ReglaDelAvisoDeRojo.toca(Instant.parse("2026-11-20T13:30:00Z"), ZoneId.of("Asia/Kathmandu"))).isTrue();
        assertThat(ReglaDelAvisoDeRojo.toca(Instant.parse("2026-11-20T13:00:00Z"), ZoneId.of("Asia/Kathmandu"))).isFalse();
    }

    @Test
    @DisplayName("spec 057 FR-032: la clave es la misma toda la semana del aprendiz (lunes a domingo) y cambia por mentor, por aprendiz y por semana")
    void unAvisoPorSemana() { // spec 057 FR-032
        UUID mentor = UUID.randomUUID();
        UUID aprendiz = UUID.randomUUID();
        LocalDate lunes = LocalDate.of(2026, 11, 16);

        assertThat(ReglaDelAvisoDeRojo.lunesDe(LocalDate.of(2026, 11, 20))).isEqualTo(lunes);
        assertThat(ReglaDelAvisoDeRojo.lunesDe(LocalDate.of(2026, 11, 22))).as("el domingo sigue en la misma semana").isEqualTo(lunes);
        assertThat(ReglaDelAvisoDeRojo.lunesDe(lunes)).isEqualTo(lunes);

        UUID clave = ReglaDelAvisoDeRojo.clave(mentor, aprendiz, lunes);
        assertThat(ReglaDelAvisoDeRojo.clave(mentor, aprendiz, lunes)).isEqualTo(clave);
        assertThat(ReglaDelAvisoDeRojo.clave(mentor, aprendiz, lunes.plusWeeks(1))).isNotEqualTo(clave);
        assertThat(ReglaDelAvisoDeRojo.clave(mentor, UUID.randomUUID(), lunes)).isNotEqualTo(clave);
        assertThat(ReglaDelAvisoDeRojo.clave(UUID.randomUUID(), aprendiz, lunes)).isNotEqualTo(clave);
        assertThat(clave).isEqualTo(UUID.nameUUIDFromBytes((mentor + "|" + aprendiz + "|ROJO_DEL_DIA|2026-11-16").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}

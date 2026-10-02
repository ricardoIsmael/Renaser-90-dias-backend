package com.renaser.os.chat.domain.model.conversacion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorDeSoportesTest {

    @Test
    @DisplayName("D-249: el cursor vuelve igual, con los microsegundos de la base")
    void idaYVuelta() {
        CursorDeSoportes cursor = new CursorDeSoportes(Instant.parse("2026-10-02T05:00:00.123456Z"),
                ConversacionId.of(UUID.randomUUID()));

        assertThat(CursorDeSoportes.leer(cursor.escribir())).isEqualTo(cursor);
        assertThat(cursor.escribir()).doesNotContain("|", "=", "+", "/");
    }

    @Test
    @DisplayName("D-249: un cursor inventado es un error de quien llama (400), no un 500")
    void cursorInventado() {
        for (String roto : new String[] {"no-es-un-cursor", "", "fA", "MjAyNnx4"}) {
            assertThatThrownBy(() -> CursorDeSoportes.leer(roto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Cursor de soportes inválido");
        }
    }
}

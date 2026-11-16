package com.renaser.os.shared.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La ruta que va al registro no lleva ids ni nada que parta una línea (spec 050, FR-019).
 */
class PatronDeRutaTest {

    // spec 050 FR-019
    @Test
    @DisplayName("los ids (UUID, números, hexadecimales largos) quedan como {id} y los nombres de ruta se conservan")
    void sinIds() {
        assertThat(PatronDeRuta.sinIds("/api/v1/wall/3f2a9c1e-7b4d-4e0a-9d5c-1a2b3c4d5e6f/comments"))
                .isEqualTo("/api/v1/wall/{id}/comments");
        assertThat(PatronDeRuta.sinIds("/api/v1/habits/12345/rename")).isEqualTo("/api/v1/habits/{id}/rename");
        assertThat(PatronDeRuta.sinIds("/api/v1/x/0123456789abcdef0123456789abcdef")).isEqualTo("/api/v1/x/{id}");
        assertThat(PatronDeRuta.sinIds("/api/v1/admin/caja/export.csv")).isEqualTo("/api/v1/admin/caja/export.csv");
        assertThat(PatronDeRuta.sinIds("/api/v1/analisis-80-20")).isEqualTo("/api/v1/analisis-80-20");
    }

    // spec 050 FR-019
    @Test
    @DisplayName("lo que escribe quien pide no sale tal cual: segmentos raros o muy largos y rutas larguísimas quedan acotados")
    void loQueEscribeQuienPideQuedaAcotado() {
        assertThat(PatronDeRuta.sinIds("/api/v1/x/a b\r\nINFO falso")).isEqualTo("/api/v1/x/{id}");
        assertThat(PatronDeRuta.sinIds("/api/v1/x/" + "a".repeat(41))).isEqualTo("/api/v1/x/{id}");
        assertThat(PatronDeRuta.sinIds("/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p").split("/")).hasSize(12);
        assertThat(PatronDeRuta.sinIds(null)).isEqualTo("/");
        assertThat(PatronDeRuta.sinIds("")).isEqualTo("/");
    }
}

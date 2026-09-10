package com.renaser.os.community.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.community.api.GrupoPorVencerEvent;
import com.renaser.os.community.application.ports.in.celula.DetectarGruposPorVencerUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El aviso de grupo por vencer, contra Postgres de verdad.
 *
 * <p><b>Por qué hace falta la base.</b> Lo que aquí se demuestra no es la regla —eso ya lo cubren
 * {@code ReglasDeVencimientoDeGrupoTest} y {@code AvisosDeVencimientoServiceTest} con dobles— sino
 * que la CONSULTA encuentra los grupos correctos: un `BETWEEN` sobre columnas `date` nulables, con
 * el filtro que deja fuera a las células sin periodo. Eso solo lo responde el motor.
 *
 * <p>Y responde la pregunta que el dueño del proyecto hizo antes de desplegar: si el aviso
 * funciona. El barrido corre a las 11:10 UTC una vez al día, así que esperar a verlo en vivo
 * significaba esperar a mañana.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, AvisoDeGrupoPorVencerIT.CapturaDeEventos.class})
class AvisoDeGrupoPorVencerIT {

    @Autowired
    private DetectarGruposPorVencerUseCase detectar;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private CapturaDeEventos capturados;

    private UUID cohorteId;

    @BeforeEach
    void seedCohorte() {
        capturados.eventos.clear();
        cohorteId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.cohortes (id, nombre, fecha_inicio)
                VALUES (?, 'Cohorte aviso', CURRENT_DATE)
                """, cohorteId);
    }

    @AfterEach
    void limpiar() {
        jdbcTemplate.update("DELETE FROM renaser.celulas WHERE cohorte_id = ?", cohorteId);
        jdbcTemplate.update("DELETE FROM renaser.cohortes WHERE id = ?", cohorteId);
    }

    private UUID celula(String nombre, int diasHastaElCierre) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.celulas (id, nombre, cohorte_id, tipo, periodo_inicio, periodo_fin)
                VALUES (?, ?, ?, CAST('REGULAR' AS renaser.tipo_celula),
                        CURRENT_DATE - 20, CURRENT_DATE + ?)
                """, id, nombre, cohorteId, diasHastaElCierre);
        return id;
    }

    private UUID celulaSinPeriodo(String nombre) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.celulas (id, nombre, cohorte_id, tipo)
                VALUES (?, ?, ?, CAST('REGULAR' AS renaser.tipo_celula))
                """, id, nombre, cohorteId);
        return id;
    }

    @Test
    @DisplayName("Un grupo que cierra dentro de la ventana genera aviso; los de fuera, no")
    void avisaSoloDeLosQueEstanPorVencer() {
        celula("Fenix cierra en 3", 3);
        celula("Aun lejos, cierra en 20", 20);
        celulaSinPeriodo("Sin periodo, no vence nunca");

        detectar.avisarDeLosQueVencen();

        assertThat(capturados.eventos)
                .extracting(GrupoPorVencerEvent::nombreDelGrupo)
                .as("solo el que entra en la ventana de 7 dias")
                .containsExactly("Fenix cierra en 3");
        assertThat(capturados.eventos).singleElement().satisfies(e -> {
            assertThat(e.diasRestantes())
                    .as("contando hoy: del dia 0 al dia 3 son 4")
                    .isEqualTo(4);
            assertThat(e.rutaApp()).startsWith("/admin/cells/");
        });
    }

    /**
     * Repetir el barrido el mismo dia no cambia la clave. Es lo que permite que corra todos los
     * dias de la ventana sin llenarle la bandeja al administrador: el indice unico de
     * `notificaciones` por (usuario, tipo, origen) rechaza la segunda.
     */
    @Test
    @DisplayName("Dos barridos seguidos producen la misma clave de deduplicacion")
    void barrerDosVecesNoCambiaLaClave() {
        celula("Fenix", 2);

        detectar.avisarDeLosQueVencen();
        detectar.avisarDeLosQueVencen();

        assertThat(capturados.eventos).hasSize(2);
        assertThat(capturados.eventos.get(1).claveDeduplicacion())
                .isEqualTo(capturados.eventos.get(0).claveDeduplicacion());
    }

    /** Escucha SINCRONA a proposito: `@ApplicationModuleListener` es async y haria la prueba floja. */
    @TestConfiguration
    static class CapturaDeEventos {

        final List<GrupoPorVencerEvent> eventos = new ArrayList<>();

        @EventListener
        void on(GrupoPorVencerEvent event) {
            eventos.add(event);
        }
    }
}

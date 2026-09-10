package com.renaser.os.community.domain.model.celula;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Cuándo se le avisa al administrador de que a un grupo se le acaba el periodo. */
class ReglasDeVencimientoDeGrupoTest {

    private static final UUID GRUPO = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final PeriodoGrupo SEPTIEMBRE = new PeriodoGrupo(
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

    @Test
    @DisplayName("Se avisa durante TODA la ventana, no solo el dia exacto del umbral")
    void seAvisaDuranteTodaLaVentana() {
        // Siete dias de antelacion: el 24 quedan 7 (contando hoy), asi que ya toca.
        assertThat(ReglasDeVencimientoDeGrupo.tocaAvisar(SEPTIEMBRE, LocalDate.of(2026, 9, 24))).isTrue();
        assertThat(ReglasDeVencimientoDeGrupo.tocaAvisar(SEPTIEMBRE, LocalDate.of(2026, 9, 27))).isTrue();
        assertThat(ReglasDeVencimientoDeGrupo.tocaAvisar(SEPTIEMBRE, LocalDate.of(2026, 9, 30)))
                .as("el ultimo dia todavia se avisa")
                .isTrue();
    }

    /**
     * La razon de que la ventana sea un rango y no un dia: si el barrido no corre un dia
     * —despliegue, caida—, un aviso atado al dia exacto se pierde y nadie se entera de que se
     * perdio. El spam lo evita la clave de deduplicacion, no esta regla.
     */
    @Test
    @DisplayName("Fuera de la ventana no se avisa: ni antes de tiempo ni cuando ya vencio")
    void fueraDeLaVentanaNoSeAvisa() {
        assertThat(ReglasDeVencimientoDeGrupo.tocaAvisar(SEPTIEMBRE, LocalDate.of(2026, 9, 23)))
                .as("quedan 8: todavia no")
                .isFalse();
        assertThat(ReglasDeVencimientoDeGrupo.tocaAvisar(SEPTIEMBRE, LocalDate.of(2026, 8, 20)))
                .as("el grupo ni siquiera empezo")
                .isFalse();
        assertThat(ReglasDeVencimientoDeGrupo.tocaAvisar(SEPTIEMBRE, LocalDate.of(2026, 10, 1)))
                .as("ya vencio: avisar llega tarde y el admin ya no previene nada")
                .isFalse();
    }

    @Test
    @DisplayName("Un grupo sin periodo no vence, asi que no se avisa")
    void sinPeriodoNoSeAvisa() {
        assertThat(ReglasDeVencimientoDeGrupo.tocaAvisar(null, LocalDate.of(2026, 9, 28))).isFalse();
    }

    /**
     * Lo que impide el aviso diario. El barrido corre todos los dias de la ventana a proposito, y
     * es esta clave —anclada al grupo y a su cierre, no al dia de la deteccion— la que hace que el
     * administrador reciba UNO.
     */
    @Test
    @DisplayName("La clave es la MISMA todos los dias de la ventana: un aviso por grupo y periodo")
    void laClaveNoDependeDelDiaEnQueSeDetecta() {
        UUID elDia24 = ReglasDeVencimientoDeGrupo.claveDeDeduplicacion(GRUPO, SEPTIEMBRE.fin());
        UUID elDia29 = ReglasDeVencimientoDeGrupo.claveDeDeduplicacion(GRUPO, SEPTIEMBRE.fin());

        assertThat(elDia24).isEqualTo(elDia29);
    }

    @Test
    @DisplayName("Si el admin mueve la fecha de cierre, se avisa de nuevo: es otro episodio")
    void moverElCierreVuelveAAvisar() {
        UUID original = ReglasDeVencimientoDeGrupo.claveDeDeduplicacion(GRUPO, LocalDate.of(2026, 9, 30));
        UUID movido = ReglasDeVencimientoDeGrupo.claveDeDeduplicacion(GRUPO, LocalDate.of(2026, 10, 15));

        assertThat(movido)
                .as("el aviso anterior hablaba de una fecha que ya no existe")
                .isNotEqualTo(original);
    }

    @Test
    @DisplayName("Dos grupos distintos no comparten aviso")
    void gruposDistintosNoSeMezclan() {
        assertThat(ReglasDeVencimientoDeGrupo.claveDeDeduplicacion(GRUPO, SEPTIEMBRE.fin()))
                .isNotEqualTo(ReglasDeVencimientoDeGrupo.claveDeDeduplicacion(UUID.randomUUID(), SEPTIEMBRE.fin()));
    }

    @Test
    @DisplayName("El texto dice cuantos dias quedan, y el ultimo dia dice 'hoy'")
    void elTextoEsAccionable() {
        assertThat(ReglasDeVencimientoDeGrupo.cuerpoDelAviso("Fenix", 5))
                .contains("Fenix").contains("5 dias");
        assertThat(ReglasDeVencimientoDeGrupo.cuerpoDelAviso("Fenix", 1))
                .as("'le queda 1 dias' se lee mal; el ultimo dia se dice distinto")
                .contains("termina hoy");
    }
}

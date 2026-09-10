package com.renaser.os.community.domain.model.acompanamiento;

import com.renaser.os.community.domain.model.cohorte.CohorteId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoliticaMentoriaTest {

    private static final CohorteId COHORTE = CohorteId.of(UUID.randomUUID());

    @Test
    @DisplayName("una cohorte sin fila configurada opera con los defaults, no revienta")
    void defaultsCuandoNoHayFila() {
        PoliticaMentoria politica = PoliticaMentoria.porDefecto(COHORTE);

        assertThat(politica.capacidadCelula()).isEqualTo(10);
        assertThat(politica.cadenciaRotacion()).isEqualTo(CadenciaRotacion.MENSUAL);
        assertThat(politica.diaTraslado()).isEqualTo(4);
        assertThat(politica.diasSinActividadAlerta()).isEqualTo(3);
        assertThat(politica.zonaHoraria()).isEqualTo("America/Lima");
        assertThat(politica.persistida()).isFalse();
    }

    @Test
    @DisplayName("los dias 1 a 3 son recepcion; desde el 4 corresponde el grupo estable (P-01)")
    void diaDeTraslado() {
        PoliticaMentoria politica = PoliticaMentoria.porDefecto(COHORTE);

        assertThat(politica.correspondeTraslado(0)).isFalse();
        assertThat(politica.correspondeTraslado(1)).isFalse();
        assertThat(politica.correspondeTraslado(3)).isFalse();
        assertThat(politica.correspondeTraslado(4)).isTrue();
        assertThat(politica.correspondeTraslado(30)).isTrue();
    }

    @Test
    @DisplayName("a las 04:59 UTC en Lima todavia es el dia anterior (V05)")
    void elDiaLocalNoEsElDelServidor() {
        PoliticaMentoria politica = PoliticaMentoria.porDefecto(COHORTE);

        assertThat(politica.fechaLocalDe(Instant.parse("2026-09-10T04:59:00Z")))
                .isEqualTo(LocalDate.of(2026, 9, 9));
        assertThat(politica.fechaLocalDe(Instant.parse("2026-09-10T05:01:00Z")))
                .isEqualTo(LocalDate.of(2026, 9, 10));
    }

    @Test
    @DisplayName("la rotacion mensual cae el dia 1, tambien saliendo de un mes corto")
    void anclajeMensual() {
        assertThat(CadenciaRotacion.MENSUAL.proximaFechaDespuesDe(LocalDate.of(2026, 9, 9)))
                .isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(CadenciaRotacion.MENSUAL.proximaFechaDespuesDe(LocalDate.of(2026, 2, 28)))
                .isEqualTo(LocalDate.of(2026, 3, 1));
        // Ya es dia de anclaje: la proxima es la del mes que viene, no hoy otra vez.
        assertThat(CadenciaRotacion.MENSUAL.proximaFechaDespuesDe(LocalDate.of(2026, 10, 1)))
                .isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    @DisplayName("la rotacion semanal cae el lunes")
    void anclajeSemanal() {
        assertThat(CadenciaRotacion.SEMANAL.proximaFechaDespuesDe(LocalDate.of(2026, 9, 9)))
                .isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(CadenciaRotacion.SEMANAL.proximaFechaDespuesDe(LocalDate.of(2026, 9, 14)))
                .isEqualTo(LocalDate.of(2026, 9, 21));
    }

    @Test
    @DisplayName("editar con una version vieja no pisa el cambio del otro administrador")
    void controlDeVersion() {
        PoliticaMentoria politica = PoliticaMentoria.rehydrate(COHORTE, 10, CadenciaRotacion.MENSUAL,
                "America/Lima", 4, 3, null, 3);

        assertThatThrownBy(() -> politica.reconfigurar(12, CadenciaRotacion.SEMANAL, "America/Lima", 4, 3, 2))
                .isInstanceOf(PoliticaDesactualizadaException.class);

        politica.reconfigurar(12, CadenciaRotacion.SEMANAL, "America/Lima", 4, 3, 3);
        assertThat(politica.capacidadCelula()).isEqualTo(12);
        assertThat(politica.version()).isEqualTo(4);
    }

    @Test
    @DisplayName("la capacidad usa el mismo limite que el cupo: una sola definicion de 10..15")
    void capacidadFueraDeRango() {
        PoliticaMentoria politica = PoliticaMentoria.porDefecto(COHORTE);

        assertThatThrownBy(() -> politica.reconfigurar(16, CadenciaRotacion.MENSUAL, "America/Lima", 4, 3, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("una zona horaria inventada se rechaza al configurar, no al usarla")
    void zonaInvalida() {
        PoliticaMentoria politica = PoliticaMentoria.porDefecto(COHORTE);

        assertThatThrownBy(() -> politica.reconfigurar(10, CadenciaRotacion.MENSUAL, "America/Nowhere", 4, 3, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("el override de la celula gana sobre la capacidad de la cohorte")
    void overridePorCelula() {
        PoliticaMentoria politica = PoliticaMentoria.porDefecto(COHORTE);

        assertThat(politica.cupoRegular(null).maximo()).contains(10);
        assertThat(politica.cupoRegular(15).maximo()).contains(15);
    }
}

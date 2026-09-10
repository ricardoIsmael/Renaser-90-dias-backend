package com.renaser.os.community.application.services;

import com.renaser.os.community.api.GrupoPorVencerEvent;
import com.renaser.os.community.application.ports.out.celula.ConsultarGruposPorVencerPort;
import com.renaser.os.community.application.ports.out.celula.ConsultarGruposPorVencerPort.GrupoQueVence;
import com.renaser.os.shared.domain.FixedClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El barrido que avisa al administrador de los grupos que cierran.
 *
 * <p>Sin mocks de Spring: el puerto y el publicador son dobles a mano, porque lo que se mide es
 * la decisión —a quién se avisa y con qué contenido—, no el cableado.
 */
class AvisosDeVencimientoServiceTest {

    /** 26 de septiembre, 15:00 UTC = 10:00 en Lima. Mismo día en las dos zonas, sin ambigüedad. */
    private static final Instant AHORA = Instant.parse("2026-09-26T15:00:00Z");
    private static final UUID FENIX = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private final List<Object> publicados = new ArrayList<>();

    private AvisosDeVencimientoService servicioCon(GrupoQueVence... grupos) {
        ConsultarGruposPorVencerPort puerto = (desde, hasta) -> List.of(grupos);
        return new AvisosDeVencimientoService(puerto, publicados::add, FixedClock.at(AHORA));
    }

    private static GrupoQueVence grupo(LocalDate inicio, LocalDate fin) {
        return new GrupoQueVence(FENIX, "Fenix", inicio, fin);
    }

    @Test
    @DisplayName("Un grupo dentro de la ventana genera aviso, con los dias que quedan contando hoy")
    void avisaDeLoQueVence() {
        AvisosDeVencimientoService servicio = servicioCon(
                grupo(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));

        assertThat(servicio.avisarDeLosQueVencen()).isEqualTo(1);
        assertThat(publicados).singleElement().isInstanceOfSatisfying(GrupoPorVencerEvent.class, e -> {
            assertThat(e.nombreDelGrupo()).isEqualTo("Fenix");
            assertThat(e.diasRestantes())
                    .as("del 26 al 30, contando hoy, son 5")
                    .isEqualTo(5);
            assertThat(e.rutaApp()).isEqualTo("/admin/cells/" + FENIX);
        });
    }

    /**
     * La consulta ya acota la ventana, pero el servicio vuelve a preguntarle a las reglas. Esta
     * prueba fija que mande la REGLA: si algún día la consulta trae de más —un BETWEEN mal
     * calculado, un huso corrido— el aviso no sale igual.
     */
    @Test
    @DisplayName("Aunque la consulta traiga uno que ya vencio, la regla lo descarta")
    void laReglaMandaSobreLaConsulta() {
        AvisosDeVencimientoService servicio = servicioCon(
                grupo(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));

        assertThat(servicio.avisarDeLosQueVencen()).isZero();
        assertThat(publicados).isEmpty();
    }

    @Test
    @DisplayName("Un grupo que todavia no empezo no se avisa: futuro no es vencido")
    void noAvisaDeLoQueNiEmpezo() {
        AvisosDeVencimientoService servicio = servicioCon(
                grupo(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));

        assertThat(servicio.avisarDeLosQueVencen()).isZero();
    }

    /**
     * Lo que hace que el barrido pueda correr todos los días de la ventana sin molestar a nadie.
     * La clave se ancla al grupo y a su cierre, no al día de la detección.
     */
    @Test
    @DisplayName("Dos corridas en dias distintos producen la MISMA clave de deduplicacion")
    void laClaveNoDependeDelDiaDeLaCorrida() {
        GrupoQueVence fenix = grupo(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        ConsultarGruposPorVencerPort puerto = (desde, hasta) -> List.of(fenix);

        new AvisosDeVencimientoService(puerto, publicados::add, FixedClock.at(AHORA))
                .avisarDeLosQueVencen();
        new AvisosDeVencimientoService(puerto, publicados::add,
                FixedClock.at(Instant.parse("2026-09-29T15:00:00Z"))).avisarDeLosQueVencen();

        assertThat(publicados).hasSize(2);
        GrupoPorVencerEvent primero = (GrupoPorVencerEvent) publicados.get(0);
        GrupoPorVencerEvent segundo = (GrupoPorVencerEvent) publicados.get(1);
        assertThat(segundo.claveDeduplicacion())
                .as("misma clave: el indice unico de notificaciones entrega UNA sola")
                .isEqualTo(primero.claveDeduplicacion());
        assertThat(segundo.diasRestantes())
                .as("pero el texto SI cambia: quedan menos dias")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("Sin grupos por vencer no se publica nada")
    void sinGruposNoPublicaNada() {
        assertThat(servicioCon().avisarDeLosQueVencen()).isZero();
        assertThat(publicados).isEmpty();
    }
}

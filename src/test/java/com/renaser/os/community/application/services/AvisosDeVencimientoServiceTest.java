package com.renaser.os.community.application.services;

import com.renaser.os.community.api.GrupoPorVencerEvent;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadPoliticaMentoriaPort;
import com.renaser.os.community.application.ports.out.celula.ConsultarGruposPorVencerPort;
import com.renaser.os.community.application.ports.out.celula.ConsultarGruposPorVencerPort.GrupoQueVence;
import com.renaser.os.community.domain.model.acompanamiento.CadenciaRotacion;
import com.renaser.os.community.domain.model.acompanamiento.PoliticaMentoria;
import com.renaser.os.shared.domain.FixedClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    private static final UUID COHORTE = UUID.fromString("99999999-2222-3333-4444-555555555555");
    private static final UUID FENIX = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private final List<Object> publicados = new ArrayList<>();

    private AvisosDeVencimientoService servicioCon(GrupoQueVence... grupos) {
        return servicioA(AHORA, SIN_POLITICA, grupos);
    }

    private AvisosDeVencimientoService servicioA(Instant ahora, LoadPoliticaMentoriaPort politicas,
                                                  GrupoQueVence... grupos) {
        ConsultarGruposPorVencerPort puerto = (desde, hasta) -> List.of(grupos);
        return new AvisosDeVencimientoService(puerto, politicas, publicados::add, FixedClock.at(ahora));
    }

    /** Sin politica guardada la cohorte usa la zona por defecto: America/Lima. */
    private static final LoadPoliticaMentoriaPort SIN_POLITICA = cohorte -> Optional.empty();

    private static LoadPoliticaMentoriaPort cohorteEnZona(String zona) {
        return cohorte -> Optional.of(PoliticaMentoria.rehydrate(cohorte, PoliticaMentoria.CAPACIDAD_POR_DEFECTO,
                CadenciaRotacion.MENSUAL, zona, PoliticaMentoria.DIA_TRASLADO_POR_DEFECTO,
                PoliticaMentoria.DIAS_SIN_ACTIVIDAD_POR_DEFECTO, null, 1));
    }

    private static GrupoQueVence grupo(LocalDate inicio, LocalDate fin) {
        return new GrupoQueVence(FENIX, COHORTE, "Fenix", inicio, fin);
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

        servicioA(AHORA, SIN_POLITICA, fenix).avisarDeLosQueVencen();
        servicioA(Instant.parse("2026-09-29T15:00:00Z"), SIN_POLITICA, fenix).avisarDeLosQueVencen();

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

    // ---- E-565: el dia de «hoy» es el de la zona de la cohorte del grupo, no el del servidor ----

    @Test
    @DisplayName("Lima de madrugada UTC (02:00Z = 21:00 del dia anterior): cuenta desde el hoy de Lima")
    void limaDeMadrugadaUtcCuentaDesdeElHoyDeLima() {
        // 2026-09-26T02:00Z es el 25/09 a las 21:00 en Lima: del 25 al 30, contando hoy, son 6.
        servicioA(Instant.parse("2026-09-26T02:00:00Z"), SIN_POLITICA,
                grupo(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))).avisarDeLosQueVencen();

        assertThat(publicados).singleElement().isInstanceOfSatisfying(GrupoPorVencerEvent.class,
                e -> assertThat(e.diasRestantes()).isEqualTo(6));
    }

    @Test
    @DisplayName("Cohorte en Tokio, 05:00 del 26/09 alla (20:00Z del 25 en UTC): ya es su dia 26 y el grupo entra en la ventana")
    void cohorteEnTokioYaVeSuDiaSiguiente() {
        // Cierra el 02/10: desde el 26 (Tokio) quedan 7 y toca avisar; con el 25 de Lima/UTC serian 8 y no.
        servicioA(Instant.parse("2026-09-25T20:00:00Z"), cohorteEnZona("Asia/Tokyo"),
                grupo(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 2))).avisarDeLosQueVencen();

        assertThat(publicados).singleElement().isInstanceOfSatisfying(GrupoPorVencerEvent.class,
                e -> assertThat(e.diasRestantes()).isEqualTo(7));
    }

    @Test
    @DisplayName("Cohorte en Los Angeles, 23:30 del 25/09 alla (01:30 del 26 en Lima): todavia no entra en la ventana")
    void cohorteEnLosAngelesTodaviaVeSuDiaAnterior() {
        // Cierra el 02/10: en Lima ya serian 7 (avisaria); en Los Angeles sigue siendo el 25, son 8.
        servicioA(Instant.parse("2026-09-26T06:30:00Z"), cohorteEnZona("America/Los_Angeles"),
                grupo(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 2))).avisarDeLosQueVencen();

        assertThat(publicados).isEmpty();
    }

    @Test
    @DisplayName("Un grupo cuyo cierre es ayer en Tokio pero hoy en UTC no se avisa (ya vencio alla)")
    void elGrupoQueYaVencioEnSuZonaNoSeAvisa() {
        // Cierra el 25/09; en Tokio ya es el 26 (vencido), en UTC y en Lima todavia es el ultimo dia.
        servicioA(Instant.parse("2026-09-25T20:00:00Z"), cohorteEnZona("Asia/Tokyo"),
                grupo(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 25))).avisarDeLosQueVencen();

        assertThat(publicados).isEmpty();
    }
}

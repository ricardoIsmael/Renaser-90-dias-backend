package com.renaser.os.evidence.domain.model.evidencia;

import com.renaser.os.evidence.api.DestinoEvidencia;
import com.renaser.os.evidence.api.EstadoValidacion;
import com.renaser.os.evidence.api.TipoEvidencia;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidenciaTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-25T12:00:00Z"));

    private static UserId participante() {
        return UserId.of(UUID.randomUUID());
    }

    private static DestinoEvidencia.RocaDiaria destinoRoca() {
        return new DestinoEvidencia.RocaDiaria(UUID.randomUUID());
    }

    private static DestinoEvidencia.RegistroHabito destinoHabito() {
        return new DestinoEvidencia.RegistroHabito(UUID.randomUUID());
    }

    private Evidencia evidenciaTexto() {
        return Evidencia.registrar(participante(), destinoRoca(), TipoEvidencia.TEXTO, null, null, "hecho", null,
                null, null, true, CLOCK.now(), CLOCK);
    }

    // ---- arco exclusivo (DestinoEvidencia sealed) ----

    @Test
    @DisplayName("DestinoEvidencia.RocaDiaria rechaza id null")
    void destinoRocaRechazaNull() {
        assertThatThrownBy(() -> new DestinoEvidencia.RocaDiaria(null)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- media-o-texto ----

    @Test
    @DisplayName("TEXTO sin contenidoTexto es rechazado")
    void textoSinContenidoRechazado() {
        assertThatThrownBy(() -> Evidencia.registrar(participante(), destinoHabito(), TipoEvidencia.TEXTO, null,
                null, null, null, null, null, false, CLOCK.now(), CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contenidoTexto");
    }

    @Test
    @DisplayName("FOTO sin bucket/rutaStorage es rechazada")
    void fotoSinBucketRechazada() {
        assertThatThrownBy(() -> Evidencia.registrar(participante(), destinoHabito(), TipoEvidencia.FOTO, null,
                null, null, Instant.now(), null, null, false, CLOCK.now(), CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bucket");
    }

    @Test
    void fotoConBucketYRutaEsValida() {
        Evidencia e = Evidencia.registrar(participante(), destinoHabito(), TipoEvidencia.FOTO, "bucket", "ruta",
                null, Instant.now(), null, null, false, CLOCK.now(), CLOCK);
        assertThat(e.tipo()).isEqualTo(TipoEvidencia.FOTO);
        assertThat(e.estadoValidacion()).isEqualTo(EstadoValidacion.PENDIENTE);
    }

    // ---- gps coherente ----

    @Test
    void gpsSoloLatitudEsRechazado() {
        assertThatThrownBy(() -> Evidencia.registrar(participante(), destinoHabito(), TipoEvidencia.TEXTO, null,
                null, "hecho", null, -12.05, null, false, CLOCK.now(), CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void gpsFueraDeRangoEsRechazado() {
        assertThatThrownBy(() -> Evidencia.registrar(participante(), destinoHabito(), TipoEvidencia.TEXTO, null,
                null, "hecho", null, 200.0, 0.0, false, CLOCK.now(), CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void gpsCompletoYValidoEsAceptado() {
        Evidencia e = Evidencia.registrar(participante(), destinoHabito(), TipoEvidencia.TEXTO, null, null, "hecho",
                null, -12.05, -77.03, false, CLOCK.now(), CLOCK);
        assertThat(e.gpsLat()).isEqualTo(-12.05);
        assertThat(e.gpsLng()).isEqualTo(-77.03);
    }

    // ---- esPrincipal solo en Roca ----

    @Test
    @DisplayName("esPrincipal=true en un destino que no es RocaDiaria es rechazado")
    void esPrincipalSoloEnRocaRechazaOtroDestino() {
        assertThatThrownBy(() -> Evidencia.registrar(participante(), destinoHabito(), TipoEvidencia.TEXTO, null,
                null, "hecho", null, null, null, true, CLOCK.now(), CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("esPrincipal");
    }

    @Test
    void esPrincipalTrueEnRocaEsAceptado() {
        Evidencia e = evidenciaTexto();
        assertThat(e.esPrincipal()).isTrue();
    }

    // ---- estado inicial ----

    @Test
    void nacePendienteConCeroIntentos() {
        Evidencia e = evidenciaTexto();
        assertThat(e.estadoValidacion()).isEqualTo(EstadoValidacion.PENDIENTE);
        assertThat(e.intentosIa()).isZero();
        assertThat(e.penalizacionAplicada()).isFalse();
        assertThat(e.publicadaEnMuro()).isFalse();
    }

    // ---- maquina de estados de validacion ----

    @Test
    void aprobarPorIaPasaAValida() {
        Evidencia e = evidenciaTexto();
        e.aprobarPorIa();
        assertThat(e.estadoValidacion()).isEqualTo(EstadoValidacion.VALIDA);
    }

    @Test
    void rechazarPorIaPasaARechazadaConNotas() {
        Evidencia e = evidenciaTexto();
        e.rechazarPorIa("no coincide con el habito");
        assertThat(e.estadoValidacion()).isEqualTo(EstadoValidacion.RECHAZADA);
        assertThat(e.notasValidacion()).isEqualTo("no coincide con el habito");
    }

    @Test
    @DisplayName("3 intentos fallidos consecutivos -> REVISION_MANUAL (fallback humano)")
    void tresIntentosFallidosCaeARevisionManual() {
        Evidencia e = evidenciaTexto();
        e.registrarIntentoFallido();
        assertThat(e.estadoValidacion()).isEqualTo(EstadoValidacion.PENDIENTE);
        assertThat(e.intentosIa()).isEqualTo(1);

        e.registrarIntentoFallido();
        assertThat(e.estadoValidacion()).isEqualTo(EstadoValidacion.PENDIENTE);
        assertThat(e.intentosIa()).isEqualTo(2);

        e.registrarIntentoFallido();
        assertThat(e.estadoValidacion()).isEqualTo(EstadoValidacion.REVISION_MANUAL);
        assertThat(e.intentosIa()).isEqualTo(3);
    }

    @Test
    void noSePuedeAprobarUnaEvidenciaQueYaNoEstaPendiente() {
        Evidencia e = evidenciaTexto();
        e.aprobarPorIa();
        assertThatThrownBy(e::aprobarPorIa).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void revisarManualmenteAprueba() {
        Evidencia e = evidenciaTexto();
        e.registrarIntentoFallido();
        e.registrarIntentoFallido();
        e.registrarIntentoFallido();

        e.revisarManualmente(true, "confirmado por mentor");

        assertThat(e.estadoValidacion()).isEqualTo(EstadoValidacion.VALIDA);
        assertThat(e.notasValidacion()).isEqualTo("confirmado por mentor");
    }

    @Test
    void revisarManualmenteRechaza() {
        Evidencia e = evidenciaTexto();
        e.registrarIntentoFallido();
        e.registrarIntentoFallido();
        e.registrarIntentoFallido();

        e.revisarManualmente(false, "no corresponde");

        assertThat(e.estadoValidacion()).isEqualTo(EstadoValidacion.RECHAZADA);
    }

    @Test
    void revisarManualmenteFueraDeRevisionManualEsRechazado() {
        Evidencia e = evidenciaTexto();
        assertThatThrownBy(() -> e.revisarManualmente(true, "x")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anularVeredictoDesdeValida() {
        Evidencia e = evidenciaTexto();
        e.aprobarPorIa();

        e.anularVeredicto("evidencia duplicada, se anula");

        assertThat(e.estadoValidacion()).isEqualTo(EstadoValidacion.ANULADA_ADMIN);
        assertThat(e.notasValidacion()).isEqualTo("evidencia duplicada, se anula");
    }

    @Test
    void anularVeredictoDesdeRechazada() {
        Evidencia e = evidenciaTexto();
        e.rechazarPorIa("no valida");

        e.anularVeredicto("revision posterior");

        assertThat(e.estadoValidacion()).isEqualTo(EstadoValidacion.ANULADA_ADMIN);
    }

    @Test
    void anularVeredictoDesdePendienteEsRechazado() {
        Evidencia e = evidenciaTexto();
        assertThatThrownBy(() -> e.anularVeredicto("x")).isInstanceOf(IllegalStateException.class);
    }
}

package com.renaser.os.calendar.domain.model.meet.transcripcion;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * La transcripción de UNA sesión por Meet (spec 047): sus estados y cuándo se vuelve a buscar. El texto viaja en claro solo dentro del servidor;
 * la persistencia lo cifra (FR-010). {@link #toString()} nunca lo incluye.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class TranscripcionDeSesion {

    /** FR-008: mientras Google la prepara, se vuelve a mirar cada 10 minutos. */
    public static final Duration CADA_CUANTO = Duration.ofMinutes(10);
    /** FR-008: hasta 8 horas después del fin agendado. */
    public static final Duration HASTA_DESPUES_DEL_FIN = Duration.ofHours(8);
    /** Sin permiso, se vuelve a mirar cada 6 horas (por si reconectaron la cuenta). */
    public static final Duration SIN_PERMISO_CADA = Duration.ofHours(6);
    /** Menos que eso no es una transcripción (la sala se abrió y nadie habló). */
    public static final int LARGO_MINIMO = 40;
    /** Tope del texto guardado (FR edge case: muy larga). */
    public static final int LARGO_MAXIMO = 500_000;
    public static final int LARGO_MAXIMO_DEL_MOTIVO = 300;

    private final UUID id;
    private final UUID salaId;
    private EstadoDeTranscripcion estado;
    private int conferencias;
    private int entradas;
    private int largo;
    private boolean recortada;
    private String texto;
    private String enlaceDeLaGrabacion;
    private int intentos;
    private Instant proximoIntento;
    private String motivo;
    private DiagnosticoDeReunion diagnostico;
    private Instant obtenidaEn;
    private Instant borrarDespuesDe;
    private Instant borradaEn;
    private final Instant creadaEn;
    private Instant actualizadaEn;

    public static TranscripcionDeSesion nueva(UUID id, UUID salaId, Instant ahora) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(salaId, "salaId es obligatorio");
        return new TranscripcionDeSesion(id, salaId, EstadoDeTranscripcion.ESPERANDO, 0, 0, 0, false, null, null, 0, ahora, null, null, null,
                null, null, ahora, ahora);
    }

    public static TranscripcionDeSesion rehydrate(UUID id, UUID salaId, EstadoDeTranscripcion estado, int conferencias, int entradas, int largo,
                                                  boolean recortada, String texto, String enlaceDeLaGrabacion, int intentos,
                                                  Instant proximoIntento, String motivo, DiagnosticoDeReunion diagnostico,
                                                  Instant obtenidaEn, Instant borrarDespuesDe, Instant borradaEn, Instant creadaEn, Instant actualizadaEn) {
        return new TranscripcionDeSesion(id, salaId, estado, conferencias, entradas, largo, recortada, texto, enlaceDeLaGrabacion, intentos,
                proximoIntento, motivo, diagnostico, obtenidaEn, borrarDespuesDe, borradaEn, creadaEn, actualizadaEn);
    }

    /**
     * Decide con lo que dijo Google (FR-002, FR-008). {@code fin} es el fin agendado de la sesión; {@code retencion}, cuánto se guarda (P3).
     */
    public void registrarLectura(LecturaDeReunion lectura, Instant fin, Instant ahora, Duration retencion) {
        this.conferencias = lectura.conferencias();
        this.entradas = lectura.entradas();
        this.intentos++;
        this.actualizadaEn = ahora;
        String limpio = lectura.texto().strip();
        boolean seAcaboElPlazo = !ahora.isBefore(fin.plus(HASTA_DESPUES_DEL_FIN));
        if (limpio.length() >= LARGO_MINIMO && (!lectura.transcripcionEnPreparacion() || seAcaboElPlazo)) {
            this.recortada = limpio.length() > LARGO_MAXIMO;
            this.texto = recortada ? limpio.substring(0, LARGO_MAXIMO) : limpio;
            this.largo = texto.length();
            this.enlaceDeLaGrabacion = lectura.enlaceDeLaGrabacion();
            this.estado = EstadoDeTranscripcion.LISTA;
            this.obtenidaEn = ahora;
            this.borrarDespuesDe = ahora.plus(retencion);
            this.proximoIntento = null;
            this.motivo = null;
            this.diagnostico = null;
            return;
        }
        if (!seAcaboElPlazo) {
            esperar(lectura.conferencias() == 0 ? "Todavía no hay una reunión en la sala."
                    : "Google todavía está preparando la transcripción.", ahora, ahora.plus(CADA_CUANTO));
            return;
        }
        if (lectura.conferencias() == 0) {
            terminarSin(EstadoDeTranscripcion.SIN_REUNION, "No hubo ninguna reunión en la sala.", ahora);
        } else {
            terminarSin(EstadoDeTranscripcion.SIN_TRANSCRIPCION, "Hubo reunión, pero Google no dejó una transcripción.", ahora);
        }
        this.diagnostico = lectura.diagnostico();
    }

    /**
     * Issue #151, FR-022: suma el diagnóstico de una sesión que ya terminó sin transcripción (lo vuelve a leer Administración mientras Google aún
     * guarda los datos). Solo vale en {@code SIN_TRANSCRIPCION} y {@code SIN_REUNION}; en cualquier otro estado devuelve {@code false}.
     */
    public boolean registrarDiagnostico(DiagnosticoDeReunion nuevo, Instant ahora) {
        if (nuevo == null || (estado != EstadoDeTranscripcion.SIN_TRANSCRIPCION && estado != EstadoDeTranscripcion.SIN_REUNION)) {
            return false;
        }
        this.diagnostico = nuevo;
        this.actualizadaEn = ahora;
        return true;
    }

    /** FR-009: la sala quedó sin transcripción automática (la cuenta no la dejó): no se le pregunta a Google. */
    public void sinTranscripcionPorLaCuenta(Instant ahora) {
        terminarSin(EstadoDeTranscripcion.SIN_TRANSCRIPCION, "La cuenta no dejó transcribir esta sala.", ahora);
    }

    /** FR-019: la cuenta no tiene el permiso de leer las reuniones. Se vuelve a mirar cada 6 horas. */
    public void sinPermiso(Instant ahora) {
        this.estado = EstadoDeTranscripcion.SIN_PERMISO;
        this.motivo = "Falta volver a conectar Google con el permiso de leer las reuniones.";
        this.proximoIntento = ahora.plus(SIN_PERMISO_CADA);
        this.actualizadaEn = ahora;
    }

    /** Google no respondió (429, 5xx, sin red): se reintenta con espera creciente, sin pasar del plazo de 8 horas que decide la próxima lectura. */
    public void googleNoRespondio(Instant ahora) {
        this.intentos++;
        Duration espera = CADA_CUANTO.multipliedBy(Math.min(6, Math.max(1, intentos)));
        esperar("Google no respondió. Se reintenta solo.", ahora, ahora.plus(espera));
    }

    /** FR-014, FR-015: vence o se borra la sala. Se vacía lo cifrado y queda la constancia. */
    public void borrar(Instant ahora) {
        this.estado = EstadoDeTranscripcion.BORRADA;
        this.texto = null;
        this.enlaceDeLaGrabacion = null;
        this.proximoIntento = null;
        this.borradaEn = ahora;
        this.actualizadaEn = ahora;
    }

    public boolean tieneTexto() {
        return estado == EstadoDeTranscripcion.LISTA && texto != null;
    }

    private void esperar(String porQue, Instant ahora, Instant proximo) {
        this.estado = EstadoDeTranscripcion.ESPERANDO;
        this.motivo = porQue;
        this.proximoIntento = proximo;
        this.actualizadaEn = ahora;
    }

    private void terminarSin(EstadoDeTranscripcion final_, String porQue, Instant ahora) {
        this.estado = final_;
        this.motivo = porQue.length() <= LARGO_MAXIMO_DEL_MOTIVO ? porQue : porQue.substring(0, LARGO_MAXIMO_DEL_MOTIVO);
        this.proximoIntento = null;
        this.texto = null;
        this.enlaceDeLaGrabacion = null;
        this.actualizadaEn = ahora;
    }

    @Override
    public String toString() {
        return "TranscripcionDeSesion[" + salaId + ", " + estado + ", " + largo + " caracteres]";
    }
}

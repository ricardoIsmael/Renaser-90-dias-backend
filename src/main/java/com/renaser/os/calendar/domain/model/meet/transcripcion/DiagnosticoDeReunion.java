package com.renaser.os.calendar.domain.model.meet.transcripcion;

import com.renaser.os.calendar.domain.model.meet.EstadoDeCoanfitriones;

import java.time.Instant;
import java.util.List;

/**
 * Por qué Meet no dejó grabación ni transcripción de una sesión (issue #151, spec 047 FR-022). Es una foto que NO identifica a nadie: cantidades de
 * participantes por tipo y estados que devolvió Google, nunca nombres, correos ni ids de usuario.
 *
 * @param conferencias       cada conexión (conferenceRecord) de la sala dentro de la ventana de la fecha
 * @param autoGrabacion      lo que dice la sala ({@code ON}, {@code OFF}) o {@code null} si no se pudo leer
 * @param autoTranscripcion  igual, para la transcripción
 * @param motivoProbable     un código corto, ver {@link Motivo}
 */
public record DiagnosticoDeReunion(List<Conferencia> conferencias, String autoGrabacion, String autoTranscripcion, String motivoProbable) {

    /** Una conexión se considera «muy corta» si dura menos de esto: Meet no llega a empezar a grabar. */
    public static final int MINUTOS_MUY_CORTA = 1;

    public enum Motivo {
        /** Google no tiene ninguna conferencia de la sala en esa ventana. */
        SIN_CONFERENCIAS,
        /** Todas las conexiones duraron menos de un minuto. */
        CONEXIONES_MUY_CORTAS,
        /** Entraron solo anónimos o teléfonos: nadie con cuenta de Google, ni quien dirige. */
        SOLO_ANONIMOS_O_TELEFONO,
        /** Google creó una grabación o transcripción pero no llegó a dejar el archivo. */
        ARCHIVO_NO_GENERADO,
        /** La sala no tenía la grabación y la transcripción automáticas encendidas cuando la leímos. */
        AUTOMATICO_APAGADO,
        /** Hubo cuentas de Google en la sala y aun así Google no generó nada: suele ser que quien dirige no es de la organización o no había licencia. */
        CON_CUENTAS_Y_SIN_ARCHIVOS,
        /**
         * Hubo cuentas de Google en la sala pero quien dirige NO quedó como coanfitrión (FR-038: el pedido a Google falló o no había correo):
         * Meet solo graba solo cuando entra el anfitrión o un coanfitrión.
         */
        QUIEN_DIRIGE_NO_ERA_COANFITRION
    }

    /**
     * Afina el motivo con cómo quedaron los coanfitriones (FR-038): si hubo cuentas en la sala y no se generó nada, y a quien dirige no se le pudo
     * dar el permiso de grabar, ese es el motivo probable. Con coanfitriones en {@code OK} o sin pedir, el motivo no cambia.
     */
    public DiagnosticoDeReunion conCoanfitriones(EstadoDeCoanfitriones coanfitriones) {
        boolean dirigeSinPermiso = coanfitriones == EstadoDeCoanfitriones.FALLIDO || coanfitriones == EstadoDeCoanfitriones.PARCIAL
                || coanfitriones == EstadoDeCoanfitriones.SIN_CORREO;
        if (!dirigeSinPermiso || !Motivo.CON_CUENTAS_Y_SIN_ARCHIVOS.name().equals(motivoProbable)) {
            return this;
        }
        return new DiagnosticoDeReunion(conferencias, autoGrabacion, autoTranscripcion, Motivo.QUIEN_DIRIGE_NO_ERA_COANFITRION.name());
    }

    /**
     * @param inicio          cuándo empezó la conferencia
     * @param fin             cuándo terminó, o {@code null} si Google no la dio por terminada
     * @param minutos         duración en minutos (0 si no hay fin)
     * @param conCuenta       participantes con cuenta de Google (de la organización o de fuera: la API no lo distingue)
     * @param anonimos        participantes anónimos (sin cuenta)
     * @param porTelefono     participantes que entraron por teléfono
     * @param grabaciones     estado de cada grabación que devolvió Google ({@code FILE_GENERATED}, {@code STARTED}, {@code ENDED}…)
     * @param transcripciones estado de cada transcripción que devolvió Google
     */
    public record Conferencia(Instant inicio, Instant fin, int minutos, int conCuenta, int anonimos, int porTelefono, List<String> grabaciones,
                              List<String> transcripciones) {

        public Conferencia {
            grabaciones = grabaciones == null ? List.of() : List.copyOf(grabaciones);
            transcripciones = transcripciones == null ? List.of() : List.copyOf(transcripciones);
        }
    }

    public DiagnosticoDeReunion {
        conferencias = conferencias == null ? List.of() : List.copyOf(conferencias);
    }

    /** Arma el diagnóstico y decide el motivo probable con lo leído. */
    public static DiagnosticoDeReunion de(List<Conferencia> conferencias, String autoGrabacion, String autoTranscripcion) {
        return new DiagnosticoDeReunion(conferencias, autoGrabacion, autoTranscripcion,
                motivoDe(conferencias, autoGrabacion, autoTranscripcion).name());
    }

    private static Motivo motivoDe(List<Conferencia> c, String grabacion, String transcripcion) {
        if (c.isEmpty()) {
            return Motivo.SIN_CONFERENCIAS;
        }
        if (c.stream().allMatch(x -> x.minutos() < MINUTOS_MUY_CORTA)) {
            return Motivo.CONEXIONES_MUY_CORTAS;
        }
        if ("OFF".equals(grabacion) && "OFF".equals(transcripcion)) {
            return Motivo.AUTOMATICO_APAGADO;
        }
        if (c.stream().allMatch(x -> x.conCuenta() == 0)) {
            return Motivo.SOLO_ANONIMOS_O_TELEFONO;
        }
        boolean huboCreada = c.stream().anyMatch(x -> !x.grabaciones().isEmpty() || !x.transcripciones().isEmpty());
        return huboCreada ? Motivo.ARCHIVO_NO_GENERADO : Motivo.CON_CUENTAS_Y_SIN_ARCHIVOS;
    }

    /** El motivo en español de Perú, para quien lee el detalle en Administración. */
    public String explicacion() {
        int n = conferencias.size();
        int conCuenta = conferencias.stream().mapToInt(Conferencia::conCuenta).sum();
        int anonimos = conferencias.stream().mapToInt(Conferencia::anonimos).sum();
        int telefono = conferencias.stream().mapToInt(Conferencia::porTelefono).sum();
        String conexiones = n == 1 ? "la única conexión" : "las " + n + " conexiones";
        Motivo motivo;
        try {
            motivo = Motivo.valueOf(motivoProbable);
        } catch (RuntimeException e) {
            return "Google no dejó grabación ni transcripción y no hay más datos.";
        }
        return switch (motivo) {
            case SIN_CONFERENCIAS -> "Google no tiene ninguna conexión de esta sala en ese horario: nadie llegó a entrar con el enlace.";
            case CONEXIONES_MUY_CORTAS -> "Google no grabó: " + conexiones + " duró menos de un minuto.";
            case SOLO_ANONIMOS_O_TELEFONO -> "Google no grabó: en " + conexiones + " solo entraron anónimos (" + anonimos + ") y teléfonos ("
                    + telefono + "), ninguna cuenta de Google. Para que grabe, quien dirige debe entrar con su cuenta de la organización.";
            case ARCHIVO_NO_GENERADO -> "Google empezó a grabar o transcribir pero no dejó el archivo (estado sin terminar). Puede ser que la "
                    + "reunión se cortara antes de tiempo.";
            case AUTOMATICO_APAGADO -> "La sala no tiene la grabación ni la transcripción automáticas encendidas.";
            case QUIEN_DIRIGE_NO_ERA_COANFITRION -> "Google no grabó aunque en " + conexiones + " entraron " + conCuenta + " cuentas de Google: quien "
                    + "dirige la sesión no quedó como coanfitrión de la sala y Meet solo graba solo cuando entra el anfitrión o un coanfitrión.";
            case CON_CUENTAS_Y_SIN_ARCHIVOS -> "Google no grabó aunque en " + conexiones + " entraron " + conCuenta + " cuentas de Google, "
                    + anonimos + " anónimos y " + telefono + " por teléfono. Suele pasar cuando quien dirige no entra con la cuenta de la "
                    + "organización o su cuenta no tiene grabación; Meet no permite saber cuál de los dos fue.";
        };
    }
}

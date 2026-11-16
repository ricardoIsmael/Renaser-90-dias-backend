package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.in.meet.ResumenDeMeet;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.meet.AccesoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.EstadoDeCoanfitriones;
import com.renaser.os.calendar.domain.model.meet.EstadoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.SalaMeet;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Arma lo que ve Administración de las salas de un evento (spec 040, FR-019 y FR-023): el peor estado de sus fechas y una frase corta, sin
 * lenguaje técnico. Solo cuentan las fechas que todavía no pasaron (o, si ya pasaron todas, las que hay).
 */
final class ResumenesDeMeet {

    static final String SALA_CON_PERMISO_SIN_ABRIR = "La sala quedó con permiso; no pudimos dejarla abierta.";
    static final String SALA_ABIERTA_SIN_PERMISO = "La sala quedó abierta; no pudimos dejarla con permiso.";
    static final String SALA_SIN_COMPROBAR = "No pudimos comprobar quién puede entrar a la sala.";
    static final String FALLIDA = "No pudimos crear el enlace de Meet.";
    static final String CREANDOSE = "El enlace de Meet todavía se está creando.";
    static final String SIN_SINCRONIZAR = "Los cambios todavía no llegaron a Google.";

    /**
     * FR-037: qué hace falta en la cuenta de Google conectada para cada cosa que Google no dejó encender: {qué, plan}. Corregido por la spec 047:
     * transcribir NO exige Workspace (RenaserOs transcribe con el mismo Gmail y Google One).
     */
    static final String[] GRABAR = {"grabar", "Google One de 2 TB o más en la cuenta conectada"};
    static final String[] TRANSCRIBIR = {"transcribir", "un plan de Google One que incluya la transcripción de Meet"};
    static final String[] NOTAS = {"las notas automáticas", "Gemini en Google Workspace"};

    private ResumenesDeMeet() {
    }

    /** {@code null} si el evento no pide Meet automático o todavía no tiene ninguna sala. */
    static ResumenDeMeet de(Evento evento, List<SalaMeet> salas, Instant ahora) {
        if (!evento.usaSalaDeMeetAutomatica()) {
            return null;
        }
        LocalDate hoy = ahora.atZone(evento.timezone()).toLocalDate();
        List<SalaMeet> vivas = salas.stream().filter(s -> s.estado() != EstadoSalaMeet.BORRADO).toList();
        List<SalaMeet> futuras = vivas.stream().filter(s -> !s.fechaOcurrencia().isBefore(hoy)).toList();
        List<SalaMeet> cuentan = futuras.isEmpty() ? vivas : futuras;
        if (cuentan.isEmpty()) {
            return new ResumenDeMeet(EstadoSalaMeet.PENDIENTE, evento.accesoSalaMeet(), false, CREANDOSE, 0, 0);
        }
        SalaMeet peor = cuentan.stream().max(Comparator.comparingInt(ResumenesDeMeet::gravedad)).orElseThrow();
        int conProblema = (int) cuentan.stream().filter(s -> gravedad(s) > 0).count();
        int conSala = (int) cuentan.stream().filter(s -> s.enlace() != null).count();
        boolean accesoAplicado = cuentan.stream().noneMatch(s -> s.estado() == EstadoSalaMeet.CREADO && s.accesoPendiente());
        EstadoDeCoanfitriones coanfitriones = peorCoanfitriones(cuentan);
        return new ResumenDeMeet(peor.estado(), evento.accesoSalaMeet(), accesoAplicado, mensajeDe(peor), conSala, conProblema,
                avisoDelPlan(cuentan), coanfitriones == null ? null : coanfitriones.name(), avisoDeCoanfitriones(coanfitriones));
    }

    static final String COANFITRIONES_FALLIDO = "Quien dirige la sesión no quedó como coanfitrión de la sala: Meet puede no grabar ni transcribir "
            + "si no entra la cuenta conectada.";
    static final String COANFITRIONES_PARCIAL = "Solo algunas de las personas que dirigen la sesión quedaron como coanfitrionas: Meet puede no "
            + "grabar ni transcribir si entra otra.";
    static final String COANFITRIONES_SIN_CORREO = "No se encontró el correo de quien dirige la sesión para hacerlo coanfitrión: Meet puede no "
            + "grabar ni transcribir si no entra la cuenta conectada.";

    /** El peor estado de coanfitriones entre las fechas (FALLIDO > PARCIAL > SIN_CORREO > OK); {@code null} si ninguna lo pidió. */
    static EstadoDeCoanfitriones peorCoanfitriones(List<SalaMeet> salas) {
        List<EstadoDeCoanfitriones> orden = List.of(EstadoDeCoanfitriones.FALLIDO, EstadoDeCoanfitriones.PARCIAL,
                EstadoDeCoanfitriones.SIN_CORREO, EstadoDeCoanfitriones.OK);
        return orden.stream().filter(e -> salas.stream().anyMatch(s -> s.coanfitriones() == e)).findFirst().orElse(null);
    }

    private static String avisoDeCoanfitriones(EstadoDeCoanfitriones estado) {
        if (estado == null) {
            return null;
        }
        return switch (estado) {
            case OK -> null;
            case FALLIDO -> COANFITRIONES_FALLIDO;
            case PARCIAL -> COANFITRIONES_PARCIAL;
            case SIN_CORREO -> COANFITRIONES_SIN_CORREO;
        };
    }

    /**
     * FR-037: si en alguna fecha Google no dejó grabar, transcribir o tomar notas, una sola frase que dice qué hace falta, en el orden de la
     * decisión del dueño («Para grabar hace falta Google One 2 TB o más en la cuenta conectada; para transcribir, Google Workspace.»; corregido por la spec 047). Lo que está
     * encendido o no se pidió no se menciona.
     */
    static String avisoDelPlan(List<SalaMeet> salas) {
        List<String[]> faltan = new ArrayList<>();
        if (salas.stream().anyMatch(s -> Boolean.FALSE.equals(s.artefactos().grabacion()))) {
            faltan.add(GRABAR);
        }
        if (salas.stream().anyMatch(s -> Boolean.FALSE.equals(s.artefactos().transcripcion()))) {
            faltan.add(TRANSCRIBIR);
        }
        if (salas.stream().anyMatch(s -> Boolean.FALSE.equals(s.artefactos().notas()))) {
            faltan.add(NOTAS);
        }
        if (faltan.isEmpty()) {
            return null;
        }
        StringBuilder frase = new StringBuilder("Para ").append(faltan.get(0)[0]).append(" hace falta ").append(faltan.get(0)[1]);
        for (String[] otro : faltan.subList(1, faltan.size())) {
            frase.append("; para ").append(otro[0]).append(", ").append(otro[1]);
        }
        return frase.append('.').toString();
    }

    /**
     * Spec 047 (P4-B): alguna sala creada del evento quedó grabando o transcribiendo según lo que Google confirmó (spec 040, FR-036). Sin esa
     * confirmación no se afirma nada: un aviso de grabación que no es cierto también engaña.
     */
    static boolean seGrabaOTranscribe(List<SalaMeet> salas) {
        return salas.stream().anyMatch(s -> s.estado() == EstadoSalaMeet.CREADO
                && (Boolean.TRUE.equals(s.artefactos().grabacion()) || Boolean.TRUE.equals(s.artefactos().transcripcion())));
    }

    /** Cuánto pesa una fila para decidir el estado del evento: 0 = sin problema. */
    private static int gravedad(SalaMeet sala) {
        return switch (sala.estado()) {
            case FALLIDO -> 3;
            case PENDIENTE -> 2;
            case PENDIENTE_DE_SINCRONIZAR -> 2;
            case CREADO -> sala.accesoPendiente() ? 1 : 0;
            case BORRADO -> 0;
        };
    }

    private static String mensajeDe(SalaMeet peor) {
        return switch (peor.estado()) {
            case FALLIDO -> FALLIDA;
            case PENDIENTE -> CREANDOSE;
            case PENDIENTE_DE_SINCRONIZAR -> SIN_SINCRONIZAR;
            case CREADO -> peor.accesoPendiente() ? mensajeDelAcceso(peor) : null;
            case BORRADO -> null;
        };
    }

    private static String mensajeDelAcceso(SalaMeet sala) {
        if (sala.accesoConfirmado() == null) {
            return SALA_SIN_COMPROBAR;
        }
        return sala.accesoElegido() == AccesoSalaMeet.ABIERTA ? SALA_CON_PERMISO_SIN_ABRIR : SALA_ABIERTA_SIN_PERMISO;
    }
}

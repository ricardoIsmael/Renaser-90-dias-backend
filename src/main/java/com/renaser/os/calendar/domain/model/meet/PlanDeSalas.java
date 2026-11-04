package com.renaser.os.calendar.domain.model.meet;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Qué hay que hacer con Google para que las salas de un evento queden como Renaser las quiere (spec 040, FR-005, FR-008, FR-017). Pura: recibe
 * lo que se quiere y lo que hay, y devuelve los pasos, en orden de fecha. No habla con Google ni con la base.
 *
 * <p>Las reglas:
 * <ul>
 *   <li>Cada fecha que se quiere y no tiene sala (o tiene una lápida) se {@link Accion#CREAR crea}, salvo que ya haya pasado; la que ya tiene una
 *       sala que falló, se vuelve a pedir cuando toca (o ya, si se fuerza).</li>
 *   <li>Una sala que Renaser cambió ({@code PENDIENTE_DE_SINCRONIZAR}) se {@link Accion#ACTUALIZAR actualiza}; el enlace no cambia.</li>
 *   <li>Una sala creada a la que le falta dejar la sala abierta o con permiso, se {@link Accion#AJUSTAR_ACCESO ajusta}.</li>
 *   <li>Una sala de una fecha que ya no se quiere se {@link Accion#BORRAR borra}. Las fechas que ya pasaron NO se tocan mientras el evento
 *       siga vivo: borrarlas no le sirve a nadie. Si el evento ya no existe o dejó de pedir Meet, se borran todas.</li>
 * </ul>
 */
public final class PlanDeSalas {

    public enum Accion { CREAR, ACTUALIZAR, AJUSTAR_ACCESO, BORRAR }

    /** Una fecha que Renaser quiere con sala: cuándo empieza y termina esa ocurrencia y cómo se llama. */
    public record OcurrenciaDeseada(LocalDate fecha, Instant inicio, Instant fin, String titulo) {
    }

    /** Un paso: la acción, la fecha y la sala que ya existe ({@code null} si hay que crear la fila). */
    public record Paso(Accion accion, LocalDate fecha, SalaMeet sala) {
    }

    private PlanDeSalas() {
    }

    /**
     * @param eventoVivo el evento existe y sigue pidiendo la sala automática
     * @param hoy        el día de hoy en la zona del evento (no el del servidor)
     * @param forzar     ignorar las esperas de los reintentos (un «Reintentar» manual, o reconectar la cuenta)
     */
    public static List<Paso> calcular(boolean eventoVivo, LocalDate hoy, Map<LocalDate, OcurrenciaDeseada> deseadas,
                                      List<SalaMeet> existentes, Instant ahora, boolean forzar) {
        Map<LocalDate, SalaMeet> porFecha = new HashMap<>();
        existentes.forEach(sala -> porFecha.put(sala.fechaOcurrencia(), sala));
        TreeMap<LocalDate, Paso> pasos = new TreeMap<>();

        if (eventoVivo) {
            deseadas.keySet().stream().sorted().forEach(fecha -> {
                Paso paso = pasoParaUnaFechaQueSeQuiere(fecha, porFecha.get(fecha), hoy, ahora, forzar);
                if (paso != null) {
                    pasos.put(fecha, paso);
                }
            });
        }
        List<Paso> borrados = new ArrayList<>();
        for (SalaMeet sala : existentes) {
            boolean seQuiere = eventoVivo && deseadas.containsKey(sala.fechaOcurrencia());
            if (!seQuiere && correspondeBorrar(sala, eventoVivo, hoy, ahora, forzar)) {
                borrados.add(new Paso(Accion.BORRAR, sala.fechaOcurrencia(), sala));
            }
        }
        List<Paso> resultado = new ArrayList<>(borrados);
        resultado.sort((a, b) -> a.fecha().compareTo(b.fecha()));
        resultado.addAll(pasos.values());
        return resultado;
    }

    private static Paso pasoParaUnaFechaQueSeQuiere(LocalDate fecha, SalaMeet sala, LocalDate hoy, Instant ahora, boolean forzar) {
        if (sala == null || sala.estado() == EstadoSalaMeet.BORRADO) {
            // Una fecha que ya pasó no recibe sala nueva: nadie va a entrar.
            return fecha.isBefore(hoy) ? null : new Paso(Accion.CREAR, fecha, sala);
        }
        boolean toca = forzar || sala.tocaReintentar(ahora);
        return switch (sala.estado()) {
            case PENDIENTE, FALLIDO -> toca ? new Paso(Accion.CREAR, fecha, sala) : null;
            case PENDIENTE_DE_SINCRONIZAR -> toca ? new Paso(Accion.ACTUALIZAR, fecha, sala) : null;
            case CREADO -> sala.accesoPendiente() && toca ? new Paso(Accion.AJUSTAR_ACCESO, fecha, sala) : null;
            case BORRADO -> null;
        };
    }

    private static boolean correspondeBorrar(SalaMeet sala, boolean eventoVivo, LocalDate hoy, Instant ahora, boolean forzar) {
        if (sala.estado() == EstadoSalaMeet.BORRADO) {
            return false;
        }
        boolean yaPaso = sala.fechaOcurrencia().isBefore(hoy);
        if (eventoVivo && yaPaso) {
            return false;
        }
        return sala.estado() == EstadoSalaMeet.CREADO || forzar || sala.tocaReintentar(ahora);
    }
}

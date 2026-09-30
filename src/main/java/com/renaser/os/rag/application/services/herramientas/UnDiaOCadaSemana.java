package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.conversacion.LoadMensajeRenasiaPort;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.rag.domain.model.conversacion.RolMensaje;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Si la persona pidio UN dia ("solo el sabado") o que se repita ("los sabados", "todos los sabados",
 * "cada sabado") (E-456, 2026-09-30).
 *
 * <p>El prompt ya lo decia (E-454) y el modelo igual propuso "Apagar 'ESCRITURA LIBRE NOCTURNA' los
 * sabado, todas las semanas" a "mejor no, entonces apagala solo el sabado". Como el cambio semanal
 * dura hasta que la persona lo quite, se decide aca, leyendo lo que escribio: el ultimo mensaje suyo al
 * acompanante, que {@code ConversacionRenasiaService} guarda ANTES de llamar al modelo.
 *
 * <p>Solo decide cuando ese mensaje nombra el dia y es reciente ({@link #VENTANA}). Un "si" que
 * confirma algo dicho antes no nombra ningun dia: no se bloquea (el modelo pregunto antes, o lo dijo
 * la persona). Tampoco hay mensaje reciente en la voz en vivo: ahi no se bloquea.
 */
@Component
public class UnDiaOCadaSemana {

    /** Un turno con herramientas tarda segundos; dos minutos cubren el mas lento sin mirar otro turno. */
    static final Duration VENTANA = Duration.ofMinutes(2);

    private final LoadMensajeRenasiaPort mensajesPort;
    private final Clock clock;

    public UnDiaOCadaSemana(LoadMensajeRenasiaPort mensajesPort, Clock clock) {
        this.mensajesPort = mensajesPort;
        this.clock = clock;
    }

    /** Verdadero si lo ultimo que escribio nombra ese dia en singular, sin "los", "todos los" ni "cada". */
    public boolean pidioUnSoloDia(UserId actorId, DayOfWeek dia) {
        return ultimaPregunta(actorId).map(pregunta -> esUnSoloDia(pregunta, dia)).orElse(false);
    }

    static boolean esUnSoloDia(String pregunta, DayOfWeek dia) {
        String texto = normalizado(pregunta);
        String nombre = normalizado(ArgumentosDeHorario.nombre(dia));
        if (!Pattern.compile("\\b" + nombre + "s?\\b").matcher(texto).find()) {
            return false;
        }
        boolean seRepite = Pattern.compile("\\b(los|todos los|cada)\\s+" + nombre + "s?\\b").matcher(texto).find()
                || Pattern.compile("\\b(cada semana|todas las semanas|semanal)").matcher(texto).find();
        return !seRepite;
    }

    private Optional<String> ultimaPregunta(UserId actorId) {
        Instant desde = clock.now().minus(VENTANA);
        return mensajesPort.pagina(actorId, AgenteConversacional.COMPANION, null, 2).stream()
                .filter(mensaje -> mensaje.rol() == RolMensaje.USUARIO)
                .findFirst()
                .filter(mensaje -> !mensaje.creadoEn().isBefore(desde))
                .map(MensajeRenasia::contenido);
    }

    private static String normalizado(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}

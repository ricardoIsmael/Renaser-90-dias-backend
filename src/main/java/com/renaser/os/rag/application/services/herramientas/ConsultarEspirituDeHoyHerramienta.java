package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.enfoque.EnfoqueDiarioDelAprendizPort;
import com.renaser.os.rag.application.ports.out.enfoque.EnfoqueDiarioDelAprendizPort.AudioDeHoy;
import com.renaser.os.rag.application.ports.out.enfoque.EnfoqueDiarioDelAprendizPort.EspirituDeHoy;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

/**
 * {@code consultar_espiritu_de_hoy} (R0, 2026-09-23): si el audio de Espiritu de hoy ya se
 * desbloqueo, hasta que hora se entrega a tiempo, si ya lo mando y si fue a tiempo.
 *
 * <p><b>No es una lectura pura</b> y es a proposito: {@code habits} resuelve el estado con el mismo
 * caso de uso que {@code GET /spirit-audio/status}, que avanza la maquina perezosa de Espiritu
 * (desbloquea el audio del dia a partir de las 07:00, da por perdido uno de un dia ya cerrado).
 * Es lo mismo que pasa cuando la persona abre Training, y es idempotente. Copiar esa regla para
 * "adivinar" el estado sin tocarlo duplicaria el negocio; el detalle esta en
 * {@code habits.application.services.EnfoqueDiarioService}.
 *
 * <p>Las horas salen de {@code habits} (constantes de {@code EspirituService}) y se dicen en la
 * zona de la persona ({@link MomentoDelAprendiz}, regla 02): el modelo solo las repite.
 */
@Component
public class ConsultarEspirituDeHoyHerramienta implements HerramientaAgente {

    public static final String NOMBRE = "consultar_espiritu_de_hoy";

    private static final DefinicionHerramienta DEFINICION = DefinicionHerramienta.sinParametros(NOMBRE,
            "Devuelve el estado del audio de Espiritu de HOY: si ya se desbloqueo, hasta que hora se entrega a "
                    + "tiempo, si ya mando su resumen y si fue a tiempo, y cuantos puntos suma 'Pastilla Renacer' "
                    + "al entregarlo. Usala antes de hablar de Espiritu y antes de proponer enviar el resumen.");

    private final EnfoqueDiarioDelAprendizPort enfoquePort;
    private final Clock clock;

    public ConsultarEspirituDeHoyHerramienta(EnfoqueDiarioDelAprendizPort enfoquePort, Clock clock) {
        this.enfoquePort = enfoquePort;
        this.clock = clock;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        return LecturaDelEnfoque.con(() -> enfoquePort.espirituDeHoy(actorId), "su Espiritu de hoy",
                espiritu -> ResultadoHerramienta.exito(textoDe(espiritu,
                        new MomentoDelAprendiz(clock.now(), espiritu.zona()))));
    }

    static String textoDe(EspirituDeHoy espiritu, MomentoDelAprendiz momento) {
        String encabezado = "Espiritu de hoy (" + FechaDelPlan.legible(espiritu.hoy()) + ", ahora son las "
                + momento.horaDe(momento.ahora()) + " para la persona). Los audios se desbloquean a las "
                + espiritu.horaDesbloqueo() + " y se entregan a tiempo hasta las " + espiritu.horaLimite() + ".\n";
        return encabezado + switch (espiritu.estado()) {
            case ANTES_DE_LA_HORA_DE_DESBLOQUEO -> "Todavia no son las " + espiritu.horaDesbloqueo()
                    + ": el audio de hoy, si le toca, aun no se abrio.";
            case SIN_AUDIO_HOY -> "Hoy no tiene un audio de Espiritu desbloqueado. No inventes el motivo.";
            case PENDIENTE -> pendiente(espiritu, momento);
            case ENTREGADO_A_TIEMPO -> audio(espiritu.audio()) + ": resumen ENTREGADO A TIEMPO a las "
                    + momento.horaDe(espiritu.audio().entregadoEn()) + ".";
            case ENTREGADO_FUERA_DE_PLAZO -> audio(espiritu.audio()) + ": resumen entregado FUERA DE PLAZO a las "
                    + momento.horaDe(espiritu.audio().entregadoEn()) + " (quedo guardado, no cuenta como a tiempo).";
            case PERDIDO -> audio(espiritu.audio()) + ": perdido, no se entrego.";
        };
    }

    private static String pendiente(EspirituDeHoy espiritu, MomentoDelAprendiz momento) {
        AudioDeHoy audio = espiritu.audio();
        String plazo = momento.ahora().isAfter(audio.fechaLimite())
                ? "ya paso la hora limite: si lo manda ahora queda guardado pero FUERA DE PLAZO."
                : "vence a las " + momento.horaDe(audio.fechaLimite()) + " (faltan "
                        + momento.faltaPara(audio.fechaLimite()) + ").";
        return audio(audio) + ": desbloqueado y SIN ENTREGAR; " + plazo + pastillaRenacer(espiritu);
    }

    private static String pastillaRenacer(EspirituDeHoy espiritu) {
        Integer puntos = espiritu.puntosPastillaRenacer();
        if (puntos == null) {
            return "";
        }
        return " Entregar el resumen marca ademas 'Pastilla Renacer' de hoy como hecha"
                + (puntos > 0 ? " (+" + puntos + " puntos si lo hace ahora)." : " (ya sin puntos a esta hora).");
    }

    private static String audio(AudioDeHoy audio) {
        return "Audio " + audio.dia() + (audio.titulo() == null ? "" : " '" + audio.titulo() + "'");
    }
}

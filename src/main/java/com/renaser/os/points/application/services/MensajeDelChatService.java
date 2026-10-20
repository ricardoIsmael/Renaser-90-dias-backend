package com.renaser.os.points.application.services;

import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.MensajeDelChatFinder;
import com.renaser.os.points.application.ports.in.semaforo.ConsultarMensajeDelSemaforoUseCase.ConsultaDelMensaje;
import com.renaser.os.points.application.ports.in.semaforo.ConsultarMiSemaforoUseCase;
import com.renaser.os.points.application.ports.out.semaforo.mensaje.AzarPort;
import com.renaser.os.points.application.ports.out.semaforo.mensaje.CatalogoDeMensajesPort;
import com.renaser.os.points.application.ports.out.semaforo.mensaje.MensajesElegidosPort;
import com.renaser.os.points.domain.model.semaforo.mensaje.CanalDelMensaje;
import com.renaser.os.points.domain.model.semaforo.mensaje.ClaveDeLaEleccion;
import com.renaser.os.points.domain.model.semaforo.mensaje.DatosDelMensaje;
import com.renaser.os.points.domain.model.semaforo.mensaje.EleccionesDelDia;
import com.renaser.os.points.domain.model.semaforo.mensaje.GrupoDelDia;
import com.renaser.os.points.domain.model.semaforo.mensaje.RedaccionDelMensaje;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

/**
 * El mensaje personalizado de la tarjeta diaria del chat de soporte (D-281, spec 006): lo mismo que Hoy (D-277) pero
 * por el canal {@link CanalDelMensaje#CHAT}.
 *
 * <p><b>Cómo se elige.</b> El grupo es el color del día de la tarjeta. La fecha es la que trae la tarjeta —el día local
 * de la persona, que ya resolvió {@code chat}—, nunca la del servidor (regla 02). Con la elección de ese día ya hecha
 * (el reintento de las 23:55) se reusa; si no, se elige entre los mensajes que el canal admite ({@code {faltan}} no) y
 * que no repiten ni lo de los últimos 14 días por cualquiera de los dos canales ni lo que Hoy mostró ese día.
 *
 * <p><b>Los datos son los de Hoy:</b> nombre, día y fase de la participación, y los hábitos más flojo y más fuerte de la
 * ventana vigente del semáforo (la misma que ve la persona), compartidos con Hoy por {@link DatosEstablesDelDia}.
 *
 * <p>Sin {@code @Transactional}: lecturas y una escritura idempotente ({@code ON CONFLICT DO NOTHING}), y quien llama es
 * un barrido que no debe retener una conexión por persona (regla 02 §4).
 */
@Service
public class MensajeDelChatService implements MensajeDelChatFinder {

    private final CatalogoDeMensajesPort catalogo;
    private final MensajesElegidosPort elegidos;
    private final ElectorDelMensajeDelDia elector;
    private final RecolectorDeDatosDelMensaje recolector;
    private final ParticipacionProgramaFinder participaciones;
    private final ConsultarMiSemaforoUseCase semaforo;

    public MensajeDelChatService(CatalogoDeMensajesPort catalogo, MensajesElegidosPort elegidos, AzarPort azar,
                                 RecolectorDeDatosDelMensaje recolector, ParticipacionProgramaFinder participaciones,
                                 ConsultarMiSemaforoUseCase semaforo, Clock clock) {
        this.catalogo = catalogo;
        this.elegidos = elegidos;
        this.elector = new ElectorDelMensajeDelDia(catalogo, elegidos, azar, clock, CanalDelMensaje.CHAT);
        this.recolector = recolector;
        this.participaciones = participaciones;
        this.semaforo = semaforo;
    }

    @Override
    public Optional<String> delDia(UserId aprendiz, LocalDate fecha, ColorSemaforo color) {
        return GrupoDelDia.delColor(color).flatMap(grupo -> consultaDe(aprendiz)
                .flatMap(consulta -> escribir(consulta, new ClaveDeLaEleccion(aprendiz, fecha, grupo, CanalDelMensaje.CHAT))));
    }

    /** Sin lo de hoy: el chat no dice cuánto falta, así que no hace falta saber qué se cumplió. */
    private Optional<ConsultaDelMensaje> consultaDe(UserId aprendiz) {
        Optional<ParticipacionPrograma> participacion = participaciones.deParticipante(aprendiz);
        return participacion.flatMap(p -> semaforo.resumenParaHoy(aprendiz)
                .map(resumen -> ConsultaDelMensaje.sinLoDeHoy(p, resumen)));
    }

    private Optional<String> escribir(ConsultaDelMensaje consulta, ClaveDeLaEleccion clave) {
        EleccionesDelDia previas = elegidos.deLaPersona(clave);
        DatosDelMensaje datos = recolector.reunir(consulta, clave.grupo(), clave.fecha(),
                CanalDelMensaje.CHAT.variablesPermitidas());
        return previas.deHoy().flatMap(catalogo::porId)
                .or(() -> elector.elegirYGuardar(clave, datos, previas.vistos()))
                .filter(mensaje -> CanalDelMensaje.CHAT.admite(mensaje) && datos.tieneTodas(mensaje.variables()))
                .map(mensaje -> RedaccionDelMensaje.redactar(mensaje, datos));
    }
}

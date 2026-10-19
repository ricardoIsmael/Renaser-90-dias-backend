package com.renaser.os.points.application.services;

import com.renaser.os.points.application.ports.in.semaforo.ConsultarMensajeDelSemaforoUseCase;
import com.renaser.os.points.application.ports.in.semaforo.ConsultarMiSemaforoUseCase.ResumenParaHoy;
import com.renaser.os.points.application.ports.out.semaforo.mensaje.AzarPort;
import com.renaser.os.points.application.ports.out.semaforo.mensaje.CatalogoDeMensajesPort;
import com.renaser.os.points.application.ports.out.semaforo.mensaje.MensajesElegidosPort;
import com.renaser.os.points.domain.model.semaforo.mensaje.CanalDelMensaje;
import com.renaser.os.points.domain.model.semaforo.mensaje.ClaveDeLaEleccion;
import com.renaser.os.points.domain.model.semaforo.mensaje.DatosDelMensaje;
import com.renaser.os.points.domain.model.semaforo.mensaje.EleccionesDelDia;
import com.renaser.os.points.domain.model.semaforo.mensaje.GrupoDelDia;
import com.renaser.os.points.domain.model.semaforo.mensaje.GrupoDelMensaje;
import com.renaser.os.points.domain.model.semaforo.mensaje.MensajeDelSemaforo;
import com.renaser.os.points.domain.model.semaforo.mensaje.RedaccionDelMensaje;
import com.renaser.os.points.domain.model.semaforo.mensaje.SituacionDelSemaforo;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * El mensaje del día del semáforo de Hoy (D-277, spec 006; canal {@link CanalDelMensaje#HOY}, D-281). Elige de forma perezosa —en la lectura, sin tarea programada—
 * la primera vez que la persona lo pide cada día (en SU zona, regla 02 §1), lo guarda por persona, fecha y grupo, y
 * lo mantiene el resto del día. Si el grupo cambia (el color se recalculó), entra una elección nueva del grupo
 * nuevo: el mensaje nunca contradice la tarjeta.
 *
 * <p>Siempre lo escribe con los datos de ESE momento: si la persona cumple un hábito, el {@code {faltan}} baja. Si el
 * dato que pedía el mensaje ya no existe (cumplió lo que faltaba), se muestra otro mensaje del grupo que no lo pida,
 * elegido de forma estable para ese día, sin guardarlo: lo guardado sigue siendo lo que se eligió.
 *
 * <p><b>Costo (SC-004).</b> Releer el mismo día es UNA consulta (lo elegido hoy). La primera del día suma el nombre y
 * los hábitos destacados (que luego quedan en {@link DatosEstablesDelDia}) y una escritura. El catálogo está en
 * memoria y {@code {faltan}} sale de lo que {@code /home} ya leyó, así que ninguno de los dos consulta nada.
 *
 * <p>Sin {@code @Transactional}: son lecturas y una sola escritura idempotente ({@code ON CONFLICT DO NOTHING}), y
 * quien llama ya trae sus propias lecturas.
 */
@Service
public class MensajeDelSemaforoService implements ConsultarMensajeDelSemaforoUseCase {

    private final CatalogoDeMensajesPort catalogo;
    private final MensajesElegidosPort elegidos;
    private final RecolectorDeDatosDelMensaje recolector;
    private final ElectorDelMensajeDelDia elector;
    private final Clock clock;

    public MensajeDelSemaforoService(CatalogoDeMensajesPort catalogo, MensajesElegidosPort elegidos, AzarPort azar,
                                     RecolectorDeDatosDelMensaje recolector, Clock clock) {
        this.catalogo = catalogo;
        this.elegidos = elegidos;
        this.recolector = recolector;
        this.elector = new ElectorDelMensajeDelDia(catalogo, elegidos, azar, clock, CanalDelMensaje.HOY);
        this.clock = clock;
    }

    @Override
    public Optional<MensajeDelDia> deHoy(ConsultaDelMensaje consulta) {
        Jornada jornada = jornadaDe(consulta);
        EleccionesDelDia previas = elegidos.deLaPersona(jornada.clave());
        return yaElegido(previas, jornada).or(() -> nuevo(previas, jornada))
                .flatMap(elegido -> escrito(elegido, jornada));
    }

    /** Lo que se necesita saber de «hoy» en todos los pasos: la persona, su día local y su grupo. */
    private record Jornada(ConsultaDelMensaje consulta, LocalDate hoy, GrupoDelMensaje grupo) {

        UserId persona() {
            return consulta.persona();
        }

        ClaveDeLaEleccion clave() {
            return new ClaveDeLaEleccion(persona(), hoy, grupo, CanalDelMensaje.HOY);
        }
    }

    /** El mensaje elegido con los datos con los que se va a escribir. */
    private record Elegido(MensajeDelSemaforo mensaje, DatosDelMensaje datos) {
    }

    private Jornada jornadaDe(ConsultaDelMensaje consulta) {
        LocalDate hoy = clock.now().atZone(consulta.participacion().zona()).toLocalDate();
        ResumenParaHoy semaforo = consulta.semaforo();
        GrupoDelMensaje grupo = GrupoDelDia.de(new SituacionDelSemaforo(semaforo.color(), semaforo.pausado(),
                semaforo.programaTerminado(), consulta.participacion().diaPrograma(), semaforo.dias()));
        return new Jornada(consulta, hoy, grupo);
    }

    private Optional<Elegido> yaElegido(EleccionesDelDia previas, Jornada jornada) {
        return previas.deHoy()
                .flatMap(catalogo::porId)
                .map(mensaje -> new Elegido(mensaje, recolector.reunir(jornada.consulta(), jornada.grupo(),
                        jornada.hoy(), mensaje.variables())));
    }

    private Optional<Elegido> nuevo(EleccionesDelDia previas, Jornada jornada) {
        DatosDelMensaje datos = datosCompletos(jornada);
        return elector.elegirYGuardar(jornada.clave(), datos, previas.vistos())
                .map(elegido -> new Elegido(elegido, datos));
    }

    private Optional<MensajeDelDia> escrito(Elegido elegido, Jornada jornada) {
        if (elegido.datos().tieneTodas(elegido.mensaje().variables())) {
            return Optional.of(aMensajeDelDia(elegido.mensaje(), elegido.datos()));
        }
        return sustituto(jornada);
    }

    /**
     * El mensaje pedía un dato que dejó de existir en el día. Se muestra otro del grupo que no lo pida, el mismo para
     * todas las lecturas de ese día (se elige por la persona y la fecha, no al azar).
     */
    private Optional<MensajeDelDia> sustituto(Jornada jornada) {
        DatosDelMensaje datos = datosCompletos(jornada);
        List<MensajeDelSemaforo> posibles = catalogo.activosDe(jornada.grupo()).stream()
                .filter(mensaje -> datos.tieneTodas(mensaje.variables())).toList();
        if (posibles.isEmpty()) {
            return Optional.empty();
        }
        int posicion = Math.floorMod(jornada.persona().value().hashCode() ^ jornada.hoy().hashCode(), posibles.size());
        return Optional.of(aMensajeDelDia(posibles.get(posicion), datos));
    }

    private DatosDelMensaje datosCompletos(Jornada jornada) {
        return recolector.reunir(jornada.consulta(), jornada.grupo(), jornada.hoy(),
                CanalDelMensaje.HOY.variablesPermitidas());
    }

    private static MensajeDelDia aMensajeDelDia(MensajeDelSemaforo mensaje, DatosDelMensaje datos) {
        return new MensajeDelDia(mensaje.id(), RedaccionDelMensaje.redactar(mensaje, datos), mensaje.sticker());
    }
}

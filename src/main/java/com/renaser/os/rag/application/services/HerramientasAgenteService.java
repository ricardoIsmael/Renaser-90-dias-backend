package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.in.herramienta.EjecutarHerramientaAgenteUseCase;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort.HabitoDelDia;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.herramienta.CatalogoHerramientasAgente;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ejecuta las herramientas del agente contra los puertos de negocio reales.
 *
 * <p><b>Sin ningun modelo detras, y probado igual.</b> Este servicio no depende de
 * {@code ChatIAPort}: se lo puede ejercitar entero con los puertos mockeados, que es como estan
 * probadas las tres herramientas hoy. Cuando exista un proveedor real, el adaptador lo llama y
 * este codigo no cambia.
 *
 * <p><b>Nada de {@code @Transactional} aca</b> (C-1, regla 01). Cada operacion de negocio abre y
 * cierra su propia transaccion corta puertas adentro; envolver la ejecucion de herramientas en
 * una transaccion propia la dejaria abierta mientras el bucle del proveedor va y vuelve, que es
 * exactamente como se agota el pool de Hikari para toda la API.
 *
 * <p><b>Todo fallo se traduce, ninguno se propaga.</b> Un modelo pide herramientas que no
 * existen, omite argumentos e inventa identificadores: es su comportamiento normal, no un caso
 * borde. Cada uno de esos vuelve como {@code Fallo} con un motivo que el asistente puede
 * repetirle a la persona. El detalle tecnico va al log, nunca al texto que ve el aprendiz.
 */
@Service
public class HerramientasAgenteService implements EjecutarHerramientaAgenteUseCase {

    private static final Logger log = LoggerFactory.getLogger(HerramientasAgenteService.class);

    private final ConsultarAgendaHabitosPort agendaHabitosPort;

    public HerramientasAgenteService(ConsultarAgendaHabitosPort agendaHabitosPort) {
        this.agendaHabitosPort = agendaHabitosPort;
    }

    @Override
    public List<DefinicionHerramienta> disponibles(AgenteConversacional agente) {
        return agente == AgenteConversacional.COMPANION ? CatalogoHerramientasAgente.definiciones() : List.of();
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        Optional<DefinicionHerramienta> definicion = CatalogoHerramientasAgente.porNombre(invocacion.nombre());
        if (definicion.isEmpty()) {
            return ResultadoHerramienta.fallo("No existe una herramienta llamada " + invocacion.nombre() + ".");
        }
        List<String> faltantes = definicion.get().obligatoriosFaltantesEn(invocacion);
        if (!faltantes.isEmpty()) {
            return ResultadoHerramienta.fallo("Faltan datos para usar esa herramienta: " + String.join(", ",
                    faltantes) + ".");
        }
        return ejecutarConDatosValidos(actorId, invocacion);
    }

    private ResultadoHerramienta ejecutarConDatosValidos(UserId actorId, InvocacionHerramienta invocacion) {
        return switch (invocacion.nombre()) {
            case CatalogoHerramientasAgente.CONSULTAR_HABITOS_DEL_DIA -> habitosDelDia(actorId);
            case CatalogoHerramientasAgente.CONSULTAR_PUNTOS_EN_JUEGO -> puntosEnJuego(actorId);
            case CatalogoHerramientasAgente.MARCAR_HABITO_COMPLETADO -> marcarCompletado(actorId,
                    invocacion.argumento(CatalogoHerramientasAgente.ARGUMENTO_REGISTRO_ID));
            default -> ResultadoHerramienta.fallo("Esa herramienta todavia no esta disponible.");
        };
    }

    private ResultadoHerramienta habitosDelDia(UserId actorId) {
        List<HabitoDelDia> habitos = agendaHabitosPort.deHoyDe(actorId);
        if (habitos.isEmpty()) {
            return ResultadoHerramienta.exito("Hoy no tiene ningun habito generado.");
        }
        StringBuilder texto = new StringBuilder();
        int totalEnJuego = 0;
        int pendientes = 0;
        for (HabitoDelDia habito : habitos) {
            texto.append(lineaDe(habito)).append('\n');
            if (habito.sigueEnJuego()) {
                totalEnJuego += habito.puntosEnJuego();
                pendientes++;
            }
        }
        // El total va en la MISMA respuesta (auditoria NFR 2026-09-06): "que me falta y cuanto
        // vale" es una pregunta frecuente, y sin esta linea el modelo encadenaba una segunda
        // herramienta (consultar_puntos_en_juego) para sumar lo que ya tenia adelante — un viaje
        // de ida y vuelta mas a Gemini, o sea uno o dos segundos mas de espera para la persona.
        // La herramienta de puntos sigue existiendo para la pregunta directa; esto solo evita
        // que haga falta llamar a las dos.
        texto.append("Total en juego: ").append(totalEnJuego).append(" puntos en ").append(pendientes)
                .append(" habito(s) que todavia puede entregar.");
        return ResultadoHerramienta.exito(texto.toString().trim());
    }

    /** Una linea por habito: el modelo la parafrasea, asi que dice lo que hace falta y nada mas. */
    private static String lineaDe(HabitoDelDia habito) {
        StringBuilder linea = new StringBuilder()
                .append("id=").append(habito.registroId())
                .append(" | ").append(habito.titulo())
                .append(" | estado=").append(habito.estado());
        if (habito.sigueEnJuego()) {
            linea.append(" | puntos_en_juego=").append(habito.puntosEnJuego())
                    .append(" de ").append(habito.puntosMaximos());
        }
        if (habito.plazo() != null) {
            linea.append(" | vence=").append(habito.plazo());
        }
        return linea.toString();
    }

    private ResultadoHerramienta puntosEnJuego(UserId actorId) {
        int total = 0;
        int pendientes = 0;
        for (HabitoDelDia habito : agendaHabitosPort.deHoyDe(actorId)) {
            if (habito.sigueEnJuego()) {
                total += habito.puntosEnJuego();
                pendientes++;
            }
        }
        return ResultadoHerramienta.exito("Le quedan " + total + " puntos en juego hoy, repartidos en " + pendientes
                + " habito(s) que todavia puede entregar.");
    }

    private ResultadoHerramienta marcarCompletado(UserId actorId, String registroId) {
        UUID id;
        try {
            id = UUID.fromString(registroId.trim());
        } catch (IllegalArgumentException noEsUnIdentificador) {
            return ResultadoHerramienta.fallo("Ese identificador de habito no es valido. Consulta primero los "
                    + "habitos del dia y usa el id que devuelven.");
        }
        try {
            return ResultadoHerramienta.exito("Habito marcado como completado. Puntos otorgados: "
                    + agendaHabitosPort.completar(actorId, id) + ".");
        } catch (RuntimeException fallaDelNegocio) {
            // El detalle va al log; al modelo solo un motivo apto para repetirle a la persona.
            log.info("[rag] la herramienta {} no pudo completar el habito: {}",
                    CatalogoHerramientasAgente.MARCAR_HABITO_COMPLETADO, fallaDelNegocio.toString());
            return ResultadoHerramienta.fallo("No se pudo marcar ese habito como completado: puede que ya este "
                    + "hecho, que se le haya vencido el plazo o que no sea uno de los suyos.");
        }
    }
}

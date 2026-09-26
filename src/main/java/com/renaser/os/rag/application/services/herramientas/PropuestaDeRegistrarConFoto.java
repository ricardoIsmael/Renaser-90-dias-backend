package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ConsultarPropuestasDelTurnoUseCase.PedidoDeEvidencia;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort.HabitoDelDia;
import com.renaser.os.rag.domain.model.herramienta.CatalogoHerramientasAgente;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ParametroHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.rag.domain.model.herramienta.TipoParametroHerramienta;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code proponer_registrar_con_foto} (D-171, decision del dueno): registrar un habito de hoy que
 * exige evidencia. El acompanante no recibe fotos, asi que no lo marca ni manda a la pantalla de
 * Hoy: le deja a la app una tarjeta con la camara ({@code EventoRenasia.Evidencia}). La persona saca
 * la foto y la app sube la evidencia FOTO y completa el habito, con los endpoints de siempre. Solo en
 * los tres rituales la app pregunta antes "¿Que sentiste?" (D-172, decision del dueno: "solo para
 * los rituales"). Aca solo se valida y se pide la tarjeta.
 *
 * <p><b>Valida con los datos de {@code habits}</b>, igual que {@link PropuestaDeMarcarHabito}: el
 * registro tiene que ser uno de SUS habitos de hoy ({@code deHoyDe} ya resuelve "hoy" en su zona),
 * seguir PENDIENTE o EN_CURSO, y pedir evidencia. La Clase diaria tambien la pide en el catalogo,
 * pero se cierra con su resumen: no se le ofrece camara.
 *
 * <p><b>La tarjeta vence al terminar el dia local de la persona</b> (regla 02): despues ese registro
 * ya no es el de hoy. No se usa la vigencia de las propuestas (10 minutos) porque no hay nada que
 * confirmar en el servidor: la app puede sacar la foto un rato despues sin que nada caduque.
 *
 * <p>Solo existe con {@code renaser.ia.acompanante.confirmacion-con-botones}: es la misma app que
 * dibuja los botones de las propuestas. Con el flag apagado, el acompanante hace lo de antes.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.acompanante.confirmacion-con-botones", havingValue = "true")
public class PropuestaDeRegistrarConFoto implements HerramientaAgente {

    public static final String NOMBRE = "proponer_registrar_con_foto";

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Para registrar un habito de hoy marcado exige_evidencia=si: le deja a la persona en la app un "
                    + "boton que abre la camara; saca la foto y la app lo registra. NO lo registra por si "
                    + "sola. Usala directo cuando pida marcar o registrar uno de esos habitos, "
                    + "sin preguntar antes. Nunca uses marcar_habito_completado para esos habitos.",
            List.of(ParametroHerramienta.obligatorio(CatalogoHerramientasAgente.ARGUMENTO_REGISTRO_ID,
                    TipoParametroHerramienta.IDENTIFICADOR, "El identificador del habito de hoy, tal cual lo "
                            + "devolvio consultar_habitos_del_dia. No lo inventes.")));

    private final ConsultarAgendaHabitosPort agendaPort;
    private final PedidosDeEvidenciaDelTurno pedidos;
    private final Clock clock;

    public PropuestaDeRegistrarConFoto(ConsultarAgendaHabitosPort agendaPort, PedidosDeEvidenciaDelTurno pedidos,
                                       Clock clock) {
        this.agendaPort = agendaPort;
        this.pedidos = pedidos;
        this.clock = clock;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        return CompletacionDeHabito.registroIdDe(invocacion.argumento(CatalogoHerramientasAgente.ARGUMENTO_REGISTRO_ID))
                .map(registroId -> pedirSiCorresponde(actorId, registroId))
                .orElseGet(CompletacionDeHabito::identificadorInvalido);
    }

    private ResultadoHerramienta pedirSiCorresponde(UserId actorId, UUID registroId) {
        Optional<HabitoDelDia> deHoy = agendaPort.deHoyDe(actorId).stream()
                .filter(habito -> registroId.equals(habito.registroId()))
                .findFirst();
        if (deHoy.isEmpty()) {
            return ResultadoHerramienta.fallo("Ese habito no esta entre los de hoy de la persona. Consulta "
                    + "consultar_habitos_del_dia y usa el id que devuelve.");
        }
        return motivoParaNoPedir(deHoy.get()).orElseGet(() -> pedir(actorId, deHoy.get()));
    }

    /** Vacio si se puede pedir la foto; si no, el motivo para el modelo. */
    static Optional<ResultadoHerramienta> motivoParaNoPedir(HabitoDelDia habito) {
        String titulo = "'" + habito.titulo() + "'";
        String motivo = switch (habito.estado()) {
            case "PENDIENTE", "EN_CURSO" -> null;
            case "COMPLETADO" -> titulo + " ya esta registrado hoy: no hace falta otra foto.";
            case "EXPIRADO" -> titulo + " ya vencio hoy: registrarlo ahora no suma puntos. Si igual lo hizo, "
                    + "puede subir la foto desde Hoy.";
            default -> titulo + " ya se cerro y no se puede registrar.";
        };
        if (motivo == null && HabitoDelDia.CLAVE_CLASE_DIARIA.equals(habito.claveSistema())) {
            motivo = "La Clase diaria se entrega con su resumen, no con foto: usa proponer_entregar_clase_de_hoy.";
        } else if (motivo == null && !habito.exigeEvidencia()) {
            motivo = titulo + " no pide foto: para marcarlo usa marcar_habito_completado.";
        }
        return Optional.ofNullable(motivo).map(ResultadoHerramienta::fallo);
    }

    private ResultadoHerramienta pedir(UserId actorId, HabitoDelDia habito) {
        Instant ahora = clock.now();
        pedidos.pedir(actorId, new PedidoDeEvidencia(habito.registroId(), habito.titulo(), ahora,
                finDelDiaLocal(ahora, agendaPort.zonaDe(actorId)), habito.preguntaQueSintio()));
        String alRegistrar = habito.preguntaQueSintio() ? "saque la foto y cuente que sintio" : "saque la foto";
        return ResultadoHerramienta.exito("Boton de la camara listo para '" + habito.titulo() + "'. TODAVIA NO "
                + "esta registrado: se registra cuando la persona " + alRegistrar + ", en la app. "
                + "Dilo en una sola frase corta, por ejemplo \"Te deje abajo el boton para sacarle foto a "
                + habito.titulo() + "\". No preguntes si quiere, no digas que ya quedo y no la mandes a Hoy.");
    }

    /** La medianoche que cierra el dia de la persona, en SU zona: no la del servidor (regla 02). */
    static Instant finDelDiaLocal(Instant ahora, ZoneId zona) {
        return ahora.atZone(zona).toLocalDate().plusDays(1).atStartOfDay(zona).toInstant();
    }
}

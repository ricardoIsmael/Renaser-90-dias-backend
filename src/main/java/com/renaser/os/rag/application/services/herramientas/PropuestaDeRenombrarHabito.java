package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase.PropuestaCreada;
import com.renaser.os.rag.application.ports.out.conversacion.LoadMensajeRenasiaPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.FichaDeHabito;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ParametroHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.rag.domain.model.herramienta.TipoParametroHerramienta;
import com.renaser.os.rag.domain.model.renombre.MotivoDeRenombre;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@code proponer_renombrar_habito} (D-236, pedido del dueño el 2026-09-30): ponerle un nombre propio
 * a un habito o volver al nombre del programa. No escribe: deja una propuesta y la persona confirma
 * con el boton. La escritura es {@link RenombrarHabitoConfirmable}, que llama a los mismos casos de
 * uso que {@code PUT/DELETE /api/v1/habits/{habitId}/rename} (D-133).
 *
 * <p><b>Las reglas son las del endpoint, no nuevas:</b> solo los habitos que {@code habits} marca
 * renombrables (hoy JUGO VERDE y AGUA TIBIA CON LIMON, D-127), nombre de hasta 60 caracteres y un
 * motivo escrito de hasta 200, los dos obligatorios. Se validan antes de proponer para no ofrecer un
 * boton que va a fallar; el caso de uso las vuelve a correr al confirmar.
 *
 * <p>Solo existe con {@code renaser.ia.acompanante.confirmacion-con-botones} prendido.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.acompanante.confirmacion-con-botones", havingValue = "true")
public class PropuestaDeRenombrarHabito implements HerramientaAgente {

    public static final String NOMBRE = "proponer_renombrar_habito";
    public static final String ARGUMENTO_HABITO_ID = "habito_id";
    public static final String ARGUMENTO_ACCION = "accion";
    public static final String ARGUMENTO_NOMBRE = "nuevo_nombre";
    public static final String ARGUMENTO_MOTIVO = "motivo";
    public static final String ACCION_RENOMBRAR = "renombrar";
    public static final String ACCION_ORIGINAL = "volver_al_original";
    /** Espejo de {@code RenombreHabito.requireTitulo} y del {@code @Size(max = 60)} del endpoint. */
    static final int MAXIMO_NOMBRE = 60;
    /** Espejo de {@code RenombreHabito.requireMotivo} y del {@code @Size(max = 200)} del endpoint. */
    static final int MAXIMO_MOTIVO = 200;

    private static final Logger log = LoggerFactory.getLogger(PropuestaDeRenombrarHabito.class);

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Propone cambiarle el nombre a un habito de la persona ('renombrar') o devolverle el nombre del "
                    + "programa ('volver_al_original'). NO lo cambia: deja una propuesta y la persona toca Confirmar "
                    + "en la app. Saca el habito_id de consultar_como_se_hace_habito. Solo algunos habitos se "
                    + "pueden renombrar. Para renombrar hace falta el motivo que dio la persona, POR QUE lo cambia, "
                    + "con sus palabras. 'Quiero que se llame X' no es un motivo: si no dijo por que, NO llames esta "
                    + "herramienta todavia; preguntaselo en una frase y llamala con su respuesta. Nunca lo inventes "
                    + "ni lo deduzcas: se rechaza un motivo que la persona no escribio.",
            List.of(ParametroHerramienta.obligatorio(ARGUMENTO_HABITO_ID, TipoParametroHerramienta.IDENTIFICADOR,
                            "El habito_id que devuelve consultar_como_se_hace_habito."),
                    ParametroHerramienta.obligatorio(ARGUMENTO_ACCION, TipoParametroHerramienta.TEXTO,
                            "'renombrar' o 'volver_al_original'."),
                    new ParametroHerramienta(ARGUMENTO_NOMBRE, TipoParametroHerramienta.TEXTO,
                            "Solo al renombrar: el nombre nuevo, tal cual lo pidio (hasta 60 caracteres).", false),
                    new ParametroHerramienta(ARGUMENTO_MOTIVO, TipoParametroHerramienta.TEXTO,
                            "Solo al renombrar: por que lo cambia, tal cual lo dijo la persona en la conversacion "
                                    + "(hasta 200 caracteres). Si no lo dijo, omitelo.",
                            false)));

    /** Cuanto hacia atras se mira lo que escribio la persona para encontrar su motivo. */
    static final Duration VENTANA_DEL_MOTIVO = Duration.ofHours(1);

    private final GestionarPlanDeHabitosPort planPort;
    private final ProponerAccionUseCase proponerAccion;
    private final LoadMensajeRenasiaPort mensajesPort;
    private final Clock clock;

    public PropuestaDeRenombrarHabito(GestionarPlanDeHabitosPort planPort, ProponerAccionUseCase proponerAccion,
                                      LoadMensajeRenasiaPort mensajesPort, Clock clock) {
        this.planPort = planPort;
        this.proponerAccion = proponerAccion;
        this.mensajesPort = mensajesPort;
        this.clock = clock;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        Optional<UUID> habitoId = CompletacionDeHabito.registroIdDe(invocacion.argumento(ARGUMENTO_HABITO_ID));
        if (habitoId.isEmpty()) {
            return ResultadoHerramienta.fallo("Ese habito_id no es valido. Consulta primero "
                    + ConsultarComoSeHaceHabitoHerramienta.NOMBRE + " y usa el habito_id que devuelve.");
        }
        String accion = accionDe(invocacion.argumento(ARGUMENTO_ACCION));
        if (ACCION_ORIGINAL.equals(accion)) {
            return conFichas(actorId, fichas -> volverAlOriginal(actorId, fichas, habitoId.get()));
        }
        if (!ACCION_RENOMBRAR.equals(accion)) {
            return ResultadoHerramienta.fallo("La accion tiene que ser 'renombrar' o 'volver_al_original'.");
        }
        Optional<String> nombre = recortado(invocacion.argumento(ARGUMENTO_NOMBRE));
        Optional<String> motivo = recortado(invocacion.argumento(ARGUMENTO_MOTIVO));
        Optional<String> problema = problemaDe(nombre, motivo);
        if (problema.isPresent()) {
            return ResultadoHerramienta.fallo(problema.get());
        }
        Pedido pedido = new Pedido(habitoId.get(), nombre.get(), motivo.get());
        return conFichas(actorId, fichas -> renombrar(actorId, fichas, pedido));
    }

    private ResultadoHerramienta renombrar(UserId actorId, List<FichaDeHabito> fichas, Pedido pedido) {
        Optional<FichaDeHabito> ficha = fichaDe(fichas, pedido.habitoId());
        if (ficha.isEmpty()) {
            return ResultadoHerramienta.fallo(noEsSuyo(fichas));
        }
        if (!ficha.get().renombrable()) {
            return ResultadoHerramienta.fallo(noRenombrable(ficha.get(), fichas));
        }
        if (pedido.nombre().equalsIgnoreCase(ficha.get().tituloVisible())) {
            return ResultadoHerramienta.fallo("Ese habito ya se llama '" + ficha.get().tituloVisible()
                    + "': no hay nada que cambiar.");
        }
        if (!MotivoDeRenombre.loDijoLaPersona(pedido.motivo(), pedido.nombre(), escritoPorLaPersona(actorId))) {
            return ResultadoHerramienta.fallo("Ese motivo no lo escribio la persona. No propongas todavia: preguntale "
                    + "en una frase por que le quiere cambiar el nombre, y vuelve a llamar con sus palabras.");
        }
        String resumen = "Cambiar el nombre de '" + ficha.get().tituloVisible() + "' a '" + pedido.nombre() + "'";
        return proponer(actorId, new InvocacionHerramienta(NOMBRE, Map.of(ARGUMENTO_HABITO_ID,
                pedido.habitoId().toString(), ARGUMENTO_ACCION, ACCION_RENOMBRAR, ARGUMENTO_NOMBRE, pedido.nombre(),
                ARGUMENTO_MOTIVO, pedido.motivo())), resumen);
    }

    /** E-466: lo que escribio en la ultima hora, en el chat o por voz; ya incluye el mensaje de este turno. */
    private List<String> escritoPorLaPersona(UserId actorId) {
        return mensajesPort.escritosPorElUsuarioDesde(actorId, clock.now().minus(VENTANA_DEL_MOTIVO)).stream()
                .map(MensajeRenasia::contenido)
                .toList();
    }

    private ResultadoHerramienta volverAlOriginal(UserId actorId, List<FichaDeHabito> fichas, UUID habitoId) {
        Optional<FichaDeHabito> ficha = fichaDe(fichas, habitoId);
        if (ficha.isEmpty()) {
            return ResultadoHerramienta.fallo(noEsSuyo(fichas));
        }
        if (!ficha.get().renombrado()) {
            return ResultadoHerramienta.fallo("'" + ficha.get().tituloDelPrograma() + "' ya tiene el nombre del "
                    + "programa: no hay nada que devolver.");
        }
        String resumen = "Volver a llamar '" + ficha.get().tituloPersonal() + "' por su nombre del programa, '"
                + ficha.get().tituloDelPrograma() + "'";
        return proponer(actorId, new InvocacionHerramienta(NOMBRE, Map.of(ARGUMENTO_HABITO_ID, habitoId.toString(),
                ARGUMENTO_ACCION, ACCION_ORIGINAL)), resumen);
    }

    private ResultadoHerramienta proponer(UserId actorId, InvocacionHerramienta normalizada, String resumen) {
        PropuestaCreada creada;
        try {
            creada = proponerAccion.proponer(actorId, normalizada, resumen);
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudo guardar la propuesta de {}", NOMBRE, falla);
            return AvisoDePropuesta.noSePudoPreparar();
        }
        return AvisoDePropuesta.yaEstaba(creada) ? AvisoDePropuesta.yaEstabaPendiente(creada)
                : AvisoDePropuesta.creada(resumen, null);
    }

    private ResultadoHerramienta conFichas(UserId actorId,
                                           Function<List<FichaDeHabito>, ResultadoHerramienta> siguiente) {
        try {
            return siguiente.apply(planPort.fichasDe(actorId));
        } catch (NoSuchElementException sinPrograma) {
            return ResultadoHerramienta.fallo("No encontre un programa activo para esta cuenta.");
        } catch (NotAuthorizedException suspendida) {
            return ResultadoHerramienta.fallo("La cuenta esta suspendida: no se puede cambiar el nombre de un habito.");
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudieron leer las fichas de habitos para {}", NOMBRE, falla);
            return ResultadoHerramienta.fallo("No pude consultar sus habitos en este momento.");
        }
    }

    /** Las mismas reglas que {@code RenombreHabito}: obligatorios, sin espacios al borde, 60 y 200. */
    static Optional<String> problemaDe(Optional<String> nombre, Optional<String> motivo) {
        if (nombre.isEmpty()) {
            return Optional.of("Falta el nombre nuevo: preguntale como lo quiere llamar.");
        }
        if (nombre.get().length() > MAXIMO_NOMBRE) {
            return Optional.of("El nombre nuevo puede tener hasta " + MAXIMO_NOMBRE + " caracteres: pidele uno mas "
                    + "corto.");
        }
        if (motivo.isEmpty()) {
            return Optional.of("Para cambiar el nombre hace falta el motivo de la persona. Preguntale en una frase por "
                    + "que lo quiere cambiar y vuelve a llamar con su respuesta; no lo inventes.");
        }
        return motivo.get().length() > MAXIMO_MOTIVO
                ? Optional.of("El motivo puede tener hasta " + MAXIMO_MOTIVO + " caracteres: resumelo con sus palabras.")
                : Optional.empty();
    }

    static Optional<String> recortado(String texto) {
        return texto == null || texto.isBlank() ? Optional.empty() : Optional.of(texto.strip());
    }

    private static Optional<FichaDeHabito> fichaDe(List<FichaDeHabito> fichas, UUID habitoId) {
        return fichas.stream().filter(ficha -> ficha.habitoId().equals(habitoId)).findFirst();
    }

    private static String noEsSuyo(List<FichaDeHabito> fichas) {
        return "Ese habito_id no es de ninguno de sus habitos: consulta primero "
                + ConsultarComoSeHaceHabitoHerramienta.NOMBRE + " y usa el habito_id que devuelve. "
                + renombrables(fichas);
    }

    private static String noRenombrable(FichaDeHabito ficha, List<FichaDeHabito> fichas) {
        return "'" + ficha.tituloVisible() + "' no se puede renombrar: en el programa solo se le cambia el nombre a "
                + "las bebidas que no todos toleran. Diselo en una frase, sin prometer otra forma. "
                + renombrables(fichas);
    }

    private static String renombrables(List<FichaDeHabito> fichas) {
        String lista = fichas.stream().filter(FichaDeHabito::renombrable)
                .map(ConsultarComoSeHaceHabitoHerramienta::lineaDe)
                .collect(Collectors.joining("\n"));
        return lista.isEmpty() ? "No tiene habitos que se puedan renombrar." : "Los que se pueden renombrar:\n" + lista;
    }

    private static String accionDe(String texto) {
        return texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
    }

    record Pedido(UUID habitoId, String nombre, String motivo) {
    }
}

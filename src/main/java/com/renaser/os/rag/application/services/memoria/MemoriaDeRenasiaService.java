package com.renaser.os.rag.application.services.memoria;

import com.renaser.os.rag.application.ports.in.memoria.BorrarMemoriaUseCase;
import com.renaser.os.rag.application.ports.in.memoria.CompactarMemoriaUseCase;
import com.renaser.os.rag.application.ports.in.memoria.ConsultarMemoriaUseCase;
import com.renaser.os.rag.application.ports.in.memoria.ConsultarMemoriaUseCase.MemoriaEnElPerfil;
import com.renaser.os.rag.application.ports.out.conversacion.LoadMensajeRenasiaPort;
import com.renaser.os.rag.application.ports.out.memoria.CompactarConversacionPort;
import com.renaser.os.rag.application.ports.out.memoria.EjecutarEnSegundoPlanoPort;
import com.renaser.os.rag.application.ports.out.memoria.MemoriaDeRenasiaPort;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.rag.domain.model.memoria.Compactacion;
import com.renaser.os.rag.domain.model.memoria.MemoriaDeRenasia;
import com.renaser.os.rag.domain.model.memoria.Recuerdo;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * La memoria del acompanante (D-167): lo que aprende de cada persona para que Renasia sea distinta
 * para cada una, con las mismas reglas para todas (pedido del dueno, 2026-09-25).
 *
 * <p><b>Como se arma.</b> El chat le da al modelo los ultimos 10 mensajes textuales. Cuando se
 * juntan {@value #UMBRAL_PARA_COMPACTAR} mensajes nuevos desde la ultima compactacion, los mas viejos
 * (todos menos los ultimos 10, que siguen yendo textuales) se comprimen con el modelo en un resumen
 * y en recuerdos por categoria. Asi la persona no pierde lo que conto hace dos semanas.
 *
 * <p><b>Sin transaccion alrededor de la IA</b> (C-1): la compactacion corre en segundo plano, lee,
 * llama al modelo y recien despues guarda (el adaptador de persistencia abre su propia transaccion).
 * Un turno nunca espera a que compacte, y una falla se reintenta en el turno siguiente, porque lo
 * pendiente se calcula desde {@code compactadoHasta}: la operacion es idempotente.
 *
 * <p>Una compactacion a la vez por persona: dos turnos seguidos no la duplican. El candado es en
 * memoria, suficiente mientras haya un solo backend; con varios, la segunda no guarda nada, porque
 * encuentra la memoria cambiada ({@link MemoriaDeRenasiaPort#reemplazar}).
 *
 * <p><b>Lo borrado no vuelve.</b> Si la persona borra algo mientras el modelo compacta, lo que
 * devuelve el modelo ya no se guarda (se compacto sobre la memoria vieja) y se reintenta en otro
 * turno. Y como {@code compactadoHasta} no retrocede, los mensajes viejos no se releen.
 */
@Service
public class MemoriaDeRenasiaService implements ConsultarMemoriaUseCase, BorrarMemoriaUseCase, CompactarMemoriaUseCase {

    private static final Logger log = LoggerFactory.getLogger(MemoriaDeRenasiaService.class);

    /** Mensajes nuevos (de la persona y del acompanante) que disparan una compactacion. */
    static final int UMBRAL_PARA_COMPACTAR = 20;
    /** Los que siguen yendo textuales al modelo: los mismos que lee el chat (TURNOS_DE_MEMORIA). */
    static final int QUEDAN_TEXTUALES = 10;
    /**
     * Cuantos mensajes recientes se miran como mucho en una compactacion. Si hay mas pendientes (la
     * memoria se encendio con mucho historial), lo anterior a la ventana queda fuera a proposito: el
     * costo de una compactacion no crece con la antiguedad de la cuenta.
     */
    static final int VENTANA_DE_LECTURA = 60;

    private final MemoriaDeRenasiaPort memoriaPort;
    private final LoadMensajeRenasiaPort mensajesPort;
    private final CompactarConversacionPort compactarPort;
    private final EjecutarEnSegundoPlanoPort segundoPlano;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final boolean activa;
    private final Set<UserId> compactando = ConcurrentHashMap.newKeySet();

    public MemoriaDeRenasiaService(MemoriaDeRenasiaPort memoriaPort, LoadMensajeRenasiaPort mensajesPort,
                                   CompactarConversacionPort compactarPort, EjecutarEnSegundoPlanoPort segundoPlano,
                                   IdGenerator idGenerator, Clock clock,
                                   @Value("${renaser.ia.acompanante.memoria:false}") boolean activa) {
        this.memoriaPort = memoriaPort;
        this.mensajesPort = mensajesPort;
        this.compactarPort = compactarPort;
        this.segundoPlano = segundoPlano;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.activa = activa;
    }

    @Override
    public MemoriaEnElPerfil paraElPerfil(UserId actorId) {
        return new MemoriaEnElPerfil(memoriaPort.de(actorId), activa);
    }

    @Override
    public Optional<MemoriaDeRenasia> paraConversar(UserId actorId) {
        if (!activa) {
            return Optional.empty();
        }
        try {
            return Optional.of(memoriaPort.de(actorId));
        } catch (RuntimeException falla) {
            // Sin memoria se conversa igual: es contexto, no un requisito. Y vacio, no "no sabes
            // nada": eso seria falso, la memoria existe y no se pudo leer.
            log.warn("[rag] no se pudo leer la memoria del acompanante ({})", falla.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public void borrarRecuerdo(UserId actorId, UUID recuerdoId) {
        if (!memoriaPort.olvidarRecuerdo(actorId, recuerdoId)) {
            throw new NoSuchElementException("Ese recuerdo no existe");
        }
    }

    @Override
    public void borrarTodo(UserId actorId) {
        memoriaPort.olvidarTodo(actorId, clock.now());
    }

    @Override
    public void compactarEnSegundoPlano(UserId actorId) {
        if (!activa || !compactando.add(actorId)) {
            return;
        }
        segundoPlano.ejecutar(() -> {
            try {
                compactarSiHaceFalta(actorId);
            } catch (RuntimeException falla) {
                log.warn("[rag] no se pudo compactar la memoria del acompanante ({}); se reintenta en otro turno",
                        falla.getClass().getSimpleName());
            } finally {
                compactando.remove(actorId);
            }
        });
    }

    /** Package-private para las pruebas: corre la compactacion en el hilo que llama. */
    void compactarSiHaceFalta(UserId actorId) {
        MemoriaDeRenasia actual = memoriaPort.de(actorId);
        List<MensajeRenasia> nuevos = mensajesPort.pagina(actorId, AgenteConversacional.COMPANION, null,
                        VENTANA_DE_LECTURA).stream()
                .filter(mensaje -> mensaje.creadoEn().isAfter(actual.compactadoHasta()))
                .sorted(Comparator.comparing(MensajeRenasia::creadoEn))
                .toList();
        if (nuevos.size() < UMBRAL_PARA_COMPACTAR) {
            return;
        }
        List<MensajeRenasia> aCompactar = nuevos.subList(0, nuevos.size() - QUEDAN_TEXTUALES);
        Compactacion resultado = compactarPort.compactar(new CompactarConversacionPort.Entrada(actual, aCompactar));
        MemoriaDeRenasia nueva = despuesDe(actual, resultado, aCompactar.getLast().creadoEn());
        if (!memoriaPort.reemplazar(actorId, actual, nueva)) {
            log.info("[rag] la memoria del acompanante cambio mientras se compactaba; se reintenta en otro turno");
        }
    }

    /**
     * Lo que queda tras compactar hasta {@code hasta}. Un recuerdo que sigue igual conserva su id y
     * su fecha (la persona lo puede estar mirando para borrarlo); un resumen inservible deja el que
     * habia. Igual se avanza {@code hasta}: nunca se recompacta lo mismo sin fin.
     */
    private MemoriaDeRenasia despuesDe(MemoriaDeRenasia actual, Compactacion resultado, Instant hasta) {
        Instant ahora = clock.now();
        List<Recuerdo> recuerdos = new ArrayList<>();
        resultado.textosQueQuedan(actual).forEach((categoria, textos) -> textos.forEach(texto -> recuerdos.add(
                actual.recuerdo(categoria, texto)
                        .orElseGet(() -> new Recuerdo(idGenerator.newId(), categoria, texto, ahora)))));
        Optional<String> resumen = Optional.of(resultado.resumenSaneado()).filter(texto -> !texto.isEmpty())
                .or(actual::resumen);
        return new MemoriaDeRenasia(recuerdos, resumen, hasta);
    }
}

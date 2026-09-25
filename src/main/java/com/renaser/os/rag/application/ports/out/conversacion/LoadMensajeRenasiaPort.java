package com.renaser.os.rag.application.ports.out.conversacion;

import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasiaId;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;

public interface LoadMensajeRenasiaPort {

    /** Paginacion keyset: los {@code limite} mensajes mas recientes que {@code usuarioId}
     * intercambio con {@code agente}, anteriores a {@code cursor} (null = pagina mas reciente),
     * orden descendente por {@code creadoEn}. Nunca devuelve mensajes del otro agente (D-102). */
    List<MensajeRenasia> pagina(UserId usuarioId, AgenteConversacional agente, Instant cursor, int limite);

    /**
     * Los mensajes que ESCRIBIO la persona (rol USUARIO) desde {@code desde} inclusive, del mas
     * viejo al mas nuevo. Lo usa {@code PatronDeMalestarRepetido} para derivar su cuenta de las
     * fechas en vez de acumularla en un contador (regla 02 §2).
     *
     * <p><b>De los DOS agentes, a diferencia de {@link #pagina}.</b> Ahi el agente separa dos chats
     * distintos porque son dos memorias distintas (D-102); aca la pregunta es sobre la PERSONA, y a
     * quien le escribio "no puedo mas" no cambia que lo haya escrito. Separarlos partiria la cuenta
     * en dos y el umbral no se alcanzaria nunca en alguien que usa los dos asistentes.
     *
     * <p>El volumen esta acotado por la cuota diaria de Renasia ({@code renaser.renasia.limite-diario},
     * hoy 25): en una ventana de siete dias son a lo sumo unos cientos de filas por persona, y las
     * cubre el indice {@code mensajes_renasia_conv_idx (usuario_id, creado_en)} que ya existe.
     */
    List<MensajeRenasia> escritosPorElUsuarioDesde(UserId usuarioId, Instant desde);

    /**
     * Si ya hay un mensaje con ese id (2026-09-23). Lo usa el aviso de habito en el chat, cuyo id
     * es DETERMINISTICO por aviso: es la forma de no escribirlo dos veces sin columna ni tabla
     * nueva. Se pregunta antes de guardar porque {@code save} con un id existente no falla —
     * JPA lo convierte en un UPDATE que pisaria el texto y la fecha del mensaje ya mostrado.
     */
    boolean existe(MensajeRenasiaId id);
}

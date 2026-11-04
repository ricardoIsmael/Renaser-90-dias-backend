package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.Map;

/**
 * El WhatsApp que cada persona declaró en su Ficha Inicial (la respuesta {@code whatsapp}, guardada como
 * {@code "+51 987654321"}). <b>Lo implementa {@code onboarding}</b>, dueño de {@code respuestas_onboarding}, y lo consume
 * {@code users} para el panel de personas de Administración: la dependencia va de {@code onboarding} hacia {@code users}, como
 * siempre, y {@code users} no importa a nadie (el mismo patrón de {@link FichaAlAceptar}). Ningún módulo manda SQL contra una
 * tabla ajena ({@code .claude/rules/01}).
 *
 * <p>El dato vive en un solo lugar: no hay otra columna ni otra tabla, y {@code usuarios.telefono} no se lee. Este contrato no
 * decide quién puede verlo: eso lo hace el endpoint que lo expone ({@code GET /api/v1/admin/trainees}, solo Administración y
 * Alquimista). <b>No lo uses desde un endpoint que vea el mentor, el líder o la propia persona.</b>
 */
public interface WhatsappDeLaFichaFinder {

    /**
     * El WhatsApp de cada persona pedida, tal como se guardó (solo se le quitan los espacios de los bordes), en <b>una sola
     * consulta</b> para todas. Quien no lo tiene —no llenó la ficha, es personal que no pasó por ella o lo dejó vacío— simplemente
     * no aparece en el mapa.
     */
    Map<UserId, String> de(Collection<UserId> personas);
}

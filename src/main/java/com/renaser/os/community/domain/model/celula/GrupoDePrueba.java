package com.renaser.os.community.domain.model.celula;

import com.renaser.os.community.domain.model.cohorte.CohorteId;

import java.util.UUID;

/**
 * El «Grupo de prueba (ver como)» y su cohorte (spec 002, fase 2, US5; contrato fase-2.md §5; D-273): donde el mentor
 * de prueba lidera al aprendiz y a la guía de prueba, para que «ver como Mentor» muestre un grupo real sin mostrar a
 * nadie real.
 *
 * <p>Ids fijos y conocidos en vez de una columna nueva en {@code celulas} y {@code cohortes}: hay un solo grupo de
 * prueba, y con el id alcanza para que la lista de grupos ({@code LoadCelulaPort.todas()}) y la de cohortes no lo
 * devuelvan nunca — de esas dos salen el panel de grupos de Administración, los semáforos y rankings por grupo, la vista
 * del administrador sobre los chats de grupo, los avisos de grupos y la rotación. Cohorte propia y grupo REGULAR sin
 * período: nunca es recepción (no recibe a nadie que se registre), nunca vence y no comparte cohorte con un grupo real.
 */
public final class GrupoDePrueba {

    public static final CohorteId COHORTE = CohorteId.of(UUID.fromString("7e57c0de-0000-4000-8000-000000000001"));
    public static final CelulaId CELULA = CelulaId.of(UUID.fromString("7e57c0de-0000-4000-8000-000000000002"));
    public static final String NOMBRE_DE_LA_COHORTE = "Cohorte de prueba (ver como)";
    public static final String NOMBRE_DEL_GRUPO = "Grupo de prueba (ver como)";

    private GrupoDePrueba() {
    }
}

package com.renaser.os.chat.domain.model.conversacion;

/**
 * La marca con la que aparece alguien en la lista de integrantes de un chat. Es lo que la persona
 * ES hoy en la plataforma (su rol), no un permiso: quién puede ver el chat lo decide
 * {@code AutorizarAccesoAConversacionUseCase}, no esta marca.
 */
public enum RolEnElChat {
    APRENDIZ,
    MENTOR,
    ADMIN,
    ALQUIMISTA
}

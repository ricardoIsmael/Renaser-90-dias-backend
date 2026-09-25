package com.renaser.os.rag.domain.model.memoria;

/**
 * Lo que el acompanante puede recordar de una persona (D-167). Las tres las eligio el dueno el
 * 2026-09-25; lo emocional quedo afuera a proposito (dato sensible, Ley 29733).
 */
public enum CategoriaDeRecuerdo {

    CONTEXTO_DE_VIDA("Tu contexto", "Contexto de vida"),
    METAS_Y_LO_QUE_FUNCIONA("Tus metas y lo que te funciona", "Sus metas y lo que le funciona"),
    PREFERENCIAS_DE_TRATO("Como prefieres que te acompañe", "Como prefiere que la acompanes");

    private final String paraLaPersona;
    private final String paraElModelo;

    CategoriaDeRecuerdo(String paraLaPersona, String paraElModelo) {
        this.paraLaPersona = paraLaPersona;
        this.paraElModelo = paraElModelo;
    }

    /** Como la ve la persona en su perfil. */
    public String paraLaPersona() {
        return paraLaPersona;
    }

    /** Como la lee el modelo en el prompt. */
    public String paraElModelo() {
        return paraElModelo;
    }
}

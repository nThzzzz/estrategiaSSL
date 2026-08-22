package model;

/** Cor de equipe conforme a SSL-Vision. */
public enum Cor {
    AZUL("blue"), AMARELO("yellow");

    private final String tag;

    Cor(String tag) { this.tag = tag; }

    /** Identificador estavel usado nos logs. */
    public String tag() { return tag; }

    public Cor oposta() { return this == AZUL ? AMARELO : AZUL; }
}

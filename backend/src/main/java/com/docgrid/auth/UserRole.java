package com.docgrid.auth;

/**
 * Papéis do sistema, por ordem crescente de alcance. O que cada um pode fazer só se
 * decide na etapa 06, com {@code @PreAuthorize}; aqui é apenas o vocabulário.
 */
public enum UserRole {
    /** Submete as suas próprias despesas e vê apenas essas. */
    EMPLOYEE,
    /** Revê a fila de documentos da organização, corrige campos e aprova. */
    FINANCE,
    /** Aprova despesas acima do limite da organização. */
    MANAGER,
    /** Administra a organização e os seus utilizadores. */
    ADMIN
}

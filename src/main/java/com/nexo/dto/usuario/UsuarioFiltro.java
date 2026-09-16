package com.nexo.dto.usuario;

import com.nexo.entity.PerfilUsuario;
import com.nexo.entity.SituacaoUsuario;

/**
 * Filtros opcionais para {@code GET /api/usuarios}, ligados aos query params
 * da requisição via {@code @ModelAttribute}. Todos os campos são opcionais;
 * nome, e-mail e departamento usam busca parcial (case-insensitive), perfil
 * e situação usam igualdade exata.
 *
 * <p>Implementada como classe simples (não record) por ser o formato de
 * data-binding mais consolidado do Spring MVC para {@code @ModelAttribute}
 * a partir de query params.</p>
 */
public class UsuarioFiltro {

    private String nome;
    private String email;
    private String departamento;
    private PerfilUsuario perfil;
    private SituacaoUsuario situacao;

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDepartamento() {
        return departamento;
    }

    public void setDepartamento(String departamento) {
        this.departamento = departamento;
    }

    public PerfilUsuario getPerfil() {
        return perfil;
    }

    public void setPerfil(PerfilUsuario perfil) {
        this.perfil = perfil;
    }

    public SituacaoUsuario getSituacao() {
        return situacao;
    }

    public void setSituacao(SituacaoUsuario situacao) {
        this.situacao = situacao;
    }
}

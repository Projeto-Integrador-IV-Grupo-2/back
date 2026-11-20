package com.nexo.service;

import com.nexo.dto.conteudo.ConteudoRequest;
import com.nexo.dto.conteudo.ConteudoResponse;
import com.nexo.entity.Area;
import com.nexo.entity.CatalogoConteudo;
import com.nexo.entity.Conteudo;
import com.nexo.entity.Perfil;
import com.nexo.entity.StatusConteudo;
import com.nexo.event.ConteudoAtualizadoEvent;
import com.nexo.event.ConteudoDespublicadoEvent;
import com.nexo.event.ConteudoPublicadoEvent;
import com.nexo.exception.OperacaoNaoPermitidaException;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.AreaRepository;
import com.nexo.repository.ConteudoFiltro;
import com.nexo.repository.ConteudoRepository;
import com.nexo.repository.ConteudoSpecifications;
import com.nexo.repository.TenantRepository;
import com.nexo.repository.TrilhaEtapaRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catálogo de conteúdos de estudo (issue #40).
 *
 * <p><strong>Visibilidade:</strong> ADMIN e GESTOR veem rascunhos e
 * publicados; COLABORADOR só vê publicados — na listagem o filtro de status é
 * forçado, e um rascunho buscado por id responde 404, como se não existisse.
 * Conteúdo de outro tenant também responde 404.</p>
 *
 * <p>Quem pode escrever é decidido no controller ({@code @PreAuthorize}); o
 * material de cada tipo é montado por {@link ConteudoFactory}.</p>
 */
@Service
public class ConteudoService {

    private final ConteudoRepository conteudoRepository;
    private final ConteudoFactory conteudoFactory;
    private final CategoriaService categoriaService;
    private final AreaRepository areaRepository;
    private final TenantRepository tenantRepository;
    private final UsuarioRepository usuarioRepository;
    private final TrilhaEtapaRepository trilhaEtapaRepository;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public ConteudoService(ConteudoRepository conteudoRepository,
                           ConteudoFactory conteudoFactory,
                           CategoriaService categoriaService,
                           AreaRepository areaRepository,
                           TenantRepository tenantRepository,
                           UsuarioRepository usuarioRepository,
                           TrilhaEtapaRepository trilhaEtapaRepository,
                           ApplicationEventPublisher publisher,
                           Clock clock) {
        this.conteudoRepository = conteudoRepository;
        this.conteudoFactory = conteudoFactory;
        this.categoriaService = categoriaService;
        this.areaRepository = areaRepository;
        this.tenantRepository = tenantRepository;
        this.usuarioRepository = usuarioRepository;
        this.trilhaEtapaRepository = trilhaEtapaRepository;
        this.publisher = publisher;
        this.clock = clock;
    }

    /** Cria o conteúdo em rascunho. Vídeo é validado no Vimeo antes de gravar. */
    @Transactional
    public ConteudoResponse criar(ConteudoRequest request, UsuarioAutenticado requisitante) {
        Conteudo conteudo = conteudoFactory.criar(request);
        conteudo.setTenant(tenantRepository.getReferenceById(requisitante.getTenantId()));
        conteudo.setCriadoPor(usuarioRepository.getReferenceById(requisitante.getId()));
        conteudo.setCatalogo(request.catalogo() == null ? CatalogoConteudo.TRILHA : request.catalogo());
        aplicarDados(conteudo, request, requisitante.getTenantId());
        return ConteudoResponse.de(conteudoRepository.saveAndFlush(conteudo));
    }

    @Transactional(readOnly = true)
    public Page<ConteudoResponse> listar(ConteudoFiltro filtro, Pageable pageable, UsuarioAutenticado requisitante) {
        ConteudoFiltro criterios = filtro == null ? ConteudoFiltro.vazio() : filtro;
        if (!podeVerRascunho(requisitante)) {
            criterios = criterios.comStatus(StatusConteudo.PUBLICADO);
        }
        var especificacao = ConteudoSpecifications.comFiltro(criterios, requisitante.getTenantId());
        if (!podeVerRascunho(requisitante)) {
            especificacao = especificacao.and(ConteudoSpecifications.semRestritosAGestao());
        }
        return conteudoRepository
                .findAll(especificacao, pageable)
                .map(ConteudoResponse::de);
    }

    @Transactional(readOnly = true)
    public ConteudoResponse buscarPorId(UUID id, UsuarioAutenticado requisitante) {
        return ConteudoResponse.de(buscarVisivel(id, requisitante));
    }

    @Transactional
    public ConteudoResponse atualizar(UUID id, ConteudoRequest request, UsuarioAutenticado requisitante) {
        Conteudo conteudo = buscarNoTenant(id, requisitante.getTenantId());
        if (request.restritoAGestao() && trilhaEtapaRepository.existsByConteudoId(conteudo.getId())) {
            throw new OperacaoNaoPermitidaException(
                    "Conteúdo usado em etapa de trilha não pode ficar visível só para a gestão: "
                            + conteudo.getTitulo());
        }
        conteudoFactory.aplicarMaterial(conteudo, request);
        aplicarDados(conteudo, request, requisitante.getTenantId());
        ConteudoResponse resposta = ConteudoResponse.de(conteudoRepository.saveAndFlush(conteudo));
        if (conteudo.isPublicado()) {
            publisher.publishEvent(new ConteudoAtualizadoEvent(conteudo.getId(), requisitante.getTenantId()));
        }
        return resposta;
    }

    /** Torna o conteúdo visível aos colaboradores. Idempotente para quem já está publicado. */
    @Transactional
    public ConteudoResponse publicar(UUID id, UsuarioAutenticado requisitante) {
        Conteudo conteudo = buscarNoTenant(id, requisitante.getTenantId());
        if (conteudo.isPublicado()) {
            return ConteudoResponse.de(conteudo);
        }
        conteudo.setStatus(StatusConteudo.PUBLICADO);
        conteudo.setPublicadoEm(OffsetDateTime.now(clock));
        ConteudoResponse resposta = ConteudoResponse.de(conteudoRepository.saveAndFlush(conteudo));
        publisher.publishEvent(new ConteudoPublicadoEvent(
                conteudo.getId(),
                requisitante.getTenantId(),
                conteudo.getArea() == null ? null : conteudo.getArea().getId(),
                requisitante.getId()));
        return resposta;
    }

    /**
     * Volta o conteúdo para rascunho: some para os colaboradores e sai da
     * busca do assistente de IA. Idempotente para quem já é rascunho.
     *
     * <p>Conteúdo usado numa etapa de trilha não pode voltar a rascunho: a
     * etapa ficaria apontando para material invisível ao colaborador.</p>
     */
    @Transactional
    public ConteudoResponse despublicar(UUID id, UsuarioAutenticado requisitante) {
        Conteudo conteudo = buscarNoTenant(id, requisitante.getTenantId());
        if (!conteudo.isPublicado()) {
            return ConteudoResponse.de(conteudo);
        }
        if (trilhaEtapaRepository.existsByConteudoId(conteudo.getId())) {
            throw new OperacaoNaoPermitidaException(
                    "Remova o conteúdo das etapas de trilha antes de despublicá-lo: " + conteudo.getTitulo());
        }
        conteudo.setStatus(StatusConteudo.RASCUNHO);
        conteudo.setPublicadoEm(null);
        ConteudoResponse resposta = ConteudoResponse.de(conteudoRepository.saveAndFlush(conteudo));
        publisher.publishEvent(new ConteudoDespublicadoEvent(conteudo.getId(), requisitante.getTenantId()));
        return resposta;
    }

    /** Usado pelas etapas: conteúdo do tenant e publicado, ou erro. */
    Conteudo buscarPublicadoNoTenant(UUID id, UUID tenantId) {
        Conteudo conteudo = buscarNoTenant(id, tenantId);
        if (!conteudo.isPublicado()) {
            throw new OperacaoNaoPermitidaException(
                    "Publique o conteúdo antes de usá-lo numa etapa: " + conteudo.getTitulo());
        }
        if (conteudo.isApenasAdmin()) {
            throw new OperacaoNaoPermitidaException(
                    "Conteúdo visível só para a gestão não pode ser usado numa trilha: " + conteudo.getTitulo());
        }
        if (conteudo.getCatalogo() != CatalogoConteudo.TRILHA) {
            throw new OperacaoNaoPermitidaException(
                    "Documento do Conhecimento não pode ser usado numa trilha: " + conteudo.getTitulo());
        }
        return conteudo;
    }

    private void aplicarDados(Conteudo conteudo, ConteudoRequest request, UUID tenantId) {
        conteudo.setTitulo(request.titulo().trim());
        conteudo.setDescricao(textoOuNulo(request.descricao()));
        conteudo.setCategoria(request.categoriaId() == null
                ? null
                : categoriaService.buscarAtivaNoTenant(request.categoriaId(), tenantId));
        conteudo.setArea(resolverArea(request.areaId(), tenantId));
        conteudo.setApenasAdmin(request.restritoAGestao());
    }

    private Area resolverArea(UUID areaId, UUID tenantId) {
        if (areaId == null) {
            return null;
        }
        return areaRepository.findById(areaId)
                .filter(area -> area.getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Área não encontrada: " + areaId));
    }

    private Conteudo buscarVisivel(UUID id, UsuarioAutenticado requisitante) {
        Conteudo conteudo = buscarNoTenant(id, requisitante.getTenantId());
        // Rascunho e conteúdo restrito à gestão não existem para o colaborador.
        if ((!conteudo.isPublicado() || conteudo.isApenasAdmin()) && !podeVerRascunho(requisitante)) {
            throw new ResourceNotFoundException("Conteúdo não encontrado: " + id);
        }
        return conteudo;
    }

    private Conteudo buscarNoTenant(UUID id, UUID tenantId) {
        return conteudoRepository.findById(id)
                .filter(conteudo -> conteudo.getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Conteúdo não encontrado: " + id));
    }

    private static boolean podeVerRascunho(UsuarioAutenticado requisitante) {
        return requisitante.getPapel() == Perfil.ADMIN || requisitante.getPapel() == Perfil.GESTOR;
    }

    private static String textoOuNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}

package com.nexo.controller;

import com.nexo.dto.assistente.CriarSessaoRequest;
import com.nexo.dto.assistente.MensagemResponse;
import com.nexo.dto.assistente.PerguntaRequest;
import com.nexo.dto.assistente.RenomearSessaoRequest;
import com.nexo.dto.assistente.RespostaAssistenteResponse;
import com.nexo.dto.assistente.SessaoResponse;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.assistente.ChatSessaoService;
import com.nexo.service.assistente.ConsultaAssistenteService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Assistente de IA (issues #59 e #60). Qualquer usuário autenticado conversa,
 * sempre nas próprias sessões — não há perfil privilegiado aqui: o admin
 * recebe 404 na conversa de outra pessoa, como qualquer um.
 *
 * <p>Nenhuma regra de negócio vive aqui: este controller só traduz HTTP
 * &lt;-&gt; DTO.</p>
 */
@RestController
@RequestMapping("/api/assistente/sessoes")
public class AssistenteController {

    private final ChatSessaoService chatSessaoService;
    private final ConsultaAssistenteService consultaAssistenteService;

    public AssistenteController(ChatSessaoService chatSessaoService,
                                ConsultaAssistenteService consultaAssistenteService) {
        this.chatSessaoService = chatSessaoService;
        this.consultaAssistenteService = consultaAssistenteService;
    }

    @PostMapping
    public ResponseEntity<SessaoResponse> criarSessao(@Valid @RequestBody(required = false) CriarSessaoRequest request,
                                                      @AuthenticationPrincipal UsuarioAutenticado usuario,
                                                      UriComponentsBuilder uriBuilder) {
        SessaoResponse response = chatSessaoService.criar(
                request == null ? null : request.titulo(),
                request == null ? null : request.contexto(), usuario);
        URI location = uriBuilder.path("/api/assistente/sessoes/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    /** Conversas do usuário autenticado, da mais recente para a mais antiga. */
    @GetMapping
    public PagedModel<SessaoResponse> listarSessoes(@PageableDefault(size = 20) Pageable pageable,
                                                    @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return new PagedModel<>(chatSessaoService.listar(pageable, usuario));
    }

    /** Histórico da conversa, na ordem em que as mensagens foram trocadas. */
    @GetMapping("/{id}/mensagens")
    public List<MensagemResponse> listarMensagens(@PathVariable UUID id,
                                                  @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return chatSessaoService.mensagens(id, usuario);
    }

    @PatchMapping("/{id}")
    public SessaoResponse renomear(@PathVariable UUID id,
                                   @Valid @RequestBody RenomearSessaoRequest request,
                                   @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return chatSessaoService.renomear(id, request.titulo(), usuario);
    }

    /** Apaga a conversa e as mensagens dela. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> apagar(@PathVariable UUID id,
                                       @AuthenticationPrincipal UsuarioAutenticado usuario) {
        chatSessaoService.apagar(id, usuario);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/mensagens")
    public RespostaAssistenteResponse perguntar(@PathVariable UUID id,
                                                @Valid @RequestBody PerguntaRequest request,
                                                @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return consultaAssistenteService.perguntar(id, request.conteudo(), usuario);
    }
}

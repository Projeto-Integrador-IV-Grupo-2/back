package com.nexo.service.documento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexo.config.DocumentosProperties;
import com.nexo.dto.arquivo.ArquivoResponse;
import com.nexo.entity.Arquivo;
import com.nexo.entity.Conteudo;
import com.nexo.entity.Perfil;
import com.nexo.entity.StatusConteudo;
import com.nexo.entity.Tenant;
import com.nexo.entity.TipoArquivo;
import com.nexo.entity.Usuario;
import com.nexo.event.ArquivoEnviadoEvent;
import com.nexo.exception.CampoInvalidoException;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.ArquivoRepository;
import com.nexo.repository.ConteudoRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.armazenamento.ArmazenamentoDeArquivos;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class ArquivoServiceTest {

    @Mock
    private ArquivoRepository arquivoRepository;

    @Mock
    private ConteudoRepository conteudoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ArmazenamentoDeArquivos armazenamento;

    @Mock
    private ApplicationEventPublisher publisher;

    private ArquivoService service;
    private Tenant tenant;
    private Usuario gestor;
    private Conteudo documento;

    @BeforeEach
    void setUp() {
        service = new ArquivoService(arquivoRepository, conteudoRepository, usuarioRepository, armazenamento,
                new DetectorDeTipoDeArquivo(), publisher, new DocumentosProperties(DataSize.ofKilobytes(64)));
        tenant = new Tenant();
        ReflectionTestUtils.setField(tenant, "id", UUID.randomUUID());
        gestor = usuario(Perfil.GESTOR);
        documento = Conteudo.novoDocumento("Manual", gestor);
        ReflectionTestUtils.setField(documento, "id", UUID.randomUUID());
    }

    @Test
    void enviaGuardaComChaveGeradaGravaEAvisaAIndexacao() throws Exception {
        when(conteudoRepository.findByIdAndTenantId(documento.getId(), tenant.getId())).thenReturn(Optional.of(documento));
        when(usuarioRepository.getReferenceById(gestor.getId())).thenReturn(gestor);
        when(arquivoRepository.saveAndFlush(any(Arquivo.class))).thenAnswer(invocation -> {
            Arquivo arquivo = invocation.getArgument(0);
            ReflectionTestUtils.setField(arquivo, "id", UUID.randomUUID());
            return arquivo;
        });

        ArquivoResponse resposta = service.enviar(documento.getId(),
                new MockMultipartFile("arquivo", "C:\\fakepath\\Relatório anual.pdf", "application/octet-stream",
                        ArquivosDeTeste.pdf("Relatorio")),
                UsuarioAutenticado.de(gestor));

        ArgumentCaptor<String> chave = ArgumentCaptor.forClass(String.class);
        verify(armazenamento).salvar(chave.capture(), any(InputStream.class));
        // A chave é gerada (tenant/uuid); o nome do usuário nunca entra nela.
        assertThat(chave.getValue()).startsWith(tenant.getId() + "/").doesNotContain("Relat");
        assertThat(resposta.nomeOriginal()).isEqualTo("Relatório anual.pdf");
        assertThat(resposta.tipo()).isEqualTo(TipoArquivo.PDF);
        assertThat(resposta.mimeType()).isEqualTo("application/pdf");
        verify(publisher).publishEvent(new ArquivoEnviadoEvent(resposta.id(), documento.getId(), tenant.getId()));
    }

    @Test
    void arquivoAcimaDoLimiteEhRecusadoAntesDeGuardar() throws Exception {
        when(conteudoRepository.findByIdAndTenantId(documento.getId(), tenant.getId())).thenReturn(Optional.of(documento));

        assertThatThrownBy(() -> service.enviar(documento.getId(),
                new MockMultipartFile("arquivo", "grande.txt", "text/plain", new byte[64 * 1024 + 1]),
                UsuarioAutenticado.de(gestor)))
                .isInstanceOf(CampoInvalidoException.class)
                .hasMessage("O arquivo passa do tamanho máximo de 64 KB");
        verify(armazenamento, never()).salvar(anyString(), any());
    }

    @Test
    void tipoNaoPermitidoEhRecusadoAntesDeGuardar() throws Exception {
        when(conteudoRepository.findByIdAndTenantId(documento.getId(), tenant.getId())).thenReturn(Optional.of(documento));

        assertThatThrownBy(() -> service.enviar(documento.getId(),
                new MockMultipartFile("arquivo", "planilha.xlsx", "application/octet-stream", new byte[] {1, 2}),
                UsuarioAutenticado.de(gestor)))
                .isInstanceOf(CampoInvalidoException.class);
        verify(armazenamento, never()).salvar(anyString(), any());
        verify(arquivoRepository, never()).saveAndFlush(any());
    }

    @Test
    void arquivoVazioEhRecusado() {
        when(conteudoRepository.findByIdAndTenantId(documento.getId(), tenant.getId())).thenReturn(Optional.of(documento));

        assertThatThrownBy(() -> service.enviar(documento.getId(),
                new MockMultipartFile("arquivo", "vazio.txt", "text/plain", new byte[0]),
                UsuarioAutenticado.de(gestor)))
                .isInstanceOf(CampoInvalidoException.class);
    }

    @Test
    void conteudoDeOutroTenantRespondeNaoEncontrado() {
        when(conteudoRepository.findByIdAndTenantId(eq(documento.getId()), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enviar(documento.getId(),
                new MockMultipartFile("arquivo", "a.txt", "text/plain", ArquivosDeTeste.texto("oi")),
                UsuarioAutenticado.de(gestor)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void colaboradorNaoBaixaArquivoDeRascunho() {
        Arquivo arquivo = arquivoDe(documento);
        when(arquivoRepository.findByIdAndTenantId(arquivo.getId(), tenant.getId())).thenReturn(Optional.of(arquivo));

        assertThatThrownBy(() -> service.baixar(arquivo.getId(), UsuarioAutenticado.de(usuario(Perfil.COLABORADOR))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void colaboradorBaixaArquivoDeConteudoPublicado() throws Exception {
        documento.setStatus(StatusConteudo.PUBLICADO);
        Arquivo arquivo = arquivoDe(documento);
        when(arquivoRepository.findByIdAndTenantId(arquivo.getId(), tenant.getId())).thenReturn(Optional.of(arquivo));
        when(armazenamento.abrir(arquivo.getChaveArmazenamento())).thenReturn(new ByteArrayInputStream(new byte[] {1}));

        ArquivoParaDownload download = service.baixar(arquivo.getId(), UsuarioAutenticado.de(usuario(Perfil.COLABORADOR)));

        assertThat(download.nomeOriginal()).isEqualTo("manual.pdf");
        assertThat(download.mimeType()).isEqualTo("application/pdf");
    }

    @Test
    void registroSemBinarioNoArmazenamentoRespondeNaoEncontrado() throws Exception {
        Arquivo arquivo = arquivoDe(documento);
        when(arquivoRepository.findByIdAndTenantId(arquivo.getId(), tenant.getId())).thenReturn(Optional.of(arquivo));
        when(armazenamento.abrir(arquivo.getChaveArmazenamento())).thenThrow(new NoSuchFileException("x"));

        assertThatThrownBy(() -> service.baixar(arquivo.getId(), UsuarioAutenticado.de(gestor)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void nomeSeguroTiraPastasEControlesELimitaOTamanho() {
        assertThat(ArquivoService.nomeSeguro("../../etc/passwd.txt")).isEqualTo("passwd.txt");
        assertThat(ArquivoService.nomeSeguro("C:\\Users\\ana\\nota\u0000.md")).isEqualTo("nota.md");
        String longo = ArquivoService.nomeSeguro("a".repeat(300) + ".pdf");
        assertThat(longo).hasSize(255).endsWith(".pdf");
        assertThatThrownBy(() -> ArquivoService.nomeSeguro("pasta/")).isInstanceOf(CampoInvalidoException.class);
    }

    @Test
    void descreveOTamanhoMaximo() {
        assertThat(ArquivoService.descreverTamanho(10L * 1024 * 1024)).isEqualTo("10 MB");
        assertThat(ArquivoService.descreverTamanho(64L * 1024)).isEqualTo("64 KB");
    }

    private Arquivo arquivoDe(Conteudo conteudo) {
        Arquivo arquivo = Arquivo.novo(conteudo, "manual.pdf", TipoArquivo.PDF, 10, tenant.getId() + "/abc", gestor);
        ReflectionTestUtils.setField(arquivo, "id", UUID.randomUUID());
        return arquivo;
    }

    private Usuario usuario(Perfil papel) {
        Usuario usuario = new Usuario();
        usuario.setTenant(tenant);
        usuario.setNomeCompleto(papel.name());
        usuario.setEmail(UUID.randomUUID() + "@empresa.com");
        usuario.setSenhaHash("hash");
        usuario.setPapel(papel);
        usuario.setAtivo(true);
        ReflectionTestUtils.setField(usuario, "id", UUID.randomUUID());
        return usuario;
    }
}

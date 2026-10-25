# Nexo — Back-end (API)

API do projeto Nexo para centralizar conhecimento corporativo e trilhas de
aprendizagem. O back-end inclui autenticação JWT, gestão de usuários e catálogo,
trilhas com etapas e progresso, PDI, histórico de interações e endpoints de
dashboards.


## Tecnologias

| Camada | O que usamos | Versão |
|---|---|---|
| Linguagem | Java | 17 |
| Framework | Spring Boot | 3.4 |
| Web / REST | Spring Web (MVC) | — |
| Banco de dados | PostgreSQL + pgvector | 16 |
| Assistente de IA | Google Gemini (chat + embeddings) | `gemini-2.5-flash` / `gemini-embedding-001` |
| Acesso a dados | Spring Data JPA (Hibernate) | — |
| Migrations | Flyway | — |
| Segurança | Spring Security | — |
| Validação | Bean Validation | — |
| Monitoramento | Spring Boot Actuator | — |
| Build | Maven (via wrapper `./mvnw`) | — |
| Testes | JUnit 5 + Spring Test + Testcontainers | — |
| Ambiente local | Docker + Docker Compose | — |

---

## O que instalar

Só três coisas. Instale, abra um terminal novo e confira com o comando da
última coluna.

| Ferramenta | Versão | Para quê serve | Conferir |
|---|---|---|---|
| **JDK 17** | 17 | Compilar e rodar a API | `java -version` → deve mostrar `17` |
| **Docker Desktop** | atual | Subir o PostgreSQL e rodar os testes | `docker --version` |
| **Git** | qualquer | Clonar e versionar o código | `git --version` |

> **Maven não precisa ser instalado.** O projeto traz o *wrapper*: use
> `./mvnw` no Linux/macOS ou `mvnw.cmd` no Windows. Ele baixa a versão certa
> do Maven sozinho na primeira execução.

Links: [JDK 17 (Temurin)](https://adoptium.net/temurin/releases/?version=17) ·
[Docker Desktop](https://www.docker.com/products/docker-desktop/)

---

## Como rodar

Tenha o **Docker Desktop aberto** e o **JDK 17** instalado
(ver [O que instalar](#o-que-instalar)).

Abra um terminal na pasta do projeto e rode **os dois comandos, nesta ordem**.
O perfil `dev` precisa ser escolhido explicitamente; a aplicação não ativa um
perfil de desenvolvimento automaticamente.

```bash
docker compose up -d
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

No PowerShell do Windows, use:

```powershell
docker compose up -d
$env:SPRING_PROFILES_ACTIVE = "dev"
.\mvnw.cmd spring-boot:run
```

Pronto. A API está em **http://localhost:8080**.

Você **não precisa configurar nada** — o `docker compose up -d` já cria o banco
com estes valores (definidos no [`docker-compose.yml`](docker-compose.yml)):

| Item | Valor |
|---|---|
| Banco | `nexo` |
| Usuário | `nexo` |
| Senha | `nexo` |
| Host / porta | `localhost:5432` |

Funciona igual em **Windows, macOS e Linux** — basta ter o Docker Desktop
aberto e o JDK 17. Cada integrante roda os mesmos dois comandos, sem ajustar
arquivo nenhum. (Só cria um `.env` quem quiser mudar algum desses valores;
veja [Variáveis de ambiente](#variáveis-de-ambiente).)

Para conferir, abra **outro terminal** e rode:

```bash
curl http://localhost:8080/api/health
```

Deve responder com código **200** e `{"status":"UP", ...}`.

### Para desligar

1. No terminal da API: aperte **`Ctrl+C`**.
2. Depois: `docker compose down` (desliga o banco; os dados continuam salvos).

### Comandos de Docker

| Comando | O que faz |
|---|---|
| `docker compose up -d` | Liga o banco |
| `docker compose down` | Desliga o banco (mantém os dados) |
| `docker compose ps` | Mostra se o banco está no ar |
| `docker compose logs -f db` | Mostra os logs do banco (`Ctrl+C` sai do log, não desliga) |
| `docker compose down -v` | Desliga e **apaga os dados** (para recomeçar do zero) |

<details>
<summary><b>Por que preciso do Docker?</b></summary>

<br>

O banco **PostgreSQL roda dentro do Docker**, então você não precisa instalar
PostgreSQL na máquina. O comando `docker compose up -d` lê o
[`docker-compose.yml`](docker-compose.yml) e sobe esse banco na porta `5432`
(usuário/senha/banco = `nexo`/`nexo`/`nexo`).

A **API roda fora do Docker** (com `./mvnw`) e se conecta nesse banco. Por isso
a ordem: banco primeiro, API depois. No start, o Flyway cria as tabelas
automaticamente.

Os dados ficam num volume chamado `nexo-db-data` e sobrevivem ao
`docker compose down` — só somem com `docker compose down -v`.

</details>

---

## Rodar os testes

```bash
./mvnw verify
```

- Compila o projeto e roda todos os testes.
- **Não precisa** rodar `docker compose up` antes: os testes sobem um
  **PostgreSQL descartável** sozinhos (Testcontainers), rodam as migrations de
  verdade e apagam tudo no fim.
- Só precisa do **Docker Desktop aberto**.

---

## Gerar o `.jar` (build final)

```bash
./mvnw clean package
java -jar target/nexo-api-0.0.1-SNAPSHOT.jar
```

Gera um `.jar` executável com a aplicação e todas as dependências dentro.

---

## Autenticação

A API é *stateless*: não há sessão nem cookie. O front faz login uma vez, guarda
o token e o envia em toda requisição no cabeçalho
`Authorization: Bearer <token>`.

| Endpoint | Acesso | O que faz |
|---|---|---|
| `POST /api/auth/login` | público | Recebe `{ "email", "senha", "tenantSubdominio?" }` e devolve o token + os dados do usuário |
| `GET /api/auth/me` | autenticado | Devolve o usuário dono do token |

Todo endpoint que não esteja na lista de públicos (`POST /api/auth/login`,
`/api/health`, `/actuator/health`, `/actuator/info`) exige token válido. As
rotas sob **`/api/admin/**`** exigem, além do token, o perfil `ADMIN`
(role `ROLE_ADMIN`) — é o prefixo reservado para os endpoints administrativos,
e um colaborador recebe `403` nelas.

```bash
# 1. login (administrador inicial, apenas em dev/test)
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@nexo.com","senha":"admin123","tenantSubdominio":"nexo"}'

# 2. usar o token devolvido
curl http://localhost:8080/api/auth/me -H 'Authorization: Bearer <token>'
```

### Administrador inicial

| Ambiente | E-mail | Senha |
|---|---|---|
| `dev` e `test` | `admin@nexo.com` | `admin123` |
| demais | `admin@nexo.com` | sem senha utilizável |

O usuário é criado pela migration `V5__seed_initial_admin.sql`, mas com um hash
que não corresponde a nenhuma senha conhecida. Quem define a senha acima é
[`db/seed/R__senha_do_admin_inicial.sql`](src/main/resources/db/seed/R__senha_do_admin_inicial.sql)
— e essa pasta **só é carregada nos perfis `dev` e `test`**
(`spring.flyway.locations`), de propósito: senha pública não entra em ambiente
real. Em produção, a senha do administrador precisa ser definida fora do
versionamento.

Quando o mesmo e-mail existir em empresas diferentes, `tenantSubdominio` é
obrigatório; sem ele o login é recusado para evitar resolver uma identidade
ambígua. E-mails e subdomínios são comparados sem diferenciar maiúsculas e
minúsculas. O JWT usa o UUID do usuário como `subject`; a cada requisição a API
recarrega usuário, perfil e tenant do banco, sem confiar no e-mail/tenant
copiados para as claims.

Respostas de erro seguem o mesmo formato do resto da API: **401** quando falta
token ou as credenciais não conferem, **403** para usuário inativo ou sem
permissão. Um tenant desativado também invalida tokens já emitidos na próxima
requisição autenticada.

---

## Áreas (departamentos)

Catálogo de áreas do tenant, usado nos selects de cadastro de usuário.

| Endpoint | Acesso | O que faz |
|---|---|---|
| `GET /api/areas` | autenticado | Lista paginada das áreas ativas do tenant; `?nome=` filtra por trecho do nome e `?incluirInativas=true` traz também as inativas |
| `GET /api/areas/{id}` | autenticado | Detalha uma área |
| `GET /api/areas/{id}/trilhas` | autenticado | Trilhas ativas da área, com os mesmos filtros de `GET /api/trilhas` |
| `POST /api/areas` | `ADMIN`, `GESTOR` | Cria uma área (201 + `Location`) |
| `PUT /api/areas/{id}` | `ADMIN`, `GESTOR` | Edita nome e descrição |
| `PATCH /api/areas/{id}/inativar` | `ADMIN`, `GESTOR` | Inativa a área |
| `PATCH /api/areas/{id}/ativar` | `ADMIN`, `GESTOR` | Reativa a área |

A leitura é liberada a qualquer usuário autenticado porque o front precisa
montar os selects; a gestão exige `ROLE_ADMIN` ou `ROLE_GESTOR` e um
colaborador recebe **403**.

Duas regras de negócio valem a pena conhecer:

- **Nome único por tenant** — nome repetido responde **409**. A checagem é feita
  no service e a UNIQUE `uq_area_tenant_nome` garante a consistência em corrida.
- **Área com usuários vinculados não pode ser inativada** (**409**). Inativar
  deixaria `usuario.area_id` apontando para um departamento que o cadastro não
  oferece mais; primeiro mova os usuários para outra área.

Tudo é restrito ao tenant do usuário autenticado: uma área de outro tenant
responde **404**, nunca 403 — assim a API não revela que aquele registro existe
em outra empresa.

---

## Trilhas

Catálogo de trilhas de aprendizagem e o progresso de cada colaborador nelas.

| Endpoint | Acesso | O que faz |
|---|---|---|
| `GET /api/trilhas` | autenticado | Lista paginada das trilhas ativas do tenant, cada uma com o progresso do usuário |
| `POST /api/trilhas` | `ADMIN` | Cria uma trilha (201 + `Location`) |
| `GET /api/trilhas/{id}` | autenticado | Detalhe da trilha + progresso do usuário |
| `PUT /api/trilhas/{id}` | `ADMIN` | Edita a trilha |
| `PATCH /api/trilhas/{id}/inativar` | `ADMIN` | Tira do catálogo (o progresso de quem já fazia é preservado) |
| `PATCH /api/trilhas/{id}/ativar` | `ADMIN` | Devolve ao catálogo |
| `POST /api/trilhas/{id}/atribuicoes` | `ADMIN`, `GESTOR` | Atribui a colaboradores ou a uma área inteira |
| `GET /api/trilhas/{id}/progresso` | autenticado | Progresso do usuário (404 se ainda não iniciou) |
| `POST /api/trilhas/{id}/progresso` | autenticado | Inicia a trilha para o usuário (idempotente) |
| `PATCH /api/trilhas/{id}/progresso` | autenticado | Muda o status do próprio progresso |
| `POST /api/trilhas/{id}/progresso/concluir` | autenticado | Conclui o próprio progresso explicitamente |

Consulta e progresso são abertos a qualquer autenticado: quem impede ver trilha
de outra empresa é o filtro por tenant no service, e o dono do progresso é
sempre o usuário do token. Gerir o catálogo é do `ADMIN`; **atribuir** é
também do `GESTOR`, porque distribuir trilha para a equipe é função dele.

Filtros de `GET /api/trilhas`, todos opcionais e combináveis:

| Parâmetro | O que faz |
|---|---|
| `nome` | Trecho do nome, sem diferenciar maiúsculas |
| `areaId` | Trilhas de uma área |
| `status` | Status do **meu** progresso: `NAO_INICIADA` (inclui as que nunca iniciei), `EM_ANDAMENTO`, `CONCLUIDA`, `ATRASADA` |
| `minhas=true` | Só as trilhas que iniciei ou recebi; aceita `sort=prazo` (sem prazo no fim) |
| `recomendar=AREA` | Trilhas da minha área primeiro; a ordenação pedida vale entre elas |
| `recomendar=IA` | Trilhas que a IA indicou primeiro, na ordem dela. Usa só o que já foi calculado por `GET /api/trilhas/sugestoes`: **ordenar uma página nunca chama o modelo**. Sem nada guardado, a ordem não muda |

### Sugestão de trilhas pela IA

`GET /api/trilhas/sugestoes` (qualquer autenticado) devolve até
`IA_SUGESTAO_MAXIMO` trilhas, cada uma com um motivo curto em português:

```json
{
  "geradoPorIa": true,
  "aviso": null,
  "sugestoes": [
    {
      "id": "…", "nome": "Atendimento ao Cliente",
      "area": { "id": "…", "nome": "Suporte" },
      "objetivos": "Resolver conflitos",
      "motivo": "Você atende clientes todo dia e ainda não fez esta trilha."
    }
  ]
}
```

É diferente de `recomendar=AREA`, que só reordena a listagem sem usar IA. Aqui:

1. O catálogo enviado ao modelo são as trilhas **ativas do tenant que o usuário
   ainda não concluiu** (id, nome, área e objetivos). Nenhuma trilha de outra
   empresa entra no prompt.
2. O perfil enviado é **só** área, cargo, trilhas concluídas e em andamento e
   as competências do PDI ativo. Nome, e-mail e ids do usuário ficam de fora.
3. O texto das trilhas é **dado, nunca instrução**: marcações `<catalogo>`,
   `<perfil>` e `<trilha>` vindas do cadastro são neutralizadas.
4. **Só id que estava no catálogo enviado é aceito.** Id inventado pelo modelo é
   descartado em silêncio, então nunca aparece trilha inexistente, inativa, de
   outro tenant ou já concluída.
5. O resultado fica guardado por usuário por `IA_SUGESTAO_VALIDADE`: dois
   acessos seguidos chamam o modelo uma vez só. Iniciar ou concluir uma trilha
   publica um evento que o `SugestoesDeTrilhaListener` usa para invalidar o
   que estava guardado.
6. **Com a IA fora do ar** — ou com uma resposta que não rendeu nenhuma trilha
   válida — a resposta continua **200**, com `geradoPorIa: false`, o `aviso`
   preenchido e a recomendação por área no lugar.

As duas formas de recomendar são estratégias do mesmo tipo
(`EstrategiaRecomendacao`), registradas na `EstrategiaRecomendacaoFactory`:
cair da IA para a área é escolher outro critério, não um `if` espalhado pelo
service.

**O banco é lido antes, não durante.** O `ColetorDeDadosDaSugestao` monta o
catálogo e o perfil numa transação curta e devolve records desligados do JPA
(`TrilhaCandidata`, `PerfilDoUsuario`); a transação fecha e só então o modelo
é consultado. Uma chamada pode levar `IA_TIMEOUT` × `IA_MAX_TENTATIVAS`, e
segurar uma conexão do pool por esse tempo esgotaria o Hikari sob carga — a
mesma razão pela qual o `ConsultaAssistenteService` não é transacional. Por
isso a `RecomendacaoPorIa` não conhece repository nenhum: recebendo só
records, não há lazy loading possível fora da transação.

### Atribuição

O corpo leva **ou** `usuarioIds` **ou** `areaId` — nunca os dois (400 se vierem
juntos ou nenhum) — mais um `prazo` opcional. Sem prazo informado, vale o
`prazoDias` da trilha contado a partir de hoje.

Atribuir é idempotente por pessoa: quem já tem a trilha é contado em
`ignorados` e **mantém o progresso que já fez**. A resposta traz
`{ atribuidos, ignorados, usuarioIds }`.

**Status efetivo:** em todas as respostas e filtros, uma trilha com prazo
vencido e não concluída aparece como `ATRASADA`, mesmo antes de essa situação
ser gravada no banco. A regra fica num lugar só (`RegraDeAtraso`), e as
consultas aplicam a mesma regra no banco (`AtribuicaoSpecifications.statusEfetivo`).
Além disso, uma **rotina diária** (`RotinaDeAtraso`, 3h) persiste o status
`ATRASADA` — a leitura não depende dela, mas o status gravado fica consultável.
Trilha concluída nunca fica atrasada, e concluir uma atrasada continua
permitido (vira `CONCLUIDA`).

> **Nome único:** a regra "nome único por tenant" vale no service, não no
> banco — o DER não define UNIQUE em `trilha.nome` (diferente de `area` e
> `cargo`). Duas requisições simultâneas com o mesmo nome não são barradas pelo
> banco; se isso virar problema, é uma migration de uma linha **e** uma
> atualização do DER.

---

## Conteúdos, etapas e progresso por etapa

Material de estudo das trilhas. Os vídeos ficam hospedados no **Vimeo**: a API
guarda só o link e, ao salvar, consulta o **oEmbed público do Vimeo** (sem chave
de API) para validar o link e preencher duração e miniatura.

| Endpoint | Acesso | O que faz |
|---|---|---|
| `GET /api/categorias` | autenticado | Lista paginada das categorias (`?nome=`, `?incluirInativas=true`) |
| `POST /api/categorias` | `ADMIN`, `GESTOR` | Cria uma categoria (nome único por tenant) |
| `PUT /api/categorias/{id}` | `ADMIN`, `GESTOR` | Edita nome e descrição |
| `GET /api/conteudos` | autenticado | Lista paginada; filtros `titulo`, `categoriaId`, `areaId`, `tipo`, `status`. Colaborador só vê `PUBLICADO` |
| `GET /api/conteudos/{id}` | autenticado | Detalha um conteúdo (rascunho responde 404 ao colaborador) |
| `POST /api/conteudos` | `ADMIN`, `GESTOR` | Cria em rascunho. `VIDEO` exige `videoUrl`, `TEXTO` exige `corpo`, `LINK` exige `linkUrl` |
| `PUT /api/conteudos/{id}` | `ADMIN`, `GESTOR` | Edita (o Vimeo só é consultado de novo se o link mudar) |
| `PATCH /api/conteudos/{id}/publicar` | `ADMIN`, `GESTOR` | Publica — passa a aparecer para os colaboradores e é indexado para o assistente de IA |
| `PATCH /api/conteudos/{id}/despublicar` | `ADMIN`, `GESTOR` | Volta para rascunho e sai da busca do assistente. Conteúdo usado em etapa de trilha responde **409** |
| `GET /api/trilhas/{id}/etapas` | autenticado | Etapas em ordem, com `concluida` para o usuário autenticado |
| `POST /api/trilhas/{id}/etapas` | `ADMIN`, `GESTOR` | Adiciona etapa (conteúdo publicado) no fim da trilha |
| `PUT /api/trilhas/{id}/etapas/{etapaId}` | `ADMIN`, `GESTOR` | Troca conteúdo, título ou descrição |
| `DELETE /api/trilhas/{id}/etapas/{etapaId}` | `ADMIN`, `GESTOR` | Remove a etapa e o progresso registrado nela; as seguintes sobem uma posição |
| `PATCH /api/trilhas/{id}/etapas/ordem` | `ADMIN`, `GESTOR` | `{"etapaIds": [...]}` com todas as etapas na nova ordem |
| `POST /api/trilhas/{id}/etapas/{etapaId}/concluir` | autenticado | Conclui a etapa para o **próprio** usuário e devolve o progresso recalculado |

Link do Vimeo inválido, privado ou fora do domínio `vimeo.com` responde **400**
com o erro em `fieldErrors[].field = "videoUrl"`; Vimeo fora do ar responde
**502**.

**Percentual:** `etapas concluídas / total de etapas` (arredondado para baixo),
gravado em `trilha_atribuicao.percentual`. Quem recalcula é o
`RecalculoDeProgressoListener`, que escuta `EtapaConcluidaEvent` e
`EtapasDaTrilhaAlteradasEvent`:

- concluir a última etapa conclui a trilha;
- adicionar uma etapa reduz o percentual de quem está em andamento;
- remover uma etapa remove o progresso dela e recalcula;
- trilha já concluída permanece concluída (e sempre aparece com 100%).

`AreaResponse.totalConteudos` conta os conteúdos com `conteudo.area_id` da área.

### Documentos da tela de Conhecimento

O documento é um `conteudo` do tipo **`DOCUMENTO`**: reaproveita `categoria` e
`conteudo` (V10/V11) e não exige vídeo, corpo nem link, porque o material dele
são os arquivos anexados (tabela `arquivo`, V21).

- `Conteudo.novoDocumento(titulo, responsavel)` cria o documento já em
  `RASCUNHO`, no tenant do responsável.
- `arquivo` guarda nome original, tipo (`PDF`, `DOCX`, `TXT`, `MD`), tipo MIME,
  tamanho e a **chave gerada no armazenamento**. O binário fica fora do banco, e o
  nome enviado nunca vira caminho no disco. Criado só por `Arquivo.novo(...)`.
- Chaves estrangeiras compostas com o tenant (como na V14): o arquivo nunca
  aponta para conteúdo ou usuário de outra empresa. Apagar o conteúdo apaga os
  registros dos arquivos dele.
- A tabela `arquivo_arquivo` do DER está errada e **não** é criada.

Endpoints dos arquivos (os de conteúdo estão na tabela acima; o documento é
criado com `POST /api/conteudos` e `"tipo": "DOCUMENTO"`):

| Endpoint | Acesso | O que faz |
|---|---|---|
| `POST /api/conteudos/{id}/arquivos` | `ADMIN`, `GESTOR` | Multipart com o arquivo no campo `arquivo`. 201 + `Location` do download |
| `GET /api/conteudos/{id}/arquivos` | autenticado | Arquivos do conteúdo, na ordem de envio (mesma visibilidade do conteúdo) |
| `GET /api/arquivos/{id}/download` | autenticado | Devolve o arquivo como anexo, com o **nome original** |

- **Só PDF, DOCX, TXT e MD**, até `DOCUMENTOS_TAMANHO_MAXIMO`. O tipo é
  conferido pelo **conteúdo** do arquivo (assinatura do PDF, estrutura do DOCX,
  texto UTF-8 para TXT/MD), não só pela extensão: um texto renomeado para `.pdf`
  é recusado. Tipo não permitido, conteúdo que não bate ou arquivo acima do
  limite respondem **400** com o erro em `fieldErrors[].field = "arquivo"`.
- **Visibilidade:** colaborador só lista e baixa arquivos de conteúdo
  **publicado**; rascunho e outro tenant respondem **404**. Enviar arquivo é de
  `ADMIN` e `GESTOR` (colaborador recebe **403**).
- **Armazenamento:** o binário vai para `ARMAZENAMENTO_DIRETORIO` com uma chave
  gerada (`tenant/uuid`); o nome enviado só volta no download. Quem escolhe o
  armazenamento é a `ArmazenamentoFactory` (`ARMAZENAMENTO_TIPO`): guardar na
  nuvem é outra implementação de `ArmazenamentoDeArquivos`, sem mudar quem usa.
  Se a gravação no banco falhar, o binário já guardado é apagado.
- **Indexação:** depois de gravar, a API publica `ArquivoEnviadoEvent`, e a
  indexação do assistente reindexa o documento publicado. O texto dos arquivos
  entra pela fonte `ArquivosDoConteudo`, junto com título e descrição.

---

## Assistente de IA

### Escolha do modelo

Toda chamada de IA passa pela interface `ClienteIa` (`com.nexo.ia`). Nenhuma
outra parte do sistema conhece o provedor: trocar de provedor é escrever uma
nova implementação e acrescentá-la na `ClienteIaFactory`, escolhida por
`IA_PROVEDOR`.

| Critério | Google Gemini (escolhido) | OpenAI |
|---|---|---|
| Chat | `gemini-2.5-flash` | `gpt-4o-mini` |
| Embeddings | `gemini-embedding-001` (768 dimensões via `outputDimensionality`) | `text-embedding-3-small` (1536) |
| Custo | Camada gratuita com limite diário, suficiente para o projeto | Pago desde a primeira chamada |
| Português | Bom em chat e em busca semântica | Bom |
| Integração | Uma chave só para chat e embeddings, API REST simples | Uma chave só, API REST simples |

**Motivo:** a camada gratuita do Gemini cobre o uso de um projeto acadêmico sem
cartão de crédito, com uma única chave para chat e embeddings e boa qualidade
em português. O Claude, da Anthropic, foi descartado por não oferecer
embeddings: exigiria um segundo provedor, como a Voyage AI.

Sem `IA_API_KEY` a API sobe normalmente, e só as rotas de IA respondem **503**
com mensagem clara. Timeout, limite de uso (429) e erro 5xx do provedor ganham
até `IA_MAX_TENTATIVAS` tentativas, com espera crescente. A chave vai só no
header da chamada: nunca entra em log nem em resposta.

Nos testes (`IA_PROVEDOR=fake`, já definido no perfil `test`), o
`ClienteIaFake` gera embeddings e respostas determinísticos, sem rede. Ele
reconhece o prompt da sugestão de trilhas e responde o JSON daquele fluxo,
escolhendo as primeiras trilhas do catálogo recebido — então
`GET /api/trilhas/sugestoes` funciona de ponta a ponta com o provedor `fake`.

### Indexação dos documentos

Cada conteúdo publicado vira trechos pesquisáveis em `conteudo_chunk`, cada um
com o seu embedding:

1. O `ConteudoService` publica `ConteudoPublicadoEvent`, `ConteudoAtualizadoEvent`
   (edição de conteúdo publicado) ou `ConteudoDespublicadoEvent`. O upload de
   arquivos (issue #55) publica `ArquivoEnviadoEvent`.
2. O `IndexadorDeConteudoListener` escuta esses eventos **depois do commit** e
   **fora da requisição** (`@TransactionalEventListener` + `@Async`). O cadastro
   de conteúdos não conhece a indexação.
3. O `IndexacaoService` junta o texto das `FonteDeTextoDoConteudo`. Hoje a
   única fonte é `CamposDoConteudo`, com título, descrição e corpo.
4. O `DivisorDeTexto` corta o texto em trechos de até `IA_TAMANHO_TRECHO`
   caracteres, com `IA_SOBREPOSICAO_TRECHO` de sobreposição. O corte cai, de
   preferência, em fim de parágrafo ou de frase.
5. O provedor gera os embeddings, e os trechos antigos são trocados pelos novos
   numa transação curta.

Despublicar remove os trechos. A busca filtra por tenant e por
`status = PUBLICADO` na própria consulta, então um rascunho nunca aparece,
mesmo antes da remoção.

**Falhas:** arquivo ilegível ou provedor fora do ar não quebra a publicação. A
falha fica em `indexacao_conteudo`, com status `FALHOU`, número de tentativas
e último erro. A `RotinaDeReindexacao` tenta de novo a cada minuto, com espera
que dobra a cada falha, até `IA_INDEXACAO_MAX_TENTATIVAS`.

**Extração de arquivos:** a `ExtratorDeTextoFactory` entrega o extrator pelo
tipo MIME: PDF (PDFBox), DOCX (Apache POI), TXT e MD (UTF-8). Quando a issue #55
criar a tabela `arquivo`, falta só uma `FonteDeTextoDoConteudo` que leia os
arquivos do armazenamento e use a factory.

**Busca:** o `BuscaDeTrechosService` compara o embedding da pergunta com os
trechos usando a distância de cosseno do pgvector (`<=>`). Trechos mais
distantes que `IA_BUSCA_DISTANCIA_MAXIMA` são descartados.

**Por que pgvector:** a similaridade roda no próprio PostgreSQL, com índice
HNSW e já filtrada por tenant, sem carregar vetores na aplicação. O
`docker-compose.yml` usa a imagem `pgvector/pgvector:pg16`, e os testes usam
`jdbc:tc:pgvector:pg16`. **Em produção, o PostgreSQL precisa ter a extensão
`vector` disponível.**

### Perguntas ao assistente

| Endpoint | Acesso | O que faz |
|---|---|---|
| `POST /api/assistente/sessoes` | autenticado | Cria uma conversa. Corpo opcional `{"titulo": "...", "contexto": "CONHECIMENTO"}` — a tela manda o começo da primeira pergunta como título; sem ele, o título nasce da primeira troca |
| `GET /api/assistente/sessoes` | autenticado | Conversas do usuário, paginadas, da mais recente para a mais antiga. A ordem é fixa: `sort` na query é ignorado |
| `POST /api/assistente/sessoes/{id}/mensagens` | autenticado | `{"conteudo": "..."}` com até 2000 caracteres (`pergunta` segue aceito como apelido). Devolve a troca inteira: `sessao`, `pergunta` e `resposta` |
| `GET /api/assistente/sessoes/{id}/mensagens` | autenticado | Histórico da conversa, na ordem em que as mensagens foram trocadas. Cada item traz `id`, `papel`, `conteudo`, `fontes` e `criadoEm` |
| `PATCH /api/assistente/sessoes/{id}` | autenticado | `{"titulo": "..."}` com até 200 caracteres — renomeia a conversa |
| `DELETE /api/assistente/sessoes/{id}` | autenticado | **204**. Apaga a conversa e, junto, todas as mensagens dela |

Formato de uma conversa e de uma mensagem:

```json
{ "id": "…", "contexto": "CONHECIMENTO", "titulo": "Quantos dias de férias?",
  "criadoEm": "2026-10-25T12:00:00Z", "atualizadoEm": "2026-10-25T12:03:00Z" }

{ "id": "…", "papel": "ASSISTENTE", "conteudo": "…",
  "fontes": ["Política de férias"], "criadoEm": "2026-10-25T12:03:00Z" }
```

> **Os nomes do JSON não são os das colunas.** `criadoEm` e `atualizadoEm`
> saem de `iniciado_em` e `ultima_interacao`, e `fontes` é uma lista de
> títulos em vez de objetos. Foi uma decisão do grupo: a tela do histórico
> (front #55) já estava escrita contra esse contrato, e adaptar a API saiu
> mais barato do que refazer a tela. O banco continua igual ao DER; só a
> borda muda. `titulo` nunca volta nulo — enquanto a conversa não tem nome
> próprio, vale `"Nova conversa"`.

**Cada um só enxerga as próprias conversas — inclusive o admin.** Sessão de
outra pessoa ou de outro tenant responde **404** em todas as rotas acima, como
se não existisse.

A **V22** alinhou o chat ao DER em três pontos que a V20 tinha deixado de
fora: `chat_sessao.titulo` passou de 120 para **200** caracteres,
`iniciada_em` virou **`iniciado_em`** (e o JSON da resposta acompanha, como
`iniciadoEm`) e `chat_mensagem` ganhou **`tenant_id`**, com a mesma chave
composta por tenant que a V14 criou para as outras tabelas — antes ela era a
única tabela do schema sem a coluna, e o isolamento dependia só de passar por
`chat_sessao`. A mesma migration criou o `historico_interacao.sessao_id` que a
V18 havia registrado como "adiado até existir a tabela de sessões".

O fluxo de uma pergunta é este:

1. A sessão precisa ser do próprio usuário. A de outra pessoa, inclusive para o
   admin, responde **404**.
2. Cada usuário faz até `IA_PERGUNTAS_POR_MINUTO` perguntas por minuto. A
   contagem vem de `chat_mensagem`, então vale mesmo com várias instâncias da
   API. Acima do limite, a resposta é **429** com `Retry-After: 60`.
3. A API busca os trechos mais parecidos do tenant, só de documentos publicados.
4. **Sem nenhum trecho relevante**, a resposta é fixa ("Não encontrei essa
   informação nos documentos da empresa…"), sem fontes e **sem chamar o
   modelo**, então ele não tem como inventar.
5. Com trechos, o `MontadorDePromptFactory` escolhe o montador pelo contexto da
   sessão. O prompt leva os trechos dentro de `<documento>` e as últimas
   `IA_MENSAGENS_HISTORICO` mensagens da sessão. O prompt de sistema diz que o
   texto dos documentos é **dado, nunca instrução**, e marcações
   `<documento>` vindas do próprio texto são neutralizadas.
6. A pergunta e a resposta são gravadas **juntas** em `chat_mensagem`, com os
   trechos usados em `fontes` (JSON). Se o provedor cair, a resposta é **503** e
   nada fica gravado pela metade.
7. A gravação publica `MensagemRegistradaEvent`. O `SessaoDoChatListener`
   escuta e é quem atualiza `ultima_interacao` e dá título à conversa a partir
   do começo da primeira pergunta — o service que grava a mensagem não mexe na
   sessão.
8. A API publica também `PerguntaRespondidaEvent`, e o
   `HistoricoInteracaoListener` grava a consulta no `historico_interacao` como
   `ASSISTENTE_CONSULTADO`, com `sessao_id` preenchido. Os detalhes guardam só
   `mensagemId`, `encontrouFontes` e `quantidadeFontes` — **nunca** a pergunta
   nem a resposta.

   Esse listener é `@EventListener` simples, e não `@TransactionalEventListener`:
   o evento é publicado **fora de transação** (a chamada ao modelo não pode
   segurar conexão do banco), então um listener ligado ao commit nunca
   dispararia e a gravação sumiria em silêncio.

Perguntas e respostas **não vão para o log** da aplicação. Resposta em
streaming (SSE) não foi implementada, porque a issue a marca como opcional.

> Quem já tinha o banco local com a imagem `postgres:16-alpine` só precisa
> rodar `docker compose down && docker compose up -d`. O volume é reaproveitado,
> porque a versão do PostgreSQL continua a 16.

---

## Acompanhamento das trilhas

Para gestor e admin acompanharem o andamento dos colaboradores. Colaborador
recebe **403**.

| Endpoint | Acesso | O que faz |
|---|---|---|
| `GET /api/atribuicoes` | `ADMIN`, `GESTOR` | Lista paginada: colaborador, área, trilha, status efetivo, prazo e percentual |
| `GET /api/atribuicoes/colaboradores/{usuarioId}` | `ADMIN`, `GESTOR` | Todas as trilhas de um colaborador e os totais por status |
| `GET /api/atribuicoes/exportar` | `ADMIN`, `GESTOR` | Mesmos filtros da listagem, em CSV (`?formato=csv`, o padrão) |

Filtros: `trilhaId`, `areaId` (área do colaborador), `usuarioId` e `status`.
A ordenação padrão é pelo nome do colaborador (`sort=usuario.nomeCompleto`).

O CSV sai em UTF-8 com BOM e ponto e vírgula como separador, para o Excel em
português abrir com os acentos certos. Um formato novo entra como mais uma
implementação de `ExportadorAcompanhamento`, sem mudar o controller (a
`ExportadorFactory` encontra os exportadores sozinha).

---


## Histórico de interações e dashboards

A API registra eventos após a transação principal da trilha confirmar. A gravação
do histórico usa uma transação independente; se a persistência da auditoria
falhar, o erro é registrado no log e não derruba a requisição de trilha. Isso
protege a operação principal, mas significa que uma falha de banco pode deixar
uma lacuna no histórico — monitore os logs para detectar esses casos.

| Endpoint | Acesso | O que entrega |
|---|---|---|
| `GET /api/historico` | autenticado | Histórico paginado; `ADMIN` vê o tenant inteiro, outros perfis veem apenas seus registros |
| `GET /api/dashboard/colaborador` | `COLABORADOR` | Trilhas por status, prazos dos próximos sete dias, média do percentual real de progresso e dez atividades recentes |
| `GET /api/dashboard/gestao` | `ADMIN`, `GESTOR` | Trilhas ativas, atribuições por status efetivo, taxa de conclusão, áreas com mais atrasos e trilhas mais iniciadas |

Os três eventos auditados são `TRILHA_ACESSADA`, `TRILHA_INICIADA` e
`TRILHA_CONCLUIDA`. A coluna opcional `conteudo_id` está disponível para
interações futuras ligadas diretamente a um conteúdo (a tabela `conteudo` já
existe); `sessao_id` continua adiado até existir a tabela de sessões. O histórico
é isolado por tenant na consulta e também por chaves estrangeiras compostas no banco. O endpoint de gestão responde **403** a
colaboradores. A paginação do histórico limita páginas a 100 registros e
recusa offsets superiores a 100.000 para evitar consultas arbitrariamente
profundas.

A taxa de conclusão é calculada a partir das atribuições concluídas. O
percentual médio do colaborador usa `trilha_atribuicao.percentual`, recalculado
pelas etapas reais; uma trilha concluída é exibida como 100%. “Trilhas mais
iniciadas” conta eventos `TRILHA_INICIADA` registrados a partir da implantação
desta funcionalidade; não é possível reconstruir inícios anteriores sem dados
históricos equivalentes.

---

## PDI (Plano de Desenvolvimento Individual)

O PDI vive dentro das trilhas: cada competência do plano tem ações, e a ação do
tipo `TRILHA` é uma trilha atribuída ao colaborador. Tabelas criadas pela
`V9__create_competencia_e_pdi.sql` (`competencia`, `pdi`, `pdi_item`, `pdi_acao`).

| Endpoint | Acesso | O que faz |
|---|---|---|
| `GET /api/competencias` | `ADMIN`, `GESTOR` | Lista as competências ativas; `?nome=` filtra, `?incluirInativas=true` traz as inativas |
| `GET /api/competencias/{id}` | `ADMIN`, `GESTOR` | Detalha uma competência |
| `POST /api/competencias` | `ADMIN`, `GESTOR` | Cria (`nome`, `descricao`, `tipo`: `TECNICA`/`COMPORTAMENTAL`) |
| `PUT /api/competencias/{id}` | `ADMIN`, `GESTOR` | Edita |
| `PATCH /api/competencias/{id}/inativar` · `/ativar` | `ADMIN`, `GESTOR` | Muda a situação |
| `GET /api/pdis/colaboradores` | `ADMIN`, `GESTOR` | Usuários ativos do tenant (id, nome, e-mail) para escolher o colaborador ao criar o PDI; `?nome=` filtra |
| `POST /api/pdis` | `ADMIN`, `GESTOR` | Cria o PDI (`colaboradorId`, `ciclo`, `inicio`, `fim`); nasce `ATIVO` e quem cria vira o gestor |
| `GET /api/pdis` | autenticado | Lista os PDIs visíveis, com percentual |
| `GET /api/pdis/meu` | autenticado | PDI ativo do usuário, com competências, trilhas ligadas e o progresso em cada uma; **404** se não houver |
| `GET /api/pdis/{id}` | autenticado | Detalhe com itens e ações |
| `PATCH /api/pdis/{id}/status` | `ADMIN`, `GESTOR` | Muda o status (`ATIVO`, `CONCLUIDO`, `CANCELADO`) |
| `POST /api/pdis/{id}/itens` | `ADMIN`, `GESTOR` | Inclui competência (`competenciaId`, `nivelAtual` e `nivelAlvo` de 1 a 5, `observacao`) |
| `POST /api/pdis/{id}/itens/{itemId}/acoes` | `ADMIN`, `GESTOR` | Inclui ação (`tipo`: `TRILHA`/`MENTORIA`/`OUTRO`, `trilhaId` se `TRILHA`, `prazo`, `descricao`) |
| `PATCH /api/pdis/{id}/itens/{itemId}/acoes/{acaoId}/status` | `ADMIN`, `GESTOR` | Status manual de ação que **não** é de trilha |

Regras que valem a pena conhecer:

- **Quem vê:** `ADMIN` vê todos os PDIs do tenant; `GESTOR`, os PDIs em que é o
  gestor; todo usuário vê o próprio. Gerenciar é do `ADMIN` ou do gestor do PDI.
  Fora disso — inclusive outro tenant — a resposta é **404**.
- **Um PDI por colaborador por ciclo** e **competência única por PDI** (**409**).
  Nível fora de 1 a 5 é **400** com o erro no campo.
- **Ação `TRILHA`** atribui a trilha ao colaborador com o prazo da ação
  (`NAO_INICIADA`), sem duplicar se ele já tinha atribuição. Quando o colaborador
  conclui a trilha, a ação é concluída sozinha (`TrilhaConcluidaEvent`).
- **Percentual do PDI** = ações concluídas / total de ações (0% sem ações).

---

## O que vai em cada pasta

Código em [`src/main/java/com/nexo/`](src/main/java/com/nexo/). A organização
segue o padrão MVC em camadas:

| Pasta | O que colocar aqui | Exemplo |
|---|---|---|
| `config` | Configurações da aplicação: `@Configuration`, beans, CORS, leitura de *properties*. | `CorsConfig.java` |
| `controller` | Endpoints REST. Recebe a requisição HTTP, chama o `service` e devolve a resposta. **Sem regra de negócio.** | `UserController.java` (`POST /api/users`) |
| `dto` | Formato dos dados que **entram e saem** da API (corpo de request/response). Nunca expor a `entity` direto. | `CreateUserRequest.java`, `UserResponse.java` |
| `service` | **Regra de negócio.** Valida, decide, coordena os repositórios. É onde fica a lógica. | `UsuarioService.java`, `AuthService.java` |
| `repository` | Interfaces de acesso ao banco (Spring Data JPA). Só a assinatura; o Spring implementa. | `UsuarioRepository`, `UsuarioSpecifications` |
| `entity` | Classes `@Entity` que representam as tabelas do banco. | `Usuario.java` (tabela `usuario`) |
| `security` | Configuração do Spring Security e filtros de autenticação (ex.: JWT). | `SecurityConfig.java`, `JwtAuthenticationFilter.java` |
| `exception` | Exceções próprias + o tratador global que transforma erro em JSON padronizado. | `ResourceNotFoundException.java`, `GlobalExceptionHandler.java` |
| `event` | Eventos de domínio publicados via `ApplicationEventPublisher`. Desacoplam "aconteceu X" de "reaja a X". | `TrilhaAtribuidaEvent.java` |

> Todas as pastas já têm conteúdo: o mapeamento de `tenant`, `area`, `cargo` e
> `usuario`, o CRUD de usuários (`/api/usuarios`) e a autenticação JWT
> (`/api/auth`).

### Como as camadas conversam

```
Requisição HTTP
      │
      ▼
  controller   ── valida formato, converte DTO
      │
      ▼
   service     ── regra de negócio
      │
      ▼
  repository   ── consulta / grava
      │
      ▼
 banco (Postgres)
```

A resposta volta pelo caminho inverso: `entity` → o `service` monta um `dto` →
o `controller` devolve como JSON.

### Outras pastas do projeto

| Caminho | Para quê |
|---|---|
| `src/main/resources/application.yml` | Configuração da app + perfis `dev` e `test` |
| `src/main/resources/db/migration/` | Migrations do Flyway (`V1__init.sql`, ...) |
| `src/test/java/com/nexo/` | Testes automatizados |
| `.github/workflows/` | Pipeline de CI (build + testes) |
| `.github/pull_request_template.md` | Checklist que aparece ao abrir um PR |

---

## Variáveis de ambiente

Nenhum segredo fica no código. A aplicação lê tudo de **variáveis de ambiente**.
Os valores abaixo são apenas *defaults* de desenvolvimento — em outros ambientes,
define-se a variável e ela prevalece.

| Variável | Default (dev) | Descrição |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | sem default | Selecione explicitamente `dev` para uso local; `test` é ativado pelos testes |
| `JWT_SECRET` | Chave local apenas no perfil `dev`; obrigatório fora de `dev`/`test` | Chave de assinatura JWT com pelo menos 32 caracteres; não reutilize a chave de desenvolvimento em produção |
| `JWT_ISSUER` | `nexo-api` | Emissor exigido na validação dos tokens |
| `JWT_EXPIRATION` | `PT2H` | Validade do token em formato ISO-8601 |
| `SERVER_PORT` | `8080` | Porta HTTP da API |
| `DB_HOST` | `localhost` | Host do PostgreSQL |
| `DB_PORT` | `5432` | Porta do PostgreSQL |
| `DB_NAME` | `nexo` | Nome do banco |
| `DB_USERNAME` | `nexo` | Usuário do banco |
| `DB_PASSWORD` | `nexo` | Senha do banco |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000` | Origens do frontend liberadas no CORS (separadas por vírgula) |
| `VIMEO_OEMBED_URL` | `https://vimeo.com/api/oembed.json` | Endpoint de oEmbed usado para validar os vídeos (público, sem chave) |
| `VIMEO_TIMEOUT` | `PT5S` | Tempo máximo de espera pela resposta do Vimeo |
| `IA_PROVEDOR` | `gemini` | Implementação do `ClienteIa`: `gemini` ou `fake` (testes) |
| `IA_API_KEY` | *(vazio)* | Chave da API do Gemini ([Google AI Studio](https://aistudio.google.com/apikey)). Sem ela, as rotas de IA respondem 503 |
| `IA_MODELO` | `gemini-2.5-flash` | Modelo de chat |
| `IA_MODELO_EMBEDDING` | `gemini-embedding-001` | Modelo de embeddings (sempre com 768 dimensões) |
| `IA_TIMEOUT` | `PT20S` | Tempo máximo de cada chamada ao provedor |
| `IA_MAX_TENTATIVAS` | `3` | Tentativas por chamada em timeout, 429 ou 5xx |
| `IA_TAMANHO_TRECHO` | `1000` | Máximo de caracteres por trecho indexado |
| `IA_SOBREPOSICAO_TRECHO` | `200` | Caracteres repetidos entre trechos vizinhos |
| `IA_INDEXACAO_MAX_TENTATIVAS` | `5` | Tentativas de indexar um documento antes de desistir |
| `IA_INDEXACAO_INTERVALO` | `PT5M` | Espera base entre uma falha de indexação e a próxima tentativa |
| `IA_BUSCA_TRECHOS` | `5` | Quantos trechos entram no prompt |
| `IA_BUSCA_DISTANCIA_MAXIMA` | `0.6` | Distância de cosseno máxima para um trecho contar como relevante |
| `IA_PERGUNTAS_POR_MINUTO` | `10` | Perguntas ao assistente aceitas por usuário a cada minuto |
| `IA_MENSAGENS_HISTORICO` | `6` | Mensagens anteriores da sessão que entram no prompt |

Para o Docker Compose, copie o modelo (opcional — há defaults):

```bash
cp .env.example .env
```

> `.env` está no `.gitignore` e **nunca** é versionado. Só o `.env.example`
> (sem valores reais) fica no repositório.

**CORS:** ajuste `CORS_ALLOWED_ORIGINS` para o endereço em que o front roda.
Use origens explícitas (não `*`), porque o envio de credenciais está habilitado.

---

## Quando o CI roda

Pipeline: [`.github/workflows/backend-ci.yml`](.github/workflows/backend-ci.yml).

**Dispara quando:**

- é aberto ou atualizado um **Pull Request** para `dev` ou `main`;
- há **push** para `dev` ou `main` (ou seja, quando um PR é mergeado).

O dia a dia acontece em `dev`: você abre PR da sua branch (`feat/...`) para
`dev`, e o CI roda nesse PR. `main` recebe `dev` de tempos em tempos (versão
estável), também via PR.

**O que ele faz:**

1. Baixa o código.
2. Instala o **JDK 17** (com cache do Maven).
3. Roda `./mvnw verify` — compila e executa **todos os testes** (o Docker do
   runner cobre o Testcontainers).

**Se falhar:** o check fica vermelho. Com a
[proteção das branches](#proteção-das-branches) ativa, isso **bloqueia o merge**
do PR. O workflow **não usa segredos**.

---

## Migrations (Flyway)

- Local: `src/main/resources/db/migration/`
- Nome: `V<número>__descricao_curta.sql` — ex.: `V2__create_users_table.sql`
- Rodam **sozinhas** no start da API e no início dos testes.
- **Nunca edite** uma migration já aplicada — crie sempre a próxima versão.
- `src/main/resources/db/seed/` guarda dados de conveniência (migrations
  repetíveis `R__`) e **só roda nos perfis `dev` e `test`**.
- O schema é propriedade do Flyway: o JPA roda com `ddl-auto: validate`, então
  toda `@Entity` precisa bater exatamente com a tabela migrada, senão a
  aplicação nem sobe.

### O que já existe no banco

| Versão | Arquivo | O que cria |
|---|---|---|
| V1 | `V1__init.sql` | Extensão `pgcrypto` (habilita `gen_random_uuid()`) |
| V2 | `V2__create_tenant_and_catalogs.sql` | Tabelas `tenant`, `area` e `cargo` |
| V3 | `V3__create_user_profiles.sql` | Enum `papel_usuario` (`ADMIN`, `GESTOR`, `COLABORADOR`) |
| V4 | `V4__create_users.sql` | Tabela `usuario` + índices de login e de filtro |
| V5 | `V5__seed_initial_admin.sql` | Semeia o tenant `nexo` e o admin `admin@nexo.com` (idempotente) |
| V6 | `V6__create_trilha.sql` | Tabela `trilha` |
| V7 | `V7__create_trilha_atribuicao.sql` | Enum `status_atribuicao` e tabela `trilha_atribuicao` |
| V8 | `V8__alter_area_add_descricao_auditoria.sql` | Descrição e auditoria em `area` |
| V9 | `V9__create_competencia_e_pdi.sql` | Tabelas `competencia`, `pdi`, `pdi_item` e `pdi_acao` |
| V10 | `V10__create_categoria.sql` | Tabela `categoria` |
| V11 | `V11__create_conteudo.sql` | Tabela `conteudo` |
| V12 | `V12__create_trilha_etapa_e_progresso.sql` | Tabelas `trilha_etapa` e `etapa_progresso` |
| V13 | `V13__alter_trilha_atribuicao_add_percentual.sql` | Percentual real de progresso por etapas na atribuição |
| V14 | `V14__enforce_tenant_relationships.sql` | Chaves estrangeiras compostas para impedir relações entre tenants diferentes |
| V15 | `V15__enforce_case_insensitive_login.sql` | Índices para login sem diferenciar maiúsculas e restrição de subdomínios repetidos |
| V16 | `V16__create_historico_interacao.sql` | Histórico de interações de trilhas, com detalhes JSONB e integridade por tenant |
| V17 | `V17__track_trilha_start.sql` | Instante de início da atribuição, para auditar a primeira transição sem duplicar eventos |
| V18 | `V18__add_conteudo_to_historico.sql` | Referência opcional de conteúdo no histórico, com integridade por tenant |
| V19 | `V19__create_conteudo_chunk_e_indexacao.sql` | Extensão `vector`, tabela `conteudo_chunk` (embedding `vector(768)` + índice HNSW), enum `status_indexacao` e tabela `indexacao_conteudo` |
| V20 | `V20__create_chat_sessao_e_mensagem.sql` | Enums `contexto_chat`/`papel_mensagem` e tabelas `chat_sessao` e `chat_mensagem` (com `fontes` em JSONB; apagar a sessão apaga as mensagens) |
| V21 | `V21__create_arquivo_e_tipo_documento.sql` | Tipo `DOCUMENTO` em `tipo_conteudo`, enum `tipo_arquivo`, tabela `arquivo` e índices tenant + categoria e tenant + área em `conteudo` |

**Atenção ao atualizar um banco que já contém dados:** as migrations V14 e V15
validam as relações entre tenants e as duplicidades de e-mail/subdomínio sem
diferenciar maiúsculas. Se encontrarem dados inconsistentes, o Flyway interrompe
a migração de propósito; corrija esses registros antes de subir a nova versão.
E-mails iguais continuam permitidos em tenants diferentes, mas não dentro do
mesmo tenant (considerando maiúsculas/minúsculas).

Regras do modelo que o código precisa respeitar:

- **Multi-tenant:** `area`, `cargo` e `usuario` têm `tenant_id` obrigatório. O
  e-mail é único **por tenant** (`uq_usuario_tenant_email`), não global — toda
  consulta de domínio filtra por tenant.
- **Senha:** `usuario.senha_hash` tem um CHECK que só aceita hash BCrypt (com o
  prefixo `{bcrypt}` opcional). Senha em texto puro é recusada pelo banco.
- **Hierarquia:** `usuario.gestor_id` é auto-referência (o gestor é outro usuário).
- **Nomes:** tabelas e colunas em **português e no singular** (`usuario`,
  `nome_completo`, `criado_em`).
- `MigrationSchemaTest` valida o enum, o seed, a unicidade por tenant, o CHECK de
  BCrypt e os índices contra um PostgreSQL real. Migration nova que mude esses
  contratos quebra o teste.

> O hash gravado pela `V5` não corresponde a nenhuma senha conhecida. Quem torna
> o admin utilizável em `dev` e `test` é o seed repetível — ver
> [Administrador inicial](#administrador-inicial).

---

## Fluxo de contribuição

O fluxo é **o mesmo para back-end e front-end**.

1. Atualize sua cópia local de `dev`.
2. Crie uma branch a partir de `dev` (ver [padrão de branches](#padrão-de-branches)).
3. Faça commits pequenos em [Conventional Commits](#padrão-de-commits).
4. Abra um **Pull Request** para `dev` com o template preenchido.
5. Garanta **CI verde** e resolva os comentários da revisão.
6. Com **pelo menos 1 aprovação**, faça **Squash and merge** e apague a branch.

Ninguém dá push direto em `dev` ou `main` — as duas são
[protegidas](#proteção-das-branches). `main` só recebe código vindo de `dev`,
também via PR (é a versão estável).

### Padrão de branches

Formato: `<tipo>/<descrição-curta-em-kebab-case>`

| Prefixo | Quando usar |
|---|---|
| `feat/` | nova funcionalidade |
| `fix/` | correção de bug |
| `refactor/` | refatoração sem mudar comportamento |
| `docs/` | documentação |
| `test/` | apenas testes |
| `chore/` | build, CI, dependências, manutenção |
| `hotfix/` | correção urgente |

Exemplos: `feat/autenticacao-jwt` · `fix/health-check-timestamp` ·
`chore/configura-flyway` · `feat/42-cadastro-de-usuario` (com o nº da issue).

### Padrão de commits

[Conventional Commits](https://www.conventionalcommits.org/):

```
<tipo>(escopo opcional): descrição no imperativo

[corpo opcional explicando o porquê]

[rodapé opcional: BREAKING CHANGE: ... / Refs #123]
```

**Tipos:** `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`,
`ci`, `chore`, `revert`.

**Regras do assunto:** verbo no imperativo ("adiciona", não "adicionado");
começa em minúscula; sem ponto final; até ~72 caracteres.

**Exemplos:**

```
feat(auth): adiciona geração de token JWT no login
fix(cors): libera o header Authorization para o front
docs: documenta as variáveis de ambiente no README
ci: roda mvnw verify em pull requests para dev
```

### Pull Request

- Abra o PR **para `dev`** (a promoção `dev` → `main` também é via PR).
- O **template é carregado automaticamente** — preencha todas as seções e o
  checklist de DoD abaixo.
- 1 PR = 1 assunto. PRs pequenos são revisados mais rápido.
- Exigido para o merge (imposto pela proteção da branch):
  - check **Backend CI** verde;
  - **pelo menos 1 aprovação** de outra pessoa;
  - nenhuma conversa de revisão em aberto;
  - branch atualizada com `dev`.
- Use **Squash and merge** e **apague a branch** depois.

### Definition of Done (DoD)

A tarefa só está concluída quando:

- [ ] O código está na pasta certa (`config`, `controller`, `service`,
      `repository`, `entity`, `dto`, `security`, `exception`).
- [ ] `./mvnw verify` passa localmente (build + testes).
- [ ] Novos comportamentos têm testes automatizados.
- [ ] Nenhum segredo, senha, token ou `.env` foi versionado.
- [ ] Config de ambiente está fora do código Java (`application.yml` / env vars).
- [ ] Migrations novas são incrementais; nenhuma migration aplicada foi editada.
- [ ] README atualizado se o setup mudou.
- [ ] Branch e commits seguem os padrões acima.
- [ ] CI verde e PR aprovado.

---

## Proteção das branches

`dev` e `main` precisam exigir **Pull Request revisado e CI verde** antes de
qualquer merge; push direto fica bloqueado. A configuração é feita por alguém
com permissão de **admin** no repositório, na interface do GitHub.
**Aplique a mesma coisa no repositório do front-end.**

**Pré-requisitos:** a branch já existe no GitHub com pelo menos 1 commit, e o
workflow **Backend CI** já rodou uma vez (abra um PR qualquer) para o status
check aparecer pelo nome na lista.

### Como verificar que ficou ativo

- `git push` direto em `dev` ou `main` é rejeitado.
- PR sem aprovação não habilita o botão de merge.
- PR com **Backend CI** vermelho não pode ser mergeado.

---

## Problemas comuns

| Sintoma | Causa provável / solução |
|---|---|
| `Connection refused` na porta 5432 | O banco não subiu → `docker compose up -d` |
| Testes falham com erro de Docker | Docker Desktop não está aberto/rodando |
| `Could not find a valid Docker environment` com `Status 400` | Docker Engine 29+ recusa a versão de API usada pelo Testcontainers 1.20. Rode com `./mvnw verify -DargLine="-Dapi.version=1.44"` |
| `UnsupportedClassVersionError` | JDK errado → use o **21** (`java -version`) |
| Porta 8080 já em uso | Defina outra em `SERVER_PORT` |
| `./mvnw: Permission denied` (Linux/macOS) | `chmod +x ./mvnw` |
| Front recebe erro de CORS no browser | Inclua a origem do front em `CORS_ALLOWED_ORIGINS` |

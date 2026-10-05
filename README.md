# Nexo — Back-end (API)

API do projeto Nexo. Este repositório contém a **estrutura base** do back-end:
já roda localmente e está pronta para receber os módulos de **autenticação** e
**usuários**.


## Tecnologias

| Camada | O que usamos | Versão |
|---|---|---|
| Linguagem | Java | 21 |
| Framework | Spring Boot | 3.4 |
| Web / REST | Spring Web (MVC) | — |
| Banco de dados | PostgreSQL | 16 |
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

Abra um terminal na pasta do projeto e rode **os dois comandos, nesta ordem**:

```bash
docker compose up -d       # 1. liga o banco de dados
./mvnw spring-boot:run     # 2. liga a API (fica rodando neste terminal)
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
| `POST /api/auth/login` | público | Recebe `{ "email", "senha" }` e devolve o token + os dados do usuário |
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
  -d '{"email":"admin@nexo.com","senha":"admin123"}'

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

Respostas de erro seguem o mesmo formato do resto da API: **401** quando falta
token ou as credenciais não conferem, **403** para usuário inativo ou sem
permissão.

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

| Endpoint | Acesso | O que faz |
|---|---|---|
| `GET /api/trilhas` | autenticado | Lista paginada das trilhas ativas do tenant, cada uma com o progresso do usuário |
| `POST /api/trilhas` | `ADMIN` | Cria uma trilha (201 + `Location`) |
| `GET /api/trilhas/{id}` | autenticado | Detalhe da trilha + progresso do usuário |
| `GET /api/trilhas/{id}/progresso` | autenticado | Progresso do usuário (404 se ainda não iniciou) |
| `POST /api/trilhas/{id}/progresso` | autenticado | Inicia a trilha para o usuário (idempotente) |
| `PATCH /api/trilhas/{id}/progresso` | autenticado | Muda o status do próprio progresso |

Filtros de `GET /api/trilhas`, todos opcionais e combináveis:

| Parâmetro | O que faz |
|---|---|
| `nome` | Trecho do nome, sem diferenciar maiúsculas |
| `areaId` | Trilhas de uma área |
| `status` | Status do **meu** progresso: `NAO_INICIADA` (inclui as que nunca iniciei), `EM_ANDAMENTO`, `CONCLUIDA`, `ATRASADA` |
| `minhas=true` | Só as trilhas que iniciei ou recebi; aceita `sort=prazo` (sem prazo no fim) |
| `recomendar=AREA` | Trilhas da minha área primeiro; a ordenação pedida vale entre elas |

**Status efetivo:** em todas as respostas e filtros, uma trilha com prazo
vencido e não concluída aparece como `ATRASADA`, mesmo antes de essa situação
ser gravada no banco. A regra fica num lugar só (`RegraDeAtraso`), e as
consultas aplicam a mesma regra no banco (`AtribuicaoSpecifications.statusEfetivo`).

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
| `SPRING_PROFILES_ACTIVE` | `dev` | Perfil ativo (`dev` ou `test`) |
| `SERVER_PORT` | `8080` | Porta HTTP da API |
| `DB_HOST` | `localhost` | Host do PostgreSQL |
| `DB_PORT` | `5432` | Porta do PostgreSQL |
| `DB_NAME` | `nexo` | Nome do banco |
| `DB_USERNAME` | `nexo` | Usuário do banco |
| `DB_PASSWORD` | `nexo` | Senha do banco |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000` | Origens do frontend liberadas no CORS (separadas por vírgula) |

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
| `UnsupportedClassVersionError` | JDK errado → use o **21** (`java -version`) |
| Porta 8080 já em uso | Defina outra em `SERVER_PORT` |
| `./mvnw: Permission denied` (Linux/macOS) | `chmod +x ./mvnw` |
| Front recebe erro de CORS no browser | Inclua a origem do front em `CORS_ALLOWED_ORIGINS` |

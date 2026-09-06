## Descrição

<!-- O que este PR faz e por quê. Dê ao revisor o contexto necessário. -->

Closes #

## Tipo de mudança

- [ ] feat — nova funcionalidade
- [ ] fix — correção de bug
- [ ] refactor — mudança interna sem alterar comportamento
- [ ] docs — apenas documentação
- [ ] test — apenas testes
- [ ] chore — build, CI, dependências

## Como testar

<!-- Passos para o revisor validar localmente. -->

1.
2.

## Checklist de Definition of Done (DoD)

- [ ] O código segue a organização de pacotes do projeto (`config`, `controller`, `service`, `repository`, `entity`, `dto`, `security`, `exception`)
- [ ] `./mvnw verify` passa localmente (build + testes)
- [ ] Novos comportamentos têm testes automatizados
- [ ] Nenhum segredo, senha, token ou arquivo `.env` foi versionado
- [ ] Configurações específicas de ambiente ficam fora do código Java (`application.yml` / variáveis de ambiente)
- [ ] Migrations Flyway novas são incrementais; nenhuma migration já aplicada foi editada
- [ ] `README.md` atualizado caso o setup tenha mudado
- [ ] Branch nomeada no padrão e commits em Conventional Commits
- [ ] O check **Backend CI** está verde
- [ ] PR com escopo único e pequeno o suficiente para revisão

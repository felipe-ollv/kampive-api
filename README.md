# Kampive API

Base Java 21, Spring Boot 4.1.1, Maven Wrapper 3.9.16 e PostgreSQL 18.
Monólito modular hexagonal. Sem endpoints de negócio, autenticação ou entidades JPA nesta etapa.

## Executar

Clone `kampive-api` e `kampive-infra` lado a lado. Siga o README da infra para preparar
o `.env`, iniciar PostgreSQL e provisionar manualmente a role restrita. Na infra:

```sh
docker compose up -d --build
curl --fail http://localhost:8080/actuator/health
docker compose logs -f api
```

Somente `GET /actuator/health` é público, sem detalhes internos. Não é necessário
instalar Java ou Maven no host. O build usa Java 21 no Docker; o runtime usa JRE 21.

## Testes

Na raiz da API:

```sh
docker build --target build --progress=plain -t kampive-api-tests:local .
```

O build executa `./mvnw -B -ntp verify` (arquitetura, segurança HTTP e erros).
Adicione `--no-cache` para forçar nova execução sem mudanças no código. Os testes
não precisam de PostgreSQL e não alteram bancos. Com JDK 21 local, `./mvnw verify`
também funciona. Não há hot reload: reconstrua a imagem após alterações.

Para execução fora do Docker, defina `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` e use
`./mvnw spring-boot:run`. Spring não carrega `.env` automaticamente; o Compose faz
a leitura na infra.

## Organização

- `configuration`: composição e configurações Spring transversais.
- `shared`: contratos e infraestrutura compartilhados, sem regras específicas.
- `module`: identity, user, geography, campsite, review, favorite, notification.
- Cada módulo possui domain, application, adapter e configuration documentados
  por package-info.java. Classes serão adicionadas conforme os casos de uso.

Veja [arquitetura](docs/architecture.md) e [segurança](docs/security.md).

## Banco

Sem Flyway, Liquibase, schema.sql, data.sql ou criação automática de tabelas.
Hibernate usa validate, SQL init está desabilitado e Open Session in View desligado.
Como ainda não há entidades, validate não comprova a existência do modelo de negócio.
O DDL será aplicado manualmente por um administrador. A API utiliza `kampive_app`,
sem DDL. Não há CRUD, JWT, uploads, push ou dados de exemplo nesta entrega.

# Indicações, cadastro e propriedade

O camping é um registro independente de usuário. A indicação é uma contribuição privada, não um camping nem um vínculo de propriedade. A equipe cadastra, revisa e publica o camping. O proprietário pode solicitar um vínculo posteriormente, sujeito a aprovação humana.

## Preparação do banco

Aplicar **uma vez**, como administrador do PostgreSQL, [sql/campsite-community.sql](sql/campsite-community.sql), depois do schema base de `kampive-infra/sql/schema.sql` e antes de iniciar esta versão da API. O script é transacional, não apaga registros e não é executado automaticamente. Novas instalações também precisam dos dois arquivos. A role `kampive_app` não pode executar as funções administrativas nem alterar o status dos pedidos.

Os valores legados de `uuid_owner` são preservados para auditoria, mas **deixam de conceder gestão sem uma reivindicação aprovada**. É necessário revisar os vínculos antigos. Não criar aprovações em massa: o fluxo anterior atribuía a propriedade automaticamente. Se o proprietário legado estiver errado, um administrador deve corrigir o campo após apuração antes de aprovar outra pessoa.

## Contratos

- `POST /api/v1/campsite-suggestions`: sessão ativa + CSRF; `{ "name": "Camping Exemplo", "instagramUrl": "https://instagram.com/camping/", "googleMapsUrl": "https://maps.app.goo.gl/exemplo" }`. Retorna `201 { uuid, status: "pending_review" }`. Não cria um camping nem libera gestão. Os três campos são obrigatórios, com nome mínimo de 3 caracteres e URLs HTTPS de perfil do Instagram e Google Maps; não exige categoria, coordenadas ou fotos.
- `POST /api/v1/campsites/{uuid}/claims`: sessão ativa + CSRF; `{ "contact": "telefone ou e-mail", "evidence": "como verificar o vínculo" }`. Somente campings publicados sem vínculo aprovado. Retorna o protocolo pendente; repetição da mesma solicitação pendente é idempotente e preserva as informações originais. Não concede permissão. Camping indisponível retorna 409.
- `POST /api/v1/campsites` foi removido. A criação oficial é administrativa.
- `GET /api/v1/users/me/campsites` e as operações de gestão consideram apenas `uuid_owner` correspondente a uma reivindicação aprovada. Indicar ou reivindicar não permite editar, enviar para revisão ou arquivar.
- `verified_at` / `uuid_verified_by` continuam representando a revisão dos **dados**, independentemente da aprovação da propriedade. As informações de contato/evidência das solicitações nunca aparecem no catálogo público.

## Trabalho da equipe

Não há painel administrativo nesta entrega. Usar uma conexão administrativa ao banco. Os UUIDs abaixo são parâmetros do `psql` (`-v reviewer=...`, por exemplo), não interpolação de texto SQL sem escape.

Consultar a fila privada:

```sql
SELECT * FROM kampive.campsite_suggestion WHERE status='pending_review' ORDER BY created_at;
SELECT * FROM kampive.campsite_claim WHERE status='pending_review' ORDER BY created_at;
```

Depois de verificar a indicação e descartar duplicidade, aceitar cria um **rascunho sem proprietário** e registra autor, revisor e camping resultante:

```sql
SELECT kampive.accept_campsite_suggestion(
  :'suggestion'::uuid, :'reviewer'::uuid, :'category'::uuid, :'city'::uuid, :'review_notes'
);
```

A equipe completa os dados oficiais e os contatos/fotos nas tabelas existentes, verifica as informações e publica. Não copiar cegamente os dados da indicação. A função não publica e não transfere contatos pessoais para o catálogo. Para cadastrar sem indicação, inserir na tabela `kampive.campsite` omitindo `uuid_owner`, mantendo as demais restrições do schema.

Exemplo de publicação após a revisão (o schema exige cidade, endereço e coordenadas):

```sql
UPDATE kampive.campsite SET status='published', verified_at=now(), uuid_verified_by=:'reviewer'::uuid
WHERE uuid_campsite=:'campsite'::uuid AND status IN ('draft','pending_review');
```

Rejeitar uma indicação, preservando o histórico:

```sql
UPDATE kampive.campsite_suggestion SET status='rejected', reviewed_at=now(),
  uuid_reviewed_by=:'reviewer'::uuid, review_notes=:'review_notes'
WHERE uuid_campsite_suggestion=:'suggestion'::uuid AND status='pending_review';
```

Se o camping já existir, vincular a indicação ao registro existente em vez de criar uma duplicata:

```sql
UPDATE kampive.campsite_suggestion SET status='accepted', uuid_campsite=:'campsite'::uuid,
  reviewed_at=now(), uuid_reviewed_by=:'reviewer'::uuid, review_notes=:'review_notes'
WHERE uuid_campsite_suggestion=:'suggestion'::uuid AND status='pending_review';
```

Após contato e comprovação da propriedade, um revisor ativo diferente do solicitante aprova:

```sql
SELECT kampive.approve_campsite_claim(:'claim'::uuid, :'reviewer'::uuid, :'review_notes');
```

A função bloqueia o camping, impede sobrescrever outro proprietário, vincula a conta e registra a aprovação atomicamente. Pedidos concorrentes pendentes são rejeitados; uma única aprovação é possível. A verificação dos dados do camping não é alterada por essa operação.

Rejeição de propriedade:

```sql
UPDATE kampive.campsite_claim SET status='rejected', reviewed_at=now(),
  uuid_reviewed_by=:'reviewer'::uuid, review_notes=:'review_notes'
WHERE uuid_campsite_claim=:'claim'::uuid AND status='pending_review';
```

Não expor essas funções por endpoints públicos. A identidade do revisor é um registro de auditoria informado pela equipe com acesso administrativo, não um mecanismo de autenticação do banco.

## Validação

`./mvnw verify` cobre contratos, sessão, CSRF e regras de aplicação. `scripts/verify-auth.py` aplica schema e alteração em PostgreSQL temporário e exercita indicação → cadastro sem proprietário → publicação → reivindicação sem acesso → aprovação administrativa → gestão, incluindo acesso entre contas, duplicidade pendente, proibição de autoaprovação e permissões da role da API. Requer Docker e a imagem atualizada `kampive-api:0.0.1-local`; não altera o banco de desenvolvimento.

Compatibilidade de persistência: novas indicações gravam `instagramUrl` na coluna existente `contact` e `googleMapsUrl` em `location`; `notes` fica vazio. Dados legados são preservados. Não é necessário aplicar nova alteração de banco.


## Cadastro completo pela comunidade

Aplicar também `sql/campsite-registration.sql` após `sql/campsite-community.sql` antes de subir a API. A coluna `uuid_submitted_by` registra quem enviou o cadastro, sem conceder propriedade.

`POST /api/v1/campsite-registrations` recebe `Content` completo (o mesmo contrato do editor), exige sessão ativa e CSRF e retorna `201 { uuid, status: "pending_review" }`. Exige cidade (cityName livre ou cityUuid), endereço, descrição, contato e foto. Coordenadas e como chegar são opcionais. Aplicar docs/sql/campsite-city-name.sql antes de atualizar a API. O nome livre fica disponível para a equipe vincular à cidade oficial antes da publicação.

O cadastro e a edição aceitam multipart/form-data: content (JSON), order (array JSON com referências file:N ou existing:N) e photos (até 9 JPEG/PNG, 10 MB cada). As imagens são limitadas a 1920 pixels no maior lado, convertidas em JPEG com qualidade 70% e salvas no volume /app/uploads. O banco guarda caminhos /api/v1/campsite-photos/{uuid}.jpg; a primeira foto é a capa.

 Persiste o camping e suas listas atomicamente, com `uuid_owner` nulo, sem publicação automática. Autor e proprietário são independentes; o autor não tem acesso às operações de gestão. O cadastro não aparece em “Meus campings”. A equipe consulta `SELECT * FROM kampive.campsite WHERE status='pending_review' AND uuid_submitted_by IS NOT NULL` e usa o procedimento de revisão/publicação acima. A reivindicação de propriedade permanece separada.

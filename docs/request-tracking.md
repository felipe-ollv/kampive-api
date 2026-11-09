# Acompanhamento de solicitações

Indicações e pedidos de propriedade são privados. O autor vem da sessão; não há parâmetro para consultar outra conta. O app mantém as referências visuais e componentes existentes e oferece o acesso em Perfil → Minhas solicitações, no recibo de envio e nas notificações de resultado.

## Banco e implantação

Aplicar uma vez `docs/sql/request-tracking.sql` como administrador, depois de `campsite-community.sql` e antes de atualizar a API. É uma alteração aditiva e transacional, sem seeds, exclusões ou migração automática. Acrescenta `public_response`, índices por autor/data e o destino das notificações. Não copia `review_notes` nem gera notificações retroativas. Solicitações antigas aparecem na lista, mas a resposta fica vazia até ser preenchida explicitamente.

Instalações com a versão original da função de aceitação precisam também de `docs/sql/fix-campsite-suggestion-review-notes.sql`, que resolve a ambiguidade do parâmetro notes sem mudar permissões.

## Contratos

- `GET /api/v1/users/me/requests?page=0&size=20`: `{items,page,size,totalElements,totalPages}`. Cada item tem `uuid,type,name,status,createdAt,reviewedAt,publishedCampsiteUuid`. `type` é `suggestion` ou `claim`. Ordem: criação decrescente, tipo e UUID; tamanho de 1 a 50, página até 10000.
- `GET /api/v1/users/me/requests/{type}/{uuid}`: `{summary,instagramUrl,googleMapsUrl,contact,evidence,response}`. Dados ausentes são nulos. UUID inexistente, de outra conta ou tipo desconhecido retorna 404. Conta desativada retorna 401.
- A central de notificações acrescenta `requestType` e `requestUuid` (nulos nos avisos sem solicitação). O app abre somente esses dois tipos de destino internos. Marcação de leitura continua explícita.

Os estados preservam o banco: indicação `pending_review/accepted/rejected`, propriedade `pending_review/approved/rejected`. Aceitar uma indicação cria um rascunho; não significa publicação. `publishedCampsiteUuid` só é retornado enquanto o camping estiver publicado, inclusive após arquivamento. Nenhum endpoint novo permite moderar ou adquirir propriedade.

O escopo desta lista são indicações resumidas e solicitações de propriedade. Cadastros completos enviados por `/campsite-registrations` e avaliações têm contratos distintos e não são incluídos nesta lista.

## Resposta pública e notificação

`review_notes` continua exclusivamente interno. `public_response` é o texto destinado ao autor (até 4000 caracteres). A equipe deve revisar o conteúdo antes de gravá-lo. Não colocar dados de outras pessoas nessa resposta.

O trigger administrativo insere uma notificação e seu destinatário na mesma transação da mudança para um estado final. Rollback não deixa avisos; atualização sem mudança de estado não duplica; há uma notificação de resultado por solicitação. Alterar apenas a resposta não cria outra notificação. Contas desativadas não recebem novos avisos. Não há push nem e-mail nesta entrega.

Exemplo de rejeição (parâmetros `psql`, não interpolar texto sem escape):

```sql
BEGIN;
UPDATE kampive.campsite_suggestion
SET status='rejected', uuid_reviewed_by=:'reviewer'::uuid, reviewed_at=now(),
    review_notes=:'internal_notes', public_response=:'response'
WHERE uuid_campsite_suggestion=:'request'::uuid AND status='pending_review';
-- Conferir UPDATE 1 antes do COMMIT.
COMMIT;
```

Para aceitação, usar `accept_campsite_suggestion` e gravar `public_response` na mesma transação. Para propriedade, usar `approve_campsite_claim` e gravar a resposta na mesma transação; outras reivindicações pendentes do camping são rejeitadas e notificadas pela função existente. Rejeitar propriedade usa o mesmo padrão acima em `campsite_claim`/`uuid_campsite_claim`. As funções continuam restritas ao administrador; `kampive_app` não pode alterar status, resposta ou notas das solicitações.

## Validação

`docker build --progress=plain -t kampive-api:requests-test .` executa os testes Java. `KAMPIVE_TEST_IMAGE=kampive-api:requests-test python3 scripts/verify-auth.py` usa PostgreSQL descartável e verifica estados, paginação, isolamento entre contas, links publicados, resposta pública, rollback e duplicação das notificações. Não grava exemplos no banco de desenvolvimento.

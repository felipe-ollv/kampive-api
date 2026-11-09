# Favoritos, notificações e avaliações

Todos os dados são persistidos nas tabelas existentes `favorite`, `notification`, `user_notification`, `review`, `review_photo`, `review_highlight_selection`, `review_highlight` e `camping_method`. Não há novo DDL, seeds automáticos nem importação dos mocks locais. Os scripts anteriores de perfil e campings continuam necessários para a API atual.

As operações privadas derivam o usuário da sessão, recusam contas desativadas e nunca recebem UUID do autor no corpo. Toda escrita exige CSRF. Favoritos e alterações de leitura são idempotentes. As transações mantêm as alterações atômicas e coordenam a exclusão de conta com as escritas.

| Método e rota (`/api/v1`) | Contrato |
| --- | --- |
| GET `/users/me/favorites` | UUIDs dos campings salvos na conta, mais recentes primeiro; inclui itens indisponíveis para permitir remoção. |
| PUT `/users/me/favorites/{campsiteUuid}` | Salva camping publicado; 204, repetição não duplica. |
| DELETE `/users/me/favorites/{campsiteUuid}` | Remove apenas da própria conta; 204 mesmo se já removido. |
| GET `/users/me/notifications?page=0&size=20&filter=all` | Página de `{uuid,title,message,date,read}`, totais e `unreadCount` global da conta. `filter`: `all`, `read`, `unread`. |
| PUT `/users/me/notifications/{uuid}` | `{read: true\|false}`; UUID do vínculo de destinatário, 404 se pertence a outra conta. |
| POST `/users/me/notifications/read-all` | Marca todas as recebidas pela própria conta como lidas; 204. |
| GET `/review-options` | Catálogos reais `highlights` e `campingMethods`, sem opções inventadas quando vazios. |
| GET `/campsites/{uuid}/reviews` | Somente avaliações publicadas de camping publicado; paginação. |
| GET `/users/me/reviews` | Avaliações do autor, incluindo `pending`, `published` e `hidden`; paginação. |
| POST `/campsites/{uuid}/reviews` | `{rating,text,campingMethodUuid,highlightUuids,photos}`; retorna 201 `{uuid,status:"pending"}`. Uma avaliação por pessoa/camping; duplicata retorna 409. |

Paginação começa em 0; `size` de 1 a 50. Comentários têm 50–1000 caracteres; notas inteiras de 1 a 5. Destaques e método são opcionais e validados contra o banco. Fotos: até três data URIs JPEG/PNG de até 2 MiB e 16 megapixels cada, reencodificadas em JPEG de até 512 px, sem metadados, antes de persistir. Caminhos locais e URLs arbitrárias não são aceitos no envio. As fotos retornam no conteúdo da avaliação; as páginas do app usam dez avaliações para limitar o payload.

Uma avaliação pendente não aparece no catálogo público nem altera a média do camping. A equipe pode publicar/ocultar pelo procedimento administrativo existente no banco; o app não concede essa permissão. A conta consulta o resultado no perfil. Notificações só aparecem quando existem vínculos reais em `user_notification`; a API não fabrica boas-vindas e não entrega push.

## Correção administrativa identificada nos testes

O script inicial `campsite-community.sql` foi corrigido para distinguir o parâmetro `notes` da coluna da indicação. Em instalações anteriores, `docs/sql/fix-campsite-suggestion-review-notes.sql` repara somente essa função, preservando assinatura e permissões. Aplicação manual por administrador; não é pré-requisito dos endpoints de favoritos, notificações ou avaliações.

## Testes em Docker

```sh
docker build --progress=plain -t kampive-api:community-test .
KAMPIVE_TEST_IMAGE=kampive-api:community-test python3 scripts/verify-auth.py
```

O build roda Maven/Java 21 dentro do container. O script cria rede e PostgreSQL temporários, aplica o schema e alterações anteriores apenas nesse banco descartável, verifica persistência entre sessões, isolamento entre contas, CSRF, idempotência, estados de leitura, moderação, validação e paginação. Ao sair remove os recursos de teste. Não usa as credenciais nem altera o banco de desenvolvimento.

## Edição e exclusão de avaliações próprias

Em “Minhas avaliações”, o autor pode carregar o conteúdo atual, editar nota, comentário, forma de acampar, destaques e até três fotos, ou excluir após confirmação. A edição reutiliza o formulário de criação. Também é possível gerenciar uma avaliação de camping que deixou de ser publicado.

Rotas privadas: `GET /api/v1/users/me/reviews/{uuid}`, `PUT /api/v1/users/me/reviews/{uuid}` e `DELETE /api/v1/users/me/reviews/{uuid}`. As escritas exigem CSRF. O ator sempre vem da sessão; avaliações inexistentes ou de outra pessoa retornam 404. Edição respeita a exigência de e-mail confirmado/período de transição. Exclusão permanece disponível para contas não verificadas.

Toda edição volta para `pending`, inclusive de avaliações publicadas ou ocultas. Nota, texto, método, destaques e fotos são salvos atomicamente; falha em qualquer filho desfaz toda a atualização. Um lock na avaliação serializa edição/exclusão concorrentes. A exclusão remove os destaques e fotos vinculados e a própria avaliação na mesma transação. Depois disso, o usuário pode enviar uma nova avaliação para o camping. Não há recuperação da avaliação excluída nem histórico de versões nesta entrega.

A view existente `vw_campsite_review_summary` considera somente avaliações `published`: edições e exclusões alteram a média e contagem imediatamente, sem tarefa de recálculo ou nova migração. A tela de detalhes recarrega ao receber foco, e “Minhas avaliações” atualiza após excluir ou retornar da edição.

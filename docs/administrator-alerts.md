# Administração e avisos de novas solicitações

Aplicar `docs/sql/administrator-alerts.sql` uma vez como administrador do PostgreSQL, após as migrações de comunidade, cadastro, acompanhamento e verificação de e-mail. Não modifica dados anteriores nem gera avisos retroativos. O script cria tabelas e triggers, mas não promove contas automaticamente.

## Administradores

`kampive.administrator` associa o UUID da conta ao acesso administrativo. `kampive_app` tem apenas SELECT nessa tabela: cadastro, login e edição de perfil não permitem conceder a permissão. A conta deve continuar ativa e com e-mail confirmado. Revogar `revoked_at` bloqueia imediatamente a próxima consulta administrativa e cancela avisos pendentes no próximo processamento.

`GET /api/v1/admin/me` utiliza a sessão existente e consulta a autorização no banco a cada chamada. Retorna `{uuid,role:"ADMIN"}` para administradores; 401 sem sessão, 403 para demais usuários. Nenhum endpoint administrativo de mutação foi liberado nesta etapa. O portal ainda é demonstrativo e não utiliza esse endpoint.

Exemplo de concessão manual, utilizando variáveis psql (nunca interpolar entradas em SQL):

```sql
INSERT INTO kampive.administrator(uuid_app_user,grant_reason)
SELECT uuid_app_user, :'reason'
FROM kampive.app_user
WHERE lower(email)=lower(:'email') AND deactivated_at IS NULL AND email_verified_at IS NOT NULL;
```

## E-mails

Triggers transacionais inserem uma tarefa por administrador inscrito quando chegam:

- Indicações de camping.
- Cadastros completos e reenvios de campings para análise.
- Pedidos de vínculo de proprietário.
- Avaliações novas ou que voltam para moderação.

Atualizações sem mudança de status não duplicam avisos; repetir um pedido de vínculo pendente mantém um único aviso. Um rollback da contribuição também remove sua tarefa. A fila passa a valer após a migração e concessão, sem enviar o histórico anterior.

O worker usa o provedor Brevo já configurado e o template versionado `admin-request`. Verifica novamente o destinatário, a inscrição e a autorização antes de enviar. Inclui tipo, camping e protocolo; não inclui evidências, documentos ou contatos dos solicitantes. Não há link para o portal demonstrativo, que ainda não consulta protocolos reais.

A cada 5 segundos processa uma tarefa disponível. Uma falha adia a próxima tentativa (1 minuto, dobrando até o limite de 1 hora), sem descartar o aviso. A fila sobrevive a reinícios. `sent_at` significa aceitação pelo provedor, não confirmação de chegada na caixa de entrada. O UUID da tarefa é reutilizado como chave de idempotência; Brevo deduplica por 30 minutos, portanto uma falha entre envio e commit ainda pode causar repetição após essa janela. Não se promete entrega exatamente uma vez.

Para suspender apenas e-mails: `UPDATE kampive.administrator SET request_email_enabled=false WHERE uuid_app_user=...`. A preferência de notificações do aplicativo é independente. Alertas de administradores desativados/revogados/não verificados são cancelados. Reativar não reenvia tarefas canceladas.

Consulta operacional (sem expor conteúdo ou endereços):

```sql
SELECT request_type, count(*) FILTER (WHERE sent_at IS NOT NULL) AS accepted,
 count(*) FILTER (WHERE sent_at IS NULL AND cancelled_at IS NULL) AS queued,
 count(*) FILTER (WHERE cancelled_at IS NOT NULL) AS cancelled,
 max(attempts) AS max_attempts
FROM kampive.admin_request_email GROUP BY request_type;
```

## Verificação

`docker build --progress=plain -t kampive-api:admin-alerts .` executa todos os testes Java. `KAMPIVE_TEST_IMAGE=kampive-api:admin-alerts python3 scripts/verify-auth.py` usa containers descartáveis e cobre permissões, revogação, deduplicação, reenvio e rollback, sem usar o Brevo real. Uma tarefa administrativa do tipo `test` pode ser inserida manualmente para confirmar o envio real sem criar solicitações fictícias.

Referências: [autorização por requisição no Spring Security](https://docs.spring.io/spring-security/reference/7.0/servlet/authorization/authorize-http-requests.html) e [funções SECURITY DEFINER no PostgreSQL](https://www.postgresql.org/docs/18/sql-createfunction.html).

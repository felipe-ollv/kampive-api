# Confirmação de e-mail

## Fluxo

O cadastro grava a conta, credenciais, aceites e um pedido de confirmação na mesma transação. O login continua disponível para contas não verificadas, assim como recuperação de senha, consulta, favoritos, perfil e notificações. O cadastro não depende da disponibilidade da Brevo: pedidos aguardam a configuração do transporte.

O worker usa o template responsivo `email-verification` ativo no banco. Ao emitir, gera um token HMAC-SHA256 separado do token de recuperação (prefixo próprio), guarda somente SHA-256 e fixa validade de 24 horas. O UUID do pedido é a chave de idempotência da Brevo. A chave secreta é a mesma configuração privada `RECOVERY_TOKEN_KEY`, mas os prefixos impedem intercâmbio entre os dois fluxos. A fila contém o e-mail destinatário para impedir que um token confirme um endereço diferente se a conta for alterada.

Abrir `/verify-email?token=...` não confirma automaticamente: o usuário toca em “Confirmar e-mail”. A operação exige CSRF e consome o token de forma atômica. Tokens expirados, usados, de contas desativadas ou com e-mail divergente retornam o mesmo erro genérico. Confirmar não autentica uma sessão e não altera senha. O sucesso remove o token da rota.

## Reenvio e contribuições

O reenvio exige autenticação e sempre usa o endereço da própria conta; não aceita destinatário no corpo. Há intervalo mínimo de 60 segundos por conta e limite por IP. Novo pedido invalida links anteriores. A fila tenta até cinco vezes, com intervalo de um minuto. Falhas não são registradas com conteúdo do provedor. A deduplicação da Brevo vale 30 minutos; após uma interrupção maior, não é garantida ausência de duplicatas. Solicitar novamente gera outro pedido após o intervalo permitido.

O backend retorna `403 EMAIL_VERIFICATION_REQUIRED` para contas não verificadas sem período de transição nas seguintes ações: indicação, cadastro de camping, envio para publicação, reivindicação e avaliação. O app apresenta aviso e acesso à tela de verificação, que mostra o prazo quando há transição. As demais autorizações e regras de proprietário continuam aplicadas.

Contas existentes recebem sete dias desde a aplicação do SQL para continuar contribuindo, sem serem marcadas como verificadas. Novas contas exigem confirmação imediatamente para essas ações. A migração não dispara e-mails em massa nem altera avaliações ou indicações anteriores.

## Configuração e implantação

Aplicar `docs/sql/email-verification.sql` após os scripts de templates e recuperação e antes de atualizar a API. O SQL adiciona a data de exigência, fila de confirmação e template versionado. Não há migração automática.

Configurar `EMAIL_VERIFICATION_URL` com HTTPS, sem query ou fragmento. No Compose local, o padrão é `https://localhost:8443/verify-email`. As demais URLs e chave privada vêm da configuração de recuperação existente. O envio permanece suspenso se essas configurações forem inválidas.

Após alterações no app, executar `npm run export:web:local` para atualizar a versão web. Em seguida, em `kampive-infra`, execute `docker compose up -d --no-deps --force-recreate gateway`: a exportação substitui o diretório montado e o gateway precisa remontá-lo. Abrir os links locais no computador; localhost não funciona como endereço do computador no celular físico. Produção ainda exige domínio público e configuração dos links do app.

## Endpoints

- `GET /api/v1/auth/email-verification`: autenticado; retorna verificado, permissão de contribuição, data de exigência e disponibilidade de envio.
- `POST /api/v1/auth/email-verification/resend`: autenticado + CSRF; 202 indica solicitação recebida, não entrega.
- `POST /api/v1/auth/email-verification/confirm`: público + CSRF, corpo `{token}`; 204 no sucesso.

Os testes HTTP cobrem autenticação, CSRF e bloqueio das ações. Os testes da fila cobrem token estável, idempotência, expiração e falha de transporte. `scripts/verify-auth.py` usa banco descartável para verificar cadastro, prazo de transição, confirmação, expiração, replay e desbloqueio de contribuições sem enviar mensagens reais.

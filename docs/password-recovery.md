# Recuperação de senha

## Estado da entrega

Frontend e backend implementam solicitação, redefinição, confirmação e invalidação de sessões. O transporte é a porta `EmailSender`, com adapter HTTP da Brevo. Sem chave/remetente configurados, o adapter está indisponível e não envia nem registra e-mails em logs. Sem transporte e URLs/chave válidos, a solicitação retorna 503 para qualquer endereço antes de consultar a conta; o app mostra indisponibilidade, sem afirmar que um e-mail foi enviado.

## Banco

Aplicar manualmente `docs/sql/password-recovery.sql`, depois de `email-templates.sql`, antes de atualizar a API. Cria tokens e fila transacional de e-mails. Reutiliza `user_credential.password_changed_at` do schema base. Não há DDL automático nem dados de exemplo.

A fila guarda UUID do destinatário, código de template, tentativas e estado. Não guarda corpo renderizado, senha ou token em claro. `password_recovery` guarda somente SHA-256 do token. O token enviado é HMAC-SHA256 de um UUID aleatório de job com uma chave secreta de pelo menos 32 bytes. Isso permite reconstruir o mesmo token nas tentativas sem gravá-lo no banco. A chave deve ser aleatória, persistente, igual entre réplicas e tratada como credencial do servidor. Uma cópia do banco sem essa chave não permite reconstruir links.

## Endpoints

Ambos são POST com JSON e CSRF, sem exigir login. O app obtém CSRF antes de cada escrita e não repete escritas automaticamente.

- `/api/v1/auth/password-recovery`: `{email}`. Quando configurado, retorna sempre `202 {status:"accepted"}` para conta existente, desconhecida ou desativada. O retorno indica solicitação recebida, não entrega de e-mail. Apenas conta ativa com senha gera job. Há cooldown de 60 segundos por conta, protegido por lock, e limite local por IP (10 por minuto).
- `/api/v1/auth/password-reset`: `{token,password}`. Retorna 204 após concluir a transação. Token inválido, expirado, consumido ou conta indisponível retorna 400 `INVALID_TOKEN`. Senha fora da política retorna 400 `INVALID_PASSWORD`: 8–128 caracteres, ao menos uma maiúscula ASCII e um número.

O token vale 30 minutos a partir da emissão pelo worker. Apenas o POST de redefinição consome o token; abrir o link não altera a conta. Nova emissão invalida os tokens anteriores. A troca grava o hash da nova senha, consome todos os tokens da conta, atualiza `password_changed_at` e inclui a confirmação `password-changed` na fila, tudo na mesma transação. Duas trocas concorrentes com o mesmo token resultam em um único sucesso. Falha no provedor não desfaz uma senha já alterada.

## Sessões

Antes de autenticar, o login captura o horário do banco. A sessão guarda esse instante. Em cada requisição autenticada, o filtro confere se a credencial ainda existe, se a conta está ativa e se a senha não mudou depois desse instante. Isso invalida sessões antigas em todas as instâncias e evita que um login iniciado antes de uma troca de senha sobreviva à troca. Requisições que já passaram pela autenticação podem concluir normalmente.

Sessões anteriores a esta implantação não têm a marca e precisarão entrar novamente. O app limpa a sessão local ao receber 401 em operações privadas, preservando a mensagem de credenciais inválidas no login.

## Configurar a Brevo

O adapter `BrevoEmailSender` envia os templates renderizados do banco para `https://api.brevo.com/v3/smtp/email`, com assunto, HTML e texto. Usa timeout de conexão de 3 segundos, requisição de 10 segundos e não segue redirecionamentos. A chave fica exclusivamente no backend; nunca usar `EXPO_PUBLIC` nem versionar credenciais. Configurar uma chave nova localmente se a anterior tiver sido compartilhada no chat.

A chave UUID do job é enviada em `headers.idempotencyKey`. HTTP 201 confirma aceite; HTTP 400 com `duplicate_parameter` confirma uma repetição já aceita. Demais erros deixam o job sujeito à política de tentativas. A [Brevo documenta uma janela de deduplicação de 30 minutos](https://developers.brevo.com/docs/heterogenous-versions-batch-emails): após uma interrupção superior a essa janela, não há garantia de envio único para confirmações pendentes. Não reenviar manualmente jobs antigos sem conferir os logs transacionais do provedor. Tokens de recuperação expirados não são enviados.

O limite diário é controlado pela conta Brevo. Quando o provedor rejeita por falta de cota, o job segue as cinco tentativas existentes e pode ficar `failed=true`; não há reenvio automático no dia seguinte. Monitorar a fila e o painel Brevo. Foram feitos envios manuais de layout pela Brevo; o aceite HTTP não confirma entrega. O fluxo completo app → fila → Brevo deve ser validado separadamente.

Configuração do backend (repassar explicitamente ao container quando integrar o provedor):

| Variável | Conteúdo |
| --- | --- |
| `BREVO_API_KEY` | Chave secreta da API Brevo, injetada somente no container da API. |
| `EMAIL_SENDER_ADDRESS` | Remetente `contact@kampive.app` por padrão; precisa estar verificado na Brevo. |
| `EMAIL_SENDER_NAME` | Nome do remetente; padrão Kampive. |
| `RECOVERY_RESET_URL` | URL HTTPS da tela `/reset-password`, sem query ou fragmento; nunca derivada do Host enviado pelo cliente. |
| `EMAIL_LOGO_URL` | Logo público HTTPS. |
| `EMAIL_SUPPORT_URL` | Canal de suporte HTTPS. |
| `RECOVERY_TOKEN_KEY` | Chave aleatória em base64 com pelo menos 32 bytes; nunca EXPO_PUBLIC. |

O worker consulta um job a cada 5 segundos, usa `FOR UPDATE SKIP LOCKED`, lê o template ativo do banco e tenta até cinco vezes com intervalo de um minuto. Tokens consumidos/expirados, pedidos anteriores à última troca de senha e contas desativadas não são enviados. `sent_at` significa aceite pelo provedor, não leitura/entrega final. Monitorar jobs `failed=true`; a futura integração pode acrescentar webhooks de entrega/bounce. Não trocar a chave com jobs de recuperação pendentes sem cancelá-los e solicitar novos links.

A chamada ao provedor ocorre com a transação aberta; usar timeout curto. Se a API parar entre o aceite do provedor e o commit, a próxima tentativa usa a mesma chave de idempotência e token. O provider precisa suportar esse contrato.

## App e links

“Esqueci minha senha” abre `/forgot-password`. O link chega à rota pública `/reset-password?token=...`, que funciona também se houver uma conta local autenticada. Sucesso substitui a rota por `/password-reset-success`, descartando o token da rota e limpando o estado da conta. Senhas ficam somente no estado da tela e são apagadas após uso.

O scheme `kampive` foi declarado para builds nativas. Essa alteração requer uma nova development build para reconhecer `kampive://reset-password?...`; Expo Go usa `exp://HOST:8081/--/reset-password?...`. O domínio HTTPS definitivo e as associações iOS Universal Links/Android App Links serão configurados quando o domínio de comunicação for definido. Não há associação de domínio fictício. Uma publicação web também exige a configuração de origem/cookies já documentada. No host web, aplicar `Referrer-Policy: no-referrer` e evitar registrar queries dos links de recuperação.

## Testes

`docker build --progress=plain -t kampive-api:recovery-test .` cobre autorização, CSRF, indisponibilidade, política de configuração, processamento da fila, idempotência e erros de transporte simulados. `KAMPIVE_TEST_IMAGE=kampive-api:recovery-test python3 scripts/verify-auth.py` cria um PostgreSQL descartável para validar expiração, concorrência, replay, confirmação na fila, recusa da senha antiga e invalidação de sessão. Os tokens de integração são criados apenas nesse banco descartável, sem endpoint de depuração e sem mensagens reais.

## Teste web local

O Compose local serve `kampive-app/dist` no gateway HTTPS existente. API e navegador usam a mesma origem, preservando cookies e CSRF sem liberar CORS. Não é uma publicação na internet.

1. Em `kampive-app`, execute `npm run export:web:local` quando alterar o frontend. Depois, em `kampive-infra`, execute `docker compose up -d --no-deps --force-recreate gateway` para remontar o diretório exportado.
2. Em `kampive-api`, execute `python3 scripts/configure-local-email.py` e informe a chave no prompt oculto. O script preserva as senhas do banco, gera a chave de recuperação somente se estiver ausente e salva o `.env` privado de `kampive-infra` com permissão 600.
3. Em `kampive-infra`, execute `docker compose up -d --no-deps --build api gateway`.
4. Abra `https://localhost:8443/forgot-password` no navegador da mesma máquina, com a CA local confiável. Solicite o link usando uma conta existente e abra o e-mail nessa máquina. A solicitação inicia um envio real.
5. Troque a senha pelo link; verifique a confirmação, o login com a nova senha e a rejeição da senha antiga.

O Compose fornece URLs locais padrão de recuperação, logo e suporte. A página de suporte usa `contact@kampive.app`. Imagens `localhost` não são acessíveis pelo proxy de imagens do Gmail; o logo pode aparecer somente como texto alternativo neste teste. O domínio público e o logo público continuam necessários para produção. No celular físico, localhost aponta para o celular: este teste é para o navegador do computador. O gateway envia `Referrer-Policy: no-referrer` e `Cache-Control: no-store` para o frontend e não habilita logs de acesso com tokens.

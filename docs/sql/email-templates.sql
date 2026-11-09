-- Apply manually as administrator. Templates contain placeholders, never recipient data or tokens.
BEGIN;
CREATE TABLE kampive.email_template (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid_email_template UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    code VARCHAR(60) NOT NULL CHECK (code IN ('password-reset','password-changed')),
    locale VARCHAR(10) NOT NULL DEFAULT 'pt-BR' CHECK (locale='pt-BR'),
    version INTEGER NOT NULL CHECK (version > 0),
    active BOOLEAN NOT NULL DEFAULT false,
    subject VARCHAR(180) NOT NULL CHECK (btrim(subject) <> '' AND subject !~ E'[\r\n]'),
    html_body TEXT NOT NULL CHECK (btrim(html_body) <> ''),
    text_body TEXT NOT NULL CHECK (btrim(text_body) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(code,locale,version)
);
CREATE UNIQUE INDEX ix_email_template_active ON kampive.email_template(code,locale) WHERE active;
REVOKE ALL ON kampive.email_template FROM PUBLIC, kampive_app;
GRANT SELECT ON kampive.email_template TO kampive_app;

INSERT INTO kampive.email_template(code,locale,version,active,subject,html_body,text_body) VALUES ('password-reset','pt-BR',1,true,'Redefina sua senha do Kampive',$html$<!doctype html>
<html lang="pt-BR" xmlns="http://www.w3.org/1999/xhtml" xmlns:v="urn:schemas-microsoft-com:vml" xmlns:o="urn:schemas-microsoft-com:office:office">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="x-apple-disable-message-reformatting">
<title>Redefina sua senha do Kampive</title>
<!--[if mso]><xml><o:OfficeDocumentSettings><o:PixelsPerInch>96</o:PixelsPerInch></o:OfficeDocumentSettings></xml><![endif]-->
<style>
body, table, td, a { -webkit-text-size-adjust:100%; -ms-text-size-adjust:100%; }
table, td { mso-table-lspace:0pt; mso-table-rspace:0pt; }
img { border:0; outline:none; text-decoration:none; -ms-interpolation-mode:bicubic; }
@media screen and (max-width:600px) {
  .outer { padding:16px 8px !important; }
  .content { padding:28px 20px !important; }
  .heading { font-size:28px !important; line-height:34px !important; }
}
</style>
</head>
<body style="margin:0;padding:0;width:100%;background-color:#fbf9f4;color:#1b1c19;font-family:Arial,Helvetica,sans-serif;">
<div style="display:none;font-size:1px;line-height:1px;color:#fbf9f4;max-height:0;max-width:0;opacity:0;overflow:hidden;mso-hide:all;">Recebemos seu pedido de recuperação de acesso.</div>
<table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="width:100%;background-color:#fbf9f4;">
<tr><td class="outer" align="center" style="padding:32px 12px;">
<!--[if mso]><table role="presentation" width="560" align="center" cellpadding="0" cellspacing="0" border="0"><tr><td><![endif]-->
<table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="width:100%;max-width:560px;background-color:#ffffff;border:1px solid #e4e2dd;border-radius:12px;">
<tr><td class="content" style="padding:40px 32px;">
<table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="width:100%;table-layout:fixed;">
<tr><td align="center" style="padding-bottom:36px;"><img src="{{logo_url}}" alt="Kampive" width="120" style="display:block;width:120px;max-width:100%;height:auto;color:#1e3a2f;font-size:24px;font-weight:bold;"></td></tr>
<tr><td><h1 class="heading" style="margin:0 0 24px;font-size:32px;line-height:38px;color:#1e3a2f;">Vamos recuperar seu acesso</h1></td></tr>
<tr><td style="font-size:16px;line-height:25px;word-wrap:break-word;overflow-wrap:anywhere;">
<p style="margin:0 0 16px;">Olá, {{first_name}}!</p>
<p style="margin:0 0 24px;">Recebemos uma solicitação para redefinir a senha da sua conta Kampive.</p>
</td></tr>
<tr><td style="padding-bottom:12px;">
<table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0"><tr><td align="center" bgcolor="#c04e18" style="border-radius:8px;mso-padding-alt:16px 20px;">
<a href="{{reset_url}}" style="display:block;padding:16px 20px;border:1px solid #c04e18;border-radius:8px;color:#ffffff;font-size:17px;line-height:24px;font-weight:bold;text-align:center;text-decoration:none;mso-padding-alt:0;">Redefinir minha senha</a>
</td></tr></table></td></tr>
<tr><td align="center" style="padding-bottom:28px;color:#727974;font-size:13px;line-height:20px;">Este link é válido por {{expires_minutes}} minutos.</td></tr>
<tr><td style="padding-bottom:28px;font-size:14px;line-height:22px;word-break:break-all;overflow-wrap:anywhere;">
<p style="margin:0 0 8px;word-break:normal;">Se o botão não funcionar, copie e cole este link no navegador:</p>
<a href="{{reset_url}}" style="color:#1e3a2f;text-decoration:underline;word-break:break-all;overflow-wrap:anywhere;">{{reset_url}}</a>
</td></tr>
<tr><td style="padding:24px 0;border-top:1px solid #e4e2dd;font-size:14px;line-height:23px;">
<strong style="color:#1e3a2f;">Não solicitou esta alteração?</strong>
<p style="margin:8px 0 0;">Pode ignorar este e-mail. Sua senha permanece a mesma.</p>
</td></tr>
<tr><td align="center" style="padding-top:24px;border-top:1px solid #e4e2dd;font-size:13px;line-height:21px;color:#727974;">
<strong style="font-size:16px;color:#1e3a2f;">Kampive</strong><br>Explore a vida ao ar livre<br>
<a href="{{support_url}}" style="color:#1e3a2f;text-decoration:underline;">Contato de suporte</a>
</td></tr>
</table>
</td></tr></table>
<!--[if mso]></td></tr></table><![endif]-->
</td></tr></table>
</body>
</html>
$html$,$text$Olá, {{first_name}}!

Recebemos uma solicitação para redefinir a senha da sua conta Kampive.

Redefinir minha senha: {{reset_url}}
Este link é válido por {{expires_minutes}} minutos.

Não solicitou esta alteração? Pode ignorar este e-mail. Sua senha permanece a mesma.

Contato de suporte: {{support_url}}
Kampive — Explore a vida ao ar livre
$text$);

INSERT INTO kampive.email_template(code,locale,version,active,subject,html_body,text_body) VALUES ('password-changed','pt-BR',1,true,'Sua senha do Kampive foi alterada',$html$<!doctype html>
<html lang="pt-BR" xmlns="http://www.w3.org/1999/xhtml" xmlns:v="urn:schemas-microsoft-com:vml" xmlns:o="urn:schemas-microsoft-com:office:office">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="x-apple-disable-message-reformatting">
<title>Sua senha do Kampive foi alterada</title>
<!--[if mso]><xml><o:OfficeDocumentSettings><o:PixelsPerInch>96</o:PixelsPerInch></o:OfficeDocumentSettings></xml><![endif]-->
<style>
body, table, td, a { -webkit-text-size-adjust:100%; -ms-text-size-adjust:100%; }
table, td { mso-table-lspace:0pt; mso-table-rspace:0pt; }
img { border:0; outline:none; text-decoration:none; -ms-interpolation-mode:bicubic; }
@media screen and (max-width:600px) {
  .outer { padding:16px 8px !important; }
  .content { padding:28px 20px !important; }
  .heading { font-size:28px !important; line-height:34px !important; }
}
</style>
</head>
<body style="margin:0;padding:0;width:100%;background-color:#fbf9f4;color:#1b1c19;font-family:Arial,Helvetica,sans-serif;">
<div style="display:none;font-size:1px;line-height:1px;color:#fbf9f4;max-height:0;max-width:0;opacity:0;overflow:hidden;mso-hide:all;">Confirmação de alteração da senha da sua conta.</div>
<table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="width:100%;background-color:#fbf9f4;">
<tr><td class="outer" align="center" style="padding:32px 12px;">
<!--[if mso]><table role="presentation" width="560" align="center" cellpadding="0" cellspacing="0" border="0"><tr><td><![endif]-->
<table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="width:100%;max-width:560px;background-color:#ffffff;border:1px solid #e4e2dd;border-radius:12px;">
<tr><td class="content" style="padding:40px 32px;">
<table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="width:100%;table-layout:fixed;">
<tr><td align="center" style="padding-bottom:36px;"><img src="{{logo_url}}" alt="Kampive" width="120" style="display:block;width:120px;max-width:100%;height:auto;color:#1e3a2f;font-size:24px;font-weight:bold;"></td></tr>
<tr><td align="center" style="padding-bottom:24px;"><span aria-hidden="true" style="display:inline-block;padding:14px 20px;background-color:#e1e9e2;border-radius:50%;color:#1e3a2f;font-size:32px;line-height:36px;">&#10003;</span></td></tr>
<tr><td><h1 class="heading" style="margin:0 0 24px;font-size:32px;line-height:38px;color:#1e3a2f;">Sua senha foi alterada</h1></td></tr>
<tr><td style="font-size:16px;line-height:25px;word-wrap:break-word;overflow-wrap:anywhere;">
<p style="margin:0 0 16px;">Olá, {{first_name}}!</p>
<p style="margin:0 0 16px;">A senha da sua conta Kampive foi alterada com sucesso.</p>
<p style="margin:0 0 28px;">Você já pode entrar no aplicativo usando sua nova senha.</p>
</td></tr>
<tr><td style="padding-bottom:28px;">
<table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0"><tr><td style="padding:24px;background-color:#ffebe2;border-radius:10px;font-size:15px;line-height:24px;">
<strong style="color:#1e3a2f;font-size:17px;">Não reconhece esta alteração?</strong>
<p style="margin:10px 0 16px;">Entre em contato com nosso suporte para receber ajuda.</p>
<a href="{{support_url}}" style="color:#1e3a2f;font-weight:bold;text-decoration:underline;">Falar com o suporte</a>
</td></tr></table></td></tr>
<tr><td align="center" style="padding-top:24px;border-top:1px solid #e4e2dd;font-size:13px;line-height:21px;color:#727974;">
<strong style="font-size:16px;color:#1e3a2f;">Kampive</strong><br>Explore a vida ao ar livre<br>
<a href="{{support_url}}" style="color:#1e3a2f;text-decoration:underline;">Contato de suporte</a>
</td></tr>
</table>
</td></tr></table>
<!--[if mso]></td></tr></table><![endif]-->
</td></tr></table>
</body>
</html>
$html$,$text$Olá, {{first_name}}!

A senha da sua conta Kampive foi alterada com sucesso.
Você já pode entrar no aplicativo usando sua nova senha.

Não reconhece esta alteração? Entre em contato com nosso suporte para receber ajuda.
Falar com o suporte: {{support_url}}

Kampive — Explore a vida ao ar livre
$text$);

COMMIT;

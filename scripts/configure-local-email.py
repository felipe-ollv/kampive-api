#!/usr/bin/env python3
"""Configure private local email settings interactively, without printing secrets."""
import base64
import getpass
import os
from pathlib import Path
import secrets
import tempfile


def main():
    target = Path(__file__).resolve().parents[2] / 'kampive-infra' / '.env'
    if not target.is_file():
        raise SystemExit('Crie primeiro o .env de kampive-infra usando o .env.example.')
    key = getpass.getpass('Chave da API Brevo (entrada oculta): ').strip()
    if not key or any(char.isspace() for char in key) or any(char in key for char in "'\"$#\\"):
        raise SystemExit('Chave inválida; nenhum arquivo foi alterado.')
    lines = target.read_text().splitlines()
    existing = dict(line.split('=', 1) for line in lines if '=' in line and not line.lstrip().startswith('#'))
    updates = {'BREVO_API_KEY': key}
    if not existing.get('RECOVERY_TOKEN_KEY', '').strip():
        updates['RECOVERY_TOKEN_KEY'] = base64.b64encode(secrets.token_bytes(32)).decode()
    result = []
    for line in lines:
        name = line.split('=', 1)[0]
        if name in updates:
            continue
        result.append(line)
    result.extend(f'{name}={value}' for name, value in updates.items())
    descriptor, temporary = tempfile.mkstemp(dir=target.parent, prefix='.env.email-')
    try:
        with os.fdopen(descriptor, 'w') as stream:
            stream.write('\n'.join(result) + '\n')
        os.replace(temporary, target)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)
    print('Credenciais salvas no .env privado (permissão 600). Nenhum e-mail foi enviado.')
    print('Para ativar: no diretório kampive-infra, execute docker compose up -d --no-deps api')


if __name__ == '__main__':
    main()

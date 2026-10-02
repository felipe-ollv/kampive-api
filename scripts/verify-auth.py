"""End-to-end auth verification against disposable Docker containers. No local data changes."""
import http.cookiejar
import json
import os
from pathlib import Path
import secrets
import subprocess
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
SCHEMA = ROOT.parent / 'kampive-infra/sql/schema.sql'
POSTGRES = 'postgres:18.6-bookworm@sha256:3725f4e2499eef5134592b3b4ab79a543ed7f8e533b05b5b637af926630f6650'
prefix = 'kampive-auth-test-' + secrets.token_hex(4)
network, database, api = prefix, prefix + '-db', prefix + '-api'
created = []

def docker(*args, stdin=None, env=None):
    result = subprocess.run(['docker', *args], input=stdin, text=True, capture_output=True, env=env)
    if result.returncode:
        # Do not print request bodies, passwords or database connection environment.
        raise RuntimeError('Docker command failed: ' + result.stderr[:1000])
    return result.stdout.strip()

def sql(statement):
    return docker('exec', '-i', database, 'psql', '-X', '-v', 'ON_ERROR_STOP=1',
                  '-U', 'postgres', '-d', 'kampive', '-At', stdin=statement)

def wait_until(check, label):
    for _ in range(60):
        try:
            if check():
                return
        except (RuntimeError, OSError):
            pass
        time.sleep(1)
    raise RuntimeError('Timed out waiting for ' + label)

try:
    schema = SCHEMA.read_text()
    docker('network', 'create', network)
    created.append(('network', network))
    env = dict(os.environ, POSTGRES_PASSWORD=secrets.token_hex(32))
    docker('run', '-d', '--name', database, '--network', network,
           '--tmpfs', '/var/lib/postgresql:rw', '-e', 'POSTGRES_DB=kampive',
           '-e', 'POSTGRES_PASSWORD', POSTGRES, env=env)
    created.append(('container', database))
    wait_until(lambda: docker('exec', database, 'pg_isready', '-U', 'postgres', '-d', 'kampive'), 'PostgreSQL')
    app_password = secrets.token_hex(32)
    sql("CREATE ROLE kampive_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD '" + app_password + "';\n"
        "REVOKE ALL ON DATABASE kampive FROM PUBLIC; GRANT CONNECT ON DATABASE kampive TO kampive_app;\n"
        "REVOKE CREATE ON SCHEMA public FROM PUBLIC; CREATE SCHEMA kampive AUTHORIZATION postgres;\n"
        "GRANT USAGE ON SCHEMA kampive TO kampive_app;")
    sql(schema)
    terms, privacy = str(uuid.uuid4()), str(uuid.uuid4())
    sql("INSERT INTO kampive.legal_document(uuid_legal_document,document_type,version,content_url,published_at) VALUES "
        f"('{terms}','terms_of_use','test-only','https://example.test/terms',now()),"
        f"('{privacy}','privacy_policy','test-only','https://example.test/privacy',now());")
    env = dict(os.environ, DB_PASSWORD=app_password)
    docker('run', '-d', '--name', api, '--network', network, '-p', '127.0.0.1::8080',
           '-e', 'SPRING_PROFILES_ACTIVE=local', '-e', f'DB_URL=jdbc:postgresql://{database}:5432/kampive',
           '-e', 'DB_USERNAME=kampive_app', '-e', 'DB_PASSWORD', 'kampive-api:0.0.1-local', env=env)
    created.append(('container', api))
    port = docker('inspect', '--format', '{{(index (index .NetworkSettings.Ports "8080/tcp") 0).HostPort}}', api)
    base = 'http://127.0.0.1:' + port
    jar = http.cookiejar.CookieJar()
    client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))

    def request(method, path, body=None, token=None):
        headers = {'Content-Type': 'application/json'}
        if token:
            headers['X-CSRF-TOKEN'] = token
        req = urllib.request.Request(base + path, data=json.dumps(body).encode() if body is not None else None,
                                     headers=headers, method=method)
        try:
            response = client.open(req, timeout=5)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else None

    wait_until(lambda: request('GET', '/actuator/health')[0] == 200, 'API')
    assert request('GET', '/api/v1/users/me')[0] == 401
    code, doc = request('GET', '/v3/api-docs')
    assert code == 200 and '/api/v1/auth/login' in doc['paths']
    assert request('GET', '/api/v1/auth/legal-documents')[0] == 200
    token = request('GET', '/api/v1/auth/csrf')[1]['token']
    body = {'name': 'Integration Explorer', 'email': 'auth-test@example.test',
            'password': 'a long integration password phrase', 'acceptedDocumentUuids': [terms, privacy]}
    assert request('POST', '/api/v1/auth/register', body)[0] == 403
    assert request('POST', '/api/v1/auth/register', dict(body, acceptedDocumentUuids=[terms, terms]), token)[0] == 400
    code, profile = request('POST', '/api/v1/auth/register', body, token)
    assert code == 201, ('register status', code)
    user_uuid = profile['uuid']
    assert not profile['emailVerified'] and 'password' not in profile
    assert request('GET', '/api/v1/users/me')[0] == 401
    assert request('POST', '/api/v1/auth/register', dict(body, email=body['email'].upper()), token)[0] == 409
    # A credential write failure must roll back the user creation as well.
    sql("CREATE FUNCTION kampive.fail_test_credential() RETURNS trigger LANGUAGE plpgsql AS $$ "
        "BEGIN RAISE EXCEPTION 'test constraint' USING ERRCODE='23514'; END $$; "
        "CREATE TRIGGER fail_test_credential BEFORE INSERT ON kampive.user_credential "
        "FOR EACH ROW EXECUTE FUNCTION kampive.fail_test_credential();")
    assert request('POST', '/api/v1/auth/register', dict(body, email='rollback@example.test'), token)[0] == 409
    assert sql("SELECT count(*) FROM kampive.app_user WHERE email='rollback@example.test'") == '0'
    sql('DROP TRIGGER fail_test_credential ON kampive.user_credential; DROP FUNCTION kampive.fail_test_credential();')
    assert sql(f"SELECT count(*) FROM kampive.document_acceptance WHERE uuid_app_user='{user_uuid}'") == '2'
    assert sql(f"SELECT password_hash LIKE '$argon2id$%' FROM kampive.user_credential WHERE uuid_app_user='{user_uuid}'") == 't'
    session_before = next(c.value for c in jar if c.name == 'JSESSIONID')
    login = {'email': body['email'], 'password': body['password']}
    code, _ = request('POST', '/api/v1/auth/login', dict(login, password='incorrect password'), token)
    assert code == 401
    assert request('POST', '/api/v1/auth/login', login, token)[0] == 200
    session_after = next(c.value for c in jar if c.name == 'JSESSIONID')
    assert session_after != session_before
    assert request('GET', '/api/v1/users/me')[1]['uuid'] == user_uuid
    assert request('GET', '/api/v1/users/' + user_uuid)[0] == 403
    assert request('POST', '/api/v1/auth/logout', token=token)[0] == 403
    token = request('GET', '/api/v1/auth/csrf')[1]['token']
    assert request('POST', '/api/v1/auth/logout', token=token)[0] == 204
    assert request('GET', '/api/v1/users/me')[0] == 401
    replay = urllib.request.Request(base + '/api/v1/users/me', headers={'Cookie': 'JSESSIONID=' + session_after})
    try:
        urllib.request.urlopen(replay, timeout=5)
        raise AssertionError('Logged out session was reusable')
    except urllib.error.HTTPError as error:
        assert error.code == 401
    token = request('GET', '/api/v1/auth/csrf')[1]['token']
    codes = [request('POST', '/api/v1/auth/login', dict(login, password='incorrect password'), token)[0] for _ in range(10)]
    assert 429 in codes
    assert sql('SELECT count(*) FROM kampive.app_user') == '1'
    # Geographic fixtures exist only in this disposable database.
    assert request('GET', '/api/v1/continents') == (200, [])
    empty = request('GET', '/api/v1/countries')[1]
    assert empty['items'] == [] and empty['totalElements'] == 0 and empty['totalPages'] == 0
    south, europe, brazil, portugal, uruguay, division, city, category = [str(uuid.uuid4()) for _ in range(8)]
    sql(f"INSERT INTO kampive.continent(uuid_continent,name) VALUES ('{south}','South America'),('{europe}','Europe');"
        f"INSERT INTO kampive.country(uuid_country,uuid_continent,name,iso_code) VALUES "
        f"('{brazil}','{south}','Brazil','BR'),('{portugal}','{europe}','Portugal','PT'),('{uruguay}','{south}','Uruguay','UY');"
        f"INSERT INTO kampive.administrative_division(uuid_administrative_division,uuid_country,name,code) VALUES ('{division}','{brazil}','Test Division','TEST');"
        f"INSERT INTO kampive.city(uuid_city,uuid_administrative_division,name) VALUES ('{city}','{division}','Test City');"
        f"INSERT INTO kampive.campsite_category(uuid_campsite_category,name) VALUES ('{category}','Test Category');"
        f"INSERT INTO kampive.campsite(uuid_owner,uuid_campsite_category,uuid_city,name,status,street_address,latitude,longitude) VALUES "
        f"('{user_uuid}','{category}','{city}','Published site','published','Test Street',0,0),"
        f"('{user_uuid}','{category}','{city}','Draft site','draft','Test Street',0,0);")
    continents = request('GET', '/api/v1/continents')[1]
    assert [item['name'] for item in continents] == ['Europe', 'South America']
    code, countries = request('GET', '/api/v1/countries?size=2')
    assert code == 200 and countries['totalElements'] == 3 and countries['totalPages'] == 2
    assert [item['isoCode'] for item in countries['items']] == ['BR', 'PT']
    assert countries['items'][0]['campsiteCount'] == 1
    second_page = request('GET', '/api/v1/countries?page=1&size=2')[1]
    assert [item['isoCode'] for item in second_page['items']] == ['UY']
    filtered = request('GET', '/api/v1/countries?continentUuid=' + south)[1]
    assert filtered['totalElements'] == 2 and {item['isoCode'] for item in filtered['items']} == {'BR','UY'}
    assert request('GET', '/api/v1/countries?query=br')[1]['totalElements'] == 1
    assert request('GET', '/api/v1/countries?query=pt')[1]['items'][0]['uuid'] == portugal
    assert request('GET', '/api/v1/countries?continentUuid=' + str(uuid.uuid4()))[1]['totalElements'] == 0
    for search in ['%25', '_', '%27%20OR%201%3D1--']:
        assert request('GET', '/api/v1/countries?query=' + search)[1]['totalElements'] == 0
    missing_page = request('GET', '/api/v1/countries?page=10000')[1]
    assert missing_page['items'] == [] and missing_page['totalElements'] == 3
    assert request('GET', '/api/v1/countries/' + brazil)[1]['campsiteCount'] == 1
    assert request('GET', '/api/v1/countries/' + portugal)[1]['campsiteCount'] == 0
    assert request('GET', '/api/v1/countries/' + str(uuid.uuid4()))[0] == 404
    assert request('GET', '/api/v1/countries/not-a-uuid')[0] == 400
    assert request('GET', '/api/v1/countries?size=101')[0] == 400
    print('PASS: authentication, transactions, CSRF, sessions, ownership, throttling, geographic filters, pagination and published campsite counts.')
finally:
    for kind, name in reversed(created):
        if kind == 'container':
            subprocess.run(['docker', 'rm', '-f', name], capture_output=True)
        else:
            subprocess.run(['docker', 'network', 'rm', name], capture_output=True)

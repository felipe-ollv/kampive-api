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
    sql((ROOT / "docs/sql/campsite-community.sql").read_text())
    sql((ROOT / "docs/sql/campsite-registration.sql").read_text())
    sql((ROOT / "docs/sql/campsite-city-name.sql").read_text())
    sql((ROOT / "docs/sql/user-profile.sql").read_text())
    sql((ROOT / "docs/sql/request-tracking.sql").read_text())
    sql((ROOT / "docs/sql/email-templates.sql").read_text())
    sql((ROOT / "docs/sql/password-recovery.sql").read_text())
    sql((ROOT / "docs/sql/email-verification.sql").read_text())
    assert sql("SELECT count(*) FROM kampive.email_template WHERE active AND locale='pt-BR'") == '3'
    assert sql("SELECT has_table_privilege('kampive_app','kampive.email_template','SELECT') AND NOT has_table_privilege('kampive_app','kampive.email_template','UPDATE')") == 't'
    for template_code in ['password-reset', 'password-changed', 'email-verification']:
        assert sql(f"SELECT html_body FROM kampive.email_template WHERE code='{template_code}'") == (ROOT / 'emails' / (template_code + '.html')).read_text().strip()
        assert sql(f"SELECT text_body FROM kampive.email_template WHERE code='{template_code}'") == (ROOT / 'emails' / (template_code + '.txt')).read_text().strip()
    try:
        sql("INSERT INTO kampive.email_template(code,locale,version,active,subject,html_body,text_body) SELECT code,locale,2,true,subject,html_body,text_body FROM kampive.email_template WHERE code='password-reset';")
        raise AssertionError('Two active template versions allowed')
    except RuntimeError:
        pass
    sql((ROOT / "docs/sql/administrator-alerts.sql").read_text())
    sql((ROOT / "docs/sql/manager.sql").read_text())
    terms, privacy = str(uuid.uuid4()), str(uuid.uuid4())
    sql("INSERT INTO kampive.legal_document(uuid_legal_document,document_type,version,content_url,published_at) VALUES "
        f"('{terms}','terms_of_use','test-only','https://example.test/terms',now()),"
        f"('{privacy}','privacy_policy','test-only','https://example.test/privacy',now());")
    env = dict(os.environ, DB_PASSWORD=app_password)
    docker('run', '-d', '--name', api, '--network', network, '-p', '127.0.0.1::8080',
           '-e', 'SPRING_PROFILES_ACTIVE=local', '-e', f'DB_URL=jdbc:postgresql://{database}:5432/kampive',
           '-e', 'DB_USERNAME=kampive_app', '-e', 'DB_PASSWORD', os.environ.get('KAMPIVE_TEST_IMAGE', 'kampive-api:0.0.1-local'), env=env)
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
    assert request('GET', '/api/v1/users/me/requests')[0] == 401
    code, doc = request('GET', '/v3/api-docs')
    assert code == 200 and '/api/v1/auth/login' in doc['paths']
    assert request('GET', '/api/v1/auth/legal-documents')[0] == 200
    token = request('GET', '/api/v1/auth/csrf')[1]['token']
    body = {'name': 'Integration Explorer', 'email': 'auth-test@example.test',
            'password': 'A long integration password phrase 7', 'acceptedDocumentUuids': [terms, privacy]}
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
    # Unverified accounts can sign in, but contributions require confirmation.
    assert sql(f"SELECT count(*) FROM kampive.email_verification WHERE uuid_app_user='{user_uuid}'") == '1'
    vstatus = request('GET', '/api/v1/auth/email-verification')[1]
    assert not vstatus['verified'] and not vstatus['contributionsAllowed']
    verify_csrf = request('GET', '/api/v1/auth/csrf')[1]['token']
    blocked = request('POST', '/api/v1/campsite-suggestions', {}, verify_csrf)
    assert blocked[0] == 403 and blocked[1]['code'] == 'EMAIL_VERIFICATION_REQUIRED', blocked
    assert request('POST', '/api/v1/auth/email-verification/resend', token=verify_csrf)[0] == 503
    # Existing accounts retain a grace period without being marked verified.
    sql(f"UPDATE kampive.app_user SET email_verification_required_at=clock_timestamp()+interval '7 days' WHERE uuid_app_user='{user_uuid}'")
    vstatus = request('GET', '/api/v1/auth/email-verification')[1]
    assert not vstatus['verified'] and vstatus['contributionsAllowed']
    sql(f"UPDATE kampive.app_user SET email_verification_required_at=clock_timestamp()-interval '1 day' WHERE uuid_app_user='{user_uuid}'")
    import hashlib
    verification_token = secrets.token_urlsafe(32)
    verification_hash = hashlib.sha256(verification_token.encode()).hexdigest()
    sql(f"UPDATE kampive.email_verification SET token_hash='{verification_hash}',expires_at=clock_timestamp()-interval '1 minute' WHERE uuid_app_user='{user_uuid}'")
    assert request('POST', '/api/v1/auth/email-verification/confirm', {'token': verification_token})[0] == 403
    assert request('POST', '/api/v1/auth/email-verification/confirm', {'token': verification_token}, verify_csrf)[0] == 400
    sql(f"UPDATE kampive.email_verification SET expires_at=clock_timestamp()+interval '24 hours' WHERE uuid_app_user='{user_uuid}'")
    assert request('POST', '/api/v1/auth/email-verification/confirm', {'token': verification_token}, verify_csrf)[0] == 204
    assert request('POST', '/api/v1/auth/email-verification/confirm', {'token': verification_token}, verify_csrf)[0] == 400
    assert request('GET', '/api/v1/users/me')[1]['emailVerified']
    # Grants are database-only and are checked on every request, including existing sessions.
    assert request('GET', '/api/v1/admin/me')[0] == 403
    assert sql("SELECT has_table_privilege('kampive_app','kampive.administrator','SELECT') AND NOT has_table_privilege('kampive_app','kampive.administrator','INSERT') AND NOT has_table_privilege('kampive_app','kampive.administrator','UPDATE')") == 't'
    assert sql("SELECT NOT has_table_privilege('kampive_app','kampive.admin_request_email','INSERT') AND NOT has_function_privilege('kampive_app','kampive.queue_admin_request_email()','EXECUTE')") == 't'
    sql(f"INSERT INTO kampive.administrator(uuid_app_user,grant_reason) VALUES ('{user_uuid}','Disposable integration test')")
    assert request('GET', '/api/v1/admin/me')[1]['role'] == 'ADMIN'
    sql(f"UPDATE kampive.administrator SET revoked_at=now() WHERE uuid_app_user='{user_uuid}'")
    assert request('GET', '/api/v1/admin/me')[0] == 403
    sql(f"UPDATE kampive.administrator SET revoked_at=NULL WHERE uuid_app_user='{user_uuid}'")
    sql(f"UPDATE kampive.app_user SET email_verified_at=NULL WHERE uuid_app_user='{user_uuid}'")
    assert request('GET', '/api/v1/admin/me')[0] == 403
    sql(f"UPDATE kampive.app_user SET email_verified_at=now() WHERE uuid_app_user='{user_uuid}'")
    assert sql('SELECT count(*) FROM kampive.admin_request_email') == '0'

    assert request('GET', '/api/v1/auth/email-verification')[1]['contributionsAllowed']
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
    # Campsite lifecycle runs against this disposable database, never the local catalog.
    assert request('GET', '/api/v1/campsites')[1]['totalElements'] == 1
    token = request('GET', '/api/v1/auth/csrf')[1]['token']
    assert request('POST', '/api/v1/auth/login', login, token)[0] == 200
    token = request('GET', '/api/v1/auth/csrf')[1]['token']
    amenity, setting, contact_type, currency = [str(uuid.uuid4()) for _ in range(4)]
    sql(f"INSERT INTO kampive.amenity(uuid_amenity,name) VALUES ('{amenity}','Test Water');"
        f"INSERT INTO kampive.natural_setting(uuid_natural_setting,name) VALUES ('{setting}','Test Forest');"
        f"INSERT INTO kampive.contact_type(uuid_contact_type,name) VALUES ('{contact_type}','Test Phone');"
        f"INSERT INTO kampive.currency(uuid_currency,name,iso_code,symbol) VALUES ('{currency}','Test Real','BRL','R$');")
    options = request('GET', '/api/v1/campsite-options')[1]
    assert options['categories'][0]['uuid'] == category and options['amenities'][0]['uuid'] == amenity
    assert request('GET', '/api/v1/cities?countryUuid=' + brazil)[1]['items'][0]['uuid'] == city
    assert request('GET', '/api/v1/cities?countryUuid=' + portugal)[1]['totalElements'] == 0
    camp = {'name': 'Owner Test Camp', 'description': 'An integration test campsite.', 'categoryUuid': category,
            'cityUuid': city, 'streetAddress': 'Test Street', 'latitude': -23.5, 'longitude': -46.5,
            'startingPricePerNight': 75, 'currencyUuid': currency,
            'amenityUuids': [amenity], 'settingUuids': [setting],
            'contacts': [{'typeUuid': contact_type, 'value': '+5511999999999'}],
            'photos': [{'url': 'https://example.test/photo-' + str(i), 'description': 'Test'} for i in range(9)]}
    assert request('POST', '/api/v1/campsites', camp)[0] == 403
    assert request('POST', '/api/v1/campsites', camp, token)[0] == 403
    suggestion = {'name': 'Suggested Camp', 'instagramUrl': 'https://instagram.com/camp/', 'googleMapsUrl': 'https://maps.app.goo.gl/example'}
    assert request('POST', '/api/v1/campsite-suggestions', suggestion)[0] == 403
    assert request('POST', '/api/v1/campsite-suggestions', dict(suggestion, googleMapsUrl=''), token)[0] == 400
    code, receipt = request('POST', '/api/v1/campsite-suggestions', dict(suggestion, ownerUuid=user_uuid), token)
    assert code == 201 and receipt['status'] == 'pending_review'
    assert sql("SELECT count(*) FROM kampive.campsite") == '2'
    assert request('GET', '/api/v1/users/me/campsites')[1]['totalElements'] == 0
    request_path = '/api/v1/users/me/requests/suggestion/' + receipt['uuid']
    tracking = request('GET', '/api/v1/users/me/requests')[1]
    assert tracking['totalElements'] == 1 and tracking['items'][0]['uuid'] == receipt['uuid']
    detail = request('GET', request_path)[1]
    assert detail['summary']['status'] == 'pending_review' and detail['response'] is None
    assert detail['instagramUrl'] == suggestion['instagramUrl'] and detail['googleMapsUrl'] == suggestion['googleMapsUrl']
    assert detail['summary']['publishedCampsiteUuid'] is None
    for query in ['size=51', 'size=0', 'page=-1', 'page=10001']:
        assert request('GET', '/api/v1/users/me/requests?' + query)[0] == 400
    assert request('GET', '/api/v1/users/me/requests/unknown/' + receipt['uuid'])[0] == 404
    assert request('GET', '/api/v1/users/me/requests/claim/' + receipt['uuid'])[0] == 404
    reviewer = str(uuid.uuid4())
    sql(f"INSERT INTO kampive.app_user(uuid_app_user,name,email) VALUES ('{reviewer}','Reviewer','reviewer@example.test');")
    camp_uuid = sql(f"SELECT kampive.accept_campsite_suggestion('{receipt['uuid']}','{reviewer}','{category}','{city}','Checked location');")
    assert sql(f"SELECT uuid_owner IS NULL FROM kampive.campsite WHERE uuid_campsite='{camp_uuid}'") == 't'
    assert request('GET', '/api/v1/campsites/' + camp_uuid)[0] == 404
    assert request('GET', '/api/v1/users/me/campsites/' + camp_uuid)[0] == 404
    claim_body = {'contact': 'test@example.test', 'evidence': 'Please verify my ownership'}
    assert request('POST', '/api/v1/campsites/' + camp_uuid + '/claims', claim_body, token)[0] == 409
    sql(f"UPDATE kampive.campsite SET street_address='Test Street',latitude=0,longitude=0,status='published',verified_at=now(),uuid_verified_by='{reviewer}' WHERE uuid_campsite='{camp_uuid}';")
    assert request('GET', '/api/v1/campsites/' + camp_uuid)[1]['summary']['verified']
    code, claim = request('POST', '/api/v1/campsites/' + camp_uuid + '/claims', claim_body, token)
    assert code == 201 and claim['status'] == 'pending_review'
    claim_path = '/api/v1/users/me/requests/claim/' + claim['uuid']
    claim_detail = request('GET', claim_path)[1]
    assert claim_detail['contact'] == claim_body['contact'] and claim_detail['evidence'] == claim_body['evidence']
    assert claim_detail['summary']['publishedCampsiteUuid'] == camp_uuid
    assert request('POST', '/api/v1/campsites/' + camp_uuid + '/claims', claim_body, token)[1]['uuid'] == claim['uuid']
    assert sql(f"SELECT count(*) FROM kampive.admin_request_email WHERE request_type='claim' AND request_uuid='{claim['uuid']}'") == '1'
    assert sql(f"SELECT count(*) FROM kampive.admin_request_email WHERE request_type='suggestion' AND request_uuid='{receipt['uuid']}'") == '1'

    assert request('GET', '/api/v1/users/me/campsites/' + camp_uuid)[0] == 404
    assert request('PUT', '/api/v1/campsites/' + camp_uuid, camp, token)[0] == 404
    assert request('POST', '/api/v1/campsites/' + camp_uuid + '/archive', token=token)[0] == 404
    # The application database role must not approve its own requests.
    try:
        sql(f"SET ROLE kampive_app; SELECT kampive.approve_campsite_claim('{claim['uuid']}','{reviewer}','forbidden');")
        raise AssertionError('Application role could approve ownership')
    except RuntimeError:
        pass
    try:
        sql(f"SELECT kampive.approve_campsite_claim('{claim['uuid']}','{user_uuid}','self approval');")
        raise AssertionError('Claimant could review their own ownership')
    except RuntimeError:
        pass
    sql(f"SELECT kampive.approve_campsite_claim('{claim['uuid']}','{reviewer}','Ownership checked');")
    assert request('GET', '/api/v1/users/me/campsites/' + camp_uuid)[0] == 200
    assert request('GET', '/api/v1/users/me/campsites')[1]['totalElements'] == 1
    assert request('GET', claim_path)[1]['summary']['status'] == 'approved'
    assert sql(f"SELECT count(*) FROM kampive.notification WHERE request_uuid='{claim['uuid']}' AND request_type='claim'") == '1'
    assert request('POST', '/api/v1/campsites/' + camp_uuid + '/claims', claim_body, token)[0] == 409
    # Continue validating the existing verified-owner editing workflow.
    sql(f"UPDATE kampive.campsite SET status='draft' WHERE uuid_campsite='{camp_uuid}';")
    assert request('PUT', '/api/v1/campsites/' + camp_uuid, camp, token)[0] == 200
    for invalid in [dict(camp, photos=camp['photos'] + [{'url': 'https://example.test/extra'}]),
                    dict(camp, photos=[{'url': 'file:///tmp/test.jpg'}]),
                    dict(camp, startingPricePerNight=-1), dict(camp, amenityUuids=[str(uuid.uuid4())])]:
        assert request('PUT', '/api/v1/campsites/' + camp_uuid, invalid, token)[0] == 400
    assert sql("SELECT count(*) FROM kampive.campsite") == '3'
    # A failure after deleting/replacing children rolls back the entire update.
    sql("CREATE FUNCTION kampive.fail_test_photo() RETURNS trigger LANGUAGE plpgsql AS $$ "
        "BEGIN RAISE EXCEPTION 'test constraint' USING ERRCODE='23514'; END $$; "
        "CREATE TRIGGER fail_test_photo BEFORE INSERT ON kampive.campsite_photo "
        "FOR EACH ROW EXECUTE FUNCTION kampive.fail_test_photo();")
    assert request('PUT', '/api/v1/campsites/' + camp_uuid, dict(camp, name='Must roll back'), token)[0] == 400
    sql('DROP TRIGGER fail_test_photo ON kampive.campsite_photo; DROP FUNCTION kampive.fail_test_photo();')
    restored = request('GET', '/api/v1/users/me/campsites/' + camp_uuid)[1]
    assert restored['content']['name'] == camp['name'] and len(restored['content']['photos']) == 9
    # Community data is persisted in this disposable DB, never populated in development.
    published_uuid = sql("SELECT uuid_campsite FROM kampive.campsite WHERE name='Published site';")
    favorite_path = '/api/v1/users/me/favorites/' + published_uuid
    assert request('GET', '/api/v1/users/me/favorites') == (200, [])
    assert request('PUT', favorite_path)[0] == 403
    assert request('PUT', favorite_path, token=token)[0] == 204
    assert request('PUT', favorite_path, token=token)[0] == 204
    assert request('GET', '/api/v1/users/me/favorites') == (200, [published_uuid])
    assert sql(f"SELECT count(*) FROM kampive.favorite WHERE uuid_app_user='{user_uuid}'") == '1'
    assert request('PUT', '/api/v1/users/me/favorites/' + camp_uuid, token=token)[0] == 404
    result_notifications = request('GET', '/api/v1/users/me/notifications')[1]['items']
    assert any(n['requestUuid'] == receipt['uuid'] and n['requestType'] == 'suggestion' for n in result_notifications)
    assert 'Checked location' not in json.dumps(result_notifications)
    assert request('POST', '/api/v1/users/me/notifications/read-all', token=token)[0] == 204
    notification_uuid, inbox_uuid, highlight_uuid, method_uuid = [str(uuid.uuid4()) for _ in range(4)]
    sql(f"INSERT INTO kampive.notification(uuid_notification,title,message) VALUES ('{notification_uuid}','Test only','Message in disposable DB');"
        f"INSERT INTO kampive.user_notification(uuid_user_notification,uuid_notification,uuid_app_user) VALUES ('{inbox_uuid}','{notification_uuid}','{user_uuid}');"
        f"INSERT INTO kampive.review_highlight(uuid_review_highlight,name) VALUES ('{highlight_uuid}','Test highlight');"
        f"INSERT INTO kampive.camping_method(uuid_camping_method,name) VALUES ('{method_uuid}','Test method');")
    inbox = request('GET', '/api/v1/users/me/notifications?filter=unread&size=1')[1]
    assert inbox['unreadCount'] == 1 and inbox['totalElements'] == 1 and inbox['items'][0]['uuid'] == inbox_uuid
    assert request('PUT', '/api/v1/users/me/notifications/' + inbox_uuid, {'read': True}, token)[0] == 204
    assert request('GET', '/api/v1/users/me/notifications?filter=unread')[1]['items'] == []
    assert request('PUT', '/api/v1/users/me/notifications/' + inbox_uuid, {'read': False}, token)[0] == 204
    assert request('POST', '/api/v1/users/me/notifications/read-all', token=token)[0] == 204
    assert request('GET', '/api/v1/users/me/notifications')[1]['unreadCount'] == 0
    for query in ['size=51', 'page=-1', 'filter=invalid']:
        assert request('GET', '/api/v1/users/me/notifications?' + query)[0] == 400
    review_path = '/api/v1/campsites/' + published_uuid + '/reviews'
    review_body = {'rating': 5, 'text': 'A real integration test comment with at least fifty characters.',
                   'campingMethodUuid': method_uuid, 'highlightUuids': [highlight_uuid], 'photos': []}
    assert request('GET', '/api/v1/review-options')[1]['highlights'][0]['uuid'] == highlight_uuid
    assert request('GET', review_path)[1]['items'] == []
    assert request('POST', review_path, review_body)[0] == 403
    assert request('POST', review_path, dict(review_body, rating=0), token)[0] == 400
    assert request('POST', review_path, dict(review_body, highlightUuids=[str(uuid.uuid4())]), token)[0] == 400
    assert request('POST', review_path, dict(review_body, photos=['file:///tmp/photo']), token)[0] == 400
    assert sql(f"SELECT count(*) FROM kampive.review WHERE uuid_app_user='{user_uuid}'") == '0'
    code, review_receipt = request('POST', review_path, dict(review_body, status='published'), token)
    assert code == 201 and review_receipt['status'] == 'pending'
    assert sql(f"SELECT count(*) FROM kampive.admin_request_email WHERE request_type='review' AND request_uuid='{review_receipt['uuid']}'") == '1'
    assert request('POST', review_path, review_body, token)[0] == 409
    assert request('GET', review_path)[1]['items'] == []
    assert request('GET', '/api/v1/users/me/reviews')[1]['items'][0]['status'] == 'pending'
    assert request('GET', '/api/v1/campsites/' + published_uuid)[1]['summary']['reviewCount'] == 0
    sql(f"UPDATE kampive.review SET status='published' WHERE uuid_review='{review_receipt['uuid']}';")
    assert request('GET', review_path)[1]['items'][0]['highlights'] == ['Test highlight']
    assert request('GET', '/api/v1/campsites/' + published_uuid)[1]['summary']['reviewCount'] == 1
    own_review_path = '/api/v1/users/me/reviews/' + review_receipt['uuid']
    editable = request('GET', own_review_path)[1]
    assert editable['input']['campingMethodUuid'] == method_uuid
    assert editable['input']['highlightUuids'] == [highlight_uuid]
    assert request('PUT', own_review_path, review_body)[0] == 403
    assert request('DELETE', own_review_path)[0] == 403
    import base64, struct, zlib
    def png_chunk(kind, data):
        return struct.pack('!I', len(data)) + kind + data + struct.pack('!I', zlib.crc32(kind + data) & 0xffffffff)
    png = b'\x89PNG\r\n\x1a\n' + png_chunk(b'IHDR', struct.pack('!IIBBBBB', 1, 1, 8, 2, 0, 0, 0)) + png_chunk(b'IDAT', zlib.compress(b'\x00\xff\x00\x00')) + png_chunk(b'IEND', b'')
    edited_body = dict(review_body, rating=3, text='Updated camping experience with useful details and at least fifty characters.', photos=['data:image/png;base64,' + base64.b64encode(png).decode()])
    assert request('PUT', own_review_path, dict(edited_body, status='published'), token)[1]['status'] == 'pending'
    assert sql(f"SELECT count(*) FROM kampive.admin_request_email WHERE request_type='review' AND request_uuid='{review_receipt['uuid']}'") == '2'
    assert request('GET', review_path)[1]['items'] == []
    summary = request('GET', '/api/v1/campsites/' + published_uuid)[1]['summary']
    assert summary['reviewCount'] == 0 and summary['averageRating'] is None
    saved = request('GET', own_review_path)[1]['input']
    assert saved['rating'] == 3 and len(saved['photos']) == 1 and saved['photos'][0].startswith('data:image/jpeg;base64,')
    sql(f"UPDATE kampive.review SET status='published' WHERE uuid_review='{review_receipt['uuid']}';")
    assert request('GET', '/api/v1/campsites/' + published_uuid)[1]['summary']['averageRating'] == 3
    assert request('PUT', own_review_path, dict(edited_body, highlightUuids=[str(uuid.uuid4())]), token)[0] == 400
    # Failure while replacing children rolls back content and moderation status.
    sql("CREATE FUNCTION kampive.fail_review_edit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test' USING ERRCODE='23514'; END; $$; CREATE TRIGGER fail_review_edit BEFORE INSERT ON kampive.review_photo FOR EACH ROW EXECUTE FUNCTION kampive.fail_review_edit();")
    assert request('PUT', own_review_path, dict(edited_body, rating=1), token)[0] == 500
    sql('DROP TRIGGER fail_review_edit ON kampive.review_photo; DROP FUNCTION kampive.fail_review_edit();')
    assert request('GET', own_review_path)[1]['input'] == saved
    assert sql(f"SELECT count(*) FROM kampive.admin_request_email WHERE request_type='review' AND request_uuid='{review_receipt['uuid']}'") == '2'
    assert request('GET', '/api/v1/campsites/' + published_uuid)[1]['summary']['averageRating'] == 3
    for query in ['size=51', 'page=-1']:
        assert request('GET', review_path + '?' + query)[0] == 400
    assert request('GET', '/api/v1/campsites/' + camp_uuid + '/reviews')[0] == 404
    # Public responses are explicit; internal moderation notes never leave the database.
    sql(f"UPDATE kampive.campsite_suggestion SET public_response='Obrigado pela indicação.', review_notes='INTERNAL SECRET' WHERE uuid_campsite_suggestion='{receipt['uuid']}';")
    detail = request('GET', request_path)[1]
    assert detail['response'] == 'Obrigado pela indicação.' and 'INTERNAL SECRET' not in json.dumps(detail)
    assert detail['summary']['publishedCampsiteUuid'] is None
    sql(f"UPDATE kampive.campsite_suggestion SET status=status WHERE uuid_campsite_suggestion='{receipt['uuid']}';")
    assert sql(f"SELECT count(*) FROM kampive.notification WHERE request_uuid='{receipt['uuid']}'") == '1'
    first = request('GET', '/api/v1/users/me/requests?size=1')[1]
    second = request('GET', '/api/v1/users/me/requests?size=1&page=1')[1]
    assert first['totalElements'] >= 2 and first['items'][0]['uuid'] != second['items'][0]['uuid']
    # Rejection creates a single linked notification atomically; rollback creates none.
    rejected_id = request('POST', '/api/v1/campsite-suggestions', suggestion, token)[1]['uuid']
    decision = f"UPDATE kampive.campsite_suggestion SET status='rejected',uuid_reviewed_by='{reviewer}',reviewed_at=now(),public_response='Local não confirmado',review_notes='PRIVATE' WHERE uuid_campsite_suggestion='{rejected_id}';"
    sql('BEGIN;' + decision + 'ROLLBACK;')
    assert sql(f"SELECT count(*) FROM kampive.notification WHERE request_uuid='{rejected_id}'") == '0'
    sql(decision)
    rejected_detail = request('GET', '/api/v1/users/me/requests/suggestion/' + rejected_id)[1]
    assert rejected_detail['summary']['status'] == 'rejected' and rejected_detail['response'] == 'Local não confirmado'
    assert 'PRIVATE' not in json.dumps(rejected_detail)
    assert sql(f"SELECT count(*) FROM kampive.notification WHERE request_uuid='{rejected_id}'") == '1'
    # Another account cannot read or mutate this owner's unpublished site.
    other = dict(body, name='Other Owner', email='other-owner@example.test')
    assert request('POST', '/api/v1/auth/register', other, token)[0] == 201
    sql("UPDATE kampive.app_user SET email_verified_at=clock_timestamp() WHERE email='other-owner@example.test'")
    assert request('POST', '/api/v1/auth/logout', token=token)[0] == 204
    token = request('GET', '/api/v1/auth/csrf')[1]['token']
    assert request('POST', '/api/v1/auth/login', {'email': other['email'], 'password': other['password']}, token)[0] == 200
    token = request('GET', '/api/v1/auth/csrf')[1]['token']
    assert request('GET', '/api/v1/users/me/requests')[1]['items'] == []
    assert request('GET', request_path)[0] == 404
    assert request('GET', claim_path)[0] == 404
    assert request('GET', '/api/v1/users/me/favorites') == (200, [])
    assert request('GET', '/api/v1/users/me/reviews')[1]['items'] == []
    assert request('GET', own_review_path)[0] == 404
    assert request('PUT', own_review_path, edited_body, token)[0] == 404
    assert request('DELETE', own_review_path, token=token)[0] == 404
    assert request('GET', '/api/v1/users/me/notifications')[1]['items'] == []
    assert request('PUT', '/api/v1/users/me/notifications/' + inbox_uuid, {'read': False}, token)[0] == 404
    assert request('DELETE', favorite_path, token=token)[0] == 204
    assert sql(f"SELECT count(*) FROM kampive.favorite WHERE uuid_app_user='{user_uuid}'") == '1'
    assert request('GET', '/api/v1/users/me/campsites/' + camp_uuid)[0] == 404
    assert request('PUT', '/api/v1/campsites/' + camp_uuid, camp, token)[0] == 404
    for action in ['submit', 'archive']:
        assert request('POST', '/api/v1/campsites/' + camp_uuid + '/' + action, token=token)[0] == 404
    assert request('POST', '/api/v1/auth/logout', token=token)[0] == 204
    token = request('GET', '/api/v1/auth/csrf')[1]['token']
    assert request('POST', '/api/v1/auth/login', login, token)[0] == 200
    token = request('GET', '/api/v1/auth/csrf')[1]['token']
    # Owner removal cleans child records and updates the public aggregate immediately.
    assert request('DELETE', own_review_path, token=token)[0] == 204
    assert request('GET', own_review_path)[0] == 404
    assert request('DELETE', own_review_path, token=token)[0] == 404
    assert sql(f"SELECT count(*) FROM kampive.review_photo WHERE uuid_review='{review_receipt['uuid']}'") == '0'
    assert sql(f"SELECT count(*) FROM kampive.review_highlight_selection WHERE uuid_review='{review_receipt['uuid']}'") == '0'
    assert request('GET', '/api/v1/campsites/' + published_uuid)[1]['summary']['reviewCount'] == 0
    assert request('POST', review_path, review_body, token)[0] == 201
    assert request('GET', '/api/v1/users/me/favorites') == (200, [published_uuid])
    assert request('DELETE', favorite_path, token=token)[0] == 204
    assert request('GET', '/api/v1/users/me/favorites') == (200, [])
    assert request('PUT', '/api/v1/campsites/' + camp_uuid, dict(camp, photos=camp['photos'][:2]), token)[0] == 200
    # Concurrent submissions serialize on the owned row: exactly one can transition.
    from concurrent.futures import ThreadPoolExecutor
    def submit_once(_):
        cookie = '; '.join(c.name + '=' + c.value for c in jar)
        req = urllib.request.Request(base + '/api/v1/campsites/' + camp_uuid + '/submit', method='POST',
                                     headers={'Cookie': cookie, 'X-CSRF-TOKEN': token})
        try:
            with urllib.request.urlopen(req, timeout=10) as response:
                return response.status
        except urllib.error.HTTPError as error:
            return error.code
    with ThreadPoolExecutor(max_workers=2) as pool:
        assert sorted(pool.map(submit_once, range(2))) == [200, 409]
    assert request('POST', '/api/v1/campsites/' + camp_uuid + '/submit', token=token)[0] == 409
    assert sql(f"SELECT count(*) FROM kampive.admin_request_email WHERE request_type='registration' AND request_uuid='{camp_uuid}'") == '1'

    assert request('PUT', '/api/v1/campsites/' + camp_uuid, camp, token)[0] == 409
    assert request('GET', '/api/v1/campsites/' + camp_uuid)[0] == 404
    # Publication is an administrative action; never exposed to the owner.
    sql(f"UPDATE kampive.campsite SET status='published' WHERE uuid_campsite='{camp_uuid}';")
    assert request('GET', '/api/v1/campsites/' + camp_uuid)[0] == 200
    assert request('GET', request_path)[1]['summary']['publishedCampsiteUuid'] == camp_uuid
    assert request('GET', '/api/v1/campsites?countryUuid=' + brazil + '&amenityUuid=' + amenity + '&settingUuid=' + setting)[1]['totalElements'] == 1
    assert request('GET', '/api/v1/campsites?countryUuid=' + portugal)[1]['totalElements'] == 0
    assert request('GET', '/api/v1/campsites?size=1')[1]['totalPages'] == 2
    assert request('GET', '/api/v1/campsites?page=1&size=1')[1]['items']
    for search in ['%25', '_', '%27%20OR%201%3D1--']:
        assert request('GET', '/api/v1/campsites?query=' + search)[1]['totalElements'] == 0
    assert request('PUT', '/api/v1/campsites/' + camp_uuid, camp, token)[0] == 409
    assert request('POST', '/api/v1/campsites/' + camp_uuid + '/archive', token=token)[0] == 200
    assert request('GET', '/api/v1/campsites/' + camp_uuid)[0] == 404
    assert request('GET', '/api/v1/countries/' + brazil)[1]['campsiteCount'] == 1
    assert request('GET', request_path)[1]['summary']['publishedCampsiteUuid'] is None
    before_jobs = sql('SELECT count(*) FROM kampive.admin_request_email')
    sql(f"BEGIN; INSERT INTO kampive.campsite_suggestion(uuid_suggested_by,name,location,contact,notes) VALUES ('{user_uuid}','Rollback test','location','contact',''); ROLLBACK;")
    assert sql('SELECT count(*) FROM kampive.admin_request_email') == before_jobs
    for setting in ["request_email_enabled=false", "revoked_at=now()"]:
        result = sql(f"BEGIN; UPDATE kampive.administrator SET {setting} WHERE uuid_app_user='{user_uuid}'; INSERT INTO kampive.campsite_suggestion(uuid_suggested_by,name,location,contact,notes) VALUES ('{user_uuid}','Disabled test','location','contact',''); SELECT count(*) FROM kampive.admin_request_email; ROLLBACK;")
        assert before_jobs in result.splitlines()
    print('PASS: admin authorization, live revocation, verified membership, transactional alert outbox, deduplication, resubmissions and rollback.', flush=True)
    import importlib.util
    spec=importlib.util.spec_from_file_location('manager_check',ROOT/'scripts/verify-manager.py')
    manager_check=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(manager_check)
    manager_check.verify(sql,request,user_uuid,category,city,token)
    # Recovery delivery is explicitly unavailable until a real provider is configured.
    for address in [body['email'], 'unknown@example.test']:
        assert request('POST', '/api/v1/auth/password-recovery', {'email': address}, token)[0] == 503
    assert sql("SELECT count(*) FROM kampive.password_email_job") == '0'
    import hashlib
    reset_secret, expired_secret = secrets.token_urlsafe(32), secrets.token_urlsafe(32)
    def recovery_token(secret, expiry):
        digest = hashlib.sha256(secret.encode()).hexdigest()
        sql(f"INSERT INTO kampive.password_recovery(uuid_app_user,token_hash,expires_at) VALUES ('{user_uuid}','{digest}',clock_timestamp()+interval '{expiry}');")
    recovery_token(reset_secret, '30 minutes')
    recovery_token(expired_secret, '-1 minute')
    assert request('POST', '/api/v1/auth/password-reset', {'token': expired_secret, 'password': 'New integration password 8'}, token)[0] == 400
    assert request('POST', '/api/v1/auth/password-reset', {'token': reset_secret, 'password': 'short'}, token)[0] == 400
    def anonymous_reset(_):
        opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        with opener.open(base + '/api/v1/auth/csrf') as response:
            csrf = json.loads(response.read())['token']
        req = urllib.request.Request(base + '/api/v1/auth/password-reset', method='POST',
            headers={'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf},
            data=json.dumps({'token': reset_secret, 'password': 'New integration password 8'}).encode())
        try:
            with opener.open(req) as response: return response.status
        except urllib.error.HTTPError as error: return error.code
    with ThreadPoolExecutor(max_workers=2) as pool:
        assert sorted(pool.map(anonymous_reset, range(2))) == [204, 400]
    # The old logged-in session is rejected on its very next request.
    assert request('GET', '/api/v1/users/me')[0] == 401
    assert sql(f"SELECT count(*) FROM kampive.password_email_job WHERE uuid_app_user='{user_uuid}' AND template_code='password-changed'") == '1'
    assert sql(f"SELECT count(*) FROM kampive.password_recovery WHERE uuid_app_user='{user_uuid}' AND consumed_at IS NULL") == '0'
    token = request('GET', '/api/v1/auth/csrf')[1]['token']
    assert request('POST', '/api/v1/auth/password-reset', {'token': reset_secret, 'password': 'Another new password 9'}, token)[0] == 400
    old_login_status = request('POST', '/api/v1/auth/login', login, token)[0]
    if old_login_status == 429:
        print('Waiting for the authentication rate-limit window before verifying the changed password.', flush=True)
        time.sleep(60)
        old_login_status = request('POST', '/api/v1/auth/login', login, token)[0]
    assert old_login_status == 401, ('old password status', old_login_status)
    login = dict(login, password='New integration password 8')
    assert request('POST', '/api/v1/auth/login', login, token)[0] == 200
    token = request('GET', '/api/v1/auth/csrf')[1]['token']
    assert request('GET', '/api/v1/users/me')[0] == 200
    sql(f"UPDATE kampive.app_user SET deactivated_at=now() WHERE uuid_app_user='{user_uuid}';")
    assert request('GET', '/api/v1/users/me/campsites')[0] == 401
    for path in ['favorites', 'notifications', 'reviews', 'requests']:
        assert request('GET', '/api/v1/users/me/' + path)[0] == 401
    token = request('GET', '/api/v1/auth/csrf')[1]['token']
    assert request('POST', '/api/v1/campsite-suggestions', suggestion, token)[0] == 401
    sql(f"UPDATE kampive.app_user SET deactivated_at=NULL WHERE uuid_app_user='{user_uuid}';")
    token = request('GET', '/api/v1/auth/csrf')[1]['token']
    codes = [request('POST', '/api/v1/auth/login', dict(login, password='incorrect password'), token)[0] for _ in range(10)]
    assert 429 in codes
    print('PASS: favorites persistence and isolation, notification read states, review moderation and validation, auth, geography, suggestions, unowned publication, reviewed ownership, campsite lifecycle, CSRF, photo limits, atomic rollback, publication visibility, filtering and pagination.')
finally:
    for kind, name in reversed(created):
        if kind == 'container':
            subprocess.run(['docker', 'rm', '-f', name], capture_output=True)
        else:
            subprocess.run(['docker', 'network', 'rm', name], capture_output=True)

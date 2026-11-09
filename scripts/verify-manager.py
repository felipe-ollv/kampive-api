"""Called only by verify-auth.py against its disposable database and API."""
def verify(sql, request, actor, category, city, token):
    def listing(collection):
        code, page = request('GET', '/api/v1/admin/' + collection + '?size=100')
        assert code == 200, (collection, code)
        return page['items']
    assert request('GET','/api/v1/admin/requests?page=-1')[0] == 400
    for kind in ['requests','campings','users','reviews']:
        listing(kind)
    # Bounded pages, stable ties, search before LIMIT, and page correction after filtering.
    for invalid in ['size=0','size=101','page=-1','from=invalid']:
        assert request('GET','/api/v1/admin/campings?'+invalid)[0] == 400
    sql(f"INSERT INTO kampive.campsite_suggestion(name,uuid_suggested_by,location,contact) SELECT 'Paginação fixture '||lpad(n::text,3,'0'),'{actor}', 'https://maps.app.goo.gl/test','https://instagram.com/test/' FROM generate_series(1,31) n")
    pages=[]
    for n in range(4):
        code, result=request('GET',f'/api/v1/admin/requests?query=paginacao&size=10&page={n}')
        assert code==200,code
        assert result['totalElements']==31 and result['totalPages']==4, result
        assert len(result['items'])==(1 if n==3 else 10)
        pages.extend(row['id'] for row in result['items'])
    assert len(set(pages))==31
    filtered=request('GET','/api/v1/admin/requests?query=paginacao%20fixture%20031&size=10&page=99')[1]
    assert filtered['totalElements']==1 and filtered['page']==0
    assert request('GET','/api/v1/admin/requests?query=does-not-exist')[1]['totalElements']==0
    assert request('GET',f'/api/v1/admin/requests?query=paginacao&userId={actor}')[1]['totalElements']==31
    assert request('GET','/api/v1/admin/requests?query=paginacao&status=accepted')[1]['totalElements']==0
    print('PASS: pagination limits, distinct stable pages, accent-insensitive search, filtered totals, empty results and page correction.',flush=True)
    def decision(kind, uid, revision, accept, **extra):
        return request('POST',f'/api/v1/admin/requests/{kind}/{uid}/decision',dict(revision=revision,accept=accept,notes='Private audit note',response='Resposta da equipe.',**extra),token)
    proposal={'name':'Manager integration campsite','instagramUrl':'https://instagram.com/manager_test/','googleMapsUrl':'https://maps.app.goo.gl/test'}
    code, receipt=request('POST','/api/v1/campsite-suggestions',proposal,token)
    assert code==201
    uid=receipt['uuid']
    assert decision('suggestion',uid,0,True,categoryUuid=category,cityUuid=city)[0]==204
    assert decision('suggestion',uid,0,True,categoryUuid=category,cityUuid=city)[0]==409
    camp_id=sql(f"SELECT uuid_campsite FROM kampive.campsite_suggestion WHERE uuid_campsite_suggestion='{uid}'")
    assert sql(f"SELECT uuid_owner IS NULL AND status='draft' FROM kampive.campsite WHERE uuid_campsite='{camp_id}'")=='t'
    camp=request('GET',f'/api/v1/admin/campings?id={camp_id}')[1]['items'][0]
    contact=sql('SELECT uuid_contact_type FROM kampive.contact_type ORDER BY id LIMIT 1')
    content=dict(camp['content'],description='Dados conferidos pela equipe.',streetAddress='Rua do Teste',latitude=-22.5,longitude=-45.1,contacts=[dict(typeUuid=contact,value='https://example.test')],photos=[dict(url='https://example.test/camping.jpg',description='Foto do camping')])
    saved=request('PUT',f'/api/v1/admin/campings/{camp_id}',dict(revision=camp['revision'],content=content),token)
    assert saved[0]==200, saved[0]
    assert request('PUT',f'/api/v1/admin/campings/{camp_id}',dict(revision=camp['revision'],content=content),token)[0]==409
    camp=request('GET',f'/api/v1/admin/campings?id={camp_id}')[1]['items'][0]
    assert request('POST',f'/api/v1/admin/campings/{camp_id}/status',dict(revision=camp['revision'],status='published'),token)[0]==204
    assert request('GET',f'/api/v1/campsites/{camp_id}')[0]==200
    # Neither client payload nor a normal profile update grants admin access.
    sql(f"UPDATE kampive.administrator SET revoked_at=now() WHERE uuid_app_user='{actor}'")
    for collection in ['requests','campings','users','reviews']:
        assert request('GET','/api/v1/admin/'+collection)[0]==403
    assert request('POST',f'/api/v1/admin/campings/{camp_id}/status',dict(revision=0,status='archived'),token)[0]==403
    sql(f"UPDATE kampive.administrator SET revoked_at=NULL WHERE uuid_app_user='{actor}'")
    # A verified administrator cannot approve their own ownership request.
    code, own=request('POST',f'/api/v1/campsites/{camp_id}/claims',dict(contact='test@example.test',evidence='Test ownership'),token)
    assert code==201
    assert decision('claim',own['uuid'],0,True)[0]==409
    assert decision('claim',own['uuid'],0,False)[0]==204
    # A complete community registration is published only via analysis.
    code, reg=request('POST','/api/v1/campsite-registrations',dict(content,name='Registration for manager test'),token)
    assert code==201, code
    assert decision('registration',reg['uuid'],0,True)[0]==204
    assert sql(f"SELECT status FROM kampive.campsite WHERE uuid_campsite='{reg['uuid']}'")=='published'
    code, review=request('POST',f'/api/v1/campsites/{camp_id}/reviews',dict(rating=4,text='Uma experiência agradável e tranquila com infraestrutura bem conservada.',highlightUuids=[],photos=[]),token)
    assert code==201,code
    assert request('POST',f"/api/v1/admin/reviews/{review['uuid']}/status",dict(revision=0,status='published'),token)[0]==204
    assert request('POST',f"/api/v1/admin/reviews/{review['uuid']}/status",dict(revision=0,status='hidden'),token)[0]==409
    assert request('POST',f"/api/v1/admin/reviews/{review['uuid']}/status",dict(revision=1,status='hidden'))[0]==403
    assert sql(f"SELECT count(*) FROM kampive.admin_audit WHERE actor='{actor}'") != '0'
    assert sql("SELECT NOT has_table_privilege('kampive_app','kampive.admin_audit','INSERT') AND NOT has_table_privilege('kampive_app','kampive.admin_audit','UPDATE')")=='t'
    assert request('GET',f'/api/v1/users/me/requests/suggestion/{uid}')[1]['response']=='Resposta da equipe.'
    print('PASS: manager queries, decisions, persistent content, public response, admin-only access, CSRF, independent ownership, audit and stale-write protection.',flush=True)

package no.jamph.bigquery

class BigQuerySchemaServiceMock {

    fun getSchemaContext(): String = """
=== BIGQUERY DATABASE SCHEMA ===
Project: fagtorsdag-prod-81a6
Dataset: umami_student

=== AVAILABLE WEBSITES ===
- Aksel (ID: fb69e1e9-1bd3-4fd9-b700-9d035cbf44e1, Domain: aksel.nav.no)
- NAV (ID: aa000001-0000-0000-0000-000000000001, Domain: nav.no)
- Skatteetaten (ID: aa000002-0000-0000-0000-000000000002, Domain: skatteetaten.no)
- Altinn (ID: aa000003-0000-0000-0000-000000000003, Domain: altinn.no)
- Helsenorge (ID: aa000004-0000-0000-0000-000000000004, Domain: helsenorge.no)
- Regjeringen (ID: aa000005-0000-0000-0000-000000000005, Domain: regjeringen.no)
- Statens vegvesen (ID: aa000006-0000-0000-0000-000000000006, Domain: vegvesen.no)
- UDI (ID: aa000007-0000-0000-0000-000000000007, Domain: udi.no)
- Brønnøysundregistrene (ID: aa000008-0000-0000-0000-000000000008, Domain: brreg.no)
- Lånekassen (ID: aa000009-0000-0000-0000-000000000009, Domain: lanekassen.no)
- SSB (ID: aa000010-0000-0000-0000-000000000010, Domain: ssb.no)
- Politiet (ID: aa000011-0000-0000-0000-000000000011, Domain: politiet.no)
- Tolletaten (ID: aa000012-0000-0000-0000-000000000012, Domain: toll.no)
- Mattilsynet (ID: aa000013-0000-0000-0000-000000000013, Domain: mattilsynet.no)
- Arbeidstilsynet (ID: aa000014-0000-0000-0000-000000000014, Domain: arbeidstilsynet.no)
- Husbanken (ID: aa000015-0000-0000-0000-000000000015, Domain: husbanken.no)
- Datatilsynet (ID: aa000016-0000-0000-0000-000000000016, Domain: datatilsynet.no)
- Folkehelseinstituttet (ID: aa000017-0000-0000-0000-000000000017, Domain: fhi.no)
- Helsedirektoratet (ID: aa000018-0000-0000-0000-000000000018, Domain: helsedirektoratet.no)
- Kartverket (ID: aa000019-0000-0000-0000-000000000019, Domain: kartverket.no)
- NVE (ID: aa000020-0000-0000-0000-000000000020, Domain: nve.no)
- Meteorologisk institutt (ID: aa000021-0000-0000-0000-000000000021, Domain: met.no)
- Patentstyret (ID: aa000022-0000-0000-0000-000000000022, Domain: patentstyret.no)
- Riksrevisjonen (ID: aa000023-0000-0000-0000-000000000023, Domain: riksrevisjonen.no)
- Språkrådet (ID: aa000024-0000-0000-0000-000000000024, Domain: sprakradet.no)
- Statsforvalteren (ID: aa000025-0000-0000-0000-000000000025, Domain: statsforvalteren.no)
- Utdanning.no (ID: aa000026-0000-0000-0000-000000000026, Domain: utdanning.no)
- Miljødirektoratet (ID: aa000027-0000-0000-0000-000000000027, Domain: miljodirektoratet.no)
- Fiskeridirektoratet (ID: aa000028-0000-0000-0000-000000000028, Domain: fiskeridir.no)
- Bufdir (ID: aa000029-0000-0000-0000-000000000029, Domain: bufdir.no)
- Kystverket (ID: aa000030-0000-0000-0000-000000000030, Domain: kystverket.no)
- Sivilforsvaret (ID: aa000031-0000-0000-0000-000000000031, Domain: sivilforsvaret.no)

=== DATABASE TABLES ===

Table: `fagtorsdag-prod-81a6.umami_student.session`
Columns:
  - session_id (STRING, NULLABLE)
  - website_id (STRING, NULLABLE)
  - hostname (STRING, NULLABLE)
  - browser (STRING, NULLABLE)
  - os (STRING, NULLABLE)
  - device (STRING, NULLABLE)
  - screen (STRING, NULLABLE)
  - language (STRING, NULLABLE)
  - country (STRING, NULLABLE)
  - created_at (TIMESTAMP, NULLABLE)
  - session_parameters (ARRAY<STRUCT<...>>, REQUIRED)

Table: `fagtorsdag-prod-81a6.umami_student.event`
Columns:
  - event_id (STRING, REQUIRED)
  - website_id (STRING, NULLABLE)
  - session_id (STRING, NULLABLE)
  - created_at (TIMESTAMP, NULLABLE)
  - url_path (STRING, NULLABLE)
  - url_query (STRING, NULLABLE)
  - referrer_path (STRING, NULLABLE)
  - referrer_query (STRING, NULLABLE)
  - referrer_domain (STRING, NULLABLE)
  - page_title (STRING, NULLABLE)
  - event_type (INT64, NULLABLE)
  - event_name (STRING, NULLABLE)
  - visit_id (STRING, NULLABLE)
  - tag (STRING, NULLABLE)
  - utm_source (STRING, NULLABLE)
  - utm_content (STRING, NULLABLE)
  - utm_campaign (STRING, NULLABLE)
  - utm_medium (STRING, NULLABLE)
  - utm_term (STRING, NULLABLE)
  - hostname (STRING, NULLABLE)
  - website_name (STRING, NULLABLE)
  - website_domain (STRING, NULLABLE)
  - website_share_id (STRING, NULLABLE)
  - website_team_id (STRING, NULLABLE)

Table: `fagtorsdag-prod-81a6.umami_student.public_website`
Columns:
  - website_id (STRING, REQUIRED)
  - name (STRING, NULLABLE)
  - domain (STRING, NULLABLE)
  - share_id (STRING, NULLABLE)
  - reset_at (TIMESTAMP, NULLABLE)
  - user_id (STRING, NULLABLE)
  - created_at (TIMESTAMP, NULLABLE)
  - updated_at (TIMESTAMP, NULLABLE)
  - deleted_at (TIMESTAMP, NULLABLE)
  - created_by (STRING, NULLABLE)
  - team_id (STRING, NULLABLE)
  - datastream_metadata (STRUCT<...>, NULLABLE)

Table: `fagtorsdag-prod-81a6.umami_student.event_data`
Columns:
  - website_event_id (STRING, REQUIRED)
  - website_id (STRING, NULLABLE)
  - event_parameters (ARRAY<STRUCT<...>>, REQUIRED)
  - created_at (TIMESTAMP, NULLABLE)

=== QUERY INSTRUCTIONS ===
- Always use fully qualified table names: `fagtorsdag-prod-81a6.umami_student.table_name`
- Use backticks (`) around table names
- Filter by website_id when querying event or event_data tables
- Match website names from user queries to website_id values listed above
    """.trimIndent()

    fun isHealthy(): Boolean = true
}

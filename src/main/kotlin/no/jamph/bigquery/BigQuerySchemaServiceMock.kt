package no.jamph.bigquery

class BigQuerySchemaServiceMock : BigQuerySchemaProvider {

    override fun getWebsites(): List<Website> = listOf(
        Website("fb69e1e9-1bd3-4fd9-b700-9d035cbf44e1", "Aksel", "aksel.nav.no"),
        Website("aa000001-0000-0000-0000-000000000001", "NAV", "nav.no"),
        Website("aa000002-0000-0000-0000-000000000002", "Skatteetaten", "skatteetaten.no"),
        Website("aa000003-0000-0000-0000-000000000003", "Altinn", "altinn.no"),
        Website("aa000004-0000-0000-0000-000000000004", "Helsenorge", "helsenorge.no"),
        Website("aa000005-0000-0000-0000-000000000005", "Regjeringen", "regjeringen.no"),
        Website("aa000006-0000-0000-0000-000000000006", "Statens vegvesen", "vegvesen.no"),
        Website("aa000007-0000-0000-0000-000000000007", "UDI", "udi.no"),
        Website("aa000008-0000-0000-0000-000000000008", "Brønnøysundregistrene", "brreg.no"),
        Website("aa000009-0000-0000-0000-000000000009", "Lånekassen", "lanekassen.no"),
        Website("aa000010-0000-0000-0000-000000000010", "SSB", "ssb.no"),
        Website("aa000011-0000-0000-0000-000000000011", "Politiet", "politiet.no"),
        Website("aa000012-0000-0000-0000-000000000012", "Tolletaten", "toll.no"),
        Website("aa000013-0000-0000-0000-000000000013", "Mattilsynet", "mattilsynet.no"),
        Website("aa000014-0000-0000-0000-000000000014", "Arbeidstilsynet", "arbeidstilsynet.no"),
        Website("aa000015-0000-0000-0000-000000000015", "Husbanken", "husbanken.no"),
        Website("aa000016-0000-0000-0000-000000000016", "Datatilsynet", "datatilsynet.no"),
        Website("aa000017-0000-0000-0000-000000000017", "Folkehelseinstituttet", "fhi.no"),
        Website("aa000018-0000-0000-0000-000000000018", "Helsedirektoratet", "helsedirektoratet.no"),
        Website("aa000019-0000-0000-0000-000000000019", "Kartverket", "kartverket.no"),
        Website("aa000020-0000-0000-0000-000000000020", "NVE", "nve.no"),
        Website("aa000021-0000-0000-0000-000000000021", "Meteorologisk institutt", "met.no"),
        Website("aa000022-0000-0000-0000-000000000022", "Patentstyret", "patentstyret.no"),
        Website("aa000023-0000-0000-0000-000000000023", "Riksrevisjonen", "riksrevisjonen.no"),
        Website("aa000024-0000-0000-0000-000000000024", "Språkrådet", "sprakradet.no"),
        Website("aa000025-0000-0000-0000-000000000025", "Statsforvalteren", "statsforvalteren.no"),
        Website("aa000026-0000-0000-0000-000000000026", "Utdanning.no", "utdanning.no"),
        Website("aa000027-0000-0000-0000-000000000027", "Miljødirektoratet", "miljodirektoratet.no"),
        Website("aa000028-0000-0000-0000-000000000028", "Fiskeridirektoratet", "fiskeridir.no"),
        Website("aa000029-0000-0000-0000-000000000029", "Bufdir", "bufdir.no"),
        Website("aa000030-0000-0000-0000-000000000030", "Kystverket", "kystverket.no"),
        Website("aa000031-0000-0000-0000-000000000031", "Sivilforsvaret", "sivilforsvaret.no"),
    )

    override fun getSchemaContext(): String = """
=== BIGQUERY DATABASE SCHEMA ===
Project: fagtorsdag-prod-81a6
Dataset: umami_student

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
  - event_type (INT64, NULLABLE) - 1: page view, 2: custom event
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

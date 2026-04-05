package no.jamph.ragumami.umami

import no.jamph.ragumami.core.llm.OllamaClient

object RagSchemaSelector {
    suspend fun selectSchema(question: String, ollamaClient: OllamaClient): String {
        val validOptions = setOf("linear", "actions", "default")
        val maxRetries = 3
        
        for (attempt in 0 until maxRetries) {
            val prompt = if (attempt == 0) {
                """
                Output only one word: {default, linear, actions}
                
                What is the context of the question:
                - default: Almost all requests Default for ALL counting, aggregating, grouping queries. Examples: "count per day", "views by month", "total users", "how many X", "top pages". Anything that is easy for an llm to create SQL for.
                - linear: ONLY For explicit TREND/REGRESSION analysis. Examples: "is traffic INCREASING", "what's the GROWTH RATE", "regression analysis", "is there an UPWARD trend"
                - actions: ONLY For specific click/interaction events. Examples: "button clicks", "accordion opens", "form submissions"
                                
                Question: $question
                
                Output one word:
                """.trimIndent()
            } else {
                """
                OUTPUT EXACTLY ONE OF THESE WORDS:
                default
                linear
                actions
                
                NO explanations. NO quotes. NO punctuation. NO symbols. NO other text.
                Just the word itself.
                
                Question: $question
                
                Word:
                """.trimIndent()
            }
            
            val response = ollamaClient.generateConstrained(prompt, temperature = 0.0, maxTokens = 10)
                .trim().lowercase()
            
            val extracted = validOptions.find { response.contains(it) }
            
            if (extracted != null) {
                return when (extracted) {
                    "linear" -> LINEAR_ADDITION
                    "actions" -> ACTIONS_ADDITION
                    "default" -> DEFAULT_ADDITION
                    else -> throw IllegalStateException("Unexpected schema option: $extracted")
                }
            }
        }
        
        return DEFAULT_ADDITION
    }
    
    private const val DEFAULT_ADDITION = ""
    
    private const val LINEAR_ADDITION = """
- For linear regression, use this template structure:

WITH base AS (
  SELECT CAST(x AS FLOAT64) AS x, CAST(y AS FLOAT64) AS y FROM (
    SELECT
      DATE_DIFF(DATE(created_at), DATE('[START_DATE]'), DAY) + 1 AS x,
      COUNT(*) AS y
    FROM [TABLE_NAME]
    WHERE event_type = 1
      AND website_id = '[WEBSITE_ID]'
      [ADD_FILTERS_HERE]
    GROUP BY x
  )
),
stats AS (
  SELECT COUNT(*) AS n, AVG(x) AS x_bar, AVG(y) AS y_bar,
         VAR_SAMP(x) AS var_x, COVAR_SAMP(x, y) AS cov_xy
  FROM base
),
params AS (
  SELECT n, x_bar, y_bar,
    SAFE_DIVIDE(cov_xy, var_x) AS slope,
    y_bar - SAFE_DIVIDE(cov_xy, var_x) * x_bar AS intercept
  FROM stats
),
resid AS (
  SELECT b.x, b.y, p.n, p.x_bar, p.y_bar, p.slope, p.intercept,
    b.y - (p.intercept + p.slope * b.x) AS r
  FROM base b CROSS JOIN params p
),
sums AS (
  SELECT MIN(n) AS n, MIN(intercept) AS a, MIN(slope) AS b,
         MIN(x_bar) AS x_bar, MIN(y_bar) AS y_bar,
    SUM(POW(r, 2)) AS sse,
    SUM(POW(y - y_bar, 2)) AS sst,
    SUM(POW(x - x_bar, 2)) AS sxx
  FROM resid
),
m AS (
  SELECT n, a, b,
    1 - SAFE_DIVIDE(sse, sst) AS r2,
    SQRT(SAFE_DIVIDE(sse, n - 2)) AS rmse,
    SQRT(SAFE_DIVIDE(SAFE_DIVIDE(sse, n - 2), sxx)) AS se_b,
    SQRT(SAFE_DIVIDE(sse, n - 2) * (1.0 / n + POW(x_bar, 2) / sxx)) AS se_a
  FROM sums
),
pv AS (
  SELECT n, a, b, r2, rmse, se_a, se_b,
    SAFE_DIVIDE(a, se_a) AS t_a,
    SAFE_DIVIDE(b, se_b) AS t_b,
    GREATEST(0, 2 * EXP(-0.5 * POW(ABS(SAFE_DIVIDE(a, se_a)), 2)) / 2.506628 * (
       0.4361836 / (1 + 0.33267 * ABS(SAFE_DIVIDE(a, se_a)))
      - 0.1201676 / POW(1 + 0.33267 * ABS(SAFE_DIVIDE(a, se_a)), 2)
      + 0.9372980 / POW(1 + 0.33267 * ABS(SAFE_DIVIDE(a, se_a)), 3))) AS p_a,
    GREATEST(0, 2 * EXP(-0.5 * POW(ABS(SAFE_DIVIDE(b, se_b)), 2)) / 2.506628 * (
       0.4361836 / (1 + 0.33267 * ABS(SAFE_DIVIDE(b, se_b)))
      - 0.1201676 / POW(1 + 0.33267 * ABS(SAFE_DIVIDE(b, se_b)), 2)
      + 0.9372980 / POW(1 + 0.33267 * ABS(SAFE_DIVIDE(b, se_b)), 3))) AS p_b
  FROM m
)
SELECT 'Skjæringspunkt (a)' AS term,
  ROUND(a, 4) AS estimat, ROUND(se_a, 4) AS std_feil,
  ROUND(t_a, 3) AS t_verdi, ROUND(p_a, 4) AS p_verdi,
  ROUND(r2, 4) AS r2, ROUND(rmse, 3) AS rmse, n
FROM pv
UNION ALL
SELECT 'Stigningstall (b)',
  ROUND(b, 4), ROUND(se_b, 4),
  ROUND(t_b, 3), ROUND(p_b, 4),
  ROUND(r2, 4), ROUND(rmse, 3), n
FROM pv
ORDER BY term

Replace [START_DATE], [TABLE_NAME], [WEBSITE_ID], and [ADD_FILTERS_HERE] with actual values.
"""
    
    private const val ACTIONS_ADDITION = """
- Click events stored in event_name column
- Accordion: event_name LIKE '%accordion%'
- Buttons: event_name LIKE '%button%' OR event_name LIKE '%click%'
- Actions require filtering by event_type where appropriate
"""
}

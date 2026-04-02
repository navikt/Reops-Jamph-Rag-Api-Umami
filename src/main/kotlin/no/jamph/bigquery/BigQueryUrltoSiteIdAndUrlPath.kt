package no.jamph.bigquery

import java.net.URL

data class SiteIdAndPath(
    val siteId: String,
    val urlPath: String
)

/**
 * Parses a full URL (e.g., "https://aksel.nav.no/designsystemet") and resolves it to:
 * - website_id (from the list of available websites)
 * - url_path (formatted for SQL LIKE operator)
 *
 * This function mirrors the frontend's pathOperator logic (synchronized with AiByggerPanel.tsx):
 * - "starts-with" + path == "/" → returns "" (whole site, no path filter)
 * - "starts-with" + path != "/" → returns "/path%" (LIKE query, includes subpages)
 * - "equals" → returns "/path" (exact match with LIKE, no wildcard)
 *
 * Backend always uses LIKE operator. The presence/absence of % controls match behavior.
 *
 * @param url Full URL string with protocol (e.g., "https://aksel.nav.no/designsystemet")
 * @param websites List of available websites from BigQuery
 * @param pathOperator Either "starts-with" (default, includes subpages) or "equals" (exact page only)
 * @return SiteIdAndPath containing the matched website_id and formatted url_path
 * @throws IllegalArgumentException if no website matches the domain
 */
fun urlToSiteIdAndPath(
    url: String, 
    websites: List<Website>,
    pathOperator: String = "starts-with"
): SiteIdAndPath {
    // Parse URL to extract domain and path
    val parsedUrl = URL(url)
    val domain = parsedUrl.host
    val rawPath = parsedUrl.path.ifEmpty { "/" }
    
    // Find matching website by domain
    val website = websites.find { it.domain == domain }
        ?: throw IllegalArgumentException("No website found for domain: $domain. Available: ${websites.map { it.domain }}")
    
    // Format path according to operator (synchronized with frontend's pathConditionSQL logic)
    val formattedPath = when {
        pathOperator == "starts-with" && rawPath == "/" -> ""                    // Root with starts-with: no filter (whole site)
        pathOperator == "starts-with" && rawPath != "/" -> "$rawPath%"          // Subpages: /path%
        pathOperator == "equals" -> rawPath                                      // Exact: /path
        else -> rawPath  // Fallback
    }
    
    return SiteIdAndPath(
        siteId = website.websiteId,
        urlPath = formattedPath
    )
}

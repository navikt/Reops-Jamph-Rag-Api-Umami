-- Top search queries - includes both site search (q=) and icon searches (iconQuery=)
-- The search bar is not tracked.

SELECT 
  COALESCE(
    REGEXP_EXTRACT(url_query, r'(?:^|&)iconQuery=([^&]*)'),
    REGEXP_EXTRACT(url_query, r'(?:^|&)q=([^&]*)')
  ) AS search_query,
  CASE 
    WHEN url_query LIKE '%iconQuery=%' THEN 'icon'
    ELSE 'site'
  END AS search_type,
  COUNT(*) AS search_count,
  COUNT(DISTINCT session_id) AS unique_sessions
FROM `fagtorsdag-prod-81a6.umami_student.event`
WHERE website_id = 'fb69e1e9-1bd3-4fd9-b700-9d035cbf44e1'
  AND (url_query LIKE '%q=%' OR url_query LIKE '%iconQuery=%')
  AND created_at >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 300 DAY)
GROUP BY search_query, search_type
HAVING search_query IS NOT NULL AND search_query != ''
ORDER BY search_count DESC
LIMIT 100;

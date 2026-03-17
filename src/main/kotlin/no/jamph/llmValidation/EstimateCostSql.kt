import com.google.cloud.bigquery.*

fun estimateCostInMB (sql: String) : Double {
    
    if (!isSqlValid(sql)) return 0.0

    // Initialize the BigQuery client
    val bigquery: BigQuery = BigQueryOptions.getDefaultInstance().service

    val config = QueryJobConfiguration.newBuilder(sql) // Create a query job configuration with the provided SQL
        .setDryRun(true)
        .setUseLegacySql(false)
        .build()

    
    // Execute the query as a dry run to get the statistics without actually running it
    val job = bigquery.create(JobInfo.of(config)) 
    val bytesProcessed: Double = job.statistics.query.totalBytesProcessed ?: 0L 

    return bytesProcessed / (1024 * 1024) // Convert to MB
}
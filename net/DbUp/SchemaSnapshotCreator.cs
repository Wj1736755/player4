using System;
using RollbackGenerator.DatabaseSchemaExtraction;
using Stln.DbUp.Extensions;

namespace DbUp
{
    /// <summary>
    /// Backwards-compatible wrapper around <see cref="DatabaseSchemaExtractor"/>
    /// used by DbUp tests and step definitions.
    /// </summary>
    public sealed class SchemaSnapshotCreator
    {
        private readonly ILogger _logger;
        private readonly DatabaseSchemaExtractor _extractor;

        public SchemaSnapshotCreator(ILogger logger)
        {
            _logger = logger ?? throw new ArgumentNullException(nameof(logger));
            _extractor = new DatabaseSchemaExtractor(_logger);
        }

        /// <summary>
        /// Legacy API alias for <see cref="ExtractToDacPac"/>.
        /// </summary>
        public void CreateSnapshot(string connectionString, string outputPath)
        {
            _extractor.ExtractToDacPac(connectionString, outputPath);
        }

        public void ExtractToDacPac(string connectionString, string outputPath)
        {
            _extractor.ExtractToDacPac(connectionString, outputPath);
        }

        public void ExtractToSqlScript(string connectionString, string outputPath)
        {
            _extractor.ExtractToSqlScript(connectionString, outputPath);
        }
    }
}





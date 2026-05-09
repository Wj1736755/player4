using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using Microsoft.Data.SqlClient;
using Microsoft.SqlServer.Dac;
using RollbackGenerator.Exceptions;
using Stln.DbUp.Extensions;

namespace RollbackGenerator.DatabaseSchemaExtraction
{
    public sealed class DatabaseSchemaExtractor(ILogger logger)
    {
        private readonly ILogger _logger = logger ?? throw new ArgumentNullException(nameof(logger));

        public DacPackage Extract(string connectionString)
        {
            if (string.IsNullOrWhiteSpace(connectionString))
                throw new EmptyConnectionStringException(nameof(connectionString));

            var tempDacpacPath = Path.Combine(Path.GetTempPath(), $"SchemaSnapshot_{Guid.NewGuid():N}.dacpac");

            try
            {
                var databaseName = ExtractDatabaseNameFromConnectionString(connectionString);
                var dacServices = new DacServices(connectionString);
                dacServices.Extract(
                    tempDacpacPath,
                    databaseName,
                    "DbUpMigration",
                    new Version(1, 0),
                    "Schema snapshot for rollback generation",
                    null);

                return DacPackage.Load(tempDacpacPath, DacSchemaModelStorageType.Memory, FileAccess.Read);
            }
            catch (Exception ex)
            {
                throw new SchemaExtractionException($"Failed to extract schema from database: {ex.Message}", ex);
            }
            finally
            {
                if (File.Exists(tempDacpacPath))
                {
                    try
                    {
                        File.Delete(tempDacpacPath);
                    }
                    catch (Exception ex)
                    {
                        _logger.LogWarning($"Failed to delete temporary DACPAC file", ex);
                    }
                }
            }
        }

        public void ExtractToDacPac(
            string connectionString,
            string outputPath,
            IEnumerable<string>? schemas = null)
        {
            ValidateParameters(connectionString, outputPath);
            EnsureOutputDirectoryExists(outputPath);

            var databaseName = ExtractDatabaseNameFromConnectionString(connectionString);
            var normalizedSchemas = NormalizeSchemaNames(schemas);

            ExtractSchema(connectionString, outputPath, databaseName, normalizedSchemas);
            LogSnapshotCreationSuccess(outputPath, normalizedSchemas);
            LogExtractionLimitations();
        }

        public void ExtractToSqlScript(
            string connectionString,
            string outputPath,
            IEnumerable<string>? schemas = null)
        {
            ValidateParameters(connectionString, outputPath);
            EnsureOutputDirectoryExists(outputPath);

            var databaseName = ExtractDatabaseNameFromConnectionString(connectionString);
            var normalizedSchemas = NormalizeSchemaNames(schemas);

            var tempDacpacPath = Path.Combine(Path.GetTempPath(), $"SchemaSnapshot_{Guid.NewGuid():N}.dacpac");

            try
            {
                ExtractSchema(connectionString, tempDacpacPath, databaseName, normalizedSchemas);
                ConvertDacpacToSqlScript(tempDacpacPath, outputPath, connectionString);
                LogSnapshotCreationSuccess(outputPath, normalizedSchemas);
            }
            finally
            {
                if (File.Exists(tempDacpacPath))
                {
                    try
                    {
                        File.Delete(tempDacpacPath);
                    }
                    catch (Exception ex)
                    {
                        _logger.LogWarning($"Failed to delete temporary DACPAC file", ex);
                    }
                }
            }
        }

        private static void ValidateParameters(string connectionString, string outputPath)
        {
            if (string.IsNullOrWhiteSpace(connectionString))
                throw new EmptyConnectionStringException(nameof(connectionString));

            if (string.IsNullOrWhiteSpace(outputPath))
                throw new EmptyOutputPathException(nameof(outputPath));
        }

        private static void EnsureOutputDirectoryExists(string outputPath)
        {
            var directory = Path.GetDirectoryName(outputPath);
            if (!string.IsNullOrEmpty(directory) && !Directory.Exists(directory))
            {
                Directory.CreateDirectory(directory);
            }
        }

        private static string ExtractDatabaseNameFromConnectionString(string connectionString)
        {
            var builder = new SqlConnectionStringBuilder(connectionString);
            var databaseName = builder.InitialCatalog;

            if (string.IsNullOrWhiteSpace(databaseName))
                throw new DatabaseNameMissingException(connectionString);

            return databaseName;
        }

        private static IReadOnlyList<string>? NormalizeSchemaNames(IEnumerable<string>? schemas)
        {
            if (schemas == null)
                return null;

            return schemas
                .Where(s => !string.IsNullOrWhiteSpace(s))
                .Select(s => s.Trim())
                .Distinct(StringComparer.OrdinalIgnoreCase)
                .ToList();
        }

        private void ExtractSchema(
            string connectionString,
            string outputPath,
            string databaseName,
            IReadOnlyList<string>? normalizedSchemas)
        {
            var dacServices = new DacServices(connectionString);
            var description = BuildSnapshotDescription(normalizedSchemas);

            try
            {
                dacServices.Extract(
                    outputPath,
                    databaseName,
                    "DbUpSnapshot",
                    new Version(1, 0),
                    description,
                    null);
            }
            catch (Exception ex)
            {
                _logger.LogError($"Failed to create schema snapshot: {ex.Message}", ex);
                throw new SchemaSnapshotCreationException($"Failed to create schema snapshot: {ex.Message}", ex);
            }
        }

        private static string BuildSnapshotDescription(IReadOnlyList<string>? normalizedSchemas)
        {
            var baseDescription = $"Schema snapshot created at {DateTime.UtcNow:yyyy-MM-dd HH:mm:ss} UTC";

            if (normalizedSchemas != null && normalizedSchemas.Count > 0)
            {
                return $"{baseDescription} (requested schemas: {string.Join(", ", normalizedSchemas)})";
            }

            return $"{baseDescription} (all schemas)";
        }

        private void LogSnapshotCreationSuccess(string outputPath, IReadOnlyList<string>? normalizedSchemas)
        {
            var schemasInfo = BuildSchemasInfo(normalizedSchemas);
            _logger.LogInfo($"Schema snapshot created successfully: {outputPath}{schemasInfo}");
        }

        private static string BuildSchemasInfo(IReadOnlyList<string>? normalizedSchemas)
        {
            if (normalizedSchemas != null && normalizedSchemas.Count > 0)
            {
                return $" (requested schemas: {string.Join(", ", normalizedSchemas)} - note: filtering not yet implemented at Extract level)";
            }

            return " (all schemas - note: security objects may be included)";
        }

        private void ConvertDacpacToSqlScript(
            string dacpacPath,
            string sqlScriptPath,
            string connectionString)
        {
            var tempEmptyDacpac = CreateEmptyDacpac(connectionString);

            try
            {
                using var sourcePackage = DacPackage.Load(dacpacPath, DacSchemaModelStorageType.Memory, FileAccess.Read);
                using var emptyTargetPackage = DacPackage.Load(tempEmptyDacpac, DacSchemaModelStorageType.Memory, FileAccess.Read);

                var deployOptions = new DacDeployOptions
                {
                    IgnorePermissions = true,
                    IgnoreRoleMembership = true,
                    IgnoreLoginSids = true,
                    IgnorePartitionSchemes = true,
                    IgnoreObjectPlacementOnPartitionScheme = true
                };

                using var scriptStream = new FileStream(sqlScriptPath, FileMode.Create, FileAccess.Write);

                DacServices.GenerateDeployScript(
                    scriptStream,
                    sourcePackage,
                    emptyTargetPackage,
                    "SchemaSnapshot",
                    deployOptions);

                scriptStream.Flush();
            }
            finally
            {
                if (File.Exists(tempEmptyDacpac))
                {
                    try
                    {
                        File.Delete(tempEmptyDacpac);
                    }
                    catch (Exception ex)
                    {
                        _logger.LogWarning($"Failed to delete temporary empty DACPAC file", ex);
                    }
                }
            }
        }

        private string CreateEmptyDacpac(string connectionString)
        {
            var emptyDatabaseConnectionString = GetEmptyDatabaseConnectionString(connectionString);
            var tempEmptyDacpac = Path.Combine(Path.GetTempPath(), $"EmptyTarget_{Guid.NewGuid():N}.dacpac");

            var dacServices = new DacServices(emptyDatabaseConnectionString);

            try
            {
                dacServices.Extract(
                    tempEmptyDacpac,
                    "master",
                    "EmptyTarget",
                    new Version(1, 0),
                    "Empty target database",
                    null);
            }
            catch (Exception ex)
            {
                _logger.LogWarning($"Failed to create empty DACPAC from master database, trying alternative approach: {ex.Message}", ex);
                throw new SchemaSnapshotCreationException("Failed to create empty target DACPAC for SQL script generation", ex);
            }

            return tempEmptyDacpac;
        }

        private static string GetEmptyDatabaseConnectionString(string originalConnectionString)
        {
            var builder = new SqlConnectionStringBuilder(originalConnectionString)
            {
                InitialCatalog = "master"
            };

            return builder.ConnectionString;
        }

        private void LogExtractionLimitations()
        {
            _logger.LogWarning(
                "Note: DacFx Extract API doesn't support excluding security objects or filtering by schema directly. " +
                "The snapshot may contain Users, Logins, Roles, and other security-related objects. " +
                "Consider filtering at application level or using SqlPackage.exe with ExcludeObjectTypes parameter.");
        }
    }
}


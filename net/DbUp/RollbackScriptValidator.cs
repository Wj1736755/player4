using System;
using System.Collections.Generic;
using System.Linq;
using Microsoft.Data.SqlClient;
using Microsoft.SqlServer.Dac;
using Stln.DbUp.Extensions;
using RollbackGenerator.Exceptions;
using DbUp.Exceptions;
using RollbackGenerator.DatabaseSchemaExtraction;
using RollbackGenerator.DatabaseSchemasComparison;
using RollbackGenerator.RollbackScriptGeneration;

namespace DbUp
{
    public sealed class Script : IEquatable<Script>
    {
        public string Name { get; }
        public string Content { get; }

        public Script(string name, string content)
        {
            Name = name ?? throw new ArgumentNullException(nameof(name));
            Content = content ?? throw new ArgumentNullException(nameof(content));
        }

        public bool Equals(Script? other)
        {
            if (other == null) return false;
            return Name == other.Name && Content == other.Content;
        }

        public override bool Equals(object? obj)
        {
            return obj is Script other && Equals(other);
        }

        public override int GetHashCode()
        {
            unchecked
            {
                return ((Name?.GetHashCode() ?? 0) * 397) ^ (Content?.GetHashCode() ?? 0);
            }
        }

        public static bool operator ==(Script? left, Script? right)
        {
            if (ReferenceEquals(left, right)) return true;
            if (left is null || right is null) return false;
            return left.Equals(right);
        }

        public static bool operator !=(Script? left, Script? right)
        {
            return !(left == right);
        }

        public override string ToString()
        {
            return $"Script {{ Name = {Name}, Content = {Content} }}";
        }
    }

    public sealed class MigrationScriptWithRollback : IEquatable<MigrationScriptWithRollback>
    {
        public Script Migration { get; }
        public Script Rollback { get; }

        public MigrationScriptWithRollback(Script migration, Script rollback)
        {
            Migration = migration ?? throw new ArgumentNullException(nameof(migration));
            Rollback = rollback ?? throw new ArgumentNullException(nameof(rollback));
        }

        public bool Equals(MigrationScriptWithRollback? other)
        {
            if (other == null) return false;
            return Equals(Migration, other.Migration) && Equals(Rollback, other.Rollback);
        }

        public override bool Equals(object? obj)
        {
            return obj is MigrationScriptWithRollback other && Equals(other);
        }

        public override int GetHashCode()
        {
            unchecked
            {
                return ((Migration?.GetHashCode() ?? 0) * 397) ^ (Rollback?.GetHashCode() ?? 0);
            }
        }

        public static bool operator ==(MigrationScriptWithRollback? left, MigrationScriptWithRollback? right)
        {
            if (ReferenceEquals(left, right)) return true;
            if (left is null || right is null) return false;
            return left.Equals(right);
        }

        public static bool operator !=(MigrationScriptWithRollback? left, MigrationScriptWithRollback? right)
        {
            return !(left == right);
        }

        public override string ToString()
        {
            return $"MigrationScriptWithRollback {{ Migration = {Migration}, Rollback = {Rollback} }}";
        }
    }

    public sealed class RollbackScriptValidator
    {
        private readonly ILogger _logger;
        private readonly ITempPathProvider _tempPathProvider;
        
        public RollbackScriptValidator(ILogger logger, ITempPathProvider? tempPathProvider = null)
        {
            _logger = logger ?? throw new ArgumentNullException(nameof(logger));
            _tempPathProvider = tempPathProvider ?? new TempPathProvider();
        }
        
        public RollbackValidationResult Validate(
        string connectionString,
        Script migrationScript,
        Script rollbackScript)
    {
        ValidateParameters(connectionString, migrationScript, rollbackScript);
        
        using var tempDb = new TemporaryDatabaseManager(connectionString, _logger);
        
        try
        {
            _logger.LogInfo($"Starting rollback script validation for migration: {migrationScript.Name}");
            
            var schemaExtractor = new DatabaseSchemaExtractor(_logger);
            var initialSnapshotPath = CreateInitialSchemaSnapshot(tempDb.ConnectionString, schemaExtractor);
            
            ApplyMigrationScript(tempDb, migrationScript);
            var afterMigrationSnapshotPath = CreateSnapshotAfterMigration(tempDb.ConnectionString, schemaExtractor);
            
            ApplyRollbackScript(tempDb, rollbackScript);
            var afterRollbackSnapshotPath = CreateSnapshotAfterRollback(tempDb.ConnectionString, schemaExtractor);
            
            var areIdentical = CompareSchemas(initialSnapshotPath, afterRollbackSnapshotPath);
            
            var result = BuildValidationResult(
                areIdentical,
                initialSnapshotPath,
                afterMigrationSnapshotPath,
                afterRollbackSnapshotPath);
            
            LogValidationResult(areIdentical);
            
            return result;
        }
        catch (Exception ex)
        {
            _logger.LogError($"Rollback validation failed with exception: {ex.Message}", ex);
            throw new RollbackValidationException($"Failed to validate rollback script: {ex.Message}", ex);
        }
        }
        
        public IReadOnlyList<RollbackValidationResult> Validate(
        string connectionString,
        IReadOnlyList<MigrationScriptWithRollback> scripts)
    {
        ValidateParameters(connectionString, scripts);
        
        var results = new List<RollbackValidationResult>();
        
        using var tempDb = new TemporaryDatabaseManager(connectionString, _logger);
        
        try
        {
            _logger.LogInfo($"Starting rollback script validation for {scripts.Count} migration(s)");
            
            var schemaExtractor = new DatabaseSchemaExtractor(_logger);
            var initialSnapshotPath = CreateInitialSchemaSnapshot(tempDb.ConnectionString, schemaExtractor);
            
            foreach (var script in scripts)
            {
                try
                {
                    _logger.LogInfo($"Validating migration: {script.Migration.Name}");
                    
                    ApplyMigrationScript(tempDb, script.Migration);
                    var afterMigrationSnapshotPath = CreateSnapshotAfterMigration(tempDb.ConnectionString, schemaExtractor);
                    
                    ApplyRollbackScript(tempDb, script.Rollback);
                    var afterRollbackSnapshotPath = CreateSnapshotAfterRollback(tempDb.ConnectionString, schemaExtractor);
                    
                    var areIdentical = CompareSchemas(initialSnapshotPath, afterRollbackSnapshotPath);
                    
                    var result = BuildValidationResult(
                        areIdentical,
                        initialSnapshotPath,
                        afterMigrationSnapshotPath,
                        afterRollbackSnapshotPath);
                    
                    LogValidationResult(areIdentical);
                    
                    results.Add(result);
                    
                    if (!areIdentical)
                    {
                        _logger.LogWarning($"Rollback validation failed for migration: {script.Migration.Name}");
                    }
                    
                    RestoreDatabaseToInitialState(tempDb.ConnectionString, initialSnapshotPath);
                }
                catch (Exception ex)
                {
                    _logger.LogError($"Rollback validation failed for migration '{script.Migration.Name}' with exception: {ex.Message}", ex);
                    throw new RollbackValidationException($"Failed to validate rollback script for migration '{script.Migration.Name}': {ex.Message}", ex);
                }
            }
            
            var allValid = results.All(r => r.IsValid);
            if (allValid)
            {
                _logger.LogInfo($"All {scripts.Count} migration(s) validated successfully");
            }
            else
            {
                var failedCount = results.Count(r => !r.IsValid);
                _logger.LogWarning($"{failedCount} out of {scripts.Count} migration(s) failed rollback validation");
            }
            
            return results;
        }
        catch (Exception ex)
        {
            _logger.LogError($"Rollback validation failed with exception: {ex.Message}", ex);
            throw new RollbackValidationException($"Failed to validate rollback scripts: {ex.Message}", ex);
        }
        }
        
        private string CreateInitialSchemaSnapshot(string connectionString, DatabaseSchemaExtractor databaseSchemaExtractor)
    {
        var snapshotPath = _tempPathProvider.Combine(_tempPathProvider.GetTempPath(), $"InitialSnapshot_{Guid.NewGuid():N}.dacpac");
        databaseSchemaExtractor.ExtractToDacPac(connectionString, snapshotPath);
        _logger.LogInfo("Initial schema snapshot created");
        return snapshotPath;
    }
    
    private void ApplyMigrationScript(TemporaryDatabaseManager tempDb, Script migrationScript)
    {
        _logger.LogInfo($"Applying migration script: {migrationScript.Name}");
        tempDb.ExecuteScript(migrationScript.Content);
        _logger.LogInfo("Migration script applied successfully");
    }
    
    private string CreateSnapshotAfterMigration(string connectionString, DatabaseSchemaExtractor databaseSchemaExtractor)
    {
        var snapshotPath = _tempPathProvider.Combine(_tempPathProvider.GetTempPath(), $"AfterMigrationSnapshot_{Guid.NewGuid():N}.dacpac");
        databaseSchemaExtractor.ExtractToDacPac(connectionString, snapshotPath);
        _logger.LogInfo("Schema snapshot after migration created");
        return snapshotPath;
    }
    
    private void ApplyRollbackScript(TemporaryDatabaseManager tempDb, Script rollbackScript)
    {
        _logger.LogInfo($"Applying rollback script: {rollbackScript.Name}");
        tempDb.ExecuteScript(rollbackScript.Content);
        _logger.LogInfo("Rollback script applied successfully");
    }
    
    private string CreateSnapshotAfterRollback(string connectionString, DatabaseSchemaExtractor databaseSchemaExtractor)
    {
        var snapshotPath = _tempPathProvider.Combine(_tempPathProvider.GetTempPath(), $"AfterRollbackSnapshot_{Guid.NewGuid():N}.dacpac");
        databaseSchemaExtractor.ExtractToDacPac(connectionString, snapshotPath);
        _logger.LogInfo("Schema snapshot after rollback created");
        return snapshotPath;
    }
    
    private bool CompareSchemas(string initialSnapshotPath, string afterRollbackSnapshotPath)
    {
        var comparator = new SnapshotComparator(_logger);
        return comparator.AreIdentical(initialSnapshotPath, afterRollbackSnapshotPath);
    }
    
    private RollbackValidationResult BuildValidationResult(
        bool areIdentical,
        string initialSnapshotPath,
        string afterMigrationSnapshotPath,
        string afterRollbackSnapshotPath)
    {
        var result = new RollbackValidationResult
        {
            IsValid = areIdentical,
            InitialSnapshotPath = initialSnapshotPath,
            AfterMigrationSnapshotPath = afterMigrationSnapshotPath,
            AfterRollbackSnapshotPath = afterRollbackSnapshotPath
        };
        
        if (!areIdentical)
        {
            result.DifferencesScriptPath = GenerateDifferencesScript(initialSnapshotPath, afterRollbackSnapshotPath);
        }
        
        return result;
    }
    
    private string GenerateDifferencesScript(string initialSnapshotPath, string afterRollbackSnapshotPath)
    {
        var differencesPath = _tempPathProvider.Combine(_tempPathProvider.GetTempPath(), $"RollbackDifferences_{Guid.NewGuid():N}.sql");
        var comparator = new SnapshotComparator(_logger);
        comparator.GenerateDifferencesScript(initialSnapshotPath, afterRollbackSnapshotPath, differencesPath);
        _logger.LogWarning($"Differences script generated: {differencesPath}");
        return differencesPath;
    }
    
    private void LogValidationResult(bool areIdentical)
    {
        if (areIdentical)
        {
            _logger.LogInfo("Rollback validation successful: schema after rollback matches initial schema");
        }
        else
        {
            _logger.LogWarning("Rollback validation failed: schema after rollback does not match initial schema");
        }
        }
        
        private void RestoreDatabaseToInitialState(string connectionString, string initialSnapshotPath)
    {
        try
        {
            _logger.LogInfo("Restoring database to initial state");
            var builder = new SqlConnectionStringBuilder(connectionString);
            var databaseName = builder.InitialCatalog;
            
            if (string.IsNullOrWhiteSpace(databaseName))
                throw new RollbackGenerator.Exceptions.DatabaseNameMissingException(connectionString);
            
            var dacServices = new DacServices(connectionString);
            var deployOptions = new DacDeployOptions
            {
                IgnorePermissions = true,
                IgnoreRoleMembership = true,
                IgnoreLoginSids = true,
                IgnorePartitionSchemes = true,
                IgnoreObjectPlacementOnPartitionScheme = true
            };
            
            using var package = DacPackage.Load(initialSnapshotPath, DacSchemaModelStorageType.Memory, System.IO.FileAccess.Read);
            dacServices.Deploy(package, databaseName, true, deployOptions);
            _logger.LogInfo("Database restored to initial state");
        }
        catch (Exception ex)
        {
            _logger.LogWarning($"Failed to restore database to initial state: {ex.Message}", ex);
            throw new RollbackValidationException($"Failed to restore database to initial state: {ex.Message}", ex);
        }
        }
        
        private static void ValidateParameters(string connectionString, Script migrationScript, Script rollbackScript)
    {
        if (string.IsNullOrWhiteSpace(connectionString))
            throw new RollbackGenerator.Exceptions.EmptyConnectionStringException(nameof(connectionString));
        
        if (migrationScript == null)
            throw new ArgumentNullException(nameof(migrationScript));
        
        if (string.IsNullOrWhiteSpace(migrationScript.Content))
            throw new ArgumentException("Migration script content cannot be null or empty", nameof(migrationScript));
        
        if (rollbackScript == null)
            throw new ArgumentNullException(nameof(rollbackScript));
        
        if (string.IsNullOrWhiteSpace(rollbackScript.Content))
            throw new ArgumentException("Rollback script content cannot be null or empty", nameof(rollbackScript));
    }
    
    private static void ValidateParameters(string connectionString, IReadOnlyList<MigrationScriptWithRollback> scripts)
    {
        if (string.IsNullOrWhiteSpace(connectionString))
            throw new RollbackGenerator.Exceptions.EmptyConnectionStringException(nameof(connectionString));
        
        if (scripts == null)
            throw new ArgumentNullException(nameof(scripts));
        
        if (scripts.Count == 0)
            throw new ArgumentException("Scripts list cannot be empty", nameof(scripts));
        
        for (var i = 0; i < scripts.Count; i++)
        {
            var script = scripts[i];
            if (script.Migration == null)
                throw new ArgumentException($"Migration script at index {i} cannot be null", nameof(scripts));
            
            if (string.IsNullOrWhiteSpace(script.Migration.Content))
                throw new ArgumentException($"Migration script content at index {i} cannot be null or empty", nameof(scripts));
            
            if (script.Rollback == null)
                throw new ArgumentException($"Rollback script at index {i} cannot be null", nameof(scripts));
            
            if (string.IsNullOrWhiteSpace(script.Rollback.Content))
                throw new ArgumentException($"Rollback script content at index {i} cannot be null or empty", nameof(scripts));
        }
        }
    }

    public sealed class RollbackValidationResult
    {
        public bool IsValid { get; set; }
        public string? InitialSnapshotPath { get; set; }
        public string? AfterMigrationSnapshotPath { get; set; }
        public string? AfterRollbackSnapshotPath { get; set; }
        public string? DifferencesScriptPath { get; set; }
    }
}


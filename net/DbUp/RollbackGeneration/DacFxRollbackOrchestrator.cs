using System;
using System.Collections.Generic;
using System.Linq;
using Stln.DbUp.Extensions;

namespace DbUp.RollbackGeneration
{
    public sealed class DacFxRollbackOrchestrator : IRollbackOrchestrator
{
    private readonly string _targetConnectionString;
    private readonly ILogger _logger;
    
    public DacFxRollbackOrchestrator(string targetConnectionString, ILogger logger)
    {
        _targetConnectionString = targetConnectionString ?? throw new ArgumentNullException(nameof(targetConnectionString));
        _logger = logger ?? throw new ArgumentNullException(nameof(logger));
    }
    
    public string? GenerateRollbackScript(
        string migrationScriptName,
        IEnumerable<string> previousScriptNames,
        Func<string, string?> getScriptContent)
    {
        if (string.IsNullOrWhiteSpace(migrationScriptName))
            return null;
        
        var migrationScriptContent = getScriptContent(migrationScriptName);
        if (string.IsNullOrWhiteSpace(migrationScriptContent))
            return null;
            
        using var tempDb = new TemporaryDatabaseManager(_targetConnectionString, _logger);
        
        try
        {
            var previousScriptNamesList = previousScriptNames?.ToList() ?? new List<string>();
            
            tempDb.ApplyMigrationHistory(previousScriptNamesList, getScriptContent);
            
            var beforeSnapshot = new DacFxSchemaSnapshot(tempDb.ConnectionString, _logger);
            var beforeModel = beforeSnapshot.ExtractSchema();
            
            tempDb.ExecuteScript(migrationScriptContent);
            
            var afterSnapshot = new DacFxSchemaSnapshot(tempDb.ConnectionString, _logger);
            var afterModel = afterSnapshot.ExtractSchema();
            
            var generator = new DacFxRollbackGenerator(_logger);
            var rollbackScript = generator.GenerateRollbackScript(
                sourceModel: afterModel,
                targetModel: beforeModel,
                connectionString: tempDb.ConnectionString);
            
            return rollbackScript;
        }
        catch (Exception ex)
        {
            _logger.LogWarning($"Rollback generation failed for script '{migrationScriptName}'", ex);
            
            return null;
        }
    }
    }
}


using System;
using System.Collections.Generic;
using System.Linq;
using RollbackGenerator.DatabaseSchemaExtraction;
using RollbackGenerator.Exceptions;
using Stln.DbUp.Extensions;

namespace RollbackGenerator.RollbackScriptGeneration
{
    public sealed class DacFxRollbackScriptGenerationOrchestrator(string targetConnectionString, ILogger logger)
        : IRollbackScriptGenerationOrchestrator
    {
        private readonly string _targetConnectionString = targetConnectionString ?? throw new ArgumentNullException(nameof(targetConnectionString));
        private readonly ILogger _logger = logger ?? throw new ArgumentNullException(nameof(logger));

        public IncrementalScript GenerateRollbackScriptForIncrementalScript(
            IncrementalScript migrationScript,
            IEnumerable<IncrementalScript> previousScripts)
        {
            if (migrationScript == null)
                throw new ArgumentNullException(nameof(migrationScript));
                
            using var tempDb = new TemporaryDatabaseManager(_targetConnectionString, _logger);
            
            var previousScriptsList = previousScripts?.ToList() ?? new List<IncrementalScript>();
            
            foreach (var script in previousScriptsList)
            {
                tempDb.ExecuteScript(script.Content);
            }
            
            var schemaExtractor = new DatabaseSchemaExtractor(_logger);
            var beforeModel = schemaExtractor.Extract(tempDb.ConnectionString);
            
            tempDb.ExecuteScript(migrationScript.Content);
            
            var afterModel = schemaExtractor.Extract(tempDb.ConnectionString);
            
            var generator = new DacFxRollbackGenerator(_logger);
            var rollbackScriptContent = generator.GenerateRollbackScript(
                sourceModel: afterModel,
                targetModel: beforeModel,
                connectionString: tempDb.ConnectionString);
            
            if (string.IsNullOrWhiteSpace(rollbackScriptContent))
            {
                throw new NoSchemaChangesException(migrationScript.Name);
            }
            
            var rollbackScriptName = GetRollbackScriptName(migrationScript.Name);
            return new IncrementalScript(rollbackScriptName, rollbackScriptContent);
        }
        
        private static string GetRollbackScriptName(string migrationScriptName)
        {
            return $"{migrationScriptName}.rollback";
        }
    }
}


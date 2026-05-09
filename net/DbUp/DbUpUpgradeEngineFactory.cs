using System;
using System.Collections.Generic;
using System.Data;
using System.Linq;
using System.Reflection;
using Microsoft.Data.SqlClient;
using DbUp;
using DbUp.Engine;
using DbUp.RollbackGeneration;
using RollbackGenerator.RollbackScriptGeneration;
using Stln.DbUp.Extensions;

namespace DbUp
{
    public static class DbUpUpgradeEngineFactory
{
    public static UpgradeEngine CreateUpgradeEngine(
        string connectionString, 
        ILogger logger,
        string schema,
        string tableName,
        bool enableAutomaticRollbackGeneration = true)
    {
        Func<IDbCommand> dbCommandFactory = () =>
        {
            var connection = new SqlConnection(connectionString);
            connection.Open();
            return connection.CreateCommand();
        };
        
        Func<string, IEnumerable<string>, Func<string, string?>, string?>? rollbackGenerator = null;
        
        if (enableAutomaticRollbackGeneration)
        {
            IRollbackScriptGenerationOrchestrator rollbackScriptGenerationOrchestrator = new DacFxRollbackScriptGenerationOrchestrator(connectionString, logger);
            var assembly = Assembly.GetExecutingAssembly();
            IScriptContentProvider scriptProvider = new ScriptContentProvider(assembly);
            
            rollbackGenerator = (scriptName, previousScripts, getScriptContent) =>
            {
                string? GetCombinedScriptContent(string name)
                {
                    var content = getScriptContent(name);
                    if (!string.IsNullOrEmpty(content))
                        return content;
                    
                    return scriptProvider.GetScriptContent(name);
                }
                
                // Build migration script
                var migrationContent = GetCombinedScriptContent(scriptName);
                if (string.IsNullOrWhiteSpace(migrationContent))
                    return null;

                var migrationScript = new IncrementalScript(scriptName, migrationContent);

                // Build previous scripts list
                var previousIncrementalScripts = previousScripts
                    .Select(name => new { Name = name, Content = GetCombinedScriptContent(name) })
                    .Where(x => !string.IsNullOrWhiteSpace(x.Content))
                    .Select(x => new IncrementalScript(x.Name, x.Content!))
                    .ToList();

                var rollbackIncremental = rollbackScriptGenerationOrchestrator.GenerateRollbackScriptForIncrementalScript(
                    migrationScript,
                    previousIncrementalScripts);

                return rollbackIncremental?.Content;
            };
        }
        
        var customJournal = new CustomJournal(
            dbCommandFactory, 
            schema, 
            tableName,
            logger,
            rollbackGenerator);
        
        var builder = DeployChanges.To
            .SqlDatabase(connectionString)
            .JournalTo(customJournal)
            .WithScriptsEmbeddedInAssembly(Assembly.GetExecutingAssembly())
            .WithTransactionPerScript();
        
        return builder.LogToConsole().Build();
    }
    }
}


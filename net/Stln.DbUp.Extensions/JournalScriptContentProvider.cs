using System;
using System.Data;
using DbUp.Engine;

namespace Stln.DbUp.Extensions
{
    public sealed class JournalScriptContentProvider
{
    private readonly string _schema;
    private readonly string _tableName;
    private readonly Func<IDbCommand> _dbCommandFactory;
    private readonly JournalQueryBuilder _queryBuilder;
    private readonly ILogger _logger;
    
    public JournalScriptContentProvider(string schema, string tableName, Func<IDbCommand> dbCommandFactory, ILogger logger)
    {
        _schema = schema ?? throw new ArgumentNullException(nameof(schema));
        _tableName = tableName ?? throw new ArgumentNullException(nameof(tableName));
        _dbCommandFactory = dbCommandFactory ?? throw new ArgumentNullException(nameof(dbCommandFactory));
        _logger = logger ?? throw new ArgumentNullException(nameof(logger));
        _queryBuilder = new JournalQueryBuilder(schema, tableName);
    }
    
    public string? GetScriptContent(string scriptName)
    {
        try
        {
            using var command = _dbCommandFactory();
            command.CommandText = _queryBuilder.BuildSelectScriptContentQuery();
            command.CommandTimeout = DatabaseDefaults.DefaultCommandTimeoutSeconds;
            
            var scriptNameParameter = command.CreateParameter();
            scriptNameParameter.ParameterName = "@ScriptName";
            scriptNameParameter.Value = scriptName;
            command.Parameters.Add(scriptNameParameter);
            
            var storedScript = command.ExecuteScalar();
            if (storedScript != null && storedScript != DBNull.Value)
                return storedScript.ToString();
        }
        catch (Exception ex)
        {
            _logger.LogWarning($"Failed to retrieve script content for '{scriptName}'", ex);
        }
        
        return null;
    }
    
    public string? GetScriptContentFromCurrentScript(string scriptName, SqlScript currentScript)
    {
        if (currentScript.Name.Equals(scriptName, StringComparison.OrdinalIgnoreCase))
            return currentScript.Contents;
        
        return null;
    }
    }
}


using System;
using System.Collections.Generic;
using System.Data;
using Microsoft.Data.SqlClient;
using Stln.DbUp.Extensions;
using DbUp.Exceptions;

namespace DbUp.RollbackGeneration
{
    public sealed class TemporaryDatabaseManager : IDisposable
{
    private readonly string _databaseName;
    private readonly string _tempDbConnectionString;
    private readonly string _masterConnectionString;
    private readonly ILogger _logger;
    private bool _disposed = false;
    
    public TemporaryDatabaseManager(string baseConnectionString, ILogger logger)
    {
        if (string.IsNullOrWhiteSpace(baseConnectionString))
            throw new ArgumentNullException(nameof(baseConnectionString));
        
        _logger = logger ?? throw new ArgumentNullException(nameof(logger));
        
        try
        {
            _databaseName = $"RollbackGen_{Guid.NewGuid():N}";
            _masterConnectionString = GetMasterConnectionString(baseConnectionString);
            _tempDbConnectionString = CreateTemporaryDatabase(baseConnectionString, _masterConnectionString);
        }
        catch
        {
            _disposed = true;
            throw;
        }
    }
    
    private const int ScriptExecutionTimeoutSeconds = 300;
    
    private static string GetMasterConnectionString(string baseConnectionString)
    {
        var builder = new SqlConnectionStringBuilder(baseConnectionString);
        builder.InitialCatalog = "master";
        return builder.ConnectionString;
    }
    
    private string CreateTemporaryDatabase(string baseConnectionString, string masterConnectionString)
    {
        SqlIdentifierQuoter.ThrowIfInvalidIdentifier(_databaseName);
        
        using var connection = new SqlConnection(masterConnectionString);
        connection.Open();
        
        using var command = connection.CreateCommand();
        command.CommandTimeout = 60;
        var quotedDatabaseName = SqlIdentifierQuoter.Quote(_databaseName);
        command.CommandText = $"CREATE DATABASE {quotedDatabaseName}";
        command.ExecuteNonQuery();
        
        var builder = new SqlConnectionStringBuilder(baseConnectionString);
        builder.InitialCatalog = _databaseName;
        return builder.ConnectionString;
    }
    
    public string ConnectionString => _tempDbConnectionString;
    
    public void ApplyMigrationHistory(IEnumerable<string> executedScripts, Func<string, string?> getScriptContent)
    {
        foreach (var scriptName in executedScripts)
        {
            var scriptContent = getScriptContent(scriptName);
            if (!string.IsNullOrEmpty(scriptContent))
            {
                ExecuteScript(scriptContent);
            }
        }
    }
    
    public void ExecuteScript(string scriptContent)
    {
        if (string.IsNullOrWhiteSpace(scriptContent))
            return;
            
        using var connection = new SqlConnection(_tempDbConnectionString);
        connection.Open();
        
        var parts = System.Text.RegularExpressions.Regex.Split(
            scriptContent, 
            @"^\s*GO\s*$", 
            System.Text.RegularExpressions.RegexOptions.IgnoreCase | System.Text.RegularExpressions.RegexOptions.Multiline);
        
        foreach (var part in parts)
        {
            var trimmedPart = part.Trim();
            if (string.IsNullOrWhiteSpace(trimmedPart))
                continue;
                
            try
            {
                using var command = connection.CreateCommand();
                command.CommandText = trimmedPart;
                command.CommandTimeout = ScriptExecutionTimeoutSeconds;
                command.ExecuteNonQuery();
            }
            catch (Exception ex)
            {
                throw new ScriptExecutionException($"Error executing script part: {ex.Message}", ex);
            }
        }
    }
    
    public void Dispose()
    {
        if (_disposed)
            return;
            
        try
        {
            using var connection = new SqlConnection(_masterConnectionString);
            connection.Open();
            
            using var command = connection.CreateCommand();
            command.CommandTimeout = 60;
            var quotedDatabaseName = SqlIdentifierQuoter.Quote(_databaseName);
            var databaseNameParam = command.CreateParameter();
            databaseNameParam.ParameterName = "@databaseName";
            databaseNameParam.Value = _databaseName;
            command.Parameters.Add(databaseNameParam);
            command.CommandText = $@"
                IF EXISTS (SELECT name FROM sys.databases WHERE name = @databaseName)
                BEGIN
                    ALTER DATABASE {quotedDatabaseName} SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
                    DROP DATABASE {quotedDatabaseName};
                END";
            command.ExecuteNonQuery();
        }
        catch (Exception ex)
        {
            _logger.LogWarning($"Failed to cleanup temporary database '{_databaseName}'", ex);
        }
        finally
        {
            _disposed = true;
        }
    }
    }
}


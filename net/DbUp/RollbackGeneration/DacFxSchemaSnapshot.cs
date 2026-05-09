using System;
using System.IO;
using Microsoft.Data.SqlClient;
using Microsoft.SqlServer.Dac;
using Stln.DbUp.Extensions;
using DbUp.Exceptions;

namespace DbUp.RollbackGeneration
{
    public sealed class DacFxSchemaSnapshot
{
    private readonly string _connectionString;
    private readonly ILogger _logger;
    
    public DacFxSchemaSnapshot(string connectionString, ILogger logger)
    {
        _connectionString = connectionString ?? throw new ArgumentNullException(nameof(connectionString));
        _logger = logger ?? throw new ArgumentNullException(nameof(logger));
    }
    
    public DacPackage ExtractSchema()
    {
        var tempDacpacPath = Path.Combine(Path.GetTempPath(), $"SchemaSnapshot_{Guid.NewGuid():N}.dacpac");
        
        try
        {
            var dacServices = new DacServices(_connectionString);
            
            dacServices.Extract(
                tempDacpacPath,
                GetDatabaseName(),
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
    
    private string GetDatabaseName()
    {
        var builder = new SqlConnectionStringBuilder(_connectionString);
        var databaseName = builder.InitialCatalog;
        if (string.IsNullOrWhiteSpace(databaseName))
            throw new RollbackGenerator.Exceptions.DatabaseNameMissingException(_connectionString);
        return databaseName;
    }
    }
}


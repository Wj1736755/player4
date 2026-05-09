using System;
using System.Data;
using System.Linq;
using Microsoft.Data.SqlClient;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Console;
using DbUp;
using Xunit;
using Xunit.Abstractions;
using MartinCostello.SqlLocalDb;
using Stln.DbUp.Extensions;

namespace DbUp.Tests
{
    public class RollbackGenerationDemoTest : IDisposable
{
    private readonly ITestOutputHelper _output;
    private readonly string? _connectionString;
    private readonly string? _databaseName;
    private static readonly bool _databaseAvailable;
    private static readonly string _baseConnectionString;
    
    static RollbackGenerationDemoTest()
    {
        _baseConnectionString = @"Server=(localdb)\mssqllocaldb;Integrated Security=true;TrustServerCertificate=true;";
        
        try
        {
            var localDb = new SqlLocalDbApi();
            var instanceInfo = localDb.GetOrCreateInstance("mssqllocaldb");
            var manager = instanceInfo.Manage();
            manager.Start();
            _baseConnectionString = instanceInfo.GetConnectionString();
        }
        catch
        {
            // Fall back to default connection string
        }
        
        _databaseAvailable = CheckDatabaseAvailability();
    }
    
    public RollbackGenerationDemoTest(ITestOutputHelper output)
    {
        _output = output;
        
        if (!_databaseAvailable)
        {
            return;
        }
        
        _databaseName = $"RollbackTest_{Guid.NewGuid():N}";
        _connectionString = _baseConnectionString.Contains("Database=")
            ? _baseConnectionString.Replace(_baseConnectionString.Split(';').First(s => s.StartsWith("Database=")), $"Database={_databaseName}")
            : _baseConnectionString + $";Database={_databaseName}";
        
        CreateDatabase();
    }
    
    private static bool CheckDatabaseAvailability()
    {
        try
        {
            var testConnectionString = _baseConnectionString.Contains("Database=") 
                ? _baseConnectionString.Replace(_baseConnectionString.Split(';').First(s => s.StartsWith("Database=")), "Database=master")
                : _baseConnectionString + ";Database=master";
            
            using var connection = new SqlConnection(testConnectionString);
            connection.Open();
            return true;
        }
        catch
        {
            return false;
        }
    }
    
    private void CreateDatabase()
    {
        if (string.IsNullOrEmpty(_connectionString) || string.IsNullOrEmpty(_databaseName))
            return;
            
        var masterConnectionString = _baseConnectionString.Contains("Database=")
            ? _baseConnectionString.Replace(_baseConnectionString.Split(';').First(s => s.StartsWith("Database=")), "Database=master")
            : _baseConnectionString + ";Database=master";
        
        using var connection = new SqlConnection(masterConnectionString);
        connection.Open();
        using var command = connection.CreateCommand();
        command.CommandText = $"CREATE DATABASE [{_databaseName}]";
        command.ExecuteNonQuery();
    }
    
    private void DropDatabase()
    {
        if (string.IsNullOrEmpty(_databaseName))
            return;
        
        try
        {
            var masterConnectionString = _baseConnectionString.Contains("Database=")
                ? _baseConnectionString.Replace(_baseConnectionString.Split(';').First(s => s.StartsWith("Database=")), "Database=master")
                : _baseConnectionString + ";Database=master";
            
            using var connection = new SqlConnection(masterConnectionString);
            connection.Open();
            using var command = connection.CreateCommand();
            command.CommandText = $@"
                IF EXISTS (SELECT name FROM sys.databases WHERE name = '{_databaseName}')
                BEGIN
                    ALTER DATABASE [{_databaseName}] SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
                    DROP DATABASE [{_databaseName}];
                END";
            command.ExecuteNonQuery();
        }
        catch
        {
            // Ignore cleanup errors
        }
    }
    
    
    [Fact]
    public void GenerateRollbacksForSampleMigrations()
    {
        if (!_databaseAvailable) return;
        if (string.IsNullOrEmpty(_connectionString)) throw new InvalidOperationException("Database connection string is not available");
        
        _output.WriteLine($"Created database: {_databaseName}");
        
        var loggerFactory = Microsoft.Extensions.Logging.LoggerFactory.Create(builder => builder.AddConsole());
        var upgradeLog = new DbUp.Engine.Output.MicrosoftUpgradeLog(loggerFactory);
        var logger = upgradeLog.AsStlnLogger();
        var upgradeEngine = DbUpUpgradeEngineFactory.CreateUpgradeEngine(_connectionString, logger, "dbo", "CustomJournal");
        var result = upgradeEngine.PerformUpgrade();
        
        Assert.True(result.Successful, $"Migration failed: {result.Error}");
        
        _output.WriteLine("\n✓ All migrations executed successfully!");
        
        VerifyRollbackScripts(_connectionString);
    }
    
    private void VerifyRollbackScripts(string connectionString)
    {
        using var connection = new SqlConnection(connectionString);
        connection.Open();
        using var command = connection.CreateCommand();
        command.CommandText = @"
            SELECT 
                [ScriptName],
                CASE 
                    WHEN [RollbackScript] IS NULL THEN 0
                    ELSE LEN([RollbackScript])
                END as RollbackLength
            FROM [dbo].[CustomJournal]
            WHERE [ScriptName] NOT LIKE '001_CreateCustomJournalTable%'
            ORDER BY [Id]";
        
        using var reader = command.ExecuteReader();
        var scripts = new System.Collections.Generic.List<(string Name, int Length)>();
        
        while (reader.Read())
        {
            scripts.Add((reader.GetString(0), reader.GetInt32(1)));
        }
        
        _output.WriteLine($"\nFound {scripts.Count} migration scripts:");
        foreach (var (name, length) in scripts)
        {
            _output.WriteLine($"  - {name}: Rollback = {length:N0} chars");
            Assert.True(length > 0, $"Rollback script for {name} should be generated (got {length} chars)");
        }
        
        Assert.True(scripts.Count >= 3, $"Expected at least 3 migration scripts, found {scripts.Count}");
    }
    
    public void Dispose()
    {
        if (!_databaseAvailable) return;
        DropDatabase();
    }
    }
}


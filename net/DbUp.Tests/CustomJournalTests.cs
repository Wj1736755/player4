using System;
using System.Data;
using System.Linq;
using System.Threading;
using Microsoft.Data.SqlClient;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Console;
using DbUp;
using DbUp.Engine;
using Xunit;
using MartinCostello.SqlLocalDb;
using Stln.DbUp.Extensions;

namespace DbUp.Tests
{
    public class CustomJournalTests : IDisposable
{
    private readonly string? _connectionString;
    private readonly string? _databaseName;
    private static readonly bool _databaseAvailable;
    private static readonly string _baseConnectionString;
    
    static CustomJournalTests()
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
    
    public CustomJournalTests()
    {
        if (!_databaseAvailable)
        {
            return;
        }
        
        _databaseName = $"DbUpTest_{Guid.NewGuid():N}";
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
        if (!_databaseAvailable) return;
        
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
        if (!_databaseAvailable || string.IsNullOrEmpty(_databaseName)) return;
        
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

    private Func<IDbCommand> CreateDbCommandFactory()
    {
        return () =>
        {
            if (string.IsNullOrEmpty(_connectionString))
                throw new InvalidOperationException("Database connection string is not available");
                
            var connection = new SqlConnection(_connectionString);
            connection.Open();
            return connection.CreateCommand();
        };
    }


    [Fact]
    public void EnsureTableExistsAndIsLatestVersion_CreatesTableWithAllColumns()
    {
        if (!_databaseAvailable || string.IsNullOrEmpty(_connectionString) || string.IsNullOrEmpty(_databaseName))
        {
            return;
        }
        
        var loggerFactory = Microsoft.Extensions.Logging.LoggerFactory.Create(builder => builder.AddConsole());
        var upgradeLog = new DbUp.Engine.Output.MicrosoftUpgradeLog(loggerFactory);
        var logger = upgradeLog.AsStlnLogger();
        var journal = new CustomJournal(CreateDbCommandFactory(), "dbo", "CustomJournal", logger);
        var commandFactory = CreateDbCommandFactory();
        
        journal.EnsureTableExistsAndIsLatestVersion(commandFactory);

        using var connection = new SqlConnection(_connectionString);
        connection.Open();
        using var command = connection.CreateCommand();
        command.CommandText = @"
            SELECT COLUMN_NAME, DATA_TYPE 
            FROM INFORMATION_SCHEMA.COLUMNS 
            WHERE TABLE_NAME = 'CustomJournal'
            ORDER BY ORDINAL_POSITION";
        
        using var reader = command.ExecuteReader();
        var columns = new System.Collections.Generic.List<(string Name, string Type)>();
        while (reader.Read())
        {
            columns.Add((reader.GetString(0), reader.GetString(1)));
        }

        Assert.Contains(columns, c => c.Name == "Id" && c.Type.Contains("int"));
        Assert.Contains(columns, c => c.Name == "RunId");
        Assert.Contains(columns, c => c.Name == "ScriptName");
        Assert.Contains(columns, c => c.Name == "AppliedAtUtc");
        Assert.Contains(columns, c => c.Name == "AppliedVersion");
        Assert.Contains(columns, c => c.Name == "PcNumber");
        Assert.Contains(columns, c => c.Name == "AppliedFromHost");
        Assert.Contains(columns, c => c.Name == "AppliedFromIpAddress");
        Assert.Contains(columns, c => c.Name == "AppliedBy");
        Assert.Contains(columns, c => c.Name == "Script");
        Assert.Contains(columns, c => c.Name == "RollbackScript");
        Assert.Contains(columns, c => c.Name == "Md5Script");
        Assert.Contains(columns, c => c.Name == "Md5RollbackScript");
        Assert.Contains(columns, c => c.Name == "ModifiedBy");
        Assert.Contains(columns, c => c.Name == "SysStartTime");
        Assert.Contains(columns, c => c.Name == "SysEndTime");
    }

    [Fact]
    public void EnsureTableExistsAndIsLatestVersion_CreatesTemporalTable()
    {
        if (!_databaseAvailable || string.IsNullOrEmpty(_connectionString) || string.IsNullOrEmpty(_databaseName))
        {
            return;
        }
        
        var loggerFactory = Microsoft.Extensions.Logging.LoggerFactory.Create(builder => builder.AddConsole());
        var upgradeLog = new DbUp.Engine.Output.MicrosoftUpgradeLog(loggerFactory);
        var logger = upgradeLog.AsStlnLogger();
        var journal = new CustomJournal(CreateDbCommandFactory(), "dbo", "CustomJournal", logger);
        var commandFactory = CreateDbCommandFactory();
        
        journal.EnsureTableExistsAndIsLatestVersion(commandFactory);

        using var connection = new SqlConnection(_connectionString);
        connection.Open();
        using var command = connection.CreateCommand();
        command.CommandText = @"
            SELECT temporal_type 
            FROM sys.tables 
            WHERE name = 'CustomJournal'";
        
        var result = command.ExecuteScalar();
        Assert.NotNull(result);
        Assert.Equal(2, Convert.ToInt32(result));
    }

    [Fact]
    public void EnsureTableExistsAndIsLatestVersion_CreatesTrigger()
    {
        if (!_databaseAvailable || string.IsNullOrEmpty(_connectionString) || string.IsNullOrEmpty(_databaseName))
        {
            return;
        }
        
        var loggerFactory = Microsoft.Extensions.Logging.LoggerFactory.Create(builder => builder.AddConsole());
        var upgradeLog = new DbUp.Engine.Output.MicrosoftUpgradeLog(loggerFactory);
        var logger = upgradeLog.AsStlnLogger();
        var journal = new CustomJournal(CreateDbCommandFactory(), "dbo", "CustomJournal", logger);
        var commandFactory = CreateDbCommandFactory();
        
        journal.EnsureTableExistsAndIsLatestVersion(commandFactory);

        using var connection = new SqlConnection(_connectionString);
        connection.Open();
        using var command = connection.CreateCommand();
        command.CommandText = @"
            SELECT COUNT(*) 
            FROM sys.triggers 
            WHERE name = 'TR_CustomJournal_SetAppliedBy'";
        
        var count = Convert.ToInt32(command.ExecuteScalar());
        Assert.Equal(1, count);
    }

    [Fact]
    public void StoreExecutedScript_InsertsRecordWithAllColumns()
    {
        if (!_databaseAvailable || string.IsNullOrEmpty(_connectionString) || string.IsNullOrEmpty(_databaseName))
        {
            return;
        }
        
        var loggerFactory = Microsoft.Extensions.Logging.LoggerFactory.Create(builder => builder.AddConsole());
        var upgradeLog = new DbUp.Engine.Output.MicrosoftUpgradeLog(loggerFactory);
        var logger = upgradeLog.AsStlnLogger();
        var journal = new CustomJournal(CreateDbCommandFactory(), "dbo", "CustomJournal", logger);
        var commandFactory = CreateDbCommandFactory();
        
        journal.EnsureTableExistsAndIsLatestVersion(commandFactory);

        var script = new SqlScript("TestScript.sql", "SELECT 1");
        journal.StoreExecutedScript(script, commandFactory);

        using var connection = new SqlConnection(_connectionString);
        connection.Open();
        using var command = connection.CreateCommand();
        command.CommandText = @"
            SELECT 
                ScriptName, 
                AppliedBy, 
                ModifiedBy,
                Script,
                RollbackScript,
                Md5Script,
                RunId,
                AppliedVersion,
                PcNumber,
                AppliedFromHost,
                AppliedFromIpAddress
            FROM CustomJournal";
        
        using var reader = command.ExecuteReader();
        Assert.True(reader.Read());
        Assert.Equal("TestScript.sql", reader.GetString(0));
        Assert.NotNull(reader.GetValue(1));
        Assert.NotNull(reader.GetValue(2));
        Assert.Equal("SELECT 1", reader.GetString(3));
        Assert.True(reader.IsDBNull(4));
        Assert.NotNull(reader.GetValue(5));
        Assert.NotNull(reader.GetValue(6));
        Assert.NotNull(reader.GetValue(7));
        Assert.NotNull(reader.GetValue(8));
        Assert.NotNull(reader.GetValue(9));
        Assert.True(reader.IsDBNull(10) || !string.IsNullOrEmpty(reader.GetString(10)));
    }

    [Fact]
    public void StoreExecutedScript_WithRollbackScript_StoresRollbackContent()
    {
        if (!_databaseAvailable || string.IsNullOrEmpty(_connectionString) || string.IsNullOrEmpty(_databaseName))
        {
            return;
        }
        
        var loggerFactory = Microsoft.Extensions.Logging.LoggerFactory.Create(builder => builder.AddConsole());
        var upgradeLog = new DbUp.Engine.Output.MicrosoftUpgradeLog(loggerFactory);
        var logger = upgradeLog.AsStlnLogger();
        var journal = new CustomJournal(CreateDbCommandFactory(), "dbo", "CustomJournal", logger);
        var commandFactory = CreateDbCommandFactory();
        
        journal.EnsureTableExistsAndIsLatestVersion(commandFactory);

        var script = new SqlScriptWithRollback("TestScript.sql", "SELECT 1", "DROP TABLE TestTable");
        journal.StoreExecutedScript(script, commandFactory);

        using var connection = new SqlConnection(_connectionString);
        connection.Open();
        using var command = connection.CreateCommand();
        command.CommandText = @"
            SELECT RollbackScript, Md5RollbackScript
            FROM CustomJournal
            WHERE ScriptName = 'TestScript.sql'";
        
        using var reader = command.ExecuteReader();
        Assert.True(reader.Read());
        Assert.Equal("DROP TABLE TestTable", reader.GetString(0));
        Assert.NotNull(reader.GetValue(1));
    }

    [Fact]
    public void StoreExecutedScript_AppliedBySetByTrigger()
    {
        if (!_databaseAvailable || string.IsNullOrEmpty(_connectionString) || string.IsNullOrEmpty(_databaseName))
        {
            return;
        }
        
        var loggerFactory = Microsoft.Extensions.Logging.LoggerFactory.Create(builder => builder.AddConsole());
        var upgradeLog = new DbUp.Engine.Output.MicrosoftUpgradeLog(loggerFactory);
        var logger = upgradeLog.AsStlnLogger();
        var journal = new CustomJournal(CreateDbCommandFactory(), "dbo", "CustomJournal", logger);
        var commandFactory = CreateDbCommandFactory();
        
        journal.EnsureTableExistsAndIsLatestVersion(commandFactory);

        var script = new SqlScript("TestScript.sql", "SELECT 1");
        journal.StoreExecutedScript(script, commandFactory);

        using var connection = new SqlConnection(_connectionString);
        connection.Open();
        using var command = connection.CreateCommand();
        command.CommandText = @"
            SELECT AppliedBy, ModifiedBy
            FROM CustomJournal
            WHERE ScriptName = 'TestScript.sql'";
        
        using var reader = command.ExecuteReader();
        Assert.True(reader.Read());
        var appliedBy = reader.GetString(0);
        var modifiedBy = reader.GetString(1);
        Assert.NotNull(appliedBy);
        Assert.NotNull(modifiedBy);
        Assert.NotEmpty(appliedBy);
        Assert.NotEmpty(modifiedBy);
    }

    [Fact]
    public void GetExecutedScripts_ReturnsStoredScriptNames()
    {
        if (!_databaseAvailable || string.IsNullOrEmpty(_connectionString) || string.IsNullOrEmpty(_databaseName))
        {
            return;
        }
        
        var loggerFactory = Microsoft.Extensions.Logging.LoggerFactory.Create(builder => builder.AddConsole());
        var upgradeLog = new DbUp.Engine.Output.MicrosoftUpgradeLog(loggerFactory);
        var logger = upgradeLog.AsStlnLogger();
        var journal = new CustomJournal(CreateDbCommandFactory(), "dbo", "CustomJournal", logger);
        var commandFactory = CreateDbCommandFactory();
        
        journal.EnsureTableExistsAndIsLatestVersion(commandFactory);

        journal.StoreExecutedScript(new SqlScript("Script1.sql", "SELECT 1"), commandFactory);
        journal.StoreExecutedScript(new SqlScript("Script2.sql", "SELECT 2"), commandFactory);
        journal.StoreExecutedScript(new SqlScript("Script3.sql", "SELECT 3"), commandFactory);

        var executedScripts = journal.GetExecutedScripts().ToList();
        
        Assert.Equal(3, executedScripts.Count);
        Assert.Contains("Script1.sql", executedScripts);
        Assert.Contains("Script2.sql", executedScripts);
        Assert.Contains("Script3.sql", executedScripts);
    }

    [Fact]
    public void Update_ModifiedBySetByTrigger()
    {
        if (!_databaseAvailable || string.IsNullOrEmpty(_connectionString) || string.IsNullOrEmpty(_databaseName))
        {
            return;
        }
        
        var loggerFactory = Microsoft.Extensions.Logging.LoggerFactory.Create(builder => builder.AddConsole());
        var upgradeLog = new DbUp.Engine.Output.MicrosoftUpgradeLog(loggerFactory);
        var logger = upgradeLog.AsStlnLogger();
        var journal = new CustomJournal(CreateDbCommandFactory(), "dbo", "CustomJournal", logger);
        var commandFactory = CreateDbCommandFactory();
        
        journal.EnsureTableExistsAndIsLatestVersion(commandFactory);

        var script = new SqlScript("TestScript.sql", "SELECT 1");
        journal.StoreExecutedScript(script, commandFactory);

        using var connection = new SqlConnection(_connectionString);
        connection.Open();
        
        using var updateCommand = connection.CreateCommand();
        updateCommand.CommandText = @"
            UPDATE CustomJournal 
            SET Script = 'SELECT 2'
            WHERE ScriptName = 'TestScript.sql'";
        updateCommand.ExecuteNonQuery();

        using var selectCommand = connection.CreateCommand();
        selectCommand.CommandText = @"
            SELECT ModifiedBy, AppliedBy
            FROM CustomJournal
            WHERE ScriptName = 'TestScript.sql'";
        
        using var reader = selectCommand.ExecuteReader();
        Assert.True(reader.Read());
        var modifiedBy = reader.GetString(0);
        var appliedBy = reader.GetString(1);
        Assert.NotNull(modifiedBy);
        Assert.NotNull(appliedBy);
    }

    [Fact]
    public void Delete_ModifiedBySetByTrigger()
    {
        if (!_databaseAvailable || string.IsNullOrEmpty(_connectionString) || string.IsNullOrEmpty(_databaseName))
        {
            return;
        }
        
        var loggerFactory = Microsoft.Extensions.Logging.LoggerFactory.Create(builder => builder.AddConsole());
        var upgradeLog = new DbUp.Engine.Output.MicrosoftUpgradeLog(loggerFactory);
        var logger = upgradeLog.AsStlnLogger();
        var journal = new CustomJournal(CreateDbCommandFactory(), "dbo", "CustomJournal", logger);
        var commandFactory = CreateDbCommandFactory();
        
        journal.EnsureTableExistsAndIsLatestVersion(commandFactory);

        var script = new SqlScript("TestScript.sql", "SELECT 1");
        journal.StoreExecutedScript(script, commandFactory);

        using var connection = new SqlConnection(_connectionString);
        connection.Open();
        
        using var deleteCommand = connection.CreateCommand();
        deleteCommand.CommandText = @"
            DELETE FROM CustomJournal 
            WHERE ScriptName = 'TestScript.sql'";
        deleteCommand.ExecuteNonQuery();

        using var historyCommand = connection.CreateCommand();
        historyCommand.CommandText = @"
            SELECT ModifiedBy
            FROM CustomJournalHistory
            WHERE ScriptName = 'TestScript.sql'";
        
        using var reader = historyCommand.ExecuteReader();
        Assert.True(reader.Read());
        var modifiedBy = reader.GetString(0);
        Assert.NotNull(modifiedBy);
        Assert.NotEmpty(modifiedBy);
    }

    [Fact]
    public void TemporalTable_HistoryPreservedOnUpdate()
    {
        if (!_databaseAvailable || string.IsNullOrEmpty(_connectionString) || string.IsNullOrEmpty(_databaseName))
        {
            return;
        }
        
        var loggerFactory = Microsoft.Extensions.Logging.LoggerFactory.Create(builder => builder.AddConsole());
        var logger = new DbUp.Engine.Output.MicrosoftUpgradeLog(loggerFactory) as Stln.DbUp.Extensions.ILogger;
        var journal = new CustomJournal(CreateDbCommandFactory(), "dbo", "CustomJournal", logger);
        var commandFactory = CreateDbCommandFactory();
        
        journal.EnsureTableExistsAndIsLatestVersion(commandFactory);

        var script = new SqlScript("TestScript.sql", "SELECT 1");
        journal.StoreExecutedScript(script, commandFactory);

        System.Threading.Thread.Sleep(100);

        using var connection = new SqlConnection(_connectionString);
        connection.Open();
        
        using var updateCommand = connection.CreateCommand();
        updateCommand.CommandText = @"
            UPDATE CustomJournal 
            SET Script = 'SELECT 2'
            WHERE ScriptName = 'TestScript.sql'";
        updateCommand.ExecuteNonQuery();

        using var historyCommand = connection.CreateCommand();
        historyCommand.CommandText = @"
            SELECT COUNT(*) 
            FROM CustomJournalHistory
            WHERE ScriptName = 'TestScript.sql'";
        
        var historyCount = Convert.ToInt32(historyCommand.ExecuteScalar());
        Assert.True(historyCount > 0);
    }

    public void Dispose()
    {
        DropDatabase();
    }
    }
}


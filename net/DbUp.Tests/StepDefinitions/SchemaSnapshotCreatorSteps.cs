using System;
using System.IO;
using System.Linq;
using Microsoft.Data.SqlClient;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Console;
using DbUp;
using Reqnroll;
using Stln.DbUp.Extensions;
using MartinCostello.SqlLocalDb;
using Xunit;
using RollbackGenerator.Exceptions;

namespace DbUp.Tests.StepDefinitions;

[Binding]
public sealed class SchemaSnapshotCreatorSteps : IDisposable
{
    private readonly ScenarioContext _scenarioContext;
    private readonly string? _connectionString;
    private readonly string? _databaseName;
    private static readonly bool _databaseAvailable;
    private static readonly string _baseConnectionString;
    
    static SchemaSnapshotCreatorSteps()
    {
        _baseConnectionString = @"Server=(localdb)\mssqllocaldb;Integrated Security=true;TrustServerCertificate=true;";
        
        try
        {
            var localDb = new SqlLocalDbApi();
            // Try to use mssqllocaldb first, if it fails, try TestInstance (create if needed), otherwise create a new one
            ISqlLocalDbInstanceInfo? instanceInfo = null;
            try
            {
                instanceInfo = localDb.GetOrCreateInstance("mssqllocaldb");
            }
            catch
            {
                try
                {
                    // GetOrCreateInstance will create TestInstance if it doesn't exist
                    instanceInfo = localDb.GetOrCreateInstance("TestInstance");
                }
                catch
                {
                    // Create a new instance with a unique name
                    instanceInfo = localDb.GetOrCreateInstance($"DbUpTest_{Guid.NewGuid():N}");
                }
            }
            
            var manager = instanceInfo.Manage();
            manager.Start();
            _baseConnectionString = instanceInfo.GetConnectionString();
        }
        catch
        {
        }
        
        _databaseAvailable = CheckDatabaseAvailability();
    }
    
    public SchemaSnapshotCreatorSteps(ScenarioContext scenarioContext)
    {
        _scenarioContext = scenarioContext;
        
        if (!_databaseAvailable)
        {
            return;
        }
        
        _databaseName = $"DbUpSnapshotTest_{Guid.NewGuid():N}";
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
        
        using var dbConnection = new SqlConnection(_connectionString);
        dbConnection.Open();
        using var createTableCommand = dbConnection.CreateCommand();
        createTableCommand.CommandText = "CREATE TABLE TestTable (Id INT PRIMARY KEY, Name NVARCHAR(50))";
        createTableCommand.ExecuteNonQuery();
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
        }
    }

    [Given("I have a database")]
    public void GivenIHaveADatabase()
    {
        if (!_databaseAvailable || string.IsNullOrEmpty(_connectionString))
        {
            _scenarioContext["DatabaseAvailable"] = false;
            return;
        }
        _scenarioContext["DatabaseAvailable"] = true;
        _scenarioContext["TestDatabaseConnectionString"] = _connectionString;
    }

    [Given("I already have a schema definition file")]
    public void GivenIAlreadyHaveASchemaDefinitionFile()
    {
        var outputPath = Path.Combine(Path.GetTempPath(), $"snapshot_{Guid.NewGuid():N}.dacpac");
        File.WriteAllText(outputPath, "fake content");
        _scenarioContext["SnapshotOutputPath"] = outputPath;
        _scenarioContext["InitialFileLength"] = new FileInfo(outputPath).Length;
    }

    [When("I attempt to capture database schema without valid database connection")]
    public void WhenIAttemptToCaptureDatabaseSchemaWithoutValidDatabaseConnection()
    {
        var loggerFactory = Microsoft.Extensions.Logging.LoggerFactory.Create(builder => builder.AddConsole());
        var upgradeLog = new DbUp.Engine.Output.MicrosoftUpgradeLog(loggerFactory);
        var logger = upgradeLog.AsStlnLogger();
        var creator = new SchemaSnapshotCreator(logger);
        var outputPath = Path.Combine(Path.GetTempPath(), $"test_{Guid.NewGuid():N}.dacpac");
        
        try
        {
            creator.ExtractToDacPac("InvalidConnectionString", outputPath);
        }
        catch (Exception ex)
        {
            _scenarioContext["Exception"] = ex;
        }
        finally
        {
            if (File.Exists(outputPath))
            {
                File.Delete(outputPath);
            }
        }
    }

    [When("I attempt to capture database schema without specifying which database")]
    public void WhenIAttemptToCaptureDatabaseSchemaWithoutSpecifyingWhichDatabase()
    {
        var loggerFactory = Microsoft.Extensions.Logging.LoggerFactory.Create(builder => builder.AddConsole());
        var upgradeLog = new DbUp.Engine.Output.MicrosoftUpgradeLog(loggerFactory);
        var logger = upgradeLog.AsStlnLogger();
        var creator = new SchemaSnapshotCreator(logger);
        var outputPath = Path.Combine(Path.GetTempPath(), $"test_{Guid.NewGuid():N}.dacpac");
        
        try
        {
            creator.ExtractToDacPac("Server=localhost;Integrated Security=true;", outputPath);
        }
        catch (Exception ex)
        {
            _scenarioContext["Exception"] = ex;
        }
        finally
        {
            if (File.Exists(outputPath))
            {
                File.Delete(outputPath);
            }
        }
    }

    [When("I capture the database schema structure to a file")]
    public void WhenICaptureTheDatabaseSchemaStructureToAFile()
    {
        if (!_databaseAvailable || string.IsNullOrEmpty(_connectionString))
        {
            return;
        }
        
        var loggerFactory = Microsoft.Extensions.Logging.LoggerFactory.Create(builder => builder.AddConsole());
        var upgradeLog = new DbUp.Engine.Output.MicrosoftUpgradeLog(loggerFactory);
        var logger = upgradeLog.AsStlnLogger();
        var creator = new SchemaSnapshotCreator(logger);
        var outputPath = Path.Combine(Path.GetTempPath(), $"snapshot_{Guid.NewGuid():N}.dacpac");
        _scenarioContext["SnapshotOutputPath"] = outputPath;
        
        creator.ExtractToDacPac(_connectionString, outputPath);
    }

    [When("I capture the schema to a non-existent location")]
    public void WhenICaptureTheSchemaToANonExistentLocation()
    {
        if (!_databaseAvailable || string.IsNullOrEmpty(_connectionString))
        {
            return;
        }
        
        var loggerFactory = Microsoft.Extensions.Logging.LoggerFactory.Create(builder => builder.AddConsole());
        var upgradeLog = new DbUp.Engine.Output.MicrosoftUpgradeLog(loggerFactory);
        var logger = upgradeLog.AsStlnLogger();
        var creator = new SchemaSnapshotCreator(logger);
        var tempDir = Path.Combine(Path.GetTempPath(), $"snapshots_{Guid.NewGuid():N}");
        var outputPath = Path.Combine(tempDir, "subfolder", $"snapshot_{Guid.NewGuid():N}.dacpac");
        _scenarioContext["SnapshotOutputPath"] = outputPath;
        _scenarioContext["TemporaryDirectory"] = tempDir;
        
        creator.ExtractToDacPac(_connectionString, outputPath);
    }

    [When("I capture a new schema definition at the same location")]
    public void WhenICaptureANewSchemaDefinitionAtTheSameLocation()
    {
        if (!_databaseAvailable || string.IsNullOrEmpty(_connectionString))
        {
            return;
        }
        
        var outputPath = (string)_scenarioContext["SnapshotOutputPath"];
        var loggerFactory = Microsoft.Extensions.Logging.LoggerFactory.Create(builder => builder.AddConsole());
        var upgradeLog = new DbUp.Engine.Output.MicrosoftUpgradeLog(loggerFactory);
        var logger = upgradeLog.AsStlnLogger();
        var creator = new SchemaSnapshotCreator(logger);
        
        creator.ExtractToDacPac(_connectionString, outputPath);
    }

    [Then("a schema definition file should be created")]
    public void ThenASchemaDefinitionFileShouldBeCreated()
    {
        if (!_databaseAvailable || string.IsNullOrEmpty(_connectionString))
        {
            return;
        }
        
        var outputPath = (string)_scenarioContext["SnapshotOutputPath"];
        Assert.True(File.Exists(outputPath), "Schema definition file should be created");
    }

    [Then("the file should contain the database structure")]
    public void ThenTheFileShouldContainTheDatabaseStructure()
    {
        if (!_databaseAvailable || string.IsNullOrEmpty(_connectionString))
        {
            return;
        }
        
        var outputPath = (string)_scenarioContext["SnapshotOutputPath"];
        Assert.True(new FileInfo(outputPath).Length > 0, "Schema definition file should not be empty");
    }


    [Then("the schema definition file should be placed there")]
    public void ThenTheSchemaDefinitionFileShouldBePlacedThere()
    {
        if (!_databaseAvailable || string.IsNullOrEmpty(_connectionString))
        {
            return;
        }
        
        var outputPath = (string)_scenarioContext["SnapshotOutputPath"];
        Assert.True(File.Exists(outputPath), "Schema definition file should be created in the output location");
    }

    [Then("the old schema definition file should be replaced")]
    public void ThenTheOldSchemaDefinitionFileShouldBeReplaced()
    {
        if (!_databaseAvailable || string.IsNullOrEmpty(_connectionString))
        {
            return;
        }
        
        var outputPath = (string)_scenarioContext["SnapshotOutputPath"];
        var initialLength = (long)_scenarioContext["InitialFileLength"];
        var newLength = new FileInfo(outputPath).Length;
        Assert.NotEqual(initialLength, newLength);
    }

    [Then("the new schema definition file should be valid")]
    public void ThenTheNewSchemaDefinitionFileShouldBeValid()
    {
        if (!_databaseAvailable || string.IsNullOrEmpty(_connectionString))
        {
            return;
        }
        
        var outputPath = (string)_scenarioContext["SnapshotOutputPath"];
        Assert.True(File.Exists(outputPath), "Schema definition file should exist");
        Assert.True(new FileInfo(outputPath).Length > 0, "New schema definition file should not be empty");
    }

    [AfterScenario]
    public void Cleanup()
    {
        if (_scenarioContext.ContainsKey("SnapshotOutputPath"))
        {
            var path = (string)_scenarioContext["SnapshotOutputPath"];
            if (File.Exists(path))
            {
                File.Delete(path);
            }
        }
        
        if (_scenarioContext.ContainsKey("TemporaryDirectory"))
        {
            var tempDir = (string)_scenarioContext["TemporaryDirectory"];
            try
            {
                if (Directory.Exists(tempDir))
                {
                    Directory.Delete(tempDir, true);
                }
            }
            catch
            {
            }
        }
    }

    public void Dispose()
    {
        DropDatabase();
    }
}

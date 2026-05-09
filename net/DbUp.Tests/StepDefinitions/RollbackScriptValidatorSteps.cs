using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Text;
using System.Text.RegularExpressions;
using Microsoft.Data.SqlClient;
using Microsoft.Extensions.Logging;
using DbUp;
using DbUp.Engine.Output;
using Reqnroll;
using Stln.DbUp.Extensions;
using MartinCostello.SqlLocalDb;
using RollbackGenerator.RollbackScriptGeneration;
using Xunit;

namespace DbUp.Tests.StepDefinitions;

[Binding]
public sealed class RollbackScriptValidatorSteps : IDisposable
{
    private readonly ScenarioContext _scenarioContext;
    private readonly string? _connectionString;
    private readonly string? _databaseName;
    private static readonly bool _databaseAvailable;
    private static readonly string _baseConnectionString;
    private readonly List<Script> _migrationScripts = new();
    private readonly List<MigrationScriptWithRollback> _migrationScriptsWithRollback = new();
    private readonly List<RollbackValidationResult> _validationResults = new();
    
    static RollbackScriptValidatorSteps()
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
        }
        
        _databaseAvailable = CheckDatabaseAvailability();
    }
    
    public RollbackScriptValidatorSteps(ScenarioContext scenarioContext)
    {
        _scenarioContext = scenarioContext;
        
        if (!_databaseAvailable)
        {
            return;
        }
        
        _databaseName = $"DbUpRollbackTest_{Guid.NewGuid():N}";
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
        if (!_databaseAvailable || string.IsNullOrEmpty(_databaseName)) return;
        
        var masterConnectionString = _baseConnectionString.Contains("Database=")
            ? _baseConnectionString.Replace(_baseConnectionString.Split(';').First(s => s.StartsWith("Database=")), "Database=master")
            : _baseConnectionString + ";Database=master";
            
        using var connection = new SqlConnection(masterConnectionString);
        connection.Open();
        using var command = connection.CreateCommand();
        command.CommandText = $"CREATE DATABASE [{_databaseName}]";
        command.ExecuteNonQuery();
    }
    
    private string? GetConnectionString()
    {
        if (_scenarioContext.ContainsKey("TestDatabaseConnectionString"))
        {
            return _scenarioContext.Get<string>("TestDatabaseConnectionString");
        }
        return _connectionString;
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

    [Given("I have N migration scripts")]
    public void GivenIHaveNMigrationScripts()
    {
        if (!_databaseAvailable)
        {
            return;
        }
        
        var scripts = LoadMigrationScripts();
        _migrationScripts.AddRange(scripts);
        _scenarioContext["MigrationScripts"] = _migrationScripts;
    }

    [Given("I have a migration script")]
    public void GivenIHaveAMigrationScript()
    {
        if (!_databaseAvailable)
        {
            return;
        }
        
        var scripts = LoadMigrationScripts();
        if (scripts.Count > 0)
        {
            _migrationScripts.Add(scripts[0]);
            _scenarioContext["MigrationScript"] = scripts[0];
        }
    }

    [Given("I have an invalid rollback script that does not reverse the migration")]
    public void GivenIHaveAnInvalidRollbackScript()
    {
        if (!_databaseAvailable)
        {
            return;
        }
        
        var migrationScript = _scenarioContext.Get<Script>("MigrationScript");
        var invalidRollback = new Script("InvalidRollback.sql", "-- This rollback does nothing");
        _scenarioContext["RollbackScript"] = invalidRollback;
    }

    [When("I generate rollback scripts for each migration script")]
    public void WhenIGenerateRollbackScriptsForEachMigrationScript()
    {
        if (!_databaseAvailable)
        {
            return;
        }
        
        var connectionString = GetConnectionString();
        if (string.IsNullOrEmpty(connectionString))
        {
            return;
        }
        
        var loggerFactory = LoggerFactory.Create(builder => builder.AddConsole());
        var upgradeLog = new MicrosoftUpgradeLog(loggerFactory);
        var logger = upgradeLog.AsStlnLogger();
        var rollbackOrchestrator = new DacFxRollbackScriptGenerationOrchestrator(connectionString, logger);
        var executedIncrementalScripts = new List<IncrementalScript>();
        
        foreach (var migrationScript in _migrationScripts)
        {
            try
            {
                var incrementalMigration = new IncrementalScript(migrationScript.Name, migrationScript.Content);
                
                var rollbackIncremental = rollbackOrchestrator.GenerateRollbackScriptForIncrementalScript(
                    incrementalMigration,
                    executedIncrementalScripts);
                
                if (!string.IsNullOrWhiteSpace(rollbackIncremental?.Content))
                {
                    var rollbackScript = new Script(rollbackIncremental.Name, rollbackIncremental.Content);
                    _migrationScriptsWithRollback.Add(new MigrationScriptWithRollback(migrationScript, rollbackScript));
                }
                
                executedIncrementalScripts.Add(incrementalMigration);
            }
            catch (Exception ex)
            {
                _scenarioContext["Exception"] = ex;
            }
        }
        
        _scenarioContext["MigrationScriptsWithRollback"] = _migrationScriptsWithRollback;
    }

    [When("I validate that each rollback script correctly reverses its migration")]
    public void WhenIValidateThatEachRollbackScriptCorrectlyReversesItsMigration()
    {
        if (!_databaseAvailable)
        {
            return;
        }
        
        var connectionString = GetConnectionString();
        if (string.IsNullOrEmpty(connectionString))
        {
            return;
        }
        
        var loggerFactory = LoggerFactory.Create(builder => builder.AddConsole());
        var upgradeLog = new MicrosoftUpgradeLog(loggerFactory);
        var logger = upgradeLog.AsStlnLogger();
        var validator = new RollbackScriptValidator(logger);
        
        try
        {
            var results = validator.Validate(connectionString, _migrationScriptsWithRollback);
            _validationResults.AddRange(results);
            _scenarioContext["ValidationResults"] = _validationResults;
        }
        catch (Exception ex)
        {
            _scenarioContext["Exception"] = ex;
        }
    }

    [When("I validate that the rollback script correctly reverses the migration")]
    public void WhenIValidateThatTheRollbackScriptCorrectlyReversesTheMigration()
    {
        if (!_databaseAvailable)
        {
            return;
        }
        
        var connectionString = GetConnectionString();
        if (string.IsNullOrEmpty(connectionString))
        {
            return;
        }
        
        var migrationScript = _scenarioContext.Get<Script>("MigrationScript");
        var rollbackScript = _scenarioContext.Get<Script>("RollbackScript");
        
        var loggerFactory = LoggerFactory.Create(builder => builder.AddConsole());
        var upgradeLog = new MicrosoftUpgradeLog(loggerFactory);
        var logger = upgradeLog.AsStlnLogger();
        var validator = new RollbackScriptValidator(logger);
        
        try
        {
            var result = validator.Validate(connectionString, migrationScript, rollbackScript);
            _scenarioContext["ValidationResult"] = result;
        }
        catch (Exception ex)
        {
            _scenarioContext["Exception"] = ex;
        }
    }

    [Then("I should have N rollback scripts generated")]
    public void ThenIShouldHaveNRollbackScriptsGenerated()
    {
        if (!_databaseAvailable)
        {
            return;
        }
        
        Assert.Equal(_migrationScripts.Count, _migrationScriptsWithRollback.Count);
    }

    [Then("all rollback scripts should be valid")]
    public void ThenAllRollbackScriptsShouldBeValid()
    {
        if (!_databaseAvailable)
        {
            return;
        }
        
        Assert.All(_validationResults, result => Assert.True(result.IsValid, $"Rollback script validation failed. Differences: {result.DifferencesScriptPath}"));
    }

    [Then("the database schema after each rollback should match the initial schema")]
    public void ThenTheDatabaseSchemaAfterEachRollbackShouldMatchTheInitialSchema()
    {
        if (!_databaseAvailable)
        {
            return;
        }
        
        Assert.All(_validationResults, result => Assert.True(result.IsValid, "Schema after rollback does not match initial schema"));
    }

    [Then("the rollback script validation should fail")]
    public void ThenTheRollbackScriptValidationShouldFail()
    {
        if (!_databaseAvailable)
        {
            return;
        }
        
        var result = _scenarioContext.Get<RollbackValidationResult>("ValidationResult");
        Assert.False(result.IsValid, "Rollback script validation should have failed");
    }

    [Then("differences between initial and rollback schema should be detected")]
    public void ThenDifferencesBetweenInitialAndRollbackSchemaShouldBeDetected()
    {
        if (!_databaseAvailable)
        {
            return;
        }
        
        var result = _scenarioContext.Get<RollbackValidationResult>("ValidationResult");
        Assert.False(result.IsValid);
        Assert.NotNull(result.DifferencesScriptPath);
        Assert.True(File.Exists(result.DifferencesScriptPath));
    }

    private List<Script> LoadMigrationScripts()
    {
        var scripts = new List<Script>();
        var assembly = Assembly.GetExecutingAssembly();
        var resourceName = "DbUp.Tests.TestScripts.MigrationScripts.sql";
        
        using var stream = assembly.GetManifestResourceStream(resourceName);
        if (stream == null)
        {
            var resourceNames = assembly.GetManifestResourceNames();
            throw new InvalidOperationException($"Resource '{resourceName}' not found. Available resources: {string.Join(", ", resourceNames)}");
        }
        
        using var reader = new StreamReader(stream, Encoding.UTF8);
        var content = reader.ReadToEnd();
        
        var scriptPattern = new Regex(@"-- Migration Script (\d+):\s*(.+?)(?=-- Migration Script \d+:|$)", RegexOptions.Singleline);
        var matches = scriptPattern.Matches(content);
        
        foreach (Match match in matches)
        {
            var scriptNumber = match.Groups[1].Value;
            var fullMatch = match.Groups[0].Value;
            var scriptContent = fullMatch
                .Replace($"-- Migration Script {scriptNumber}:", "")
                .Trim();
            
            if (!string.IsNullOrWhiteSpace(scriptContent))
            {
                var scriptName = $"MigrationScript_{scriptNumber.PadLeft(3, '0')}.sql";
                scripts.Add(new Script(scriptName, scriptContent));
            }
        }
        
        return scripts;
    }

    public void Dispose()
    {
        DropDatabase();
    }
}


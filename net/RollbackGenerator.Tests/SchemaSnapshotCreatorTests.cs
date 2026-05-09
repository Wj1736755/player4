using System;
using System.IO;
using System.Linq;
using Microsoft.Data.SqlClient;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Console;
using DbUp;
using Xunit;
using MartinCostello.SqlLocalDb;
using Stln.DbUp.Extensions;
using RollbackGenerator.Exceptions;

namespace DbUp.Tests
{
    public class SchemaSnapshotCreatorTests : IDisposable
    {
        private readonly string? _connectionString;
        private readonly string? _databaseName;
        private static readonly bool _databaseAvailable;
        private static readonly string _baseConnectionString;
        
        static SchemaSnapshotCreatorTests()
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
        
        public SchemaSnapshotCreatorTests()
        {
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
            
            // Create a simple table to have some schema
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
                // Ignore cleanup errors
            }
        }

        [Fact]
        public void CreateSnapshot_NullConnectionString_ThrowsArgumentException()
        {
            var loggerFactory = Microsoft.Extensions.Logging.LoggerFactory.Create(builder => builder.AddConsole());
            var upgradeLog = new DbUp.Engine.Output.MicrosoftUpgradeLog(loggerFactory);
            var logger = upgradeLog.AsStlnLogger();
            var creator = new SchemaSnapshotCreator(logger);
            
            Assert.Throws<EmptyConnectionStringException>(() => creator.CreateSnapshot(null!, "output.dacpac"));
        }

        [Fact]
        public void CreateSnapshot_EmptyConnectionString_ThrowsArgumentException()
        {
            var loggerFactory = Microsoft.Extensions.Logging.LoggerFactory.Create(builder => builder.AddConsole());
            var upgradeLog = new DbUp.Engine.Output.MicrosoftUpgradeLog(loggerFactory);
            var logger = upgradeLog.AsStlnLogger();
            var creator = new SchemaSnapshotCreator(logger);
            
            Assert.Throws<EmptyConnectionStringException>(() => creator.CreateSnapshot("", "output.dacpac"));
            Assert.Throws<EmptyConnectionStringException>(() => creator.CreateSnapshot("   ", "output.dacpac"));
        }

        [Fact]
        public void CreateSnapshot_NullOutputPath_ThrowsArgumentException()
        {
            var loggerFactory = Microsoft.Extensions.Logging.LoggerFactory.Create(builder => builder.AddConsole());
            var upgradeLog = new DbUp.Engine.Output.MicrosoftUpgradeLog(loggerFactory);
            var logger = upgradeLog.AsStlnLogger();
            var creator = new SchemaSnapshotCreator(logger);
            
            Assert.Throws<EmptyOutputPathException>(() => creator.CreateSnapshot("Server=test;Database=test", null!));
        }

        [Fact]
        public void CreateSnapshot_EmptyOutputPath_ThrowsArgumentException()
        {
            var loggerFactory = Microsoft.Extensions.Logging.LoggerFactory.Create(builder => builder.AddConsole());
            var upgradeLog = new DbUp.Engine.Output.MicrosoftUpgradeLog(loggerFactory);
            var logger = upgradeLog.AsStlnLogger();
            var creator = new SchemaSnapshotCreator(logger);
            
            Assert.Throws<EmptyOutputPathException>(() => creator.CreateSnapshot("Server=test;Database=test", ""));
            Assert.Throws<EmptyOutputPathException>(() => creator.CreateSnapshot("Server=test;Database=test", "   "));
        }

        [Fact]
        public void CreateSnapshot_NullLogger_ThrowsArgumentNullException()
        {
            Assert.Throws<ArgumentNullException>(() => new SchemaSnapshotCreator(null!));
        }

        [Fact]
        public void CreateSnapshot_InvalidConnectionString_ThrowsArgumentException()
        {
            var loggerFactory = Microsoft.Extensions.Logging.LoggerFactory.Create(builder => builder.AddConsole());
            var upgradeLog = new DbUp.Engine.Output.MicrosoftUpgradeLog(loggerFactory);
            var logger = upgradeLog.AsStlnLogger();
            var creator = new SchemaSnapshotCreator(logger);
            var outputPath = Path.Combine(Path.GetTempPath(), $"test_{Guid.NewGuid():N}.dacpac");
            
            try
            {
                Assert.Throws<ArgumentException>(() => 
                    creator.ExtractToDacPac("InvalidConnectionString", outputPath));
            }
            finally
            {
                if (File.Exists(outputPath))
                {
                    File.Delete(outputPath);
                }
            }
        }

        [Fact]
        public void CreateSnapshot_ConnectionStringWithoutDatabase_ThrowsInvalidOperationException()
        {
            var loggerFactory = Microsoft.Extensions.Logging.LoggerFactory.Create(builder => builder.AddConsole());
            var upgradeLog = new DbUp.Engine.Output.MicrosoftUpgradeLog(loggerFactory);
            var logger = upgradeLog.AsStlnLogger();
            var creator = new SchemaSnapshotCreator(logger);
            var outputPath = Path.Combine(Path.GetTempPath(), $"test_{Guid.NewGuid():N}.dacpac");
            
            try
            {
                Assert.Throws<DatabaseNameMissingException>(() => 
                    creator.ExtractToDacPac("Server=localhost;Integrated Security=true;", outputPath));
            }
            finally
            {
                if (File.Exists(outputPath))
                {
                    File.Delete(outputPath);
                }
            }
        }

        [Fact]
        public void CreateSnapshot_CreatesSnapshotFile()
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
            
            try
            {
                creator.ExtractToDacPac(_connectionString, outputPath);
                
                Assert.True(File.Exists(outputPath), "Snapshot file should be created");
                Assert.True(new FileInfo(outputPath).Length > 0, "Snapshot file should not be empty");
            }
            finally
            {
                if (File.Exists(outputPath))
                {
                    File.Delete(outputPath);
                }
            }
        }

        [Fact]
        public void CreateSnapshot_CreatesDirectoryIfNotExists()
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
            
            try
            {
                creator.ExtractToDacPac(_connectionString, outputPath);
                
                Assert.True(Directory.Exists(Path.GetDirectoryName(outputPath)), "Directory should be created");
                Assert.True(File.Exists(outputPath), "Snapshot file should be created");
            }
            finally
            {
                if (File.Exists(outputPath))
                {
                    File.Delete(outputPath);
                }
                
                try
                {
                    if (Directory.Exists(tempDir))
                    {
                        Directory.Delete(tempDir, true);
                    }
                }
                catch
                {
                    // Ignore cleanup errors
                }
            }
        }

        [Fact]
        public void CreateSnapshot_OverwritesExistingFile()
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
            
            try
            {
                // Create initial file
                File.WriteAllText(outputPath, "fake content");
                var initialLength = new FileInfo(outputPath).Length;
                
                // Create snapshot should overwrite
                creator.ExtractToDacPac(_connectionString, outputPath);
                
                Assert.True(File.Exists(outputPath), "Snapshot file should exist");
                var newLength = new FileInfo(outputPath).Length;
                Assert.NotEqual(initialLength, newLength);
                Assert.True(newLength > 0, "New snapshot file should not be empty");
            }
            finally
            {
                if (File.Exists(outputPath))
                {
                    File.Delete(outputPath);
                }
            }
        }

        public void Dispose()
        {
            DropDatabase();
        }
    }
}




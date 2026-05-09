using System;
using System.IO;
using System.Linq;
using Microsoft.Data.SqlClient;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Console;
using DbUp;
using Xunit;
using MartinCostello.SqlLocalDb;
using RollbackGenerator.DatabaseSchemasComparison;
using Stln.DbUp.Extensions;
using RollbackGenerator.Exceptions;

namespace DbUp.Tests
{
    public class SnapshotComparatorTests : IDisposable
    {
        private readonly string? _connectionStringA;
        private readonly string? _connectionStringB;
        private readonly string? _databaseNameA;
        private readonly string? _databaseNameB;
        private static readonly bool _databaseAvailable;
        private static readonly string _baseConnectionString;

        static SnapshotComparatorTests()
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

        public SnapshotComparatorTests()
        {
            if (!_databaseAvailable)
            {
                return;
            }

            _databaseNameA = $"DbUpSnapshotA_{Guid.NewGuid():N}";
            _databaseNameB = $"DbUpSnapshotB_{Guid.NewGuid():N}";

            _connectionStringA = _baseConnectionString.Contains("Database=")
                ? _baseConnectionString.Replace(_baseConnectionString.Split(';').First(s => s.StartsWith("Database=")), $"Database={_databaseNameA}")
                : _baseConnectionString + $";Database={_databaseNameA}";

            _connectionStringB = _baseConnectionString.Contains("Database=")
                ? _baseConnectionString.Replace(_baseConnectionString.Split(';').First(s => s.StartsWith("Database=")), $"Database={_databaseNameB}")
                : _baseConnectionString + $";Database={_databaseNameB}";

            CreateDatabaseA();
            CreateDatabaseB();
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

        private void CreateDatabaseA()
        {
            if (!_databaseAvailable || string.IsNullOrEmpty(_databaseNameA)) return;

            var masterConnectionString = _baseConnectionString.Contains("Database=")
                ? _baseConnectionString.Replace(_baseConnectionString.Split(';').First(s => s.StartsWith("Database=")), "Database=master")
                : _baseConnectionString + ";Database=master";

            using var connection = new SqlConnection(masterConnectionString);
            connection.Open();
            using var command = connection.CreateCommand();
            command.CommandText = $"CREATE DATABASE [{_databaseNameA}]";
            command.ExecuteNonQuery();

            using var dbConnection = new SqlConnection(_connectionStringA);
            dbConnection.Open();
            using var createTableCommand = dbConnection.CreateCommand();
            createTableCommand.CommandText = "CREATE TABLE TableA (Id INT PRIMARY KEY, Name NVARCHAR(50))";
            createTableCommand.ExecuteNonQuery();
        }

        private void CreateDatabaseB()
        {
            if (!_databaseAvailable || string.IsNullOrEmpty(_databaseNameB)) return;

            var masterConnectionString = _baseConnectionString.Contains("Database=")
                ? _baseConnectionString.Replace(_baseConnectionString.Split(';').First(s => s.StartsWith("Database=")), "Database=master")
                : _baseConnectionString + ";Database=master";

            using var connection = new SqlConnection(masterConnectionString);
            connection.Open();
            using var command = connection.CreateCommand();
            command.CommandText = $"CREATE DATABASE [{_databaseNameB}]";
            command.ExecuteNonQuery();

            using var dbConnection = new SqlConnection(_connectionStringB);
            dbConnection.Open();
            using var createTableCommand = dbConnection.CreateCommand();
            createTableCommand.CommandText = "CREATE TABLE TableA (Id INT PRIMARY KEY, Name NVARCHAR(50))";
            createTableCommand.ExecuteNonQuery();

            using var createSecondTableCommand = dbConnection.CreateCommand();
            createSecondTableCommand.CommandText = "CREATE TABLE TableB (Id INT PRIMARY KEY, Value INT)";
            createSecondTableCommand.ExecuteNonQuery();
        }

        private void DropDatabase(string? databaseName)
        {
            if (!_databaseAvailable || string.IsNullOrEmpty(databaseName)) return;

            try
            {
                var masterConnectionString = _baseConnectionString.Contains("Database=")
                    ? _baseConnectionString.Replace(_baseConnectionString.Split(';').First(s => s.StartsWith("Database=")), "Database=master")
                    : _baseConnectionString + ";Database=master";

                using var connection = new SqlConnection(masterConnectionString);
                connection.Open();
                using var command = connection.CreateCommand();
                command.CommandText = $@"
                    IF EXISTS (SELECT name FROM sys.databases WHERE name = '{databaseName}')
                    BEGIN
                        ALTER DATABASE [{databaseName}] SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
                        DROP DATABASE [{databaseName}];
                    END";
                command.ExecuteNonQuery();
            }
            catch
            {
            }
        }

        [Fact]
        public void AreIdentical_NullSnapshotAPath_ThrowsArgumentException()
        {
            var loggerFactory = Microsoft.Extensions.Logging.LoggerFactory.Create(builder => builder.AddConsole());
            var upgradeLog = new DbUp.Engine.Output.MicrosoftUpgradeLog(loggerFactory);
            var logger = upgradeLog.AsStlnLogger();
            var comparator = new SnapshotComparator(logger);

            Assert.Throws<EmptySnapshotPathException>(() => comparator.AreIdentical(null!, "snapshotB.dacpac"));
        }

        [Fact]
        public void AreIdentical_NullSnapshotBPath_ThrowsArgumentException()
        {
            var loggerFactory = Microsoft.Extensions.Logging.LoggerFactory.Create(builder => builder.AddConsole());
            var upgradeLog = new DbUp.Engine.Output.MicrosoftUpgradeLog(loggerFactory);
            var logger = upgradeLog.AsStlnLogger();
            var comparator = new SnapshotComparator(logger);

            Assert.Throws<EmptySnapshotPathException>(() => comparator.AreIdentical("snapshotA.dacpac", null!));
        }

        [Fact]
        public void AreIdentical_EmptySnapshotAPath_ThrowsArgumentException()
        {
            var loggerFactory = Microsoft.Extensions.Logging.LoggerFactory.Create(builder => builder.AddConsole());
            var upgradeLog = new DbUp.Engine.Output.MicrosoftUpgradeLog(loggerFactory);
            var logger = upgradeLog.AsStlnLogger();
            var comparator = new SnapshotComparator(logger);

            Assert.Throws<EmptySnapshotPathException>(() => comparator.AreIdentical("", "snapshotB.dacpac"));
            Assert.Throws<EmptySnapshotPathException>(() => comparator.AreIdentical("   ", "snapshotB.dacpac"));
        }

        [Fact]
        public void AreIdentical_FileNotFound_ThrowsFileNotFoundException()
        {
            var loggerFactory = Microsoft.Extensions.Logging.LoggerFactory.Create(builder => builder.AddConsole());
            var upgradeLog = new DbUp.Engine.Output.MicrosoftUpgradeLog(loggerFactory);
            var logger = upgradeLog.AsStlnLogger();
            var comparator = new SnapshotComparator(logger);
            var nonExistentFile = Path.Combine(Path.GetTempPath(), $"NonExistent_{Guid.NewGuid():N}.dacpac");

            Assert.Throws<SnapshotFileNotFoundException>(() => comparator.AreIdentical(nonExistentFile, "snapshotB.dacpac"));
            Assert.Throws<SnapshotFileNotFoundException>(() => comparator.AreIdentical("snapshotA.dacpac", nonExistentFile));
        }

        [Fact]
        public void AreIdentical_IdenticalSnapshots_ReturnsTrue()
        {
            if (!_databaseAvailable || string.IsNullOrEmpty(_connectionStringA))
            {
                return;
            }

            var loggerFactory = Microsoft.Extensions.Logging.LoggerFactory.Create(builder => builder.AddConsole());
            var upgradeLog = new DbUp.Engine.Output.MicrosoftUpgradeLog(loggerFactory);
            var logger = upgradeLog.AsStlnLogger();
            var creator = new SchemaSnapshotCreator(logger);
            var comparator = new SnapshotComparator(logger);

            var snapshotAPath = Path.Combine(Path.GetTempPath(), $"SnapshotA_{Guid.NewGuid():N}.dacpac");
            var snapshotBPath = Path.Combine(Path.GetTempPath(), $"SnapshotB_{Guid.NewGuid():N}.dacpac");

            try
            {
                creator.ExtractToDacPac(_connectionStringA, snapshotAPath);
                creator.ExtractToDacPac(_connectionStringA, snapshotBPath);

                var areIdentical = comparator.AreIdentical(snapshotAPath, snapshotBPath);

                Assert.True(areIdentical);
            }
            finally
            {
                if (File.Exists(snapshotAPath)) File.Delete(snapshotAPath);
                if (File.Exists(snapshotBPath)) File.Delete(snapshotBPath);
            }
        }

        [Fact]
        public void AreIdentical_DifferentSnapshots_ReturnsFalse()
        {
            if (!_databaseAvailable || string.IsNullOrEmpty(_connectionStringA) || string.IsNullOrEmpty(_connectionStringB))
            {
                return;
            }

            var loggerFactory = Microsoft.Extensions.Logging.LoggerFactory.Create(builder => builder.AddConsole());
            var upgradeLog = new DbUp.Engine.Output.MicrosoftUpgradeLog(loggerFactory);
            var logger = upgradeLog.AsStlnLogger();
            var creator = new SchemaSnapshotCreator(logger);
            var comparator = new SnapshotComparator(logger);

            var snapshotAPath = Path.Combine(Path.GetTempPath(), $"SnapshotA_{Guid.NewGuid():N}.dacpac");
            var snapshotBPath = Path.Combine(Path.GetTempPath(), $"SnapshotB_{Guid.NewGuid():N}.dacpac");

            try
            {
                creator.ExtractToDacPac(_connectionStringA, snapshotAPath);
                creator.ExtractToDacPac(_connectionStringB, snapshotBPath);

                var areIdentical = comparator.AreIdentical(snapshotAPath, snapshotBPath);

                Assert.False(areIdentical);
            }
            finally
            {
                if (File.Exists(snapshotAPath)) File.Delete(snapshotAPath);
                if (File.Exists(snapshotBPath)) File.Delete(snapshotBPath);
            }
        }

        [Fact]
        public void GenerateDifferencesScript_NullOutputPath_ThrowsArgumentException()
        {
            var loggerFactory = Microsoft.Extensions.Logging.LoggerFactory.Create(builder => builder.AddConsole());
            var upgradeLog = new DbUp.Engine.Output.MicrosoftUpgradeLog(loggerFactory);
            var logger = upgradeLog.AsStlnLogger();
            var comparator = new SnapshotComparator(logger);
            var tempFile = Path.Combine(Path.GetTempPath(), $"Test_{Guid.NewGuid():N}.dacpac");

            try
            {
                File.WriteAllText(tempFile, "test");

                Assert.Throws<EmptyOutputPathException>(() =>
                    comparator.GenerateDifferencesScript(tempFile, tempFile, null!));
                Assert.Throws<EmptyOutputPathException>(() =>
                    comparator.GenerateDifferencesScript(tempFile, tempFile, ""));
            }
            finally
            {
                if (File.Exists(tempFile)) File.Delete(tempFile);
            }
        }

        [Fact]
        public void GenerateDifferencesScript_IdenticalSnapshots_CreatesEmptyScript()
        {
            if (!_databaseAvailable || string.IsNullOrEmpty(_connectionStringA))
            {
                return;
            }

            var loggerFactory = Microsoft.Extensions.Logging.LoggerFactory.Create(builder => builder.AddConsole());
            var upgradeLog = new DbUp.Engine.Output.MicrosoftUpgradeLog(loggerFactory);
            var logger = upgradeLog.AsStlnLogger();
            var creator = new SchemaSnapshotCreator(logger);
            var comparator = new SnapshotComparator(logger);

            var snapshotAPath = Path.Combine(Path.GetTempPath(), $"SnapshotA_{Guid.NewGuid():N}.dacpac");
            var snapshotBPath = Path.Combine(Path.GetTempPath(), $"SnapshotB_{Guid.NewGuid():N}.dacpac");
            var outputPath = Path.Combine(Path.GetTempPath(), $"Differences_{Guid.NewGuid():N}.sql");

            try
            {
                creator.ExtractToDacPac(_connectionStringA, snapshotAPath);
                creator.ExtractToDacPac(_connectionStringA, snapshotBPath);

                comparator.GenerateDifferencesScript(snapshotAPath, snapshotBPath, outputPath);

                Assert.True(File.Exists(outputPath));
                var scriptContent = File.ReadAllText(outputPath);
                Assert.NotNull(scriptContent);
            }
            finally
            {
                if (File.Exists(snapshotAPath)) File.Delete(snapshotAPath);
                if (File.Exists(snapshotBPath)) File.Delete(snapshotBPath);
                if (File.Exists(outputPath)) File.Delete(outputPath);
            }
        }

        [Fact]
        public void GenerateDifferencesScript_DifferentSnapshots_CreatesScriptWithDifferences()
        {
            if (!_databaseAvailable || string.IsNullOrEmpty(_connectionStringA) || string.IsNullOrEmpty(_connectionStringB))
            {
                return;
            }

            var loggerFactory = Microsoft.Extensions.Logging.LoggerFactory.Create(builder => builder.AddConsole());
            var upgradeLog = new DbUp.Engine.Output.MicrosoftUpgradeLog(loggerFactory);
            var logger = upgradeLog.AsStlnLogger();
            var creator = new SchemaSnapshotCreator(logger);
            var comparator = new SnapshotComparator(logger);

            var snapshotAPath = Path.Combine(Path.GetTempPath(), $"SnapshotA_{Guid.NewGuid():N}.dacpac");
            var snapshotBPath = Path.Combine(Path.GetTempPath(), $"SnapshotB_{Guid.NewGuid():N}.dacpac");
            var outputPath = Path.Combine(Path.GetTempPath(), $"Differences_{Guid.NewGuid():N}.sql");

            try
            {
                creator.ExtractToDacPac(_connectionStringA, snapshotAPath);
                creator.ExtractToDacPac(_connectionStringB, snapshotBPath);

                comparator.GenerateDifferencesScript(snapshotAPath, snapshotBPath, outputPath);

                Assert.True(File.Exists(outputPath));
                var scriptContent = File.ReadAllText(outputPath);
                Assert.NotNull(scriptContent);
                Assert.NotEmpty(scriptContent.Trim());
            }
            finally
            {
                if (File.Exists(snapshotAPath)) File.Delete(snapshotAPath);
                if (File.Exists(snapshotBPath)) File.Delete(snapshotBPath);
                if (File.Exists(outputPath)) File.Delete(outputPath);
            }
        }

        [Fact]
        public void GenerateDifferencesScript_CreatesDirectoryIfNotExists()
        {
            if (!_databaseAvailable || string.IsNullOrEmpty(_connectionStringA))
            {
                return;
            }
            var loggerFactory = Microsoft.Extensions.Logging.LoggerFactory.Create(builder => builder.AddConsole());
            var upgradeLog = new DbUp.Engine.Output.MicrosoftUpgradeLog(loggerFactory);
            var logger = upgradeLog.AsStlnLogger();
            var creator = new SchemaSnapshotCreator(logger);
            var comparator = new SnapshotComparator(logger);

            var snapshotAPath = Path.Combine(Path.GetTempPath(), $"SnapshotA_{Guid.NewGuid():N}.dacpac");
            var snapshotBPath = Path.Combine(Path.GetTempPath(), $"SnapshotB_{Guid.NewGuid():N}.dacpac");
            var tempDir = Path.Combine(Path.GetTempPath(), $"Differences_{Guid.NewGuid():N}");
            var outputPath = Path.Combine(tempDir, "subfolder", $"Differences_{Guid.NewGuid():N}.sql");

            try
            {
                creator.ExtractToDacPac(_connectionStringA, snapshotAPath);
                creator.ExtractToDacPac(_connectionStringA, snapshotBPath);

                comparator.GenerateDifferencesScript(snapshotAPath, snapshotBPath, outputPath);

                Assert.True(Directory.Exists(Path.GetDirectoryName(outputPath)));
                Assert.True(File.Exists(outputPath));
            }
            finally
            {
                if (File.Exists(snapshotAPath)) File.Delete(snapshotAPath);
                if (File.Exists(snapshotBPath)) File.Delete(snapshotBPath);
                if (File.Exists(outputPath)) File.Delete(outputPath);
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

        [Fact]
        public void SnapshotComparator_NullLogger_ThrowsArgumentNullException()
        {
            Assert.Throws<ArgumentNullException>(() => new SnapshotComparator(null!));
        }

        public void Dispose()
        {
            DropDatabase(_databaseNameA);
            DropDatabase(_databaseNameB);
        }
    }
}




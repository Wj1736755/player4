using System;
using System.Collections.Generic;
using System.Data;
using System.Linq;
using System.Reflection;
using DbUp.Engine;

namespace Stln.DbUp.Extensions;

public sealed class CustomJournal : IJournal
{
    private readonly Func<IDbCommand> _dbCommandFactory;
    private readonly string _schema;
    private readonly string _tableName;
    private readonly Func<string, IEnumerable<string>, Func<string, string?>, string?>? _rollbackGenerator;
    private readonly JournalQueryBuilder _queryBuilder;
    private readonly JournalScriptContentProvider _scriptContentProvider;
    private readonly ILogger _logger;

    public CustomJournal(
        Func<IDbCommand> dbCommandFactory,
        string schema,
        string tableName,
        ILogger logger,
        Func<string, IEnumerable<string>, Func<string, string?>, string?>? rollbackGenerator = null)
    {
        _dbCommandFactory = dbCommandFactory ?? throw new ArgumentNullException(nameof(dbCommandFactory));
        _logger = logger ?? throw new ArgumentNullException(nameof(logger));

        SqlIdentifierQuoter.ThrowIfInvalidIdentifier(schema);
        SqlIdentifierQuoter.ThrowIfInvalidIdentifier(tableName);

        _schema = schema;
        _tableName = tableName;
        _rollbackGenerator = rollbackGenerator;
        _queryBuilder = new JournalQueryBuilder(schema, tableName);
        _scriptContentProvider = new JournalScriptContentProvider(schema, tableName, dbCommandFactory, _logger);
    }

    public void EnsureTableExistsAndIsLatestVersion(Func<IDbCommand> dbCommandFactory)
    {
        using var command = dbCommandFactory();
        var quotedSchema = SqlIdentifierQuoter.Quote(_schema);
        var quotedTableName = SqlIdentifierQuoter.Quote(_tableName);
        var quotedHistoryTableName = SqlIdentifierQuoter.Quote($"{_tableName}History");
        var quotedTriggerName = SqlIdentifierQuoter.Quote($"TR_{_tableName}_SetAppliedBy");

        DropExistingTable(command, quotedSchema, quotedTableName, quotedHistoryTableName);
        CreateJournalTable(command, quotedSchema, quotedTableName, quotedHistoryTableName);
        CreateIndex(command, quotedSchema, quotedTableName);
        CreateAuditTrigger(command, quotedSchema, quotedTableName, quotedTriggerName);
    }

    private static void DropExistingTable(IDbCommand command, string quotedSchema, string quotedTableName, string quotedHistoryTableName)
    {
        command.CommandText = $@"
            IF EXISTS (SELECT * FROM sys.tables WHERE name = {quotedTableName} AND is_memory_optimized = 0)
            BEGIN
                IF EXISTS (SELECT * FROM sys.tables WHERE name = {quotedTableName} AND temporal_type = 2)
                BEGIN
                    ALTER TABLE {quotedSchema}.{quotedTableName} SET (SYSTEM_VERSIONING = OFF);
                END

                IF EXISTS (SELECT * FROM sys.tables WHERE name = {quotedHistoryTableName})
                BEGIN
                    DROP TABLE {quotedSchema}.{quotedHistoryTableName};
                END

                DROP TABLE {quotedSchema}.{quotedTableName};
            END";
        command.ExecuteNonQuery();
    }

    private void CreateJournalTable(IDbCommand command, string quotedSchema, string quotedTableName, string quotedHistoryTableName)
    {
        command.CommandText = $@"
            CREATE TABLE {quotedSchema}.{quotedTableName} (
                [Id] INT IDENTITY(1,1) NOT NULL,
                [RunId] UNIQUEIDENTIFIER NOT NULL,
                [ScriptName] NVARCHAR(255) NOT NULL,
                [AppliedAtUtc] DATETIME2 NOT NULL,
                [AppliedVersion] NVARCHAR(50) NULL,
                [PcNumber] NVARCHAR(255) NULL,
                [AppliedFromHost] NVARCHAR(255) NULL,
                [AppliedFromIpAddress] NVARCHAR(45) NULL,
                [AppliedBy] NVARCHAR(255) NULL,
                [Script] NVARCHAR(MAX) NULL,
                [RollbackScript] NVARCHAR(MAX) NULL,
                [Md5Script] NVARCHAR(32) NULL,
                [Md5RollbackScript] NVARCHAR(32) NULL,
                [ModifiedBy] NVARCHAR(255) NULL,
                [SysStartTime] DATETIME2 GENERATED ALWAYS AS ROW START NOT NULL,
                [SysEndTime] DATETIME2 GENERATED ALWAYS AS ROW END NOT NULL,
                PERIOD FOR SYSTEM_TIME ([SysStartTime], [SysEndTime]),
                CONSTRAINT {SqlIdentifierQuoter.Quote($"PK_{_tableName}")} PRIMARY KEY CLUSTERED ([Id] ASC)
            )
            WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = {quotedSchema}.{quotedHistoryTableName}))";
        command.ExecuteNonQuery();
    }

    private void CreateIndex(IDbCommand command, string quotedSchema, string quotedTableName)
    {
        command.CommandText = $@"
            CREATE NONCLUSTERED INDEX {SqlIdentifierQuoter.Quote($"IX_{_tableName}_ScriptName")}
            ON {quotedSchema}.{quotedTableName} ([ScriptName] ASC)";
        command.ExecuteNonQuery();
    }

    private void CreateAuditTrigger(IDbCommand command, string quotedSchema, string quotedTableName, string quotedTriggerName)
    {
        command.CommandText = $@"
            IF EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID({SqlIdentifierQuoter.Quote($"{_schema}.TR_{_tableName}_SetAppliedBy", true)}) AND type = 'TR')
            BEGIN
                DROP TRIGGER {quotedSchema}.{quotedTriggerName};
            END";
        command.ExecuteNonQuery();

        command.CommandText = $@"
            CREATE TRIGGER {quotedSchema}.{quotedTriggerName}
            ON {quotedSchema}.{quotedTableName}
            INSTEAD OF INSERT, UPDATE, DELETE
            AS
            BEGIN
                SET NOCOUNT ON;

                DECLARE @CurrentUser NVARCHAR(255) = SUSER_SNAME();
                DECLARE @ClientIpAddress NVARCHAR(45) = CAST(CONNECTIONPROPERTY('client_net_address') AS NVARCHAR(45));

                IF EXISTS (SELECT * FROM inserted) AND EXISTS (SELECT * FROM deleted)
                BEGIN
                    UPDATE cj
                    SET
                        [RunId] = i.[RunId],
                        [ScriptName] = i.[ScriptName],
                        [AppliedAtUtc] = i.[AppliedAtUtc],
                        [AppliedVersion] = i.[AppliedVersion],
                        [PcNumber] = i.[PcNumber],
                        [AppliedFromHost] = i.[AppliedFromHost],
                        [AppliedFromIpAddress] = @ClientIpAddress,
                        [Script] = i.[Script],
                        [RollbackScript] = i.[RollbackScript],
                        [Md5Script] = i.[Md5Script],
                        [Md5RollbackScript] = i.[Md5RollbackScript],
                        [ModifiedBy] = @CurrentUser
                    FROM {quotedSchema}.{quotedTableName} cj
                    INNER JOIN inserted i ON cj.[Id] = i.[Id];
                END
                ELSE IF EXISTS (SELECT * FROM inserted)
                BEGIN
                    INSERT INTO {quotedSchema}.{quotedTableName} ([RunId], [ScriptName], [AppliedAtUtc], [AppliedVersion], [PcNumber], [AppliedFromHost], [AppliedFromIpAddress], [AppliedBy], [Script], [RollbackScript], [Md5Script], [Md5RollbackScript], [ModifiedBy])
                    SELECT [RunId], [ScriptName], [AppliedAtUtc], [AppliedVersion], [PcNumber], [AppliedFromHost], @ClientIpAddress, @CurrentUser, [Script], [RollbackScript], [Md5Script], [Md5RollbackScript], @CurrentUser
                    FROM inserted;
                END
                ELSE IF EXISTS (SELECT * FROM deleted)
                BEGIN
                    UPDATE cj
                    SET [ModifiedBy] = @CurrentUser
                    FROM {quotedSchema}.{quotedTableName} cj
                    INNER JOIN deleted d ON cj.[Id] = d.[Id];

                    DELETE FROM {quotedSchema}.{quotedTableName}
                    WHERE [Id] IN (SELECT [Id] FROM deleted);
                END
            END";
        command.ExecuteNonQuery();
    }

    public string[] GetExecutedScripts()
    {
        var executedScripts = new List<string>();

        using var command = _dbCommandFactory();
        command.CommandText = _queryBuilder.BuildSelectExecutedScriptsQuery();
        command.CommandTimeout = DatabaseDefaults.DefaultCommandTimeoutSeconds;

        using var reader = command.ExecuteReader();
        while (reader.Read())
        {
            executedScripts.Add(reader.GetString(0));
        }

        return executedScripts.ToArray();
    }

    public void StoreExecutedScript(SqlScript script, Func<IDbCommand> dbCommandFactory)
    {
        var rollbackContent = TryGenerateRollback(script);
        var parameters = CreateInsertParameters(script, rollbackContent);
        InsertIntoJournal(dbCommandFactory, parameters);
    }

    private string? TryGenerateRollback(SqlScript script)
    {
        if (script is SqlScriptWithRollback scriptWithRollback)
            return scriptWithRollback.RollbackContents;

        if (_rollbackGenerator == null)
            return null;

        try
        {
            var executedScripts = GetExecutedScripts();
            return _rollbackGenerator(script.Name, executedScripts, CreateScriptContentProvider(script));
        }
        catch (Exception ex)
        {
            _logger.LogWarning($"Rollback generation failed for script '{script.Name}'", ex);
            return null;
        }
    }

    private Func<string, string?> CreateScriptContentProvider(SqlScript script)
    {
        return scriptName =>
        {
            var storedScript = _scriptContentProvider.GetScriptContent(scriptName);
            if (!string.IsNullOrEmpty(storedScript))
                return storedScript;

            return _scriptContentProvider.GetScriptContentFromCurrentScript(scriptName, script);
        };
    }

    private Dictionary<string, object> CreateInsertParameters(SqlScript script, string? rollbackContent)
    {
        var md5RollbackScript = HashCalculator.ComputeMd5Hash(rollbackContent);

        return new Dictionary<string, object>
        {
            { "@RunId", Guid.NewGuid() },
            { "@ScriptName", script.Name },
            { "@AppliedAtUtc", DateTime.UtcNow },
            { "@AppliedVersion", Assembly.GetExecutingAssembly().GetName().Version?.ToString() ?? (object)DBNull.Value },
            { "@PcNumber", Environment.GetEnvironmentVariable("PC_NUMBER") ?? Environment.MachineName ?? (object)DBNull.Value },
            { "@AppliedFromHost", Environment.MachineName ?? (object)DBNull.Value },
            { "@AppliedBy", Environment.UserName ?? (object)DBNull.Value },
            { "@Script", script.Contents ?? (object)DBNull.Value },
            { "@RollbackScript", rollbackContent ?? (object)DBNull.Value },
            { "@Md5Script", HashCalculator.ComputeMd5Hash(script.Contents) ?? (object)DBNull.Value },
            { "@Md5RollbackScript", md5RollbackScript ?? (object)DBNull.Value }
        };
    }

    private void InsertIntoJournal(Func<IDbCommand> dbCommandFactory, Dictionary<string, object> parameters)
    {
        using var command = dbCommandFactory();
        command.CommandText = _queryBuilder.BuildInsertScriptQuery();
        command.CommandTimeout = DatabaseDefaults.DefaultCommandTimeoutSeconds;

        foreach (var parameter in parameters)
        {
            var dbParameter = command.CreateParameter();
            dbParameter.ParameterName = parameter.Key;
            dbParameter.Value = parameter.Value;
            command.Parameters.Add(dbParameter);
        }

        command.ExecuteNonQuery();
    }
}

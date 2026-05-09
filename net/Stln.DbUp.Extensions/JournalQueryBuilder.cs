using System;

namespace Stln.DbUp.Extensions
{
    public sealed class JournalQueryBuilder
{
    private readonly string _schema;
    private readonly string _tableName;
    
    public JournalQueryBuilder(string schema, string tableName)
    {
        _schema = schema ?? throw new ArgumentNullException(nameof(schema));
        _tableName = tableName ?? throw new ArgumentNullException(nameof(tableName));
    }
    
    public string BuildSelectExecutedScriptsQuery()
    {
        var quotedSchema = SqlIdentifierQuoter.Quote(_schema);
        var quotedTableName = SqlIdentifierQuoter.Quote(_tableName);
        return $"SELECT [ScriptName] FROM {quotedSchema}.{quotedTableName} ORDER BY [Id]";
    }
    
    public string BuildSelectScriptContentQuery()
    {
        var quotedSchema = SqlIdentifierQuoter.Quote(_schema);
        var quotedTableName = SqlIdentifierQuoter.Quote(_tableName);
        return $"SELECT [Script] FROM {quotedSchema}.{quotedTableName} WHERE [ScriptName] = @ScriptName";
    }
    
    public string BuildInsertScriptQuery()
    {
        var quotedSchema = SqlIdentifierQuoter.Quote(_schema);
        var quotedTableName = SqlIdentifierQuoter.Quote(_tableName);
        return $@"
            INSERT INTO {quotedSchema}.{quotedTableName} ([RunId], [ScriptName], [AppliedAtUtc], [AppliedVersion], [PcNumber], [AppliedFromHost], [AppliedBy], [Script], [RollbackScript], [Md5Script], [Md5RollbackScript])
            VALUES (@RunId, @ScriptName, @AppliedAtUtc, @AppliedVersion, @PcNumber, @AppliedFromHost, @AppliedBy, @Script, @RollbackScript, @Md5Script, @Md5RollbackScript)";
    }
    }
}


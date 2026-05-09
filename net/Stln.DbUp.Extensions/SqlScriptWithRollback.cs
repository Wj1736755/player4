using DbUp.Engine;

namespace Stln.DbUp.Extensions;

public sealed class SqlScriptWithRollback : SqlScript
{
    public string RollbackContents { get; }

    public SqlScriptWithRollback(string name, string contents, string rollbackContents) 
        : base(name, contents)
    {
        RollbackContents = rollbackContents;
    }
}



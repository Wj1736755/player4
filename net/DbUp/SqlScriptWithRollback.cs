using DbUp.Engine;

namespace DbUp
{
    public class SqlScriptWithRollback : SqlScript
    {
        public string RollbackContents { get; }

        public SqlScriptWithRollback(string name, string contents, string rollbackContents) 
            : base(name, contents)
        {
            RollbackContents = rollbackContents;
        }
    }
}


namespace DbUp.RollbackGeneration
{
    public interface IScriptContentProvider
    {
        string? GetScriptContent(string scriptName);
    }
}


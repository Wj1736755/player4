using System;
using System.Collections.Generic;

namespace DbUp.RollbackGeneration
{
    public interface IRollbackOrchestrator
    {
        string? GenerateRollbackScript(
            string migrationScriptName,
            IEnumerable<string> previousScriptNames,
            Func<string, string?> getScriptContent);
    }
}


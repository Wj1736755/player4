using System.Collections.Generic;

namespace RollbackGenerator.RollbackScriptGeneration
{
    public interface IRollbackScriptGenerationOrchestrator
    {
        IncrementalScript GenerateRollbackScriptForIncrementalScript(
            IncrementalScript migrationScript,
            IEnumerable<IncrementalScript> previousScripts);
    }
}

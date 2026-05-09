using System;

namespace RollbackGenerator.Exceptions
{
    public sealed class NoSchemaChangesException(string scriptName) : InvalidOperationException(
        $"Migration script '{scriptName}' does not introduce any schema structure changes. Only schema changes (CREATE/ALTER/DROP) are supported, not data-only changes (INSERT/UPDATE/DELETE).");
}


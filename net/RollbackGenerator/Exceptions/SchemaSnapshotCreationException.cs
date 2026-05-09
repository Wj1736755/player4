using System;

namespace RollbackGenerator.Exceptions
{
    public sealed class SchemaSnapshotCreationException : InvalidOperationException
    {
        public SchemaSnapshotCreationException(string message)
            : base(message)
        {
        }
        
        public SchemaSnapshotCreationException(string message, Exception innerException)
            : base(message, innerException)
        {
        }
    }
}


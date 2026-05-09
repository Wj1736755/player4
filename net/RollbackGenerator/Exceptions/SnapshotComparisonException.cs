using System;

namespace RollbackGenerator.Exceptions
{
    public sealed class SnapshotComparisonException : InvalidOperationException
    {
        public SnapshotComparisonException(string message)
            : base(message)
        {
        }
        
        public SnapshotComparisonException(string message, Exception innerException)
            : base(message, innerException)
        {
        }
    }
}


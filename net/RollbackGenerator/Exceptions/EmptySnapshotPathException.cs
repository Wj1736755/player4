using System;

namespace RollbackGenerator.Exceptions
{
    public sealed class EmptySnapshotPathException : ArgumentException
    {
        public EmptySnapshotPathException()
            : base("Snapshot path cannot be null or empty")
        {
        }
        
        public EmptySnapshotPathException(string paramName)
            : base("Snapshot path cannot be null or empty", paramName)
        {
        }
        
        public EmptySnapshotPathException(string message, string paramName)
            : base(message, paramName)
        {
        }
    }
}


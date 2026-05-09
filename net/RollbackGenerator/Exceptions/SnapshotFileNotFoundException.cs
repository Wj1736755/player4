using System;
using System.IO;

namespace RollbackGenerator.Exceptions
{
    public sealed class SnapshotFileNotFoundException : FileNotFoundException
    {
        public SnapshotFileNotFoundException(string snapshotPath)
            : base($"Snapshot file not found: {snapshotPath}", snapshotPath)
        {
        }
        
        public SnapshotFileNotFoundException(string message, string snapshotPath)
            : base(message, snapshotPath)
        {
        }
    }
}


using System;

namespace RollbackGenerator.Exceptions
{
    public sealed class DatabaseNameMissingException : InvalidOperationException
    {
        public DatabaseNameMissingException()
            : base("Database name cannot be empty or null in connection string")
        {
        }
        
        public DatabaseNameMissingException(string connectionString)
            : base($"Database name cannot be empty or null in connection string: {connectionString}")
        {
        }
    }
}




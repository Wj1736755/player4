using System;

namespace RollbackGenerator.Exceptions
{
    public sealed class EmptyConnectionStringException : ArgumentException
    {
        public EmptyConnectionStringException()
            : base("Connection string cannot be null or empty")
        {
        }
        
        public EmptyConnectionStringException(string paramName)
            : base("Connection string cannot be null or empty", paramName)
        {
        }
    }
}


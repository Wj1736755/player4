using System;

namespace RollbackGenerator.Exceptions
{
    public sealed class EmptyOutputPathException : ArgumentException
    {
        public EmptyOutputPathException(string paramName)
            : base("Output path cannot be null or empty", paramName)
        {
        }
        
        public EmptyOutputPathException(string message, string paramName)
            : base(message, paramName)
        {
        }
    }
}


using System;

namespace DbUp.Exceptions
{
    public sealed class RollbackValidationException : InvalidOperationException
{
    public RollbackValidationException(string message)
        : base(message)
    {
    }
    
    public RollbackValidationException(string message, Exception innerException)
        : base(message, innerException)
    {
    }
    }
}


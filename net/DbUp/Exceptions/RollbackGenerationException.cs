using System;

namespace DbUp.Exceptions
{
    public sealed class RollbackGenerationException : InvalidOperationException
{
    public RollbackGenerationException(string message)
        : base(message)
    {
    }
    
    public RollbackGenerationException(string message, Exception innerException)
        : base(message, innerException)
    {
    }
    }
}







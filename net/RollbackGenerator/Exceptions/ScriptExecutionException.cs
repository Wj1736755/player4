using System;

namespace RollbackGenerator.Exceptions
{
    public sealed class ScriptExecutionException : InvalidOperationException
    {
        public ScriptExecutionException(string message)
            : base(message)
        {
        }
        
        public ScriptExecutionException(string message, Exception innerException)
            : base(message, innerException)
        {
        }
    }
}




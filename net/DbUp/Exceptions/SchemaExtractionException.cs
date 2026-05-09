using System;

namespace DbUp.Exceptions
{
    public sealed class SchemaExtractionException : InvalidOperationException
{
    public SchemaExtractionException(string message)
        : base(message)
    {
    }
    
    public SchemaExtractionException(string message, Exception innerException)
        : base(message, innerException)
    {
    }
    }
}







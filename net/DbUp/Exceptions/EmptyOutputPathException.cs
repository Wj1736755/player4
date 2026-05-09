using System;

namespace DbUp.Exceptions
{
    public sealed class EmptyOutputPathException : ArgumentException
{
    public EmptyOutputPathException()
        : base("Output path cannot be null or empty")
    {
    }
    
    public EmptyOutputPathException(string paramName)
        : base("Output path cannot be null or empty", paramName)
    {
    }
    }
}







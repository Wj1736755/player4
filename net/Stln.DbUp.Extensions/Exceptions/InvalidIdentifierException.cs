using System;

namespace Stln.DbUp.Extensions.Exceptions
{
    public sealed class InvalidIdentifierException : ArgumentException
    {
        private const string NullOrEmptyMessage = "Identifier cannot be empty";

        public InvalidIdentifierException(string identifier)
            : base($"Invalid identifier format: {identifier}", nameof(identifier))
        {
        }

        private InvalidIdentifierException(string message, string paramName)
            : base(message, paramName)
        {
        }

        public static InvalidIdentifierException NullOrEmpty(string identifier)
        {
            return new InvalidIdentifierException(NullOrEmptyMessage, nameof(identifier));
        }
    }
}







using System.Text.RegularExpressions;
using Stln.DbUp.Extensions.Exceptions;

namespace Stln.DbUp.Extensions;

public static class SqlIdentifierQuoter
{
    private static readonly Regex ValidIdentifierPattern = new(@"^[\w@#$]+$", RegexOptions.Compiled);

    public static string Quote(string identifier, bool quoteString = false)
    {
        if (string.IsNullOrWhiteSpace(identifier))
            throw InvalidIdentifierException.NullOrEmpty(identifier);

        if (!ValidIdentifierPattern.IsMatch(identifier))
            throw new InvalidIdentifierException(identifier);

        var quoted = $"[{identifier.Replace("]", "]]")}]";
        return quoteString ? $"N'{quoted}'" : quoted;
    }

    public static void ThrowIfInvalidIdentifier(string identifier)
    {
        if (string.IsNullOrWhiteSpace(identifier))
            throw InvalidIdentifierException.NullOrEmpty(identifier);

        if (!ValidIdentifierPattern.IsMatch(identifier))
            throw new InvalidIdentifierException(identifier);
    }
}


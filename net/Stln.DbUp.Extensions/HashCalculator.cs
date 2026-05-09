using System;
using System.Security.Cryptography;
using System.Text;

namespace Stln.DbUp.Extensions
{
    public static class HashCalculator
{
    public static string ComputeMd5Hash(string? input)
    {
        if (string.IsNullOrEmpty(input))
            return "MD5:<empty>";

        using var md5 = MD5.Create();
        var hashBytes = md5.ComputeHash(Encoding.UTF8.GetBytes(input));
        return "MD5:"+BitConverter.ToString(hashBytes).Replace("-", "").ToLowerInvariant();
    }
    }
}


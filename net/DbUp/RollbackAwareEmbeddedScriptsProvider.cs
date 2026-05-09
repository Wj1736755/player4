using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Text;
using DbUp.Engine;
using DbUp.Engine.Transactions;
using Stln.DbUp.Extensions;

namespace DbUp
{
    public sealed class RollbackAwareEmbeddedScriptsProvider : IScriptProvider
{
    private readonly Assembly _assembly;
    private readonly Func<string, bool> _filter;

    public RollbackAwareEmbeddedScriptsProvider(Assembly assembly, Func<string, bool>? filter = null)
    {
        _assembly = assembly ?? throw new ArgumentNullException(nameof(assembly));
        _filter = filter ?? (s => true);
    }

    public IEnumerable<SqlScript> GetScripts(IConnectionManager connectionManager)
    {
        var resourceNames = _assembly.GetManifestResourceNames()
            .Where(name => name.EndsWith(".sql", StringComparison.OrdinalIgnoreCase) && _filter(name))
            .ToList();

        var rollbackScripts = LoadRollbackScripts(_assembly, resourceNames);
        var scripts = new List<SqlScript>();

        foreach (var resourceName in resourceNames)
        {
            if (resourceName.EndsWith(".rollback.sql", StringComparison.OrdinalIgnoreCase))
                continue;

            using var stream = _assembly.GetManifestResourceStream(resourceName);
            if (stream == null) continue;

            using var reader = new StreamReader(stream, Encoding.UTF8);
            var contents = reader.ReadToEnd();

            var baseName = ExtractBaseScriptName(resourceName);
            var rollbackContents = rollbackScripts.TryGetValue(baseName, out var content) ? content : string.Empty;

            scripts.Add(new SqlScriptWithRollback(resourceName, contents, rollbackContents));
        }

        return scripts.OrderBy(s => s.Name);
    }

    private static Dictionary<string, string> LoadRollbackScripts(Assembly assembly, List<string> allResourceNames)
    {
        var rollbackScripts = new Dictionary<string, string>();
        var rollbackResourceNames = allResourceNames
            .Where(name => name.EndsWith(".rollback.sql", StringComparison.OrdinalIgnoreCase))
            .ToList();

        foreach (var resourceName in rollbackResourceNames)
        {
            using var stream = assembly.GetManifestResourceStream(resourceName);
            if (stream == null) continue;

            using var reader = new StreamReader(stream, Encoding.UTF8);
            var rollbackContent = reader.ReadToEnd();

            var baseName = ExtractBaseScriptName(resourceName);
            if (!string.IsNullOrEmpty(baseName))
            {
                rollbackScripts[baseName] = rollbackContent;
            }
        }

        return rollbackScripts;
    }

    private static string ExtractBaseScriptName(string fullResourceName)
    {
        var parts = fullResourceName.Split('.');
        var rollbackIndex = Array.FindIndex(parts, p => p.Equals("rollback", StringComparison.OrdinalIgnoreCase));
        
        if (rollbackIndex > 0)
        {
            var baseParts = new string[rollbackIndex];
            Array.Copy(parts, 0, baseParts, 0, rollbackIndex);
            return string.Join(".", baseParts);
        }

        if (fullResourceName.EndsWith(".rollback.sql", StringComparison.OrdinalIgnoreCase))
        {
            return fullResourceName.Substring(0, fullResourceName.Length - 14);
        }

        if (fullResourceName.EndsWith(".sql", StringComparison.OrdinalIgnoreCase))
        {
            return fullResourceName.Substring(0, fullResourceName.Length - 4);
        }

        return fullResourceName;
    }
    }
}


using System;
using System.IO;
using System.Reflection;
using System.Text;

namespace DbUp.RollbackGeneration
{
    public sealed class ScriptContentProvider : IScriptContentProvider
{
    private readonly Assembly _assembly;
    private readonly Func<string, string?>? _additionalSource;
    
    public ScriptContentProvider(Assembly assembly, Func<string, string?>? additionalSource = null)
    {
        _assembly = assembly ?? throw new ArgumentNullException(nameof(assembly));
        _additionalSource = additionalSource;
    }
    
    public string? GetScriptContent(string scriptName)
    {
        if (_additionalSource != null)
        {
            var content = _additionalSource(scriptName);
            if (!string.IsNullOrEmpty(content))
                return content;
        }
        
        var resourceName = FindResourceName(scriptName);
        if (string.IsNullOrEmpty(resourceName))
            return null;
            
        using var stream = _assembly.GetManifestResourceStream(resourceName);
        if (stream == null)
            return null;
            
        using var reader = new StreamReader(stream, Encoding.UTF8);
        return reader.ReadToEnd();
    }
    
    private string? FindResourceName(string scriptName)
    {
        var allResources = _assembly.GetManifestResourceNames();
        
        var exactMatch = Array.Find(allResources, r => r.Equals(scriptName, StringComparison.OrdinalIgnoreCase) || r.EndsWith(scriptName, StringComparison.OrdinalIgnoreCase));
        if (!string.IsNullOrEmpty(exactMatch))
            return exactMatch;
            
        var baseName = Path.GetFileNameWithoutExtension(scriptName);
        var match = Array.Find(allResources, r => 
            r.EndsWith($".{scriptName}", StringComparison.OrdinalIgnoreCase) ||
            r.EndsWith($"{baseName}.sql", StringComparison.OrdinalIgnoreCase));
            
        return match;
    }
    }
}


using System;

namespace RollbackGenerator.RollbackScriptGeneration
{
    public sealed record IncrementalScript
    {
        public string Name { get; }
        public string Content { get; }

        public IncrementalScript(string name, string content)
        {
            Name = name ?? throw new ArgumentNullException(nameof(name));
            Content = content ?? throw new ArgumentNullException(nameof(content));
            
            if (string.IsNullOrWhiteSpace(name))
                throw new ArgumentException("Script name cannot be null or whitespace", nameof(name));
            
            if (string.IsNullOrWhiteSpace(content))
                throw new ArgumentException("Script content cannot be null or whitespace", nameof(content));
        }
    }
}

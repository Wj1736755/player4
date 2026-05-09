using System.IO;

namespace DbUp
{
    public sealed class TempPathProvider : ITempPathProvider
    {
        public string GetTempPath() => Path.GetTempPath();
        
        public string Combine(string path1, string path2) => Path.Combine(path1, path2);
    }
}


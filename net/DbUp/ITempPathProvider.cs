namespace DbUp
{
    public interface ITempPathProvider
    {
        string GetTempPath();
        string Combine(string path1, string path2);
    }
}

